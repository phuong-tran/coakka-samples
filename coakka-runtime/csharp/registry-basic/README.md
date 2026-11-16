# C# Runtime 3.2.1: First Run

Send one request to a C# handler and print its reply.

**You need:** .NET SDK 8 or later and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
The project uses CoAkka.Runtime 3.2.1 from NuGet.

## Run

From the Samples repository root:

~~~sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
~~~

Expected output:

~~~text
accepted-hello
~~~

The app exits after closing Runtime. No separate service or Core build is
needed for this local example.

## Read the code

Open [Program.cs](Program.cs). It starts a local host with await-using,
registers a text handler, sends a request and prints the result. Await-using
closes the host when the application ends.

[Choose a use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md) ·
[Add Runtime to an application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md) ·
[Local-feed evaluation](../../../docs/coakka-runtime/previews/README.md).
