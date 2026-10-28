package coakka.samples.runtime.jvm.javadeadletter;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.DeadletterException;
import coakka.v2.connector.DeadletterSubscription;
import coakka.v2.connector.CoakkaRuntimeDeliveryModes;
import coakka.v2.connector.CoakkaRuntimeStartupCpuPolicies;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStartupConfigSnapshot;
import coakka.v2.connector.RuntimeStatsSnapshot;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.protocol.ConnectorDeadletter;
import coakka.v2.connector.protocol.ConnectorDeadletterReason;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.connector.protocol.ObservedDeadletter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {
    }

    static void requireRouteMiss(ConnectorDeadletter deadletter, String target) {
        ConnectorEnvelope original = deadletter.getOriginalEnvelope();
        if (!ConnectorDeadletterReason.ROUTE_MISS.equals(deadletter.getReason())
            || original == null || !target.equals(original.getTarget())) {
            throw new IllegalStateException("expected route miss with its original target");
        }
    }

    public static void main(String[] args) throws Exception {
        String liveTarget = "samples.runtime.jvm.java.deadletter.live";
        String missingTarget = "samples.runtime.jvm.java.deadletter.missing";
        RuntimeStartSpec startSpec = new RuntimeStartSpec(
            "jvm-java-deadletter-sample",
            "jvm-java-deadletter-sample-node",
            Collections.singletonList(RuntimeClient.localRoute(liveTarget))
        );

        ConnectorOrchestrator orchestrator = ConnectorOrchestrator.start(startSpec);
        Throwable primaryFailure = null;
        try {
            RuntimeStartupConfigSnapshot startup = orchestrator.startupConfig();
            if (startup.getExecution().getRequestedPolicy() != CoakkaRuntimeStartupCpuPolicies.AUTO
                || !RuntimeTcpConnectionMode.MULTIPLEXING.equals(startup.getConnection().getMode())
                || startup.getMonitorEnabled()
                || orchestrator.deliveryConfig().getRequestMode() != CoakkaRuntimeDeliveryModes.OWNED_QUEUE
                || orchestrator.deliveryConfig().getResponseMode() != CoakkaRuntimeDeliveryModes.OWNED_QUEUE) {
                throw new IllegalStateException("unexpected Core startup/delivery facts");
            }
            CompletableFuture<ObservedDeadletter> observation = new CompletableFuture<>();
            // Subscribe before ask; callback completion, not ask timing, proves observation.
            try (DeadletterSubscription subscription = orchestrator.getJava().subscribeDeadletters(
                observation::complete, 1
            )) {
                if (subscription == null) throw new IllegalStateException("missing subscription");
                try {
                    orchestrator.getJava().ask(
                        "samples-runtime-jvm-java-deadletter-client",
                        missingTarget,
                        "{\"message\":\"route-miss\"}",
                        new ConnectorPayloadIdentity(
                            "samples.runtime.jvm.java.deadletter.request.v1",
                            1,
                            ConnectorPayloadFormat.JSON
                        ),
                        2_000,
                        "route-miss",
                        ConnectorDeliveryHint.ROUTER_DEFAULT
                    ).get(3, TimeUnit.SECONDS);
                    throw new IllegalStateException("expected route miss deadletter");
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (!(cause instanceof DeadletterException)) {
                        throw error;
                    }
                    requireRouteMiss(((DeadletterException) cause).getDeadletter(), missingTarget);
                }

                ObservedDeadletter observed = observation.get(1, TimeUnit.SECONDS);
                requireRouteMiss(observed.getDeadletter(), missingTarget);
                if (!observed.getMatchedPendingRequest()) {
                    throw new IllegalStateException("expected observed deadletter to match pending request");
                }
                RuntimeStatsSnapshot stats = orchestrator.stats();
                RuntimeClientStats clientStats = orchestrator.clientStats();
                if (stats.getRouteMissCount() != 1 || stats.getDeadletterCount() != 1
                    || clientStats.getMatchedDeadletters() != 1 || clientStats.getPendingRequests() != 0) {
                    throw new IllegalStateException("unexpected terminal counters");
                }

                System.out.println(
                    "coakka_runtime_deadletter_observed matchedPending=" +
                        observed.getMatchedPendingRequest() +
                        " target=" + missingTarget + " reason=" + observed.getDeadletter().getReason().getValue()
                );
                System.out.println(
                    "coakka_runtime_stats matchedDeadletters=" +
                        clientStats.getMatchedDeadletters()
                );
            }
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            // The subscription is already closed, including exceptional paths.
            try {
                orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
            } catch (Exception | Error cleanup) {
                if (primaryFailure == null) throw cleanup;
                primaryFailure.addSuppressed(cleanup);
            }
        }
    }
}
