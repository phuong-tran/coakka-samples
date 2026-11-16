# Python Runtime 3.2.1: First Run

Send one request to a Python handler and print its reply.

**You need:** standard CPython 3.11 or later and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
PyPy and experimental free-threaded builds are outside this release's qualification.

## Run

From the Samples repository root, use a virtual environment.

macOS or Linux:

~~~sh
python3 -m venv .venv
.venv/bin/python -m pip install coakka-v2-connector==3.2.1
.venv/bin/python coakka-runtime/python/registry-basic/main.py
~~~

Windows PowerShell:

~~~powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install coakka-v2-connector==3.2.1
.\.venv\Scripts\python.exe coakka-runtime/python/registry-basic/main.py
~~~

Expected output:

~~~text
accepted-hello
~~~

The app exits after its context manager closes Runtime. No separate service
or Core installation is needed for this local example.

## Read the code

Open [main.py](main.py). It creates startup settings, starts Runtime inside a
with statement, registers one handler and sends a request to that target.
The handler is an ordinary Python function.

This is a one-process example. [Choose a use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md)
or [move the same steps into an application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).

[Runtime examples](../../README.md) ·
[Local-package evaluation](../../../docs/coakka-runtime/previews/README.md).
