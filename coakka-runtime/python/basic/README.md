# Python Runtime Basic

One same-process typed echo through the
[exact private platform wheel](../../docs/runtime-python-candidate.md),
not the historical public2.5.2 wheel. Select the exact wheel/work directory
as that guide describes, then from the repository root:

```sh
bash coakka-runtime/python/basic/run.sh
```

The shared runner admits/installs offline, then uses the package's normal
installed Core image with isolated imports. Native Windows uses the Python
entrypoint in the guide, not WSL. No extra native library/source override.

Only app identity/route is chosen. Core-issued startup facts and inspection
errors are projected as returned; the sample does not infer defaults from
input. It checks the JSON response, one delivered/matched request and pending0.
The2s ask remains unchanged; host close executes before failure escapes and
original worker diagnostics remain visible after close.

Output includes:

```text
coakka_runtime_info abi=1 version=2.5.1 git=f6b61768f60a08d81b1dc06625a211ffca8e89e2
coakka_runtime_startup cpuPolicy=0 connectionMode=3 monitorEnabled=false requestMode=2 responseMode=2
coakka_runtime_response payload={"echo":"hello-runtime-python"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```

The disposable stage is removed only after its direct child is reaped; caller
roots/tools are retained. Forced termination is not graceful Core shutdown.
