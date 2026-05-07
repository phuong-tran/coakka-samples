# Python Desktop Local Runtime Customer CRUD

This scenario mirrors the Kotlin desktop local demo with Python and Tk. It runs
one Python process with two CoAkka runtime handles:

| Role | Target | Transport |
| --- | --- | --- |
| desktop frontend runtime | `samples.customer.frontend` | source of typed asks |
| in-memory store runtime | `samples.customer.store` | local runtime handler |

The desktop UI is not a REST client. Customer create, update, delete, and list
commands are sent as typed runtime asks from the frontend runtime to the store
runtime. The store has no HTTP API.

## Run

`run.sh` creates a disposable Python virtual environment, installs the published
CoAkka wheel into that environment, runs the sample, then removes the
environment. It does not install packages into your global Python.

Build/check the scenario with the headless smoke path:

```sh
bash run.sh check
```

Open the desktop UI:

```sh
bash run.sh app
```

The desktop UI requires Python Tk support. If your default `python3` does not
include `_tkinter`, install or select a Python build that includes Tk and run:

```sh
COAKKA_PYTHON=/path/to/python-with-tk bash run.sh app
```

The headless smoke path does not require Tk.

Print the smoke output:

```sh
bash run.sh smoke
```

The smoke path creates, updates, lists, deletes, then triggers one intentional
route miss. A successful smoke prints `coakka_python_desktop_stats` with
delivered requests, matched responses, and one matched deadletter.

## What To Look For

The first screen shows the full local path:

```text
Desktop UI -> frontend runtime -> CoAkka runtime ask -> store runtime -> reply
```

Runtime diagnostics show the version/git/backend for both runtime handles,
active route generations, delivered request count, matched response count,
pending count, and deadletter count. This is the visual happy path for Python
without introducing a REST fallback.
