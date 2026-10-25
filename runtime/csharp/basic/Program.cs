using CoAkka.Runtime;

namespace CoAkka.Samples.Runtime.CSharp.Basic;

internal static class Program
{
    private static void Require(bool condition, string message)
    {
        if (!condition) throw new InvalidOperationException(message);
    }

    internal static async Task WithCleanup(Func<Task> operation, Action cleanup)
    {
        Exception? primary = null;
        try { await operation(); }
        catch (Exception error) { primary = error; throw; }
        finally
        {
            try { cleanup(); }
            catch (Exception error) when (primary is not null)
            {
                // Neither the operation nor a failed shutdown may hide the other.
                throw new AggregateException("Sample operation and shutdown both failed.", primary, error);
            }
        }
    }

    public static async Task Main()
    {
        const string systemName = "csharp-runtime-sample";
        const string target = "samples.runtime.csharp.echo";
        const string missingTarget = "samples.customer.missing";
        var runtime = RuntimeHost.StartLocal(systemName, target);
        await WithCleanup(async () =>
        {
            var requestIdentity = PayloadIdentity.Text("samples.runtime.csharp.echo.request.v1");
            runtime.RegisterTextHandler(target, name => $"hello {name}");

            var response = await runtime.AskTextAsync(
                source: systemName, target: target, payload: "Ada", payloadIdentity: requestIdentity,
                timeoutMs: 2_000, operation: "echo");

            RuntimeDeadletter? routeMiss = null;
            try
            {
                await runtime.AskTextAsync(
                    source: systemName, target: missingTarget, payload: "Missing", payloadIdentity: requestIdentity,
                    timeoutMs: 2_000, operation: "missing-target");
            }
            catch (DeadletterException error) { routeMiss = error.Deadletter; }

            var info = runtime.RuntimeInfo();
            var config = runtime.RuntimeConfig();
            var health = runtime.Health();
            var startup = runtime.StartupConfig();
            var delivery = runtime.DeliveryConfig();
            var stats = runtime.Stats();
            var client = runtime.ClientStats();

            Require(info.AbiVersion == 1, $"unexpected ABI {info.AbiVersion}");
            Require(config.SystemName == systemName && config.RouteCount == 1, "unexpected accepted identity/routes");
            Require(config.RuntimeState == RuntimeState.Started && health.RuntimeState == RuntimeState.Started,
                "Core must report the started state");
            Require(startup.Execution.RequestedPolicy == 0 && startup.Connection.Mode == RuntimeTcpConnectionMode.Multiplexing
                && !startup.MonitorEnabled, "unexpected Core AUTO/MULTIPLEX/monitor facts");
            Require(delivery.RequestMode == 2 && delivery.ResponseMode == 2, "unexpected Core delivery facts");
            Require(startup.QueueCapacity > 0 && startup.QueueCapacity == stats.IngressQueueCapacity
                && startup.QueueCapacity == config.QueueCapacity, "Core queue snapshots disagree");
            Require(response == "hello Ada", $"unexpected response: {response}");
            Require(routeMiss is { ReasonCode: 2 } && routeMiss.OriginalEnvelope.Target == missingTarget,
                "expected Core route miss with its original target");
            Require(client.DeliveredRequests == 1 && client.MatchedResponses == 1 && client.MatchedDeadletters == 1
                && client.PendingRequests == 0, "unexpected terminal counters");

            Console.WriteLine($"coakka_runtime_info abi={info.AbiVersion} version={info.RuntimeVersion} git={info.GitCommit}");
            Console.WriteLine($"coakka_runtime_response payload={response}");
            Console.WriteLine($"coakka_runtime_startup auto={startup.Execution.RequestedPolicy} connection={startup.Connection.Mode} monitor={startup.MonitorEnabled} queueCapacity={startup.QueueCapacity}");
            Console.WriteLine($"coakka_runtime_client_stats delivered={client.DeliveredRequests} matchedResponses={client.MatchedResponses} matchedDeadletters={client.MatchedDeadletters} pending={client.PendingRequests}");
        }, runtime.Dispose);
    }
}
