# Python Runtime Samples

Python runtime samples document the `coakka_v2_connector` wheel shape. This
runtime lane consumes the public Python wheel built against native runtime
`0.1.0+a671b3a`.

## Run

```sh
bash run.sh runtime python basic
bash run.sh runtime python deadletter
bash run.sh runtime python hot-reload
```

The Python samples run from disposable virtual environments. They install the
published wheel into a temporary venv, run the sample, and remove the venv on
exit so the user's global Python installation is not modified.

## Integration Recipe

Install the wheel through your normal packaging path. The samples resolve the
public wheel into a temporary directory, install it into a disposable venv, and
remove the venv on exit.

Start one `RuntimeHost` per process:

```python
start_spec = ConnectorStartSpec(
    system_name="customer-store",
    node_id="customer-store-node-1",
    queue_capacity=128,
    strict_no_drop=True,
    generation=1,
    routes=[
        RouteSpec(
            target="samples.customer.store",
            endpoints=[
                EndpointSpec("127.0.0.1", 19102, int(EndpointFlag.LOCAL)),
            ],
        )
    ],
)

with RuntimeHost.start(start_spec=start_spec) as runtime:
    ...
```

Name roles in this snippet:

- `system_name` says this process belongs to the logical `customer-store`
  runtime participant.
- `node_id` says this concrete process is `customer-store-node-1`.
- `target` says which capability the runtime can route to:
  `samples.customer.store`.

`target` is not `system_name` and not `node_id`. A process can own multiple
targets; the route table maps those target names to endpoints.

`samples.customer.store` is the sample's capability target name. In your app,
choose your own target name, then use the same value in the route table, the
process-owned `register_handler(...)`, and every caller `target`. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

The same string appears again in the reply helper as `source`: the response is
coming from the target handler that produced it.

This sample registers one handler to stay small. A real app-host can register
multiple handlers if it owns multiple targets, such as
`samples.customer.create`, `samples.customer.update`, and
`samples.customer.list`. The route table and caller target must use the same
names.

Register handlers only for targets this process owns:

```python
def handle_customer(request):
    return runtime.client.make_json_reply_from_request_identity(
        request=request,
        source="samples.customer.store",
        payload={"status": "ACCEPTED"},
    )

runtime.register_handler("samples.customer.store", handle_customer)
```

`ask_json(...)` below is a convenience helper for JSON samples. It is not the
runtime saying that only JSON is supported. The payload identity carries the
message type, schema version, and `PayloadFormat.JSON`; other payload formats
are still runtime envelopes with a different payload format and byte encoding,
exposed through the connector API surface for that host language.

Send typed requests with explicit timeout and operation metadata:

```python
response = runtime.ask_json(
    source="customer-web",
    target="samples.customer.store",
    payload={"id": "cust-001"},
    payload_identity=PayloadIdentity(
        message_type="samples.customer.create.request.v1",
        payload_schema_version=1,
        payload_format=PayloadFormat.JSON,
    ),
    timeout_ms=5000,
    operation="create_customer",
    delivery_hint=DeliveryHint.ROUTER_DEFAULT,
)
```

Use the context manager or call `close()` during application shutdown.

## Before: Internal REST

A same-process capability can become a small Flask/FastAPI service just to look
distributed:

```python
@app.post("/internal/customers")
def create_customer(command: CustomerDraft):
    return store.create(command)
```

The web/API side then forwards business work through HTTP:

```python
reply = requests.post(
    "http://customer-store/internal/customers",
    json=command,
    timeout=5,
)
reply.raise_for_status()
customer = reply.json()
```

## After: Runtime Target

Read the address change like this:

```text
Before internal REST:
  POST /internal/customers -> route function

After CoAkka:
  target = "samples.customer.store" -> registered handler
```

The target plays a similar addressing role to an internal REST path, but it is
runtime routing vocabulary, not an HTTP URL.

With CoAkka, the store is a runtime target:

```python
def handle_customer(request):
    command = json.loads(request.payload.decode("utf-8"))
    return runtime.client.make_json_reply_from_request_identity(
        request=request,
        source="samples.customer.store",
        payload=store.create(command),
    )

runtime.register_handler("samples.customer.store", handle_customer)
```

`ask_json(...)` below is used because the sample wants readable payloads in logs
and browser-visible diagnostics. It is a helper around typed runtime delivery,
not a statement that runtime is JSON-only.

The caller sends one typed runtime request:

```python
response = runtime.ask_json(
    source="samples.customer.frontend",
    target="samples.customer.store",
    payload=command,
    payload_identity=PayloadIdentity(
        "samples.customer.create.request.v1",
        1,
        PayloadFormat.JSON,
    ),
    timeout_ms=5000,
    operation="create_customer",
)
```

The extra internal REST path spreads private runtime work across URL config,
HTTP parsing, headers, middleware, status/error mapping, timeout policy, and
test setup. CoAkka keeps the internal path as a typed runtime message with
request/reply and deadletter behavior, while HTTP remains available for real
client-facing or legacy boundaries. Existing code using
`ConnectorOrchestrator.start(...)` still works as the compatibility name; new
samples use `RuntimeHost.start(...)`.

## Production Notes

- Keep queue sizes bounded and monitor deadletter counters.
- Treat `DeadletterError` as a first-class route/delivery result.
- Increment `generation` when applying a new route table.
- Rejecting stale or invalid route snapshots should remain visible through
  control rejection counters and unchanged active generation.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.
