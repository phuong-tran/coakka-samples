# Kotlin Desktop Local Runtime Customer CRUD

This scenario is the smallest visual happy path for the customer workflow.
It runs one desktop JVM process with one CoAkka runtime handle and two local
runtime roles:

| Role | Target | Transport |
| --- | --- | --- |
| desktop frontend | `samples.customer.frontend` | source of typed asks |
| in-memory store handler | `samples.customer.store` | local runtime handler |

The desktop UI is not a REST client. Customer create, update, delete, and list
commands are sent as typed runtime asks from the frontend target to the store
target. The store has no HTTP API.

## Why This Exists

Cross-process customer scenarios intentionally avoid a store REST fallback. With
the current public `backend=stub` runtime, those scenarios show remote delivery
deadletters until a remote-capable backend is published.

This local desktop scenario gives a successful, visual path today without
changing the architectural point:

1. The UI command becomes a typed CoAkka request.
2. The route snapshot resolves `samples.customer.store`.
3. The local handler owns customer state and replies through runtime
   request/reply.
4. The UI shows customer state, route generation, runtime version/git/backend,
   delivered request count, matched responses, and deadletters.

## Run

Build the app:

```sh
bash run.sh check
```

Open the desktop UI:

```sh
bash run.sh app
```

Run the headless smoke path:

```sh
bash run.sh smoke
```

The smoke path creates, updates, lists, deletes, then triggers one intentional
route miss. A successful smoke prints `coakka_desktop_stats` with delivered
requests, matched responses, and one matched deadletter.
