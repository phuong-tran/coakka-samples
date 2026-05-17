from __future__ import annotations

import json
import os
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from coakka_v2_connector import (
    ConnectorStartSpec,
    EndpointFlag,
    EndpointSpec,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    RouteSpec,
)

WEB_TARGET = "samples.container.node.web"
STORE_TARGET = "samples.container.python.store"

CREATE_REQUEST = "samples.container.customer.create.request.v1"
UPDATE_REQUEST = "samples.container.customer.update.request.v1"
DELETE_REQUEST = "samples.container.customer.delete.request.v1"
LIST_REQUEST = "samples.container.customer.list.request.v1"
MUTATION_RESPONSE = PayloadIdentity("samples.container.customer.mutation.response.v1", 1, PayloadFormat.JSON)
LIST_RESPONSE = PayloadIdentity("samples.container.customer.list.response.v1", 1, PayloadFormat.JSON)


def env_int(name: str, default: int) -> int:
    return int(os.environ.get(name, str(default)))


def resolve_runtime_bind_host(name: str) -> str:
    value = os.environ.get(name, "auto")
    if value and value != "auto":
        return value
    return socket.gethostbyname(socket.gethostname())


def decode_payload(request: Any) -> dict[str, Any]:
    text = request.payload.decode("utf-8")
    return json.loads(text) if text else {}


class CustomerStore:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._customers: dict[str, dict[str, Any]] = {}
        self._events: list[dict[str, Any]] = []
        self._revision = 0

    def upsert(self, operation: str, customer: dict[str, Any], correlation_id: str) -> dict[str, Any]:
        with self._lock:
            self._revision += 1
            view = {
                "id": customer["id"],
                "name": customer["name"],
                "email": customer.get("email", ""),
                "tier": customer.get("tier", "silver"),
                "notes": customer.get("notes", ""),
                "revision": self._revision,
            }
            self._customers[view["id"]] = view
            self._remember(operation, view["id"], correlation_id)
            return self._mutation(operation, view["id"])

    def delete(self, customer_id: str, correlation_id: str) -> dict[str, Any]:
        with self._lock:
            self._revision += 1
            self._customers.pop(customer_id, None)
            self._remember("delete", customer_id, correlation_id)
            return self._mutation("delete", customer_id)

    def list_payload(self) -> dict[str, Any]:
        with self._lock:
            return {
                "customers": sorted(
                    (customer.copy() for customer in self._customers.values()),
                    key=lambda customer: customer["id"],
                ),
                "revision": self._revision,
                "handledBy": "python-store",
                "deliveryMode": "runtime",
            }

    def state(self) -> dict[str, Any]:
        with self._lock:
            return {
                "customers": sorted(
                    (customer.copy() for customer in self._customers.values()),
                    key=lambda customer: customer["id"],
                ),
                "events": list(self._events),
                "revision": self._revision,
            }

    def _mutation(self, operation: str, customer_id: str) -> dict[str, Any]:
        return {
            "status": "ACCEPTED",
            "operation": operation,
            "customerId": customer_id,
            "revision": self._revision,
            "handledBy": "python-store",
            "deliveryMode": "runtime",
        }

    def _remember(self, operation: str, customer_id: str, correlation_id: str) -> None:
        self._events.insert(
            0,
            {
                "operation": operation,
                "customerId": customer_id,
                "revision": self._revision,
                "correlationId": correlation_id,
                "observedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            },
        )
        del self._events[20:]


def make_store_page() -> str:
    return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CoAkka Python Store</title>
  <style>
    :root { font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #20242c; background: #f6f7f9; }
    body { margin: 0; }
    header { background: #fff; border-bottom: 1px solid #dde1e8; padding: 18px 24px; }
    main { padding: 18px; display: grid; gap: 18px; }
    section { background: #fff; border: 1px solid #dde1e8; border-radius: 8px; padding: 16px; }
    h1 { margin: 0; font-size: 22px; }
    h2 { margin: 0 0 12px; font-size: 16px; }
    p { margin: 6px 0 0; color: #5f6876; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; border-bottom: 1px solid #e4e7ec; padding: 9px 8px; font-size: 14px; }
    th { color: #4b5563; background: #fafbfc; }
    pre { overflow: auto; margin: 0; background: #111827; color: #e5e7eb; border-radius: 8px; padding: 14px; font-size: 13px; line-height: 1.45; }
    .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
    .metric { border: 1px solid #dde1e8; border-radius: 8px; padding: 10px; background: #fafbfc; }
    .metric b { display: block; font-size: 18px; }
    .metric span { display: block; color: #667085; font-size: 12px; margin-top: 2px; }
    @media (max-width: 800px) { .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  </style>
</head>
<body>
  <header>
    <h1>CoAkka Python Store</h1>
    <p>Read-only view of state changed by runtime messages from Node.</p>
  </header>
  <main>
    <section>
      <h2>Runtime Counters</h2>
      <div class="metrics" id="metrics"></div>
    </section>
    <section>
      <h2>Customers</h2>
      <table>
        <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Tier</th><th>Revision</th></tr></thead>
        <tbody id="customers"></tbody>
      </table>
    </section>
    <section>
      <h2>Recent Runtime Messages</h2>
      <pre id="events">[]</pre>
    </section>
  </main>
  <script>
    const customers = document.getElementById("customers");
    const metrics = document.getElementById("metrics");
    const events = document.getElementById("events");
    function escapeHtml(value) {
      return String(value ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
    }
    function render(data) {
      const counters = data.runtime.counters;
      metrics.innerHTML = [
        ["generation", counters.generation],
        ["delivered", counters.delivered],
        ["deadletters", counters.deadletters],
        ["route misses", counters.routeMisses],
      ].map(([label, value]) => '<div class="metric"><b>' + value + '</b><span>' + label + '</span></div>').join("");
      customers.innerHTML = data.store.customers.map((customer) =>
        '<tr><td>' + escapeHtml(customer.id) + '</td><td>' + escapeHtml(customer.name) + '</td><td>' +
        escapeHtml(customer.email) + '</td><td>' + escapeHtml(customer.tier) + '</td><td>' +
        escapeHtml(customer.revision) + '</td></tr>'
      ).join("") || '<tr><td colspan="5">No customers yet</td></tr>';
      events.textContent = JSON.stringify(data.store.events, null, 2);
    }
    async function refresh() {
      const response = await fetch("/api/state", { cache: "no-store" });
      render(await response.json());
    }
    refresh();
    setInterval(refresh, 1000);
  </script>
</body>
</html>"""


def make_handler(store: CustomerStore, runtime: RuntimeHost):
    class StoreHttpHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path == "/" or self.path == "/index.html":
                body = make_store_page().encode("utf-8")
                self.send_response(200)
                self.send_header("content-type", "text/html; charset=utf-8")
                self.send_header("content-length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if self.path == "/api/state":
                stats = runtime.stats()
                client_stats = runtime.client_stats()
                info = runtime.runtime_info()
                body = json.dumps(
                    {
                        "runtime": {
                            "info": {
                                "runtimeVersion": info["runtimeVersion"],
                                "gitCommit": info["gitCommit"],
                                "storeTarget": STORE_TARGET,
                                "webTarget": WEB_TARGET,
                                "remoteDelivery": "enabled",
                            },
                            "counters": {
                                "generation": stats["appliedGeneration"],
                                "routes": stats["routeCount"],
                                "delivered": client_stats.delivered_requests,
                                "matchedResponses": client_stats.matched_responses,
                                "matchedDeadletters": client_stats.matched_deadletters,
                                "routeMisses": stats["routeMissCount"],
                                "deadletters": stats["deadletterCount"],
                            },
                        },
                        "store": store.state(),
                    },
                    separators=(",", ":"),
                ).encode("utf-8")
                self.send_response(200)
                self.send_header("content-type", "application/json; charset=utf-8")
                self.send_header("cache-control", "no-store")
                self.send_header("content-length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            self.send_response(404)
            self.end_headers()

        def log_message(self, format: str, *args: Any) -> None:
            return

    return StoreHttpHandler


def main() -> None:
    http_host = os.environ.get("COAKKA_SAMPLE_STORE_WEB_HOST", "0.0.0.0")
    http_port = env_int("COAKKA_SAMPLE_STORE_WEB_PORT", 8081)
    store_runtime_bind_host = resolve_runtime_bind_host("COAKKA_SAMPLE_STORE_RUNTIME_BIND_HOST")
    store_runtime_host = os.environ.get("COAKKA_SAMPLE_STORE_RUNTIME_HOST", "python-store")
    store_runtime_port = env_int("COAKKA_SAMPLE_STORE_RUNTIME_PORT", 19232)
    node_runtime_host = os.environ.get("COAKKA_SAMPLE_NODE_RUNTIME_HOST", "node-web")
    node_route_host = socket.gethostbyname(node_runtime_host)
    node_runtime_port = env_int("COAKKA_SAMPLE_NODE_RUNTIME_PORT", 19231)
    store = CustomerStore()

    start_spec = ConnectorStartSpec(
        system_name="container-python-store",
        node_id="container-python-store",
        queue_capacity=64,
        strict_no_drop=True,
        generation=1,
        routes=[
            RouteSpec(
                target=STORE_TARGET,
                endpoints=[
                    EndpointSpec(
                        host=store_runtime_bind_host,
                        port=store_runtime_port,
                        flags=int(EndpointFlag.LOCAL),
                    )
                ],
            ),
            RouteSpec(
                target=WEB_TARGET,
                endpoints=[
                    EndpointSpec(
                        host=node_route_host,
                        port=node_runtime_port,
                        flags=int(EndpointFlag.NONE),
                    )
                ],
            ),
        ],
    )

    with RuntimeHost.start(start_spec=start_spec) as runtime:
        info = runtime.runtime_info()

        def handle_customer(request: Any) -> Any:
            payload = decode_payload(request)
            if request.message_type == CREATE_REQUEST:
                response = store.upsert("create", payload, request.correlation_id)
                identity = MUTATION_RESPONSE
            elif request.message_type == UPDATE_REQUEST:
                response = store.upsert("update", payload, request.correlation_id)
                identity = MUTATION_RESPONSE
            elif request.message_type == DELETE_REQUEST:
                response = store.delete(payload["id"], request.correlation_id)
                identity = MUTATION_RESPONSE
            elif request.message_type == LIST_REQUEST:
                response = store.list_payload()
                identity = LIST_RESPONSE
            else:
                raise RuntimeError(f"unsupported message type: {request.message_type}")

            print(
                f"python-store | handled: type={request.message_type} "
                f"correlation={request.correlation_id}",
                flush=True,
            )
            return runtime.client.make_json_reply(
                request=request,
                source=STORE_TARGET,
                payload=response,
                payload_identity=identity,
            )

        runtime.register_handler(STORE_TARGET, handle_customer)
        server = ThreadingHTTPServer((http_host, http_port), make_handler(store, runtime))
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        print(
            f"python-store | ready: http://localhost:{http_port} runtime={info['runtimeVersion']} "
            f"storeTarget={STORE_TARGET} endpoint={store_runtime_host}:{store_runtime_port}",
            flush=True,
        )

        try:
            while True:
                time.sleep(3600)
        finally:
            server.shutdown()


if __name__ == "__main__":
    main()
