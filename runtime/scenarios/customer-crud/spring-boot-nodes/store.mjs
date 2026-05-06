import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  ConnectorOrchestrator,
  EndpointFlag,
  NodeRuntimeClient,
  PayloadFormat,
  PayloadIdentity,
} from "coakka-v2-connector-node";

const here = dirname(fileURLToPath(import.meta.url));
const indexHtml = readFileSync(join(here, "store-index.html"), "utf8");

const localTarget = "samples.customer.store";
const peerTarget = "samples.customer.frontend";
const auditTarget = "samples.customer.audit";
const identities = {
  create: new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
  update: new PayloadIdentity("samples.customer.update.request.v1", 1, PayloadFormat.JSON),
  delete: new PayloadIdentity("samples.customer.delete.request.v1", 1, PayloadFormat.JSON),
  list: new PayloadIdentity("samples.customer.list.request.v1", 1, PayloadFormat.JSON),
  mutationResponse: new PayloadIdentity("samples.customer.mutation.response.v1", 1, PayloadFormat.JSON),
  listResponse: new PayloadIdentity("samples.customer.list.response.v1", 1, PayloadFormat.JSON),
  auditEvent: new PayloadIdentity("samples.customer.audit.event.v1", 1, PayloadFormat.JSON),
};

const customers = new Map();
let revision = 0;
let auditSubmitted = 0;
let auditRejected = 0;

// Runtime route table for the Node.js store process.
//
// localTarget is the handler owned by this process. peerTarget points back to
// the Spring Boot web process, and auditTarget points to the audit Node.js
// process. Only localTarget is marked LOCAL; the other routes are remote
// addresses. generation=1 is the first static route snapshot for this demo.
const startSpec = {
  systemName: "customer-store-node-multi",
  nodeId: "customer-store-node-multi",
  queueCapacity: 128,
  strictNoDrop: true,
  separateDeliveredRequestLane: true,
  generation: 1,
  routes: [
    {
      target: localTarget,
      endpoints: [{ host: "127.0.0.1", port: 19132, flags: EndpointFlag.LOCAL }],
    },
    {
      target: peerTarget,
      endpoints: [{ host: "127.0.0.1", port: 19131, flags: EndpointFlag.NONE }],
    },
    {
      target: auditTarget,
      endpoints: [{ host: "127.0.0.1", port: 19134, flags: EndpointFlag.NONE }],
    },
  ],
};

const orchestrator = ConnectorOrchestrator.start(startSpec);
orchestrator.registerHandler(localTarget, (request) => {
  const payload = decodeJson(request.payload);
  const reply = handleRuntimeRequest(request.message_type, payload, request.correlation_id);
  return NodeRuntimeClient.makeJsonReply(request, localTarget, reply.payload, reply.identity);
});

function decodeJson(payload) {
  const text = new TextDecoder().decode(payload);
  return text ? JSON.parse(text) : {};
}

function customerList() {
  return [...customers.values()].sort((left, right) => left.id.localeCompare(right.id));
}

function mutation(operation, id) {
  return {
    status: "ACCEPTED",
    operation,
    customerId: id,
    revision,
    handledBy: "customer-store-node-multi",
  };
}

function publishAudit(operation, customerId, customer, correlationId) {
  const event = {
    eventId: `audit-${revision}`,
    operation,
    customerId,
    revision,
    customer: customer || null,
    emittedBy: "customer-store-node-multi",
    correlationId: correlationId || "",
    observedAt: new Date().toISOString(),
  };
  try {
    orchestrator.sendOneWayJson(localTarget, auditTarget, event, identities.auditEvent);
    auditSubmitted += 1;
    console.log(`customer-store-node-multi audit operation=${operation} id=${customerId} revision=${revision}`);
  } catch (error) {
    auditRejected += 1;
    console.log(`customer-store-node-multi audit rejected operation=${operation} id=${customerId} error=${error.message}`);
  }
}

function upsertCustomer(operation, customer, correlationId) {
  revision += 1;
  const view = {
    id: customer.id,
    name: customer.name,
    email: customer.email,
    tier: customer.tier,
    notes: customer.notes || "",
    revision,
  };
  customers.set(customer.id, view);
  console.log(`customer-store-node-multi ${operation} id=${customer.id} tier=${customer.tier}`);
  publishAudit(operation, customer.id, view, correlationId);
  return mutation(operation, customer.id);
}

function handleRuntimeRequest(messageType, payload, correlationId) {
  switch (messageType) {
    case identities.create.messageType:
      return { payload: upsertCustomer("create", payload, correlationId), identity: identities.mutationResponse };
    case identities.update.messageType:
      return { payload: upsertCustomer("update", payload, correlationId), identity: identities.mutationResponse };
    case identities.delete.messageType:
      revision += 1;
      customers.delete(payload.id);
      console.log(`customer-store-node-multi delete id=${payload.id} correlation=${correlationId}`);
      publishAudit("delete", payload.id, null, correlationId);
      return { payload: mutation("delete", payload.id), identity: identities.mutationResponse };
    case identities.list.messageType:
      console.log(`customer-store-node-multi list correlation=${correlationId}`);
      return { payload: { customers: customerList() }, identity: identities.listResponse };
    default:
      throw new Error(`unsupported customer message type: ${messageType}`);
  }
}

function runtimeDiagnostics() {
  return {
    runtimeInfo: orchestrator.runtimeInfo(),
    runtimeConfig: orchestrator.runtimeConfig(),
    clientStats: orchestrator.clientStats(),
    connector: {
      serviceRole: "customer-store-node-multi",
      localTarget,
      peerTarget,
      auditTarget,
      createType: identities.create.messageType,
      updateType: identities.update.messageType,
      deleteType: identities.delete.messageType,
      listType: identities.list.messageType,
      auditEventType: identities.auditEvent.messageType,
      auditSubmitted,
      auditRejected,
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

function readJsonRequest(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const text = Buffer.concat(chunks).toString("utf8");
      resolve(text ? JSON.parse(text) : {});
    });
    request.on("error", reject);
  });
}

function customerIdFromPath(pathname) {
  const prefix = "/api/customers/";
  if (!pathname.startsWith(prefix) || pathname === "/api/customers/runtime") return null;
  return decodeURIComponent(pathname.slice(prefix.length));
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url || "/", "http://127.0.0.1:8092");
  try {
    if (request.method === "GET" && url.pathname === "/") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
      response.end(indexHtml);
      return;
    }
    if (request.method === "GET" && url.pathname === "/api/customers") {
      sendJson(response, 200, { customers: customerList() });
      return;
    }
    if (request.method === "POST" && url.pathname === "/api/customers") {
      sendJson(response, 201, upsertCustomer("create", await readJsonRequest(request), "http-fallback"));
      return;
    }
    const customerId = customerIdFromPath(url.pathname);
    if (request.method === "PUT" && customerId) {
      const customer = await readJsonRequest(request);
      sendJson(response, 200, upsertCustomer("update", { ...customer, id: customerId }, "http-fallback"));
      return;
    }
    if (request.method === "DELETE" && customerId) {
      revision += 1;
      customers.delete(customerId);
      console.log(`customer-store-node-multi delete id=${customerId}`);
      publishAudit("delete", customerId, null, "http-fallback");
      sendJson(response, 200, mutation("delete", customerId));
      return;
    }
    if (request.method === "GET" && url.pathname === "/api/customers/runtime") {
      sendJson(response, 200, runtimeDiagnostics());
      return;
    }
    sendJson(response, 404, { error: "not_found", path: url.pathname });
  } catch (error) {
    sendJson(response, 400, { error: "bad_request", detail: error.message });
  }
});

server.listen(8092, "127.0.0.1", () => {
  const info = orchestrator.runtimeInfo();
  console.log(
    `customer-store-node-multi ready http=8092 runtime=${info.runtimeVersion} backend=${info.southboundBackend} target=${localTarget}`,
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
