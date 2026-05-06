package coakka.samples.runtime.jvm.javabasic;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeEndpointFlags;
import coakka.v2.connector.RuntimeEndpointSpec;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeRouteSpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStatsSnapshot;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import coakka.v2.control.RouteResolutionStrategy;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String target = "samples.runtime.jvm.java.echo";
        RuntimeStartSpec startSpec = new RuntimeStartSpec(
            "jvm-java-runtime-sample",
            "jvm-java-runtime-sample-node",
            128,
            true,
            true,
            1,
            Arrays.asList(
                new RuntimeRouteSpec(
                    target,
                    Arrays.asList(
                        new RuntimeEndpointSpec(
                            "127.0.0.1",
                            19341,
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
        try {
            RuntimeInfoSnapshot info = orchestrator.runtimeInfo();
            System.out.println(
                "coakka_runtime_info abi=" + info.getAbiVersion() +
                    " version=" + info.getRuntimeVersion() +
                    " git=" + info.getGitCommit() +
                    " backend=" + info.getSouthboundBackend() +
                    " language=java"
            );

            orchestrator.registerHandler(target, (request, continuation) ->
                RuntimeClient.Companion.replyTypedTo(
                    request,
                    target,
                    "{\"echo\":\"hello-runtime-java\"}"
                )
            );

            ConnectorEnvelope response = orchestrator.getJava().ask(
                "samples-runtime-jvm-java-client",
                target,
                "{\"message\":\"hello-runtime-java\"}",
                new ConnectorPayloadIdentity(
                    "samples.runtime.jvm.java.echo.request.v1",
                    1,
                    ConnectorPayloadFormat.JSON
                ),
                2_000,
                "echo",
                ConnectorDeliveryHint.ROUTER_DEFAULT
            ).get(3, TimeUnit.SECONDS);

            System.out.println("coakka_runtime_response payload=" + response.payloadUtf8());

            RuntimeStatsSnapshot stats = orchestrator.stats();
            RuntimeClientStats clientStats = orchestrator.clientStats();
            System.out.println(
                "coakka_runtime_stats generation=" + stats.getAppliedGeneration() +
                    " routes=" + stats.getRouteCount() +
                    " delivered=" + clientStats.getDeliveredRequests() +
                    " matchedResponses=" + clientStats.getMatchedResponses() +
                    " language=java"
            );
        } finally {
            orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
        }
    }
}
