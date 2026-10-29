package coakka.quarkus;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeMonitorOptions;
import coakka.v2.connector.RuntimeRouteStrategy;
import coakka.v2.connector.RuntimeRouteFlags;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStartupConfigSnapshot;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
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

    @ConfigProperty(name = "coakka.runtime.queue-capacity", defaultValue = "0")
    int queueCapacity;

    @ConfigProperty(name = "coakka.runtime.strict-no-drop", defaultValue = "true")
    boolean strictNoDrop;

    @ConfigProperty(name = "coakka.runtime.monitor-enabled")
    Optional<Boolean> monitorEnabled;

    @ConfigProperty(name = "coakka.runtime.local-endpoint-host", defaultValue = "127.0.0.1")
    String localEndpointHost;

    @ConfigProperty(name = "coakka.runtime.local-endpoint-port", defaultValue = "0")
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

    // CDI/application code can hold the bean monitor; it must not own this
    // adapter's shutdown serialization or delay entry to its bounded wait.
    private final Object closeLock = new Object();
    private volatile ConnectorOrchestrator orchestrator;

    @PostConstruct
    void start() {
        start(ConnectorOrchestrator::start);
    }

    // Cold lifecycle seam for exercising real-owner registration failures.
    void start(Function<RuntimeStartSpec, ConnectorOrchestrator> orchestratorFactory) {
        if (!enabled) {
            return;
        }
        if (!"local".equals(mode)) {
            throw new IllegalArgumentException("CoAkka Quarkus extension currently supports only coakka.runtime.mode=local");
        }

        // Bean-to-target registration belongs to this adapter. Resolve each
        // application callback once before allocating the runtime owner.
        List<HandlerBinding> orderedHandlers = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (CoAkkaLocalHandler handler : handlers) {
            String target = Objects.requireNonNull(handler.target(), "handler target");
            if (!targets.add(target)) {
                throw new IllegalArgumentException("Multiple CoAkka handlers declare target " + target);
            }
            orderedHandlers.add(new HandlerBinding(target, handler));
        }
        orderedHandlers.sort(Comparator.comparing(HandlerBinding::target));

        List<RuntimeRouteSpec> routes = new ArrayList<>();
        for (HandlerBinding binding : orderedHandlers) {
            routes.add(new RuntimeRouteSpec(
                binding.target(),
                List.of(new RuntimeEndpointSpec(
                    localEndpointHost,
                    localEndpointPort,
                    0,
                    RuntimeEndpointFlags.LOCAL
                )),
                RuntimeRouteStrategy.UNSPECIFIED,
                null,
                RuntimeRouteFlags.NONE
            ));
        }

        ConnectorOrchestrator candidate = orchestratorFactory.apply(new RuntimeStartSpec(
            systemName,
            nodeId,
            queueCapacity,
            strictNoDrop,
            generation,
            null,
            routes,
            connectionStrategy(),
            securityPolicy(),
            null,
            null,
            null,
            monitorOptions()
        ));

        try {
            for (HandlerBinding binding : orderedHandlers) {
                candidate.registerHandler(binding.target(), (request, continuation) -> {
                    try {
                        return binding.handler().handle(request, objectMapper);
                    } catch (Exception error) {
                        throw new IllegalStateException(
                            "CoAkka Quarkus handler failed for target " + binding.target(), error
                        );
                    }
                });
            }
        } catch (RuntimeException | Error failure) {
            try {
                shutdown(candidate);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        // Publish only the fully registered owner. Core remains authoritative
        // for runtime state; this field owns CDI availability, not Core facts.
        orchestrator = candidate;
    }

    @PreDestroy
    void close() {
        synchronized (closeLock) {
            ConnectorOrchestrator current = orchestrator;
            if (current == null) {
                return;
            }
            orchestrator = null;
            shutdown(current);
        }
    }

    private static void shutdown(ConnectorOrchestrator owner) {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        try {
            var completion = owner.getJava().shutdown();
            while (true) {
                try {
                    completion.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    return;
                } catch (InterruptedException error) {
                    // CDI still owns cleanup. Repeated interrupts cannot extend
                    // the original bound, but the caller's flag is restored.
                    interrupted = true;
                    if (deadline - System.nanoTime() <= 0) {
                        throw new TimeoutException("CoAkka shutdown deadline expired after interruption");
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
     * Returns the live connector or fails when the adapter is disabled or not started.
     *
     * @return process-owned connector orchestrator
     * @throws IllegalStateException when no runtime is active
     */
    public ConnectorOrchestrator connector() {
        ConnectorOrchestrator current = orchestrator;
        if (current == null) {
            throw new IllegalStateException("CoAkka runtime is not started");
        }
        return current;
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

    /** @return accepted Core startup settings, including monitoring and defaults */
    public RuntimeStartupConfigSnapshot startupConfig() {
        return connector().startupConfig();
    }

    /** @return request/reply client counters */
    public RuntimeClientStats clientStats() {
        return connector().clientStats();
    }

    RuntimeMonitorOptions monitorOptions() {
        return monitorEnabled.map(RuntimeMonitorOptions::new).orElse(null);
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

    private record HandlerBinding(String target, CoAkkaLocalHandler handler) {
    }
}
