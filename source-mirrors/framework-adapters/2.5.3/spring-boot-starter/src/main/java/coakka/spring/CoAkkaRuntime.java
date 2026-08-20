package coakka.spring;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeOverloadPolicySpec;
import coakka.v2.connector.RuntimeRouteFlags;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeTcpConnectionStrategySpec;
import coakka.v2.connector.RuntimeTcpSecurityMode;
import coakka.v2.connector.RuntimeTcpSecuritySpec;
import coakka.v2.connector.RuntimeTlsReloadMode;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.control.RouteResolutionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Process-owned CoAkka runtime managed by the Spring application context.
 *
 * <p>The starter creates one instance, registers discovered local handlers,
 * and closes it during context shutdown. Runtime queues, threads, routes, and
 * native resources remain owned by the underlying connector orchestrator.</p>
 */
public final class CoAkkaRuntime implements AutoCloseable {
    private final CoAkkaRuntimeProperties properties;
    private final ConnectorOrchestrator orchestrator;

    private CoAkkaRuntime(CoAkkaRuntimeProperties properties, ConnectorOrchestrator orchestrator) {
        this.properties = properties;
        this.orchestrator = orchestrator;
    }

    /**
     * Returns the configuration properties used to start this instance.
     *
     * @return bound startup properties
     */
    public CoAkkaRuntimeProperties getProperties() {
        return properties;
    }

    /**
     * Returns the underlying connector for advanced runtime operations.
     *
     * @return live process-owned orchestrator
     */
    public ConnectorOrchestrator getOrchestrator() {
        return orchestrator;
    }

    /**
     * Reads immutable native runtime build metadata.
     *
     * @return runtime information snapshot
     */
    public RuntimeInfoSnapshot runtimeInfo() {
        return orchestrator.runtimeInfo();
    }

    /**
     * Reads effective state for this runtime instance.
     *
     * @return runtime configuration snapshot
     */
    public RuntimeConfigSnapshot runtimeConfig() {
        return orchestrator.runtimeConfig();
    }

    /**
     * Reads request/reply client counters.
     *
     * @return client statistics snapshot
     */
    public RuntimeClientStats clientStats() {
        return orchestrator.clientStats();
    }

    /**
     * Requests runtime shutdown and waits at most three seconds.
     *
     * @throws IllegalStateException when shutdown fails or does not complete in time
     */
    @Override
    public void close() {
        try {
            orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while shutting down CoAkka runtime", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("Failed to shut down CoAkka runtime within three seconds", error);
        }
    }

    /**
     * Starts one local-first runtime and registers all discovered handlers.
     *
     * @param properties validated runtime configuration
     * @param objectMapper application JSON mapper
     * @param beanFactory Spring bean source used for handler discovery
     * @return started runtime owned by the application context
     * @throws IllegalArgumentException when the selected mode or transport configuration is unsupported
     */
    public static CoAkkaRuntime start(
        CoAkkaRuntimeProperties properties,
        ObjectMapper objectMapper,
        ListableBeanFactory beanFactory
    ) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("coakka.runtime.enabled=false is not supported by this prototype starter");
        }
        if (!"local".equals(properties.getMode())) {
            throw new IllegalArgumentException("prototype starter only supports coakka.runtime.mode=local");
        }

        List<DiscoveredHandler> handlers = discoverHandlers(beanFactory);
        List<RuntimeRouteSpec> routes = new ArrayList<>();
        for (DiscoveredHandler handler : handlers) {
            routes.add(new RuntimeRouteSpec(
                handler.target(),
                List.of(new RuntimeEndpointSpec(
                    properties.getLocalEndpointHost(),
                    properties.getLocalEndpointPort(),
                    1,
                    RuntimeEndpointFlags.LOCAL
                )),
                RouteResolutionStrategy.ROUTE_RESOLUTION_STRATEGY_SINGLE_OWNER,
                null,
                RuntimeRouteFlags.NONE
            ));
        }

        ConnectorOrchestrator orchestrator = ConnectorOrchestrator.start(new RuntimeStartSpec(
            properties.getSystemName(),
            properties.getNodeId(),
            properties.getQueueCapacity(),
            properties.isStrictNoDrop(),
            properties.isSeparateDeliveredRequestLane(),
            properties.getGeneration(),
            new RuntimeOverloadPolicySpec(),
            routes,
            connectionStrategy(properties),
            securityPolicy(properties)
        ));

        for (DiscoveredHandler handler : handlers) {
            orchestrator.registerHandler(handler.target(), (request, continuation) -> {
                try {
                    Object reply = handler.invoke(request, objectMapper);
                    return RuntimeClient.Companion.replyTo(
                        request,
                        handler.target(),
                        objectMapper.writeValueAsString(reply),
                        payloadIdentity(reply)
                    );
                } catch (Exception error) {
                    throw new IllegalStateException("CoAkka handler failed for target " + handler.target(), error);
                }
            });
        }

        return new CoAkkaRuntime(properties, orchestrator);
    }

    static RuntimeTcpConnectionStrategySpec connectionStrategy(CoAkkaRuntimeProperties properties) {
        if (isRuntimeDefault(properties.getTcpConnectionStrategy())) {
            if (properties.getTcpMaxConnections() != null ||
                properties.getTcpMaxRequestsPerConnection() != null ||
                properties.getTcpIdleTimeoutMs() != null) {
                throw new IllegalArgumentException(
                    "TCP tuning requires coakka.runtime.tcp-connection-strategy"
                );
            }
            return null;
        }
        return new RuntimeTcpConnectionStrategySpec(
            RuntimeTcpConnectionMode.fromConfigValue(properties.getTcpConnectionStrategy()),
            properties.getTcpMaxConnections(),
            properties.getTcpMaxRequestsPerConnection(),
            properties.getTcpIdleTimeoutMs()
        );
    }

    static RuntimeTcpSecuritySpec securityPolicy(CoAkkaRuntimeProperties properties) {
        if (isRuntimeDefault(properties.getTcpSecurityMode())) {
            if (!isGracefulReloadDefault(properties.getTlsReloadMode()) ||
                properties.getTlsCredentialGeneration() != null ||
                hasText(properties.getTlsCredentialId()) ||
                hasText(properties.getTlsCaCertificateFile()) ||
                hasText(properties.getTlsIdentityCertificateFile()) ||
                hasText(properties.getTlsPrivateKeyFile())) {
                throw new IllegalArgumentException(
                    "TLS credentials require coakka.runtime.tcp-security-mode"
                );
            }
            return null;
        }
        return new RuntimeTcpSecuritySpec(
            RuntimeTcpSecurityMode.fromConfigValue(properties.getTcpSecurityMode()),
            RuntimeTlsReloadMode.fromConfigValue(properties.getTlsReloadMode()),
            properties.getTlsCredentialGeneration() == null ? 0L : properties.getTlsCredentialGeneration(),
            valueOrEmpty(properties.getTlsCredentialId()),
            valueOrEmpty(properties.getTlsCaCertificateFile()),
            valueOrEmpty(properties.getTlsIdentityCertificateFile()),
            valueOrEmpty(properties.getTlsPrivateKeyFile())
        );
    }

    private static boolean isRuntimeDefault(String value) {
        return value != null && "runtime-default".equalsIgnoreCase(value.trim().replace('_', '-'));
    }

    private static boolean isGracefulReloadDefault(String value) {
        return value != null && "graceful".equalsIgnoreCase(value.trim().replace('_', '-'));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    static ConnectorPayloadIdentity payloadIdentity(Object payload) {
        return new ConnectorPayloadIdentity(
            payload.getClass().getName(),
            1,
            ConnectorPayloadFormat.JSON
        );
    }

    private static List<DiscoveredHandler> discoverHandlers(ListableBeanFactory beanFactory) {
        List<DiscoveredHandler> handlers = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType == null || !hasHandler(beanType)) {
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                CoAkkaHandler annotation = AnnotationUtils.findAnnotation(method, CoAkkaHandler.class);
                if (annotation == null) {
                    return;
                }
                if (method.getParameterCount() != 1) {
                    throw new IllegalArgumentException(
                        "@CoAkkaHandler method " + targetClass.getName() + "." + method.getName() +
                            " must accept exactly one request parameter"
                    );
                }
                ReflectionUtils.makeAccessible(method);
                handlers.add(new DiscoveredHandler(annotation.value(), bean, method));
            });
        }
        handlers.sort(Comparator.comparing(DiscoveredHandler::target));
        return handlers;
    }

    private static boolean hasHandler(Class<?> beanType) {
        final boolean[] found = {false};
        ReflectionUtils.doWithMethods(beanType, method -> {
            if (AnnotationUtils.findAnnotation(method, CoAkkaHandler.class) != null) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private record DiscoveredHandler(String target, Object bean, Method method) {
        Object invoke(ConnectorEnvelope request, ObjectMapper objectMapper) throws Exception {
            Object payload = objectMapper.readValue(request.payloadUtf8(), method.getParameterTypes()[0]);
            return method.invoke(bean, payload);
        }
    }
}
