import {
  BunRuntimeClient,
  BunRuntimeHost,
  DeliveryHint,
  localRoute,
  PayloadFormat,
  PayloadIdentity,
} from "coakka-v2-connector-bun";

const target = "samples.runtime.bun.echo";
const requestIdentity = new PayloadIdentity("samples.runtime.bun.echo.request.v1", 1, PayloadFormat.JSON);

const startSpec = {
  systemName: "bun-runtime-sample",
  nodeId: "bun-runtime-sample-node",
  queueCapacity: 128,
  strictNoDrop: true,
  generation: 1,
  routes: [localRoute(target, 19323)],
};

const runtime = BunRuntimeHost.start(startSpec);
try {
  const info = runtime.runtimeInfo();
  console.log(
    `coakka_runtime_info abi=${info.abiVersion} ` +
      `version=${info.runtimeVersion} git=${info.gitCommit}`,
  );

  runtime.registerHandler(target, (request) =>
    BunRuntimeClient.makeJsonReplyFromRequestIdentity(request, target, { echo: "hello-runtime-bun" }),
  );

  const response = await runtime.askJson(
    "samples-runtime-bun-client",
    target,
    { message: "hello-runtime-bun" },
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
