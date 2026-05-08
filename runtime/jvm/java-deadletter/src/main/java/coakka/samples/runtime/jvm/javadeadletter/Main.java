package coakka.samples.runtime.jvm.javadeadletter;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.DeadletterException;
import coakka.v2.connector.DeadletterSubscription;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeOverloadPolicySpec;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.connector.protocol.ObservedDeadletter;
import coakka.v2.control.RouteResolutionStrategy;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String liveTarget = "samples.runtime.jvm.java.deadletter.live";
        String missingTarget = "samples.runtime.jvm.java.deadletter.missing";
        RuntimeStartSpec startSpec = new RuntimeStartSpec(
            "jvm-java-deadletter-sample",
            "jvm-java-deadletter-sample-node",
            128,
            true,
            true,
            1,
            new RuntimeOverloadPolicySpec(),
            Arrays.asList(
                new RuntimeRouteSpec(
                    liveTarget,
                    Arrays.asList(
                        new RuntimeEndpointSpec(
                            "127.0.0.1",
                            19441,
                            1,
                            RuntimeEndpointFlags.LOCAL
                        )
                    ),
                    RouteResolutionStrategy.ROUTE_RESOLUTION_STRATEGY_SINGLE_OWNER,
                    null,
                    0
                )
            )
        );

        ConnectorOrchestrator orchestrator = ConnectorOrchestrator.start(null, startSpec);
        AtomicReference<ObservedDeadletter> observedRef = new AtomicReference<>();
        DeadletterSubscription subscription = orchestrator.getJava().subscribeDeadletters(observedRef::set);
        try {
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
            }

            ObservedDeadletter observed = observedRef.get();
            if (observed == null) {
                throw new IllegalStateException("expected observed deadletter");
            }
            if (!observed.getMatchedPendingRequest()) {
                throw new IllegalStateException("expected observed deadletter to match pending request");
            }
            if (!missingTarget.equals(observed.getDeadletter().getOriginalEnvelope().getTarget())) {
                throw new IllegalStateException(
                    "expected target=" + missingTarget + ", got " +
                        observed.getDeadletter().getOriginalEnvelope().getTarget()
                );
            }

            System.out.println(
                "coakka_runtime_deadletter_observed matchedPending=" +
                    observed.getMatchedPendingRequest() +
                    " target=" + observed.getDeadletter().getOriginalEnvelope().getTarget()
            );
            System.out.println(
                "coakka_runtime_stats matchedDeadletters=" +
                    orchestrator.clientStats().getMatchedDeadletters()
            );
        } finally {
            subscription.close();
            orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
        }
    }
}
