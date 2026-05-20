package coakka.samples.runtime.jvm.javabasic;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeOverloadPolicySpec;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStatsSnapshot;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
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
            new RuntimeOverloadPolicySpec(),
            Arrays.asList(RuntimeClient.Companion.localRoute(target, 19341))
        );

        ConnectorOrchestrator orchestrator = ConnectorOrchestrator.start(null, startSpec);
        try {
            RuntimeInfoSnapshot info = orchestrator.runtimeInfo();
            System.out.println(
                "coakka_runtime_info abi=" + info.getAbiVersion() +
                    " version=" + info.getRuntimeVersion() +
                    " git=" + info.getGitCommit() +
                    " language=java"
            );

            orchestrator.registerTextHandler(target, request -> "hello-" + request);

            String response = orchestrator.getJava().askTextBlocking(
                "samples-runtime-jvm-java-client",
                target,
                "runtime-java",
                2_000,
                "echo",
                ConnectorDeliveryHint.ROUTER_DEFAULT
            );

            System.out.println("coakka_runtime_response payload=" + response);

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
