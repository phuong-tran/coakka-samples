# Go Runtime Samples

Go runtime samples consume the published `coakka-v2-connector-go` source
package tarball. The package includes the native runtime for supported
platforms.

## Run

```sh
bash run.sh runtime go basic
bash run.sh runtime go deadletter
```

Go runtime v2 samples expect Go 1.23 or newer.

## Integration Recipe

Add the package through your normal module path. The samples unpack the public
tarball into a temporary workspace and use a local `replace` directive.

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
