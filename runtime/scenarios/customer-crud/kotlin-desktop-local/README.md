# Kotlin Desktop In-App Runtime Customer CRUD

This scenario is the smallest visual happy path for the customer workflow.
It runs one desktop JVM process with two CoAkka runtime handles:

| Role | Target | Transport |
| --- | --- | --- |
| desktop frontend runtime | `samples.customer.frontend` | source of typed asks |
| in-memory store runtime | `samples.customer.store` | process-owned runtime handler |

The desktop UI is not a REST client. Customer create, update, delete, and list
commands are sent as typed runtime asks from the frontend runtime to the store
runtime. The store has no HTTP API.

## Why This Exists

This in-app desktop scenario gives a successful, visual path without changing
the architectural point:

1. The UI command becomes a typed CoAkka request.
2. The route snapshot resolves `samples.customer.store`.
3. The store runtime handler owns customer state and replies through runtime
   request/reply.
4. The UI shows customer state, route generations, runtime version/git
   for both handles, delivered request count, matched responses, and
   deadletters.

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
route miss. A successful smoke prints `coakka_desktop_stats` with store
delivered requests, matched responses, and one matched deadletter.
