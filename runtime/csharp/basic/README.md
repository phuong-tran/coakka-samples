# C# Runtime Basic

This public runtime sample starts the C# runtime package with the local-first
helper API, registers one process-owned text capability, sends one request/reply
call through the runtime, then checks that a missing target returns a matched
deadletter.

This sample covers:

- NuGet package install from the public artifact surface
- embedded native runtime loading
- runtime version/git diagnostics
- one process-owned route target owned by the .NET process
- request/reply from C# into a registered runtime handler
- route-miss deadletter handling without a backend HTTP endpoint
- config, health, runtime stats, and client request/reply counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime csharp basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=0.2.0 git=<git>
coakka_runtime_config system=csharp-runtime-sample node=csharp-runtime-sample-node generation=1 routes=1 state=Started
coakka_runtime_response payload=hello Ada
coakka_runtime_client_stats delivered=1 matchedResponses=1 matchedDeadletters=1
coakka_runtime_stats generation=1 routes=1 queueCapacity=64 queueDepth=0
```
