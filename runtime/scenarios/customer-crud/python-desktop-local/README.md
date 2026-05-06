# Python Desktop Local Runtime Customer CRUD

This scenario mirrors the Kotlin desktop local demo with Python and Tk. It runs
one Python process with one CoAkka runtime handle and two local runtime roles:

| Role | Target | Transport |
| --- | --- | --- |
| desktop frontend | `samples.customer.frontend` | source of typed asks |
| in-memory store handler | `samples.customer.store` | local runtime handler |

The desktop UI is not a REST client. Customer create, update, delete, and list
commands are sent as typed runtime asks from the frontend target to the store
target. The store has no HTTP API.

## Run

Build/check the scenario with the headless smoke path:

```sh
bash run.sh check
```

Open the desktop UI:

```sh
bash run.sh app
```

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
Desktop UI -> samples.customer.frontend -> CoAkka runtime ask -> samples.customer.store -> reply
```

Runtime diagnostics show the runtime version/git/backend, active route
generation, delivered request count, matched response count, pending count, and
deadletter count. This is the visual happy path for Python while cross-process
customer scenarios still wait for a remote-capable runtime backend.
