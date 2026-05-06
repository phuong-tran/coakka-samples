from __future__ import annotations

import argparse
import json
import queue
import threading
import time
from dataclasses import dataclass, field
from typing import Any

from coakka_v2_connector import (
    ConnectorOrchestrator,
    ConnectorStartSpec,
    DeadletterError,
    DeliveryHint,
    EndpointFlag,
    EndpointSpec,
    PayloadFormat,
    PayloadIdentity,
    RouteSpec,
)

FRONTEND_TARGET = "samples.customer.frontend"
STORE_TARGET = "samples.customer.store"
STORE_HOST = "127.0.0.1"
FRONTEND_PORT = 19161
STORE_PORT = 19162
GENERATION = 1
ROUTE_MISS_REASON = 2

CREATE_REQUEST = "samples.customer.create.request.v1"
UPDATE_REQUEST = "samples.customer.update.request.v1"
DELETE_REQUEST = "samples.customer.delete.request.v1"
LIST_REQUEST = "samples.customer.list.request.v1"
MUTATION_RESPONSE = "samples.customer.mutation.response.v1"
LIST_RESPONSE = "samples.customer.list.response.v1"


def identity(message_type: str) -> PayloadIdentity:
    return PayloadIdentity(
        message_type=message_type,
        payload_schema_version=1,
        payload_format=PayloadFormat.JSON,
    )


CREATE_IDENTITY = identity(CREATE_REQUEST)
UPDATE_IDENTITY = identity(UPDATE_REQUEST)
DELETE_IDENTITY = identity(DELETE_REQUEST)
LIST_IDENTITY = identity(LIST_REQUEST)
MUTATION_RESPONSE_IDENTITY = identity(MUTATION_RESPONSE)
LIST_RESPONSE_IDENTITY = identity(LIST_RESPONSE)

tk: Any = None
ttk: Any = None
messagebox: Any = None


def load_tk() -> None:
    global messagebox, tk, ttk
    if tk is not None:
        return

    import tkinter as tk_module
    from tkinter import messagebox as messagebox_module
    from tkinter import ttk as ttk_module

    tk = tk_module
    ttk = ttk_module
    messagebox = messagebox_module


@dataclass
class CustomerStore:
    customers: dict[str, dict[str, Any]] = field(default_factory=dict)
    revision: int = 0
    lock: threading.RLock = field(default_factory=threading.RLock)

    def create(self, customer: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            self.revision += 1
            self.customers[customer["id"]] = {
                "id": customer["id"],
                "name": customer["name"],
                "email": customer["email"],
                "tier": customer["tier"],
                "notes": customer.get("notes", ""),
                "revision": self.revision,
            }
            return self._mutation("create", customer["id"])

    def update(self, customer: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            self.revision += 1
            self.customers[customer["id"]] = {
                "id": customer["id"],
                "name": customer["name"],
                "email": customer["email"],
                "tier": customer["tier"],
                "notes": customer.get("notes", ""),
                "revision": self.revision,
            }
            return self._mutation("update", customer["id"])

    def delete(self, customer_id: str) -> dict[str, Any]:
        with self.lock:
            self.customers.pop(customer_id, None)
            self.revision += 1
            return self._mutation("delete", customer_id)

    def list(self) -> dict[str, Any]:
        with self.lock:
            return {
                "customers": sorted(
                    (customer.copy() for customer in self.customers.values()),
                    key=lambda customer: (customer["id"], customer["revision"]),
                ),
                "deliveryMode": "runtime",
            }

    def _mutation(self, operation: str, customer_id: str) -> dict[str, Any]:
        return {
            "status": "ACCEPTED",
            "operation": operation,
            "customerId": customer_id,
            "revision": self.revision,
            "handledBy": "customer-python-desktop-store-local-handler",
            "deliveryMode": "runtime",
        }


class CustomerDesktopRuntime:
    def __init__(self) -> None:
        store_spec = ConnectorStartSpec(
            system_name="customer-python-desktop-store",
            node_id="customer-python-desktop-store-node",
            queue_capacity=128,
            strict_no_drop=True,
            separate_delivered_request_lane=True,
            generation=GENERATION,
            routes=[
                RouteSpec(
                    target=STORE_TARGET,
                    endpoints=[
                        EndpointSpec(
                            host=STORE_HOST,
                            port=STORE_PORT,
                            flags=int(EndpointFlag.LOCAL),
                        )
                    ],
                ),
                RouteSpec(
                    target=FRONTEND_TARGET,
                    endpoints=[
                        EndpointSpec(
                            host=STORE_HOST,
                            port=FRONTEND_PORT,
                        )
                    ],
                ),
            ],
        )
        frontend_spec = ConnectorStartSpec(
            system_name="customer-python-desktop-frontend",
            node_id="customer-python-desktop-frontend-node",
            queue_capacity=128,
            strict_no_drop=True,
            separate_delivered_request_lane=True,
            generation=GENERATION,
            routes=[
                RouteSpec(
                    target=FRONTEND_TARGET,
                    endpoints=[
                        EndpointSpec(
                            host=STORE_HOST,
                            port=FRONTEND_PORT,
                            flags=int(EndpointFlag.LOCAL),
                        )
                    ],
                ),
                RouteSpec(
                    target=STORE_TARGET,
                    endpoints=[
                        EndpointSpec(
                            host=STORE_HOST,
                            port=STORE_PORT,
                        )
                    ],
                )
            ],
        )
        self._store = CustomerStore()
        self._store_orchestrator = ConnectorOrchestrator.start(start_spec=store_spec)
        self._frontend_orchestrator = ConnectorOrchestrator.start(start_spec=frontend_spec)
        self._closed = False
        self._store_orchestrator.register_handler(STORE_TARGET, self._handle_store_request)

    @property
    def orchestrator(self) -> ConnectorOrchestrator:
        return self._frontend_orchestrator

    @property
    def store_orchestrator(self) -> ConnectorOrchestrator:
        return self._store_orchestrator

    def close(self) -> None:
        if not self._closed:
            self._closed = True
            self._frontend_orchestrator.close()
            self._store_orchestrator.close()

    def create(self, customer: dict[str, Any]) -> dict[str, Any]:
        return self._ask(customer, CREATE_IDENTITY, "create_customer")

    def update(self, customer: dict[str, Any]) -> dict[str, Any]:
        return self._ask(customer, UPDATE_IDENTITY, "update_customer")

    def delete(self, customer_id: str) -> dict[str, Any]:
        return self._ask({"id": customer_id}, DELETE_IDENTITY, "delete_customer")

    def list(self) -> dict[str, Any]:
        return self._ask({"requestedBy": "customer-python-desktop-local"}, LIST_IDENTITY, "list_customers")

    def route_miss(self) -> dict[str, Any]:
        try:
            self._frontend_orchestrator.ask_json(
                source=FRONTEND_TARGET,
                target="samples.customer.missing",
                payload={"message": "missing customer route"},
                payload_identity=LIST_IDENTITY,
                timeout_ms=2000,
                operation="route_miss",
                delivery_hint=DeliveryHint.ROUTER_DEFAULT,
            )
            raise RuntimeError("expected route miss deadletter")
        except DeadletterError as error:
            deadletter = error.deadletter
            return {
                "reason": "DEADLETTER_REASON_ROUTE_MISS"
                if deadletter.reason == ROUTE_MISS_REASON
                else str(deadletter.reason),
                "target": deadletter.original_envelope.target,
                "generation": deadletter.active_generation,
            }

    def diagnostics_text(self) -> str:
        frontend_info = self._frontend_orchestrator.runtime_info()
        store_info = self._store_orchestrator.runtime_info()
        frontend_config = self._frontend_orchestrator.runtime_config()
        store_config = self._store_orchestrator.runtime_config()
        frontend_stats = self._frontend_orchestrator.stats()
        store_stats = self._store_orchestrator.stats()
        client_stats = self._frontend_orchestrator.client_stats()
        store_client_stats = self._store_orchestrator.client_stats()
        return "\n".join(
            [
                "Frontend runtime",
                f"  abi: {frontend_info['abiVersion']}",
                f"  version: {frontend_info['runtimeVersion']}",
                f"  git: {frontend_info['gitCommit']}",
                f"  backend: {frontend_info['southboundBackend']}",
                f"  system: {frontend_config['systemName']}",
                f"  node: {frontend_config['nodeId']}",
                f"  generation: {frontend_config['appliedGeneration']}",
                "",
                "Store runtime",
                f"  abi: {store_info['abiVersion']}",
                f"  version: {store_info['runtimeVersion']}",
                f"  git: {store_info['gitCommit']}",
                f"  backend: {store_info['southboundBackend']}",
                f"  system: {store_config['systemName']}",
                f"  node: {store_config['nodeId']}",
                f"  generation: {store_config['appliedGeneration']}",
                "",
                "Route snapshot",
                f"  frontend endpoint: {STORE_HOST}:{FRONTEND_PORT} LOCAL on frontend runtime",
                f"  store endpoint: {STORE_HOST}:{STORE_PORT} LOCAL on store runtime",
                f"  frontend routes: {frontend_config['routeCount']}",
                f"  store routes: {store_config['routeCount']}",
                "",
                "Counters",
                f"  delivered: {client_stats.delivered_requests}",
                f"  matched responses: {client_stats.matched_responses}",
                f"  matched deadletters: {client_stats.matched_deadletters}",
                f"  pending: {client_stats.pending_requests}",
                f"  frontend route misses: {frontend_stats['routeMissCount']}",
                f"  frontend deadletters: {frontend_stats['deadletterCount']}",
                f"  store delivered requests: {store_client_stats.delivered_requests}",
                f"  store deadletters: {store_stats['deadletterCount']}",
            ]
        )

    def _ask(self, payload: dict[str, Any], payload_identity: PayloadIdentity, operation: str) -> dict[str, Any]:
        return self._frontend_orchestrator.ask_json(
            source=FRONTEND_TARGET,
            target=STORE_TARGET,
            payload=payload,
            payload_identity=payload_identity,
            timeout_ms=5000,
            operation=operation,
            delivery_hint=DeliveryHint.REQUIRE_REMOTE,
        )

    def _handle_store_request(self, request: Any) -> Any:
        payload = json.loads(request.payload.decode("utf-8"))
        if request.message_type == CREATE_REQUEST:
            response = self._store.create(payload)
            response_identity = MUTATION_RESPONSE_IDENTITY
        elif request.message_type == UPDATE_REQUEST:
            response = self._store.update(payload)
            response_identity = MUTATION_RESPONSE_IDENTITY
        elif request.message_type == DELETE_REQUEST:
            response = self._store.delete(payload["id"])
            response_identity = MUTATION_RESPONSE_IDENTITY
        elif request.message_type == LIST_REQUEST:
            response = self._store.list()
            response_identity = LIST_RESPONSE_IDENTITY
        else:
            raise RuntimeError(f"unsupported customer message type: {request.message_type}")

        return self._store_orchestrator.client.make_json_reply(
            request=request,
            source=STORE_TARGET,
            payload=response,
            payload_identity=response_identity,
        )


class CustomerDesktopApp:
    def __init__(self, runtime: CustomerDesktopRuntime) -> None:
        load_tk()
        self.runtime = runtime
        self.events: queue.Queue[tuple[str, Any]] = queue.Queue()
        self.root = tk.Tk()
        self.root.title("CoAkka Python Desktop Local Runtime")
        self.root.geometry("1120x720")
        self.root.minsize(980, 640)
        self.root.protocol("WM_DELETE_WINDOW", self.close)

        self.id_var = tk.StringVar(value="cust-001")
        self.name_var = tk.StringVar(value="Ada Lovelace")
        self.email_var = tk.StringVar(value="ada@example.com")
        self.tier_var = tk.StringVar(value="silver")
        self.notes_var = tk.StringVar(value="python desktop local runtime")
        self._build_ui()
        self._start_background_poll()
        self.refresh_customers("startup")
        self.refresh_diagnostics()

    def run(self) -> None:
        self.root.mainloop()

    def close(self) -> None:
        self.runtime.close()
        self.root.destroy()

    def _build_ui(self) -> None:
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(1, weight=1)

        header = ttk.Frame(self.root, padding=(14, 12, 14, 8))
        header.grid(row=0, column=0, sticky="ew")
        ttk.Label(header, text="Customer Python Desktop Local Runtime", font=("", 20, "bold")).pack(anchor="w")
        ttk.Label(
            header,
            text=f"Desktop UI -> {FRONTEND_TARGET} -> CoAkka runtime ask -> {STORE_TARGET} -> reply",
        ).pack(anchor="w", pady=(6, 0))
        ttk.Label(
            header,
            text="One Python process, two runtime handles, frontend talks to store by runtime messages, no store REST API.",
        ).pack(anchor="w", pady=(2, 0))

        body = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        body.grid(row=1, column=0, sticky="nsew", padx=14, pady=(0, 14))

        left = ttk.Frame(body)
        right = ttk.Frame(body)
        body.add(left, weight=3)
        body.add(right, weight=2)

        form = ttk.LabelFrame(left, text="Customer command", padding=10)
        form.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        for index, (label, variable) in enumerate(
            [
                ("ID", self.id_var),
                ("Name", self.name_var),
                ("Email", self.email_var),
                ("Tier", self.tier_var),
                ("Notes", self.notes_var),
            ]
        ):
            ttk.Label(form, text=label).grid(row=index, column=0, sticky="w", pady=4)
            ttk.Entry(form, textvariable=variable, width=30).grid(row=index, column=1, sticky="ew", pady=4)

        buttons = ttk.Frame(form)
        buttons.grid(row=6, column=0, columnspan=2, sticky="w", pady=(12, 0))
        ttk.Button(buttons, text="Create", command=lambda: self.mutate("create", self.runtime.create)).pack(
            side=tk.LEFT, padx=(0, 6)
        )
        ttk.Button(buttons, text="Update", command=lambda: self.mutate("update", self.runtime.update)).pack(
            side=tk.LEFT, padx=(0, 6)
        )
        ttk.Button(buttons, text="Delete", command=self.delete).pack(side=tk.LEFT, padx=(0, 6))
        ttk.Button(buttons, text="Refresh", command=lambda: self.refresh_customers("manual_refresh")).pack(
            side=tk.LEFT, padx=(0, 6)
        )
        ttk.Button(buttons, text="Route Miss", command=self.route_miss).pack(side=tk.LEFT)

        table_frame = ttk.Frame(left)
        table_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        columns = ("id", "name", "email", "tier", "notes", "revision")
        self.table = ttk.Treeview(table_frame, columns=columns, show="headings", height=20)
        for column in columns:
            self.table.heading(column, text=column.title())
            self.table.column(column, minwidth=70, width=120 if column != "notes" else 180, stretch=True)
        self.table.pack(fill=tk.BOTH, expand=True)

        diagnostics_frame = ttk.LabelFrame(right, text="Runtime diagnostics", padding=8)
        diagnostics_frame.pack(fill=tk.BOTH, expand=True)
        self.diagnostics = tk.Text(diagnostics_frame, height=18, wrap=tk.NONE, font=("Menlo", 12))
        self.diagnostics.pack(fill=tk.BOTH, expand=True)

        log_frame = ttk.LabelFrame(right, text="Message log", padding=8)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=(10, 0))
        self.log = tk.Text(log_frame, height=10, wrap=tk.WORD, font=("Menlo", 12))
        self.log.pack(fill=tk.BOTH, expand=True)

    def _start_background_poll(self) -> None:
        self.root.after(100, self._drain_events)

    def _drain_events(self) -> None:
        while True:
            try:
                kind, payload = self.events.get_nowait()
            except queue.Empty:
                break
            if kind == "error":
                messagebox.showerror("Runtime operation failed", str(payload))
                self.append_log(f"error {payload}")
                self.refresh_diagnostics()
            elif kind == "customers":
                self.render_customers(payload["customers"])
                self.append_log(
                    f"list after {payload['source']} count={len(payload['customers'])} via {payload['deliveryMode']}"
                )
                self.refresh_diagnostics()
            elif kind == "log":
                self.append_log(payload)
                self.refresh_diagnostics()
        self.root.after(100, self._drain_events)

    def read_customer(self) -> dict[str, Any]:
        return {
            "id": self.id_var.get().strip(),
            "name": self.name_var.get().strip(),
            "email": self.email_var.get().strip(),
            "tier": self.tier_var.get().strip(),
            "notes": self.notes_var.get().strip(),
        }

    def mutate(self, label: str, action: Any) -> None:
        customer = self.read_customer()

        def work() -> None:
            response = action(customer)
            self.events.put(
                (
                    "log",
                    f"{label} accepted id={response['customerId']} revision={response['revision']} "
                    f"via {response['deliveryMode']}",
                )
            )
            self.refresh_customers(label)

        self.run_background(work)

    def delete(self) -> None:
        customer_id = self.id_var.get().strip()

        def work() -> None:
            response = self.runtime.delete(customer_id)
            self.events.put(
                (
                    "log",
                    f"delete accepted id={response['customerId']} revision={response['revision']} "
                    f"via {response['deliveryMode']}",
                )
            )
            self.refresh_customers("delete")

        self.run_background(work)

    def refresh_customers(self, source: str) -> None:
        def work() -> None:
            response = self.runtime.list()
            self.events.put(
                (
                    "customers",
                    {
                        "source": source,
                        "customers": response["customers"],
                        "deliveryMode": response["deliveryMode"],
                    },
                )
            )

        self.run_background(work)

    def route_miss(self) -> None:
        def work() -> None:
            deadletter = self.runtime.route_miss()
            self.events.put(
                (
                    "log",
                    f"deadletter reason={deadletter['reason']} target={deadletter['target']} "
                    f"generation={deadletter['generation']}",
                )
            )

        self.run_background(work)

    def run_background(self, action: Any) -> None:
        def wrapped() -> None:
            try:
                action()
            except Exception as error:  # noqa: BLE001 - UI must show runtime failures directly.
                self.events.put(("error", error))

        threading.Thread(target=wrapped, daemon=True).start()

    def render_customers(self, customers: list[dict[str, Any]]) -> None:
        self.table.delete(*self.table.get_children())
        for customer in customers:
            self.table.insert(
                "",
                tk.END,
                values=(
                    customer["id"],
                    customer["name"],
                    customer["email"],
                    customer["tier"],
                    customer.get("notes", ""),
                    customer["revision"],
                ),
            )

    def refresh_diagnostics(self) -> None:
        self.diagnostics.delete("1.0", tk.END)
        self.diagnostics.insert(tk.END, self.runtime.diagnostics_text())

    def append_log(self, message: str) -> None:
        self.log.insert(tk.END, f"{time.strftime('%H:%M:%S')} {message}\n")
        self.log.see(tk.END)


def run_smoke() -> None:
    runtime = CustomerDesktopRuntime()
    try:
        info = runtime.orchestrator.runtime_info()
        print(
            f"coakka_python_desktop_runtime_info version={info['runtimeVersion']} "
            f"git={info['gitCommit']} backend={info['southboundBackend']}"
        )
        create = runtime.create(
            {
                "id": "cust-001",
                "name": "Ada Lovelace",
                "email": "ada@example.com",
                "tier": "silver",
                "notes": "python-desktop-smoke",
            }
        )
        if create["deliveryMode"] != "runtime":
            raise RuntimeError("expected runtime delivery for create")
        print(f"coakka_python_desktop_create status={create['status']} revision={create['revision']}")

        update = runtime.update(
            {
                "id": "cust-001",
                "name": "Ada Lovelace",
                "email": "ada@example.com",
                "tier": "gold",
                "notes": "python-desktop-smoke-updated",
            }
        )
        if update["deliveryMode"] != "runtime":
            raise RuntimeError("expected runtime delivery for update")
        print(f"coakka_python_desktop_update status={update['status']} revision={update['revision']}")

        listed = runtime.list()
        if len(listed["customers"]) != 1 or listed["customers"][0]["tier"] != "gold":
            raise RuntimeError(f"expected one updated customer, got {listed}")
        print(f"coakka_python_desktop_list count={len(listed['customers'])} tier={listed['customers'][0]['tier']}")

        delete = runtime.delete("cust-001")
        if delete["deliveryMode"] != "runtime":
            raise RuntimeError("expected runtime delivery for delete")
        print(f"coakka_python_desktop_delete status={delete['status']} revision={delete['revision']}")

        after_delete = runtime.list()
        if after_delete["customers"]:
            raise RuntimeError(f"expected empty list after delete, got {after_delete}")

        deadletter = runtime.route_miss()
        if deadletter["reason"] != "DEADLETTER_REASON_ROUTE_MISS":
            raise RuntimeError(f"expected route miss deadletter, got {deadletter}")
        print(f"coakka_python_desktop_deadletter reason={deadletter['reason']} target={deadletter['target']}")

        stats = runtime.orchestrator.client_stats()
        store_stats = runtime.store_orchestrator.client_stats()
        if stats.matched_responses < 5 or stats.matched_deadletters < 1 or store_stats.delivered_requests < 5:
            raise RuntimeError(f"unexpected client stats: frontend={stats} store={store_stats}")
        print(
            f"coakka_python_desktop_stats storeDelivered={store_stats.delivered_requests} "
            f"matchedResponses={stats.matched_responses} matchedDeadletters={stats.matched_deadletters}"
        )
    finally:
        runtime.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="CoAkka Python desktop local customer runtime demo")
    parser.add_argument("--smoke", action="store_true", help="Run the headless smoke path instead of opening Tk")
    args = parser.parse_args()
    if args.smoke:
        run_smoke()
        return

    runtime = CustomerDesktopRuntime()
    app = CustomerDesktopApp(runtime)
    app.run()


if __name__ == "__main__":
    main()
