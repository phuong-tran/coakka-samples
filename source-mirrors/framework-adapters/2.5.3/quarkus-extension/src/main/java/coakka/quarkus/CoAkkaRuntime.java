package coakka.quarkus;

import coakka.v2.connector.ConnectorOrchestrator;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Application-scoped CoAkka runtime owned by the Quarkus CDI lifecycle.
 *
 * <p>Startup registers discovered local handlers. {@code @PreDestroy}
 * requests bounded shutdown; the underlying connector retains ownership of
 * native resources, queues, routes, workers, and transport state.</p>
 */
@ApplicationScoped
public class CoAkkaRuntime {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    Instance<CoAkkaLocalHandler> handlers;

    @ConfigProperty(name = "coakka.runtime.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "coakka.runtime.mode", defaultValue = "local")
    String mode;

    @ConfigProperty(name = "coakka.runtime.system-name", defaultValue = "coakka-quarkus-app")
    String systemName;

    @ConfigProperty(name = "coakka.runtime.node-id", defaultValue = "coakka-quarkus-node")
    String nodeId;

    @ConfigProperty(name = "coakka.runtime.source-target", defaultValue = "coakka.quarkus.source")
    String sourceTarget;

    @ConfigProperty(name = "coakka.runtime.generation", defaultValue = "1")
    long generation;

    @ConfigProperty(name = "coakka.runtime.queue-capacity", defaultValue = "128")
    int queueCapacity;

    @ConfigProperty(name = "coakka.runtime.strict-no-drop", defaultValue = "true")
    boolean strictNoDrop;

    @ConfigProperty(name = "coakka.runtime.separate-delivered-request-lane", defaultValue = "true")
    boolean separateDeliveredRequestLane;

    @ConfigProperty(name = "coakka.runtime.local-endpoint-host", defaultValue = "127.0.0.1")
    String localEndpointHost;

    @ConfigProperty(name = "coakka.runtime.local-endpoint-port", defaultValue = "19182")
    int localEndpointPort;

    @ConfigProperty(name = "coakka.runtime.tcp-connection-strategy", defaultValue = "runtime-default")
    String tcpConnectionStrategy;

    @ConfigProperty(name = "coakka.runtime.tcp-max-connections")
    Optional<Integer> tcpMaxConnections;

    @ConfigProperty(name = "coakka.runtime.tcp-max-requests-per-connection")
    Optional<Long> tcpMaxRequestsPerConnection;

    @ConfigProperty(name = "coakka.runtime.tcp-idle-timeout-ms")
    Optional<Long> tcpIdleTimeoutMs;

    @ConfigProperty(name = "coakka.runtime.tcp-security-mode", defaultValue = "runtime-default")
    String tcpSecurityMode;

    @ConfigProperty(name = "coakka.runtime.tls-reload-mode", defaultValue = "graceful")
    String tlsReloadMode;

    @ConfigProperty(name = "coakka.runtime.tls-credential-generation")
    Optional<Long> tlsCredentialGeneration;

    @ConfigProperty(name = "coakka.runtime.tls-credential-id")
    Optional<String> tlsCredentialId;

    @ConfigProperty(name = "coakka.runtime.tls-ca-certificate-file")
    Optional<String> tlsCaCertificateFile;

    @ConfigProperty(name = "coakka.runtime.tls-identity-certificate-file")
    Optional<String> tlsIdentityCertificateFile;

    @ConfigProperty(name = "coakka.runtime.tls-private-key-file")
    Optional<String> tlsPrivateKeyFile;

    private ConnectorOrchestrator orchestrator;

    @PostConstruct
    void start() {
        if (!enabled) {
            return;
        }
        if (!"local".equals(mode)) {
            throw new IllegalArgumentException("CoAkka Quarkus extension currently supports only coakka.runtime.mode=local");
        }

        List<CoAkkaLocalHandler> orderedHandlers = new ArrayList<>();
        for (CoAkkaLocalHandler handler : handlers) {
            orderedHandlers.add(handler);
        }
        orderedHandlers.sort(Comparator.comparing(CoAkkaLocalHandler::target));

        List<RuntimeRouteSpec> routes = new ArrayList<>();
        for (CoAkkaLocalHandler handler : orderedHandlers) {
            routes.add(new RuntimeRouteSpec(
                handler.target(),
                List.of(new RuntimeEndpointSpec(
                    localEndpointHost,
                    localEndpointPort,
                    1,
                    RuntimeEndpointFlags.LOCAL
                )),
                coakka.v2.control.RouteResolutionStrategy.ROUTE_RESOLUTION_STRATEGY_SINGLE_OWNER,
                null,
                RuntimeRouteFlags.NONE
            ));
        }

        orchestrator = ConnectorOrchestrator.Companion.start(new RuntimeStartSpec(
            systemName,
            nodeId,
            queueCapacity,
            strictNoDrop,
            separateDeliveredRequestLane,
            generation,
            new RuntimeOverloadPolicySpec(),
            routes,
            connectionStrategy(),
            securityPolicy()
        ));

        for (CoAkkaLocalHandler handler : orderedHandlers) {
            orchestrator.registerHandler(handler.target(), (request, continuation) -> {
                try {
                    return handler.handle(request, objectMapper);
                } catch (Exception error) {
                    throw new IllegalStateException("CoAkka Quarkus handler failed for target " + handler.target(), error);
                }
            });
        }
    }

    @PreDestroy
    void close() {
        if (orchestrator == null) {
            return;
        }
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
     * Returns the live connector or fails when the adapter is disabled or not started.
     *
     * @return process-owned connector orchestrator
     * @throws IllegalStateException when no runtime is active
     */
    public ConnectorOrchestrator connector() {
        if (orchestrator == null) {
            throw new IllegalStateException("CoAkka runtime is not started");
        }
        return orchestrator;
    }

    /** @return configured source target used for application requests */
    public String sourceTarget() {
        return sourceTarget;
    }

    /** @return immutable native runtime build metadata */
    public RuntimeInfoSnapshot runtimeInfo() {
        return connector().runtimeInfo();
    }

    /** @return effective state for this runtime instance */
    public RuntimeConfigSnapshot runtimeConfig() {
        return connector().runtimeConfig();
    }

    /** @return request/reply client counters */
    public RuntimeClientStats clientStats() {
        return connector().clientStats();
    }

    RuntimeTcpConnectionStrategySpec connectionStrategy() {
        if (isRuntimeDefault(tcpConnectionStrategy)) {
            if (tcpMaxConnections.isPresent() ||
                tcpMaxRequestsPerConnection.isPresent() ||
                tcpIdleTimeoutMs.isPresent()) {
                throw new IllegalArgumentException(
                    "TCP tuning requires coakka.runtime.tcp-connection-strategy"
                );
            }
            return null;
        }
        return new RuntimeTcpConnectionStrategySpec(
            RuntimeTcpConnectionMode.fromConfigValue(tcpConnectionStrategy),
            tcpMaxConnections.orElse(null),
            tcpMaxRequestsPerConnection.orElse(null),
            tcpIdleTimeoutMs.orElse(null)
        );
    }

    RuntimeTcpSecuritySpec securityPolicy() {
        if (isRuntimeDefault(tcpSecurityMode)) {
            if (!isGracefulReloadDefault(tlsReloadMode) ||
                tlsCredentialGeneration.isPresent() ||
                tlsCredentialId.isPresent() ||
                tlsCaCertificateFile.isPresent() ||
                tlsIdentityCertificateFile.isPresent() ||
                tlsPrivateKeyFile.isPresent()) {
                throw new IllegalArgumentException(
                    "TLS credentials require coakka.runtime.tcp-security-mode"
                );
            }
            return null;
        }
        return new RuntimeTcpSecuritySpec(
            RuntimeTcpSecurityMode.fromConfigValue(tcpSecurityMode),
            RuntimeTlsReloadMode.fromConfigValue(tlsReloadMode),
            tlsCredentialGeneration.orElse(0L),
            tlsCredentialId.orElse(""),
            tlsCaCertificateFile.orElse(""),
            tlsIdentityCertificateFile.orElse(""),
            tlsPrivateKeyFile.orElse("")
        );
    }

    private static boolean isRuntimeDefault(String value) {
        return value != null && "runtime-default".equalsIgnoreCase(value.trim().replace('_', '-'));
    }

    private static boolean isGracefulReloadDefault(String value) {
        return value != null && "graceful".equalsIgnoreCase(value.trim().replace('_', '-'));
    }
}
