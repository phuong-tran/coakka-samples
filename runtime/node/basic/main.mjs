import {
  DeliveryHint,
  EndpointFlag,
  NodeRuntimeClient,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const target = "samples.runtime.node.echo";
const requestIdentity = new PayloadIdentity("samples.runtime.node.echo.request.v1", 1, PayloadFormat.JSON);
// Minimal single-process runtime configuration.
//
// systemName groups diagnostics for one logical runtime participant.
// nodeId identifies this concrete process in logs and runtime snapshots.
// queueCapacity=128 is bounded but roomy enough for a sample.
// strictNoDrop=true makes overload visible instead of silently dropping messages.
// The delivered-request lane is enabled by default for request/reply hosts.
// generation=1 is the first route-table version; increment it for new route snapshots.
// EndpointFlag.LOCAL means the target handler is registered in this process.
const startSpec = {
  systemName: "node-runtime-sample",
  nodeId: "node-runtime-sample-node",
  queueCapacity: 128,
  strictNoDrop: true,
  generation: 1,
  routes: [
    {
      target,
      endpoints: [{ host: "127.0.0.1", port: 19321, flags: EndpointFlag.LOCAL }],
    },
  ],
};

const runtime = RuntimeHost.start(startSpec);
try {
  const info = runtime.runtimeInfo();
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

  console.log(`coakka_runtime_response payload=${JSON.stringify(response)}`);

  const stats = runtime.stats();
  const clientStats = runtime.clientStats();
  console.log(
    `coakka_runtime_stats generation=${stats.appliedGeneration} ` +
      `routes=${stats.routeCount} delivered=${clientStats.deliveredRequests} ` +
      `matchedResponses=${clientStats.matchedResponses}`,
  );
} finally {
  runtime.close();
}
