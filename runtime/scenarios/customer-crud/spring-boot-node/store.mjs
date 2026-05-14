import {
  EndpointFlag,
  NodeRuntimeClient,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const localTarget = "samples.customer.store";
const peerTarget = "samples.customer.frontend";
const identities = {
  create: new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
  update: new PayloadIdentity("samples.customer.update.request.v1", 1, PayloadFormat.JSON),
  delete: new PayloadIdentity("samples.customer.delete.request.v1", 1, PayloadFormat.JSON),
  list: new PayloadIdentity("samples.customer.list.request.v1", 1, PayloadFormat.JSON),
  mutationResponse: new PayloadIdentity("samples.customer.mutation.response.v1", 1, PayloadFormat.JSON),
  listResponse: new PayloadIdentity("samples.customer.list.response.v1", 1, PayloadFormat.JSON),
};

const customers = new Map();
let revision = 0;

// Runtime route table for the Node.js store process.
//
// The store owns localTarget, so that endpoint is LOCAL. peerTarget points to
// the Spring Boot web process and is intentionally non-local. queueCapacity=128
// keeps the sample bounded, strictNoDrop=true makes pressure visible, and
// generation=1 marks the first static route snapshot applied at startup.
const startSpec = {
  systemName: "customer-store-node",
  nodeId: "customer-store-node",
  queueCapacity: 128,
  strictNoDrop: true,
  generation: 1,
  routes: [
    {
      target: localTarget,
      endpoints: [{ host: "127.0.0.1", port: 19112, flags: EndpointFlag.LOCAL }],
    },
    {
      target: peerTarget,
      endpoints: [{ host: "127.0.0.1", port: 19111, flags: EndpointFlag.NONE }],
    },
  ],
};

const runtime = RuntimeHost.start(startSpec);
runtime.registerHandler(localTarget, (request) => {
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
    handledBy: "customer-store-node",
  };
}

function upsertCustomer(operation, customer) {
  revision += 1;
  customers.set(customer.id, {
    id: customer.id,
    name: customer.name,
    email: customer.email,
    tier: customer.tier,
    notes: customer.notes || "",
    revision,
  });
  console.log(`customer-store-node ${operation} id=${customer.id} tier=${customer.tier}`);
  return mutation(operation, customer.id);
}

function handleRuntimeRequest(messageType, payload, correlationId) {
  switch (messageType) {
    case identities.create.messageType:
      return { payload: upsertCustomer("create", payload), identity: identities.mutationResponse };
    case identities.update.messageType:
      return { payload: upsertCustomer("update", payload), identity: identities.mutationResponse };
    case identities.delete.messageType:
      revision += 1;
      customers.delete(payload.id);
      console.log(`customer-store-node delete id=${payload.id} correlation=${correlationId}`);
      return { payload: mutation("delete", payload.id), identity: identities.mutationResponse };
    case identities.list.messageType:
      console.log(`customer-store-node list correlation=${correlationId}`);
      return { payload: { customers: customerList() }, identity: identities.listResponse };
    default:
      throw new Error(`unsupported customer message type: ${messageType}`);
  }
}

const info = runtime.runtimeInfo();
console.log(
  `customer-store-node ready headless runtime=${info.runtimeVersion} target=${localTarget}`,
);
process.stdin.resume();

function shutdown() {
  runtime.close();
  process.exit(0);
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
