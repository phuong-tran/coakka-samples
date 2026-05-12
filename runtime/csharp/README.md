# C# Runtime Samples

C# runtime samples document the `CoAkka.Runtime` NuGet package shape. This
runtime lane consumes the public NuGet package built against native runtime
`0.1.0+a671b3a`.

For a CRUD developer, the point is not to replace ASP.NET Core. Keep HTTP at
the browser/API edge. Use CoAkka for work that is internal to the application
or deployment and should not become a fake REST service just to cross an
in-process or runtime boundary.

This lane is macOS/Linux-first. Windows support is not claimed yet because most
server-side .NET deployments can be validated on Linux first, and the current
goal is to show the boundary/API shape clearly before adding Windows DLL
packaging.

## Run

```sh
bash run.sh runtime csharp basic
```

The C# sample expects .NET SDK 10 or newer.

## Before: Internal REST

Without CoAkka, an ASP.NET Core CRUD app often creates an internal controller
and an HTTP client even when the store/audit work is still owned by the same
application or deployment unit:

```csharp
app.MapPost("/api/customers", async (
    CreateCustomerRequest request,
    HttpClient internalClient) =>
{
    var response = await internalClient.PostAsJsonAsync(
        "http://customer-store/internal/customers",
        request);
    response.EnsureSuccessStatusCode();
    return Results.Json(await response.Content.ReadFromJsonAsync<CustomerDto>());
});
```

The receiving side then grows another web surface that is not a real product
API:

```csharp
app.MapPost("/internal/customers", async (
    CreateCustomerRequest request,
    CustomerStore store) =>
{
    var customer = await store.Create(request);
    return Results.Json(customer);
});
```

That adds URL config, serialization, timeout/error mapping, retry policy, and
test setup before there is a real process or network boundary.

That extra HTTP layer is not free. Even with keep-alive and small JSON payloads,
the app is still paying for web-stack semantics: request parsing, headers,
middleware, status-code mapping, client/server lifecycle, and tests for an
endpoint that is not meant to be a product API. CoAkka is not presented here as
a universal speed-contest winner; it is a better boundary for internal work,
and it is usually a lighter path than pretending the work is REST.

## After: Runtime Target

The C# adapter lets the app own one embedded runtime host, declare an internal
target, and keep HTTP at the real edge:

```csharp
using CoAkka.Runtime;

var identity = new PayloadIdentity(
    "samples.customer.create.request.v1",
    1,
    PayloadFormat.Json);

var spec = new ConnectorStartSpec(
    SystemName: "customer-api",
    NodeId: "customer-api-node",
    Routes:
    [
        new RuntimeRouteSpec(
            Target: "customers.create",
            Endpoints:
            [
                new RuntimeEndpointSpec(
                    Host: "127.0.0.1",
                    Port: 19141,
                    Flags: RuntimeEndpointFlags.Local),
            ])
    ],
    SeparateDeliveredRequestLane: true);

using var runtime = RuntimeHost.Start(spec);
```

Name roles in this snippet:

- `SystemName` says this process belongs to the logical `customer-api`
  runtime participant.
- `NodeId` says this concrete process is `customer-api-node`.
- `Target` says which capability the runtime can route to:
  `customers.create`.

`Target` is not `SystemName` and not `NodeId`. A process can own multiple
targets; the route table maps those target names to endpoints.

`customers.create` is the sample's capability target name. In your app, choose
your own target name, then use the same value in the route table, the local
`RegisterHandler(...)`, and every caller `target`. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

The same string appears again in the reply helper as `source`: the response is
coming from the target handler that produced it.

This sample registers one handler to stay small. A real app-host can register
multiple handlers if it owns multiple targets, such as
`customers.create`, `customers.update`, and `customers.list`. The route table
and caller target must use the same names.

```csharp
runtime.RegisterHandler(
    "customers.create",
    request => RuntimeHost.MakeJsonReplyFromRequestIdentity(
        request,
        "customers.create",
        customerStore.Create(request.PayloadUtf8())));
```

`AskJsonAsync(...)` below is a convenience helper for JSON samples. It is not
the runtime saying that only JSON is supported. The payload identity carries
the message type, schema version, and `PayloadFormat.Json`; other payload
formats are still runtime envelopes with a different payload format and byte
encoding, exposed through the connector API surface for that host language.

```csharp
var response = await runtime.AskJsonAsync(
    source: "customer-api",
    target: "customers.create",
    payload: command,
    payloadIdentity: identity,
    deliveryHint: DeliveryHint.RequireLocal);
```

The ASP.NET Core adapter shape on top of that runtime API is one API endpoint
calling a local runtime target, while the store logic is a capability instead
of an internal REST endpoint:

```csharp
app.MapPost("/api/customers", async (
    CreateCustomerRequest request,
    CoAkkaClient coakka) =>
{
    var customer = await coakka.Request<CustomerDto>(
        "customers.create",
        request);
    return Results.Json(customer);
});

[CoAkkaHandler("customers.create")]
public sealed class CreateCustomerHandler(CustomerStore store)
{
    public Task<CustomerDto> Handle(CreateCustomerRequest request)
    {
        return store.Create(request);
    }
}
```

The `[CoAkkaHandler]` scanning and ASP.NET Core DI sugar are still a later
adapter step. The package sample in this directory already proves the
connector-level route snapshot, local handler, request/reply, matched
deadletter, native loading, lifecycle, and diagnostics baseline.

## What This Sample Proves

- `dotnet` can install `CoAkka.Runtime` from the public artifact surface.
- The package can load the native runtime on supported macOS/Linux platforms.
- A .NET process can start one local `RuntimeHost`.
- A .NET process can register a local target handler and call it with
  request/reply.
- Missing targets surface as matched deadletters instead of hidden HTTP 404
  policy.
- Runtime version, git commit, config, health, queue diagnostics, and
  client request/reply counters are visible from C#.

## Production Notes

- Keep one active `RuntimeHost` per process.
- Keep queue sizes bounded.
- Treat Windows support as not claimed until a separate DLL packaging and smoke
  pass exists.
- Use Linux validation before presenting this lane as a server deployment path.
