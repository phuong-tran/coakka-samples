using CoAkka.Runtime;

const string target = "samples.echo";
await using var runtime = RuntimeHost.StartLocal("csharp-first-run", target);
runtime.RegisterTextHandler(target, text => $"accepted-{text}");
var response = await runtime.AskTextAsync(
    source: "csharp-first-run",
    target: target,
    payload: "hello",
    timeoutMs: 2_000);
Console.WriteLine(response);
