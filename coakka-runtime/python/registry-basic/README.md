# Python Runtime 3.0.0: First Run

Use GIL-enabled CPython 3.11 or newer and `coakka-v2-connector==3.0.0`.
This example uses the new API; a 2.x package cannot run the same source.
See the [package ledger](../../../docs/common/current-packages.md) for registry
availability. Install the published wheel, or use the local-wheel check below
when evaluating a package before publication.

From the Samples checkout:

```sh
python3 -m venv .venv
source .venv/bin/activate
python -m pip install coakka-v2-connector==3.0.0
python coakka-runtime/python/registry-basic/main.py
```

On Windows, activate with `.venv\Scripts\Activate.ps1` in PowerShell.
Expected output:

```text
accepted-hello
```

The application starts one host, registers a handler and sends one request.
The context manager closes its resources on exit. The package includes its
matching native Core; no separate native installation is needed. Normal CPU,
connection and monitor defaults stay owned by Core.

For an unpublished local wheel, replace the install command with:

```sh
python -m pip install --no-index --find-links=/absolute/admitted/wheels coakka-v2-connector==3.0.0
```

Use a fresh virtual environment when comparing prepublication builds with the
same version. The local-wheel check does not establish registry availability.
[Runtime samples](../../README.md) ·
[Integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
