import {
  EndpointFlag,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const localTarget = "samples.customer.audit";
const storeTarget = "samples.customer.store";
const peerTarget = "samples.customer.frontend";
const identities = {
  auditEvent: new PayloadIdentity("samples.customer.audit.event.v1", 1, PayloadFormat.JSON),
};

const auditEvents = [];

// Runtime route table for the audit process.
//
// The audit target is LOCAL because this process owns that handler. Store and
// web targets are present as remote route entries so the topology is explicit
// in diagnostics when customer traffic crosses process boundaries.
const startSpec = {
  systemName: "customer-audit-node",
  nodeId: "customer-audit-node",
  queueCapacity: 128,
  strictNoDrop: true,
  separateDeliveredRequestLane: true,
  generation: 1,
  routes: [
    {
      target: localTarget,
      endpoints: [{ host: "127.0.0.1", port: 19134, flags: EndpointFlag.LOCAL }],
    },
    {
      target: storeTarget,
      endpoints: [{ host: "127.0.0.1", port: 19132, flags: EndpointFlag.NONE }],
    },
    {
      target: peerTarget,
      endpoints: [{ host: "127.0.0.1", port: 19131, flags: EndpointFlag.NONE }],
    },
  ],
};

const runtime = RuntimeHost.start(startSpec);
runtime.registerHandler(localTarget, (request) => {
  if (request.message_type !== identities.auditEvent.messageType) {
    console.log(`customer-audit-node ignored type=${request.message_type}`);
    return null;
  }
  const event = decodeJson(request.payload);
  auditEvents.unshift(event);
  if (auditEvents.length > 100) auditEvents.pop();
  console.log(`customer-audit-node event operation=${event.operation} id=${event.customerId} revision=${event.revision}`);
  return null;
}, false);

function decodeJson(payload) {
  const text = new TextDecoder().decode(payload);
  return text ? JSON.parse(text) : {};
}

const info = runtime.runtimeInfo();
console.log(
  `customer-audit-node ready headless runtime=${info.runtimeVersion} target=${localTarget}`,
);
process.stdin.resume();

function shutdown() {
  console.log(`customer-audit-node retainedEvents=${auditEvents.length}`);
  runtime.close();
  process.exit(0);
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
