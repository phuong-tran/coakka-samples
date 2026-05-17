import http from "node:http";
import dns from "node:dns";
import os from "node:os";
import {
  DeliveryHint,
  EndpointFlag,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const WEB_TARGET = "samples.container.node.web";
const STORE_TARGET = "samples.container.python.store";
const MISSING_TARGET = "samples.container.python.missing";

const identities = {
  create: new PayloadIdentity("samples.container.customer.create.request.v1", 1, PayloadFormat.JSON),
  update: new PayloadIdentity("samples.container.customer.update.request.v1", 1, PayloadFormat.JSON),
  delete: new PayloadIdentity("samples.container.customer.delete.request.v1", 1, PayloadFormat.JSON),
  list: new PayloadIdentity("samples.container.customer.list.request.v1", 1, PayloadFormat.JSON),
};

const webHost = process.env.COAKKA_SAMPLE_WEB_HOST || "0.0.0.0";
const webPort = Number(process.env.COAKKA_SAMPLE_WEB_PORT || "8080");
const runtimeBindHost = resolveRuntimeBindHost(process.env.COAKKA_SAMPLE_NODE_RUNTIME_BIND_HOST);
const runtimeHost = process.env.COAKKA_SAMPLE_NODE_RUNTIME_HOST || "node-web";
const runtimePort = Number(process.env.COAKKA_SAMPLE_NODE_RUNTIME_PORT || "19231");
const storeHost = process.env.COAKKA_SAMPLE_STORE_RUNTIME_HOST || "python-store";
const storeRouteHost = resolveRouteHost(storeHost);
const storePort = Number(process.env.COAKKA_SAMPLE_STORE_RUNTIME_PORT || "19232");
const askTimeoutMs = Number(process.env.COAKKA_SAMPLE_ASK_TIMEOUT_MS || "3000");

function resolveRuntimeBindHost(value) {
  if (value && value !== "auto") {
    return value;
  }
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries || []) {
      if (entry.family === "IPv4" && !entry.internal) {
        return entry.address;
      }
    }
  }
  return "127.0.0.1";
}

function resolveRouteHost(host) {
  try {
    return dns.lookupSync(host, { family: 4 }).address;
  } catch {
    return host;
  }
}

const startSpec = {
  systemName: "container-node-web",
  nodeId: "container-node-web",
  queueCapacity: 64,
  strictNoDrop: true,
  generation: 1,
  routes: [
    {
      target: WEB_TARGET,
      endpoints: [{ host: runtimeBindHost, port: runtimePort, flags: EndpointFlag.LOCAL }],
    },
    {
      target: STORE_TARGET,
      endpoints: [{ host: storeRouteHost, port: storePort, flags: EndpointFlag.NONE }],
    },
  ],
};

const runtime = RuntimeHost.start(startSpec);
const runtimeInfo = runtime.runtimeInfo();

function jsonResponse(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
  });
  res.end(body);
}

function htmlResponse(res, body) {
  res.writeHead(200, {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const text = Buffer.concat(chunks).toString("utf8");
      if (!text) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(text));
      } catch (error) {
        reject(new Error(`invalid JSON request body: ${error.message}`));
      }
    });
    req.on("error", reject);
  });
}

async function askStore(payload, identity, operation, target = STORE_TARGET) {
  return runtime.askJson(
    WEB_TARGET,
    target,
    payload,
    identity,
    askTimeoutMs,
    operation,
    DeliveryHint.ROUTER_DEFAULT,
  );
}

function runtimeSnapshot() {
  const stats = runtime.stats();
  const clientStats = runtime.clientStats();
  return {
    runtime: {
      abiVersion: runtimeInfo.abiVersion,
      runtimeVersion: runtimeInfo.runtimeVersion,
      gitCommit: runtimeInfo.gitCommit,
      nodeTarget: WEB_TARGET,
      storeTarget: STORE_TARGET,
      nodeRuntimeEndpoint: `${runtimeHost}:${runtimePort}`,
      storeRuntimeEndpoint: `${storeHost}:${storePort}`,
      remoteDelivery: "enabled",
    },
    counters: {
      generation: stats.appliedGeneration,
      routes: stats.routeCount,
      routeMisses: stats.routeMissCount,
      deadletters: stats.deadletterCount,
      matchedResponses: clientStats.matchedResponses,
      matchedDeadletters: clientStats.matchedDeadletters,
      pending: clientStats.pendingRequests,
    },
  };
}

async function handleApi(req, res, url) {
  if (req.method === "GET" && url.pathname === "/api/runtime") {
    jsonResponse(res, 200, runtimeSnapshot());
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/customers") {
    const response = await askStore({ requestedBy: WEB_TARGET }, identities.list, "list_customers");
    jsonResponse(res, 200, { response, runtime: runtimeSnapshot() });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/customers") {
    const payload = await readBody(req);
    const response = await askStore(payload, identities.create, "create_customer");
    jsonResponse(res, 200, { response, runtime: runtimeSnapshot() });
    return;
  }

  const customerMatch = url.pathname.match(/^\/api\/customers\/([^/]+)$/);
  if (customerMatch && req.method === "PUT") {
    const payload = await readBody(req);
    payload.id = decodeURIComponent(customerMatch[1]);
    const response = await askStore(payload, identities.update, "update_customer");
    jsonResponse(res, 200, { response, runtime: runtimeSnapshot() });
    return;
  }

  if (customerMatch && req.method === "DELETE") {
    const response = await askStore(
      { id: decodeURIComponent(customerMatch[1]) },
      identities.delete,
      "delete_customer",
    );
    jsonResponse(res, 200, { response, runtime: runtimeSnapshot() });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/route-miss") {
    try {
      await askStore({ message: "missing route" }, identities.list, "route_miss", MISSING_TARGET);
      throw new Error("expected route miss deadletter");
    } catch (error) {
      jsonResponse(res, 200, {
        status: "RUNTIME_DELIVERY_FAILED",
        message: error.message,
        runtime: runtimeSnapshot(),
      });
    }
    return;
  }

  jsonResponse(res, 404, { error: "not found" });
}

const page = String.raw`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CoAkka Node Web</title>
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #f6f7f9; color: #20242c; }
    header { background: #ffffff; border-bottom: 1px solid #dde1e8; padding: 18px 24px; }
    main { display: grid; grid-template-columns: minmax(320px, 420px) minmax(0, 1fr); gap: 18px; padding: 18px; }
    h1 { margin: 0; font-size: 22px; font-weight: 700; }
    h2 { margin: 0 0 12px; font-size: 16px; }
    p { margin: 6px 0 0; color: #5f6876; }
    section { background: #fff; border: 1px solid #dde1e8; border-radius: 8px; padding: 16px; }
    label { display: block; margin: 10px 0 4px; font-size: 13px; color: #485160; }
    input, textarea, select { box-sizing: border-box; width: 100%; border: 1px solid #c9d0da; border-radius: 6px; padding: 9px 10px; font: inherit; background: #fff; }
    textarea { min-height: 72px; resize: vertical; }
    .buttons { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
    button { border: 1px solid #b9c2cf; border-radius: 6px; background: #fff; color: #20242c; padding: 9px 12px; font: inherit; cursor: pointer; }
    button.primary { background: #235f9f; color: #fff; border-color: #235f9f; }
    button.danger { color: #9b1c1c; border-color: #d8aaaa; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
    .stack { display: grid; gap: 18px; }
    pre { overflow: auto; margin: 0; min-height: 180px; background: #111827; color: #e5e7eb; border-radius: 8px; padding: 14px; font-size: 13px; line-height: 1.45; }
    .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
    .metric { border: 1px solid #dde1e8; border-radius: 8px; padding: 10px; background: #fafbfc; }
    .metric b { display: block; font-size: 18px; }
    .metric span { display: block; color: #667085; font-size: 12px; margin-top: 2px; }
    .status { color: #667085; font-size: 13px; margin-top: 10px; }
    @media (max-width: 900px) { main { grid-template-columns: 1fr; } .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  </style>
</head>
<body>
  <header>
    <h1>CoAkka Node Web</h1>
    <p>Browser edge on HTTP, customer traffic on CoAkka runtime to Python store.</p>
  </header>
  <main>
    <section>
      <h2>Customer Command</h2>
      <label for="customer-id">ID</label>
      <input id="customer-id" value="cus_001">
      <label for="customer-name">Name</label>
      <input id="customer-name" value="Ada">
      <label for="customer-email">Email</label>
      <input id="customer-email" value="ada@example.com">
      <label for="customer-tier">Tier</label>
      <select id="customer-tier">
        <option>silver</option>
        <option>gold</option>
        <option>platinum</option>
      </select>
      <label for="customer-notes">Notes</label>
      <textarea id="customer-notes">created from Node web UI</textarea>
      <div class="buttons">
        <button class="primary" id="create">Create</button>
        <button id="update">Update</button>
        <button id="list">List</button>
        <button class="danger" id="delete">Delete</button>
        <button id="route-miss">Route miss</button>
      </div>
      <div class="status" id="status">Ready</div>
    </section>
    <div class="stack">
      <section>
        <h2>Runtime Counters</h2>
        <div class="metrics" id="metrics"></div>
      </section>
      <section>
        <h2>Last Runtime Result</h2>
        <pre id="output">{}</pre>
      </section>
    </div>
  </main>
  <script>
    const $ = (id) => document.getElementById(id);
    const output = $("output");
    const status = $("status");
    const metrics = $("metrics");

    function payload() {
      return {
        id: $("customer-id").value,
        name: $("customer-name").value,
        email: $("customer-email").value,
        tier: $("customer-tier").value,
        notes: $("customer-notes").value,
      };
    }

    function render(data) {
      output.textContent = JSON.stringify(data, null, 2);
      const counters = data.runtime?.counters || data.counters || {};
      metrics.innerHTML = [
        ["generation", counters.generation],
        ["matched responses", counters.matchedResponses],
        ["deadletters", counters.deadletters],
        ["route misses", counters.routeMisses],
      ].map(([label, value]) => '<div class="metric"><b>' + (value ?? "-") + '</b><span>' + label + '</span></div>').join("");
    }

    async function request(label, url, options = {}) {
      status.textContent = label + "...";
      try {
        const response = await fetch(url, options);
        const data = await response.json();
        render(data);
        status.textContent = response.ok ? label + " complete" : label + " failed";
      } catch (error) {
        status.textContent = label + " failed";
        output.textContent = JSON.stringify({ error: error.message }, null, 2);
      }
    }

    $("create").onclick = () => request("Create", "/api/customers", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload()),
    });
    $("update").onclick = () => request("Update", "/api/customers/" + encodeURIComponent($("customer-id").value), {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload()),
    });
    $("delete").onclick = () => request("Delete", "/api/customers/" + encodeURIComponent($("customer-id").value), { method: "DELETE" });
    $("list").onclick = () => request("List", "/api/customers");
    $("route-miss").onclick = () => request("Route miss", "/api/route-miss", { method: "POST" });

    request("Load runtime", "/api/runtime");
  </script>
</body>
</html>`;

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    if (req.method === "GET" && url.pathname === "/") {
      htmlResponse(res, page);
      return;
    }
    if (url.pathname.startsWith("/api/")) {
      await handleApi(req, res, url);
      return;
    }
    jsonResponse(res, 404, { error: "not found" });
  } catch (error) {
    jsonResponse(res, 500, {
      error: error.message,
      runtime: runtimeSnapshot(),
    });
  }
});

server.listen(webPort, webHost, () => {
  console.log(
    `node-web | ready: http://localhost:${webPort} runtime=${runtimeInfo.runtimeVersion} ` +
      `nodeTarget=${WEB_TARGET} storeTarget=${STORE_TARGET}`,
  );
});

function shutdown() {
  server.close(() => {
    runtime.close();
    process.exit(0);
  });
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
