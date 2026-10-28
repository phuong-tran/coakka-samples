import {
  DeliveryHint,
  localRoute,
  NodeRuntimeClient,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const target = "samples.runtime.node.echo";
const requestIdentity = new PayloadIdentity("samples.runtime.node.echo.request.v1", 1, PayloadFormat.JSON);
// Only application identity and owned routes; Core resolves runtime defaults.
const startSpec = {
  systemName: "node-runtime-sample",
  nodeId: "node-runtime-sample-node",
  routes: [localRoute(target)],
};

const runtime = RuntimeHost.start(startSpec);
try {
  const info = runtime.runtimeInfo();
  const startup = runtime.runtimeStartupConfig();
  console.log(`coakka_runtime_startup ${JSON.stringify({
    requestedCpuPolicy: startup.requestedCpuPolicy,
    connectionMode: startup.connection.mode,
    monitorEnabled: startup.monitorEnabled,
    requestMode: startup.delivery.requestMode,
    responseMode: startup.delivery.responseMode,
  })}`);
  console.log(
    `coakka_runtime_info abi=${info.abiVersion} ` +
      `version=${info.runtimeVersion} git=${info.gitCommit}`,
  );

  runtime.registerHandler(target, (request) =>
    NodeRuntimeClient.makeJsonReplyFromRequestIdentity(request, target, { echo: "hello-runtime-node" }),
  );

  const response = await runtime.askJson(
    "samples-runtime-node-client",
    target,
    { message: "hello-runtime-node" },
    requestIdentity,
    2000,
    "echo",
    DeliveryHint.ROUTER_DEFAULT,
  );

  if (response.echo !== "hello-runtime-node") throw new Error("echo payload differs");
  console.log(`coakka_runtime_response payload=${JSON.stringify(response)}`);

  const stats = runtime.stats();
  const clientStats = runtime.clientStats();
  console.log(
    `coakka_runtime_stats generation=${stats.appliedGeneration} ` +
      `routes=${stats.routeCount} delivered=${clientStats.deliveredRequests} ` +
      `matchedResponses=${clientStats.matchedResponses}`,
  );
} finally {
  await runtime.close();
}
