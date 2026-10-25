package coakka.samples.runtime.jvm.javabasic;

import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.CoakkaRuntimeDeliveryModes;
import coakka.v2.connector.CoakkaRuntimeStartupCpuPolicies;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.RuntimeStartupConfigSnapshot;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeStartSpec;
import coakka.v2.connector.RuntimeStatsSnapshot;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String target = "samples.runtime.jvm.java.echo";
        RuntimeStartSpec startSpec = new RuntimeStartSpec(
            "jvm-java-runtime-sample",
            "jvm-java-runtime-sample-node",
            Collections.singletonList(RuntimeClient.localRoute(target))
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

            if (!"hello-runtime-java".equals(response)) {
                throw new IllegalStateException("unexpected echo response: " + response);
            }
            System.out.println("coakka_runtime_response payload=" + response);

            RuntimeStatsSnapshot stats = orchestrator.stats();
            RuntimeClientStats clientStats = orchestrator.clientStats();
            if (stats.getRouteCount() != 1 || clientStats.getDeliveredRequests() != 1
                || clientStats.getMatchedResponses() != 1 || clientStats.getPendingRequests() != 0) {
                throw new IllegalStateException("unexpected terminal counters");
            }
            System.out.println(
                "coakka_runtime_stats generation=" + stats.getAppliedGeneration() +
                    " routes=" + stats.getRouteCount() +
                    " delivered=" + clientStats.getDeliveredRequests() +
                    " matchedResponses=" + clientStats.getMatchedResponses() +
                    " language=java"
            );
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                orchestrator.getJava().shutdown().get(3, TimeUnit.SECONDS);
            } catch (Exception | Error cleanup) {
                if (primaryFailure == null) throw cleanup;
                primaryFailure.addSuppressed(cleanup);
            }
        }
    }
}
