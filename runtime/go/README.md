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

Start one orchestrator per process:

```go
orchestrator, err := connector.StartConnectorOrchestrator(connector.ConnectorStartSpec{
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
defer orchestrator.Close()
```

Register handlers only for local targets:

```go
err = orchestrator.RegisterHandler("samples.customer.store", func(request *connector.Envelope) *connector.Envelope {
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
response, err := orchestrator.AskJSON(
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

## Production Notes

- Wire `Close()` into service shutdown, not just `defer` in short programs.
- Treat `DeadletterError` as a route/delivery result.
- Keep route generations monotonic.
- Customer scenarios keep inter-service business traffic runtime-only; the
  current public `backend=stub` artifact returns explicit delivery failures for
  cross-process CRUD.
