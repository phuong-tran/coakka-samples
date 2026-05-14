using CoAkka.Runtime;

static void Require(bool condition, string message)
{
    if (!condition)
    {
        throw new InvalidOperationException(message);
    }
}

var spec = new ConnectorStartSpec(
    SystemName: "csharp-runtime-sample",
    NodeId: "csharp-runtime-sample-node",
    StrictNoDrop: true,
    QueueCapacity: 64,
    Generation: 1,
    Routes:
    [
        new RuntimeRouteSpec(
            Target: "samples.customer.create",
            Endpoints:
            [
                new RuntimeEndpointSpec(
                    Host: "127.0.0.1",
                    Port: 19141,
                    Flags: RuntimeEndpointFlags.Local),
            ])
    ]);

using var runtime = RuntimeHost.Start(spec);

var requestIdentity = new PayloadIdentity(
    "samples.customer.create.request.v1",
    1,
    PayloadFormat.Json);

runtime.RegisterHandler(
    "samples.customer.create",
    request =>
    {
        var command = System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, string>>(request.Payload);
        return RuntimeHost.MakeJsonReplyFromRequestIdentity(
            request,
            "samples.customer.create",
            new
            {
                id = "cust-csharp-001",
                name = command?["name"] ?? "unknown",
                source = "csharp-runtime-handler",
            });
    });

var response = await runtime.AskJsonAsync(
    source: "csharp-runtime-sample",
    target: "samples.customer.create",
    payload: new { name = "Ada" },
    payloadIdentity: requestIdentity,
    timeoutMs: 2_000,
    operation: "create-customer",
    deliveryHint: DeliveryHint.RequireLocal);

var sawRouteMiss = false;
try
{
    await runtime.AskJsonAsync(
        source: "csharp-runtime-sample",
        target: "samples.customer.missing",
        payload: new { name = "Missing" },
        payloadIdentity: requestIdentity,
        timeoutMs: 2_000,
        operation: "missing-customer",
        deliveryHint: DeliveryHint.RequireLocal);
}
catch (DeadletterException error)
{
    sawRouteMiss = error.Deadletter.Reason == "DEADLETTER_REASON_ROUTE_MISS";
}

var info = runtime.RuntimeInfo();
var config = runtime.RuntimeConfig();
var health = runtime.Health();
var stats = runtime.Stats();
var clientStats = runtime.ClientStats();

Require(info.AbiVersion == 1, $"expected ABI version 1, got {info.AbiVersion}");
Require(config.SystemName == "csharp-runtime-sample", $"unexpected system name {config.SystemName}");
Require(config.NodeId == "csharp-runtime-sample-node", $"unexpected node id {config.NodeId}");
Require(config.RouteCount == 1, $"expected one route, got {config.RouteCount}");
Require(config.RuntimeState == RuntimeState.Started, $"unexpected config state {config.RuntimeState}");
Require(health.RuntimeState == RuntimeState.Started, $"unexpected health state {health.RuntimeState}");
Require(stats.IngressQueueCapacity > 0, "ingress queue capacity should be positive");
Require(response.PayloadUtf8().Contains("Ada", StringComparison.Ordinal), "response should contain customer payload");
Require(sawRouteMiss, "missing target should surface as a matched deadletter");
Require(clientStats.DeliveredRequests == 1, $"expected one delivered request, got {clientStats.DeliveredRequests}");
Require(clientStats.MatchedResponses == 1, $"expected one matched response, got {clientStats.MatchedResponses}");
Require(clientStats.MatchedDeadletters == 1, $"expected one matched deadletter, got {clientStats.MatchedDeadletters}");

Console.WriteLine(
    $"coakka_runtime_info abi={info.AbiVersion} " +
    $"version={info.RuntimeVersion} git={info.GitCommit}");
Console.WriteLine(
    $"coakka_runtime_config system={config.SystemName} node={config.NodeId} " +
    $"generation={config.AppliedGeneration} routes={config.RouteCount} state={config.RuntimeState}");
Console.WriteLine($"coakka_runtime_response payload={response.PayloadUtf8()}");
Console.WriteLine(
    $"coakka_runtime_client_stats delivered={clientStats.DeliveredRequests} " +
    $"matchedResponses={clientStats.MatchedResponses} matchedDeadletters={clientStats.MatchedDeadletters}");
Console.WriteLine(
    $"coakka_runtime_stats generation={stats.AppliedGeneration} routes={stats.RouteCount} " +
    $"queueCapacity={stats.IngressQueueCapacity} queueDepth={stats.IngressQueueDepth}");
