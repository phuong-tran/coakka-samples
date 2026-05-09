# Python Runtime Samples

Python runtime samples document the `coakka_v2_connector` wheel shape. This
runtime lane consumes the public Python wheel built against native runtime
`0.1.0+3362b67`.

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
    separate_delivered_request_lane=True,
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

Register handlers only for local targets:

```python
def handle_customer(request):
    return runtime.client.make_json_reply_from_request_identity(
        request=request,
        source="samples.customer.store",
        payload={"status": "ACCEPTED"},
    )

runtime.register_handler("samples.customer.store", handle_customer)
```

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

A local capability can become a small Flask/FastAPI service just to look
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

The fake internal REST path brings URL config, HTTP parsing, headers,
middleware, status/error mapping, timeout policy, and test setup. CoAkka keeps
the internal path as a typed runtime message with request/reply and deadletter
behavior, while HTTP remains available for real client-facing or legacy
boundaries. Existing code using `ConnectorOrchestrator.start(...)` still works
as the compatibility name; new samples use `RuntimeHost.start(...)`.

## Production Notes

- Keep queue sizes bounded and monitor deadletter counters.
- Treat `DeadletterError` as a first-class route/delivery result.
- Increment `generation` when applying a new route table.
- Rejecting stale or invalid route snapshots should remain visible through
  control rejection counters and unchanged active generation.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.
