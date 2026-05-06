# Python Runtime Samples

Python runtime samples consume the published `coakka_v2_connector` wheel. The
wheel includes the native runtime for supported platforms.

## Run

```sh
bash run.sh runtime python basic
bash run.sh runtime python deadletter
```

## Integration Recipe

Install the wheel through your normal packaging path. The samples resolve it
from `coakka-publish` into a temporary directory.

Start one orchestrator per process:

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

with ConnectorOrchestrator.start(start_spec=start_spec) as orchestrator:
    ...
```

Register handlers only for local targets:

```python
def handle_customer(request):
    return orchestrator.client.make_json_reply_from_request_identity(
        request=request,
        source="samples.customer.store",
        payload={"status": "ACCEPTED"},
    )

orchestrator.register_handler("samples.customer.store", handle_customer)
```

Send typed requests with explicit timeout and operation metadata:

```python
response = orchestrator.ask_json(
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

## Production Notes

- Keep queue sizes bounded and monitor deadletter counters.
- Treat `DeadletterError` as a first-class route/delivery result.
- Increment `generation` when applying a new route table.
- Customer scenarios keep inter-service business traffic runtime-only; the
  current public `backend=stub` artifact returns explicit delivery failures for
  cross-process CRUD.
