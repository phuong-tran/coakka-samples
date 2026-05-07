package coakka.spring;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeOverloadPolicySpec;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.control.RouteResolutionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

public final class CoAkkaRuntime implements AutoCloseable {
    private final CoAkkaRuntimeProperties properties;
    private final ConnectorOrchestrator orchestrator;

    private CoAkkaRuntime(CoAkkaRuntimeProperties properties, ConnectorOrchestrator orchestrator) {
        this.properties = properties;
        this.orchestrator = orchestrator;
    }

    public CoAkkaRuntimeProperties getProperties() {
        return properties;
    }

    public ConnectorOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public RuntimeInfoSnapshot runtimeInfo() {
        return orchestrator.runtimeInfo();
    }

    public RuntimeConfigSnapshot runtimeConfig() {
        return orchestrator.runtimeConfig();
    }

    public RuntimeClientStats clientStats() {
        return orchestrator.clientStats();
    }

    @Override
    public void close() throws Exception {
        orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
    }

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
                0
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
            routes
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
