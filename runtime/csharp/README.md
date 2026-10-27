# C# runtime candidate sample

This lane is a private candidate using exactly CoAkka.Runtime `3.0.0`
and Core2.5.1+f6b61768/ABI1, not the historical public package train. Use the
[admitted candidate guide](../../docs/runtime-csharp-candidate.md) for the
standard .NET8/C#12/ILLink build and matching-host scope.

```sh
bash run.sh runtime csharp basic
```

Set the explicit candidate/work/cache roots in the guide first. The basic
example starts one host, registers a text handler, sends an ask, observes a
matched route miss and awaits asynchronous disposal after success or failure. No application
queue/transport/capability default is guessed; the package projects Core truth.
Keep ASP.NET Core/HTTP at the external edge and application business policy
above the runtime. File/Stream and other integrations require the exact package
API and their separate ownership/evidence, not translated identifiers.
