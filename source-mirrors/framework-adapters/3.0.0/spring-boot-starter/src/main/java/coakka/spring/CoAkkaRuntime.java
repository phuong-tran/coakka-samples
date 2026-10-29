package coakka.spring;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeMonitorOptions;
import coakka.v2.connector.RuntimeRouteFlags;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStartupConfigSnapshot;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeTcpConnectionStrategySpec;
import coakka.v2.connector.RuntimeTcpSecurityMode;
import coakka.v2.connector.RuntimeTcpSecuritySpec;
import coakka.v2.connector.RuntimeTlsReloadMode;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.connector.RuntimeRouteStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
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
    private final String sourceTarget;
    private final ConnectorOrchestrator orchestrator;
    private final Object closeLock = new Object();

    private CoAkkaRuntime(CoAkkaRuntimeProperties properties, ConnectorOrchestrator orchestrator) {
        this.properties = properties;
        this.sourceTarget = properties.getSourceTarget();
        this.orchestrator = orchestrator;
    }

    /**
     * Returns a copy of the application properties captured at startup,
     * not Core's accepted settings. Changes to the copy do not reconfigure this runtime.
     *
     * @return independent copy of the requested startup properties
     */
    public CoAkkaRuntimeProperties getProperties() {
        return copyProperties(properties);
    }

    // Request metadata is captured once; the request path never copies a bound bean.
    String sourceTarget() {
        return sourceTarget;
    }

    private static CoAkkaRuntimeProperties copyProperties(CoAkkaRuntimeProperties source) {
        CoAkkaRuntimeProperties copy = new CoAkkaRuntimeProperties();
        BeanUtils.copyProperties(source, copy);
        return copy;
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

    /** @return Core's accepted startup settings, including default and monitor facts */
    public RuntimeStartupConfigSnapshot startupConfig() {
        return orchestrator.startupConfig();
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
     * Serializes close callers and waits up to three seconds for shutdown completion.
     * A caller's interrupt flag is restored after the bounded cleanup attempt.
     *
     * @throws IllegalStateException when shutdown fails or does not complete in time
     */
    @Override
    public void close() {
        synchronized (closeLock) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            awaitShutdown(orchestrator.getJava().shutdown(), deadline);
        }
    }

    // The absolute bound also applies when a caller is repeatedly interrupted.
    static void awaitShutdown(CompletableFuture<?> completion, long deadline) {
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    completion.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    return;
                } catch (InterruptedException error) {
                    // The context still owns cleanup; an interrupt must not abandon it.
                    interrupted = true;
                    if (deadline - System.nanoTime() <= 0) {
                        throw new TimeoutException("CoAkka shutdown deadline expired");
                    }
                }
            }
        } catch (ExecutionException | TimeoutException error) {
            throw new IllegalStateException("Failed to shut down CoAkka runtime within three seconds", error);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
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
        return start(properties, objectMapper, beanFactory, ConnectorOrchestrator::start);
    }

    // Cold lifecycle seam: tests can fail a real registration after owner creation.
    static CoAkkaRuntime start(
        CoAkkaRuntimeProperties properties,
        ObjectMapper objectMapper,
        ListableBeanFactory beanFactory,
        Function<RuntimeStartSpec, ConnectorOrchestrator> orchestratorFactory
    ) {
        // Binding owns the mutable input; the context owner retains one startup snapshot.
        properties = copyProperties(properties);
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("coakka.runtime.enabled=false does not request runtime startup");
        }
        if (!"local".equals(properties.getMode())) {
            throw new IllegalArgumentException("starter only supports coakka.runtime.mode=local");
        }

        List<DiscoveredHandler> handlers = discoverHandlers(beanFactory);
        List<RuntimeRouteSpec> routes = new ArrayList<>();
        for (DiscoveredHandler handler : handlers) {
            routes.add(new RuntimeRouteSpec(
                handler.target(),
                List.of(new RuntimeEndpointSpec(
                    properties.getLocalEndpointHost(),
                    properties.getLocalEndpointPort(),
                    0,
                    RuntimeEndpointFlags.LOCAL
                )),
                RuntimeRouteStrategy.UNSPECIFIED,
                null,
                RuntimeRouteFlags.NONE
            ));
        }

        ConnectorOrchestrator orchestrator = orchestratorFactory.apply(new RuntimeStartSpec(
            properties.getSystemName(),
            properties.getNodeId(),
            properties.getQueueCapacity(),
            properties.isStrictNoDrop(),
            properties.getGeneration(),
            null,
            routes,
            connectionStrategy(properties),
            securityPolicy(properties),
            null,
            null,
            null,
            monitorOptions(properties)
        ));

        CoAkkaRuntime runtime = new CoAkkaRuntime(properties, orchestrator);
        try {
            for (DiscoveredHandler handler : handlers) {
                orchestrator.registerHandler(handler.target(), (request, continuation) -> {
                    try {
                        Object reply = handler.invoke(request, objectMapper);
                        return RuntimeClient.Companion.replyTo(
                            request,
                            handler.target(),
                            objectMapper.writeValueAsString(reply),
                            payloadIdentity(reply == null ? handler.method().getReturnType() : reply.getClass())
                        );
                    } catch (Exception error) {
                        throw new IllegalStateException("CoAkka handler failed for target " + handler.target(), error);
                    }
                });
            }
            return runtime;
        } catch (RuntimeException | Error failure) {
            // Startup has one owner even when a later registration fails.
            try {
                runtime.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static RuntimeMonitorOptions monitorOptions(CoAkkaRuntimeProperties properties) {
        Boolean enabled = properties.getMonitorEnabled();
        return enabled == null ? null : new RuntimeMonitorOptions(enabled);
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
        return payloadIdentity(payload.getClass());
    }

    private static ConnectorPayloadIdentity payloadIdentity(Class<?> payloadType) {
        return new ConnectorPayloadIdentity(
            payloadType.getName(),
            1,
            ConnectorPayloadFormat.JSON
        );
    }

    private static List<DiscoveredHandler> discoverHandlers(ListableBeanFactory beanFactory) {
        List<DiscoveredHandler> handlers = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType == null || !hasHandler(beanType)) {
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(
                targetClass, ReflectionUtils.USER_DECLARED_METHODS
            )) {
                CoAkkaHandler annotation = AnnotationUtils.findAnnotation(method, CoAkkaHandler.class);
                if (annotation == null) {
                    continue;
                }
                if (method.getParameterCount() != 1) {
                    throw new IllegalArgumentException(
                        "@CoAkkaHandler method " + targetClass.getName() + "." + method.getName() +
                            " must accept exactly one request parameter"
                    );
                }
                if (method.getReturnType() == Void.TYPE) {
                    throw new IllegalArgumentException(
                        "@CoAkkaHandler method " + targetClass.getName() + "." + method.getName() +
                            " must return a JSON reply value"
                    );
                }
                if (!targets.add(annotation.value())) {
                    throw new IllegalArgumentException("duplicate @CoAkkaHandler target: " + annotation.value());
                }
                Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
                ReflectionUtils.makeAccessible(invocableMethod);
                handlers.add(new DiscoveredHandler(annotation.value(), bean, invocableMethod));
            }
        }
        handlers.sort(Comparator.comparing(DiscoveredHandler::target));
        return handlers;
    }

    private static boolean hasHandler(Class<?> beanType) {
        for (Method method : ReflectionUtils.getUniqueDeclaredMethods(
            beanType, ReflectionUtils.USER_DECLARED_METHODS
        )) {
            if (AnnotationUtils.findAnnotation(method, CoAkkaHandler.class) != null) {
                return true;
            }
        }
        return false;
    }

    private record DiscoveredHandler(String target, Object bean, Method method) {
        Object invoke(ConnectorEnvelope request, ObjectMapper objectMapper) throws Exception {
            Object payload = objectMapper.readValue(request.payloadUtf8(), method.getParameterTypes()[0]);
            return method.invoke(bean, payload);
        }
    }
}
