import {
  ConnectorOrchestrator,
  DeadletterError,
  DeliveryHint,
  EndpointFlag,
  PayloadFormat,
  PayloadIdentity,
} from "coakka-v2-connector-node";

const ROUTE_MISS_REASON = 2;
const liveTarget = "samples.runtime.node.deadletter.live";
const missingTarget = "samples.runtime.node.deadletter.missing";
const requestIdentity = new PayloadIdentity("samples.runtime.node.deadletter.request.v1", 1, PayloadFormat.JSON);
const startSpec = {
  systemName: "node-deadletter-sample",
  nodeId: "node-deadletter-sample-node",
  queueCapacity: 128,
  strictNoDrop: true,
  separateDeliveredRequestLane: true,
  generation: 1,
  routes: [
    {
      target: liveTarget,
      endpoints: [{ host: "127.0.0.1", port: 19421, flags: EndpointFlag.LOCAL }],
    },
  ],
};

const orchestrator = ConnectorOrchestrator.start(startSpec);
try {
  const observedDeadletters = orchestrator.deadletters({ bufferCapacity: 1 });
  const observedPromise = observedDeadletters.next();
  let deadletter = null;
  try {
    await orchestrator.askJson(
      "samples-runtime-node-deadletter-client",
      missingTarget,
      { message: "route-miss" },
      requestIdentity,
      2000,
      "route-miss",
      DeliveryHint.ROUTER_DEFAULT,
    );
    throw new Error("expected route miss deadletter");
  } catch (error) {
    if (!(error instanceof DeadletterError)) {
      throw error;
    }
    deadletter = error.deadletter;
  }

  const stats = orchestrator.stats();
  const clientStats = orchestrator.clientStats();

  if (deadletter.reason !== ROUTE_MISS_REASON) {
    throw new Error(`expected route miss reason=${ROUTE_MISS_REASON}, got ${deadletter.reason}`);
  }
  if (deadletter.original_envelope.target !== missingTarget) {
    throw new Error(`expected target=${missingTarget}, got ${deadletter.original_envelope.target}`);
  }
  if (stats.routeMissCount !== 1 || stats.deadletterCount !== 1) {
    throw new Error(`expected routeMissCount=1 deadletterCount=1, got ${JSON.stringify(stats)}`);
  }
  if (clientStats.matchedDeadletters !== 1) {
    throw new Error(`expected matchedDeadletters=1, got ${clientStats.matchedDeadletters}`);
  }
  const observed = await observedPromise;
  if (observed.done || observed.value == null) {
    throw new Error("expected observed deadletter");
  }
  if (!observed.value.matchedPendingRequest) {
    throw new Error("expected observed deadletter to match pending request");
  }
  if (observed.value.deadletter.original_envelope.target !== missingTarget) {
    throw new Error(`expected observed target=${missingTarget}, got ${observed.value.deadletter.original_envelope.target}`);
  }

  console.log(
    `coakka_runtime_deadletter reason=DEADLETTER_REASON_ROUTE_MISS target=${deadletter.original_envelope.target} ` +
      `generation=${deadletter.active_generation}`,
  );
  console.log(
    `coakka_runtime_deadletter_observed matchedPending=${observed.value.matchedPendingRequest} ` +
      `target=${observed.value.deadletter.original_envelope.target}`,
  );
  console.log(
    `coakka_runtime_stats routeMisses=${stats.routeMissCount} deadletters=${stats.deadletterCount} ` +
      `matchedDeadletters=${clientStats.matchedDeadletters}`,
  );
} finally {
  orchestrator.close();
}
