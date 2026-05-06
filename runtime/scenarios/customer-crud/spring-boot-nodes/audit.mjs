import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  ConnectorOrchestrator,
  EndpointFlag,
  PayloadFormat,
  PayloadIdentity,
} from "coakka-v2-connector-node";

const here = dirname(fileURLToPath(import.meta.url));
const indexHtml = readFileSync(join(here, "audit-index.html"), "utf8");

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
// in diagnostics even while the public runtime backend is still stubbed.
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

const orchestrator = ConnectorOrchestrator.start(startSpec);
orchestrator.registerHandler(localTarget, (request) => {
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

function runtimeDiagnostics() {
  return {
    runtimeInfo: orchestrator.runtimeInfo(),
    runtimeConfig: orchestrator.runtimeConfig(),
    clientStats: orchestrator.clientStats(),
    connector: {
      serviceRole: "customer-audit-node",
      localTarget,
      storeTarget,
      peerTarget,
      auditEventType: identities.auditEvent.messageType,
      retainedEvents: auditEvents.length,
    },
  };
}

function sendJson(response, status, body) {
  const bytes = Buffer.from(JSON.stringify(body));
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": bytes.length,
  });
  response.end(bytes);
}

const server = createServer((request, response) => {
  const url = new URL(request.url || "/", "http://127.0.0.1:8094");
  if (request.method === "GET" && url.pathname === "/") {
    response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
    response.end(indexHtml);
    return;
  }
  if (request.method === "GET" && url.pathname === "/api/audit/events") {
    sendJson(response, 200, { events: auditEvents });
    return;
  }
  if (request.method === "GET" && url.pathname === "/api/audit/runtime") {
    sendJson(response, 200, runtimeDiagnostics());
    return;
  }
  sendJson(response, 404, { error: "not_found", path: url.pathname });
});

server.listen(8094, "127.0.0.1", () => {
  const info = orchestrator.runtimeInfo();
  console.log(
    `customer-audit-node ready http=8094 runtime=${info.runtimeVersion} backend=${info.southboundBackend} target=${localTarget}`,
  );
});

function shutdown() {
  server.close(() => {
    orchestrator.close();
    process.exit(0);
  });
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
