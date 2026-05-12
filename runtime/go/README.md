# Go Runtime Samples

Go runtime samples document the `coakka-v2-connector-go` source package shape.
This runtime lane consumes the public Go source package built against native
runtime `0.1.0+a671b3a`.

## Run

```sh
bash run.sh runtime go basic
bash run.sh runtime go deadletter
```

Go runtime v2 samples expect Go 1.23 or newer.

## Integration Recipe

Add the package through your normal module path. The samples unpack the public
tarball into a temporary workspace for each run.

Start one runtime host per process:

```go
runtimeHost, err := connector.StartRuntimeHost(connector.ConnectorStartSpec{
    SystemName:                   "customer-store",
    NodeID:                       "customer-store-node-1",
    StrictNoDrop:                 true,
    QueueCapacity:                128,
    EnableMonitor:                true,
    SeparateDeliveredRequestLane: true,
    Generation:                   1,
    Routes: []connector.RouteSpec{{
        Target: "samples.customer.store",
        Endpoints: []connector.EndpointSpec{{
            Host:  "127.0.0.1",
            Port:  19102,
            Flags: uint32(connector.EndpointFlagLocal),
        }},
    }},
}, "")
if err != nil {
    return err
}
defer runtimeHost.Close()
```

Name roles in this snippet:

- `SystemName` says this process belongs to the logical `customer-store`
  runtime participant.
- `NodeID` says this concrete process is `customer-store-node-1`.
- `Target` says which capability the runtime can route to:
  `samples.customer.store`.

`Target` is not `SystemName` and not `NodeID`. A process can own multiple
targets; the route table maps those target names to endpoints.

Register handlers only for local targets:

```go
err = runtimeHost.RegisterHandler("samples.customer.store", func(request *connector.Envelope) *connector.Envelope {
    reply, _ := connector.MakeJSONReplyFromRequestIdentity(
        request,
        "samples.customer.store",
        map[string]any{"status": "ACCEPTED"},
    )
    return reply
}, true)
```

Send typed requests with explicit timeout and operation metadata:

```go
response, err := runtimeHost.AskJSON(
    "customer-web",
    "samples.customer.store",
    map[string]any{"id": "cust-001"},
    connector.NewPayloadIdentity("samples.customer.create.request.v1", 1, connector.PayloadFormatJSON),
    5*time.Second,
    "create_customer",
    connector.DeliveryHintRouterDefault,
    nil,
)
```

`AskJSON(...)` is a convenience helper for JSON samples. It is not the runtime
saying that only JSON is supported. The payload identity carries the message
type, schema version, and `PayloadFormatJSON`; other payload formats are still
runtime envelopes with a different payload format and byte encoding, exposed
through the connector API surface for that host language.

## Before: Internal REST

The store can become an internal `net/http` service even when the call is
really an application capability:

```go
http.HandleFunc("/internal/customers", func(w http.ResponseWriter, r *http.Request) {
    var command customerDraft
    json.NewDecoder(r.Body).Decode(&command)
    json.NewEncoder(w).Encode(store.Create(command))
})
```

The web/API side then forwards business work through HTTP:

```go
body, _ := json.Marshal(command)
reply, err := http.Post(
    "http://customer-store/internal/customers",
    "application/json",
    bytes.NewReader(body),
)
```

## After: Runtime Target

With CoAkka, the store is a runtime target:

```go
runtimeHost.RegisterHandler("samples.customer.store", func(request *connector.Envelope) *connector.Envelope {
    var command customerDraft
    json.Unmarshal(request.GetPayload(), &command)
    reply, _ := connector.MakeJSONReplyFromRequestIdentity(
        request,
        "samples.customer.store",
        store.Create(command),
    )
    return reply
}, true)
```

The caller sends one typed runtime request:

```go
response, err := runtimeHost.AskJSON(
    "samples.customer.frontend",
    "samples.customer.store",
    command,
    connector.NewPayloadIdentity("samples.customer.create.request.v1", 1, connector.PayloadFormatJSON),
    5*time.Second,
    "create_customer",
    connector.DeliveryHintRouterDefault,
    nil,
)
```

`AskJSON(...)` is used here because the sample wants readable payloads in logs
and browser-visible diagnostics. It is a helper around typed runtime delivery,
not a statement that runtime is JSON-only.

The goal is not to win a synthetic contest against every possible HTTP stack.
The goal is to avoid fake internal REST: URL config, request parsing, headers,
status/error mapping, timeout policy, and tests for a boundary that is not a
product API. Existing code using `StartConnectorOrchestrator(...)` still works
as the compatibility name; new samples use `StartRuntimeHost(...)`.

## Production Notes

- Wire `Close()` into service shutdown, not just `defer` in short programs.
- Treat `DeadletterError` as a route/delivery result.
- Keep route generations monotonic.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.
