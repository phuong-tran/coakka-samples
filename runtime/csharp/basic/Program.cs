using CoAkka.Runtime;

static void Require(bool condition, string message)
{
    if (!condition)
    {
        throw new InvalidOperationException(message);
    }
}

const string systemName = "csharp-runtime-sample";
const string target = "samples.runtime.csharp.echo";

using var runtime = RuntimeHost.StartLocal(systemName, target, queueCapacity: 64);

var requestIdentity = PayloadIdentity.Text("samples.runtime.csharp.echo.request.v1");
runtime.RegisterTextHandler(target, name => $"hello {name}");

var response = await runtime.AskTextAsync(
    source: systemName,
    target: target,
    payload: "Ada",
    payloadIdentity: requestIdentity,
    timeoutMs: 2_000,
    operation: "echo",
    deliveryHint: DeliveryHint.RequireLocal);

var sawRouteMiss = false;
try
{
    await runtime.AskTextAsync(
        source: systemName,
        target: "samples.customer.missing",
        payload: "Missing",
        payloadIdentity: requestIdentity,
        timeoutMs: 2_000,
        operation: "missing-target",
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
Require(config.SystemName == systemName, $"unexpected system name {config.SystemName}");
Require(config.NodeId == $"{systemName}-node", $"unexpected node id {config.NodeId}");
Require(config.RouteCount == 1, $"expected one route, got {config.RouteCount}");
Require(config.RuntimeState == RuntimeState.Started, $"unexpected config state {config.RuntimeState}");
Require(health.RuntimeState == RuntimeState.Started, $"unexpected health state {health.RuntimeState}");
Require(stats.IngressQueueCapacity > 0, "ingress queue capacity should be positive");
Require(response == "hello Ada", $"unexpected response: {response}");
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
Console.WriteLine($"coakka_runtime_response payload={response}");
Console.WriteLine(
    $"coakka_runtime_client_stats delivered={clientStats.DeliveredRequests} " +
    $"matchedResponses={clientStats.MatchedResponses} matchedDeadletters={clientStats.MatchedDeadletters}");
Console.WriteLine(
    $"coakka_runtime_stats generation={stats.AppliedGeneration} routes={stats.RouteCount} " +
    $"queueCapacity={stats.IngressQueueCapacity} queueDepth={stats.IngressQueueDepth}");
