# CoAkka Sample Rollout Path

This note gives a first-pass path through the samples. It is not a production
rollout checklist; it is a reading and experimentation order for understanding
the runtime boundary without opening every sample at once.

## Step 1: See The Runtime Path

Start with a container sample:

```sh
bash run.sh containers node-python
```

Look for the visible shape:

```text
browser
  -> web app
  -> connector
  -> CoAkka runtime
  -> store process
  -> runtime reply
  -> web UI
```

The important observation is that the store is not exposed as a REST fallback
for the web service. Business traffic crosses the runtime path.

## Step 2: Read One Basic Sample

Run the smallest runtime sample in a language you know:

```sh
bash run.sh runtime jvm basic
bash run.sh runtime python basic
bash run.sh runtime node basic
bash run.sh runtime go basic
```

In the code, find these five pieces:

| Piece | What it answers |
| --- | --- |
| start spec | What process am I, what route snapshot do I start with, and what queue policy do I use? |
| route target | Which capability name should runtime route to? |
| local handler registration | Which target does this process actually own? |
| typed ask/event | What work is submitted through runtime? |
| stats/deadletters | What happened after delivery? |

That is the smallest useful CoAkka model.

## Step 3: Observe Failure Semantics

Run one deadletter sample:

```sh
bash run.sh runtime jvm deadletter
bash run.sh runtime python deadletter
```

The point is not to make failure disappear. The point is to make delivery
failure visible as runtime vocabulary: target, generation, reason, and matched
pending request.

## Step 4: Understand Route Snapshots

Run the route reload sample when you want to see the apply semantics directly:

```sh
bash run.sh runtime python hot-reload
```

Most applications can start with one route snapshot from platform config and
change it through rollout. This sample exists to show why route generation
exists when live route changes are needed:

- a newer route snapshot can be applied
- a stale snapshot is rejected
- an invalid snapshot does not replace the active route table
- diagnostics report the active generation

## Step 5: Move To A Real Workflow

Run a local customer CRUD scenario:

```sh
bash run.sh scenario customer-crud spring-boot-starter-local dev
```

This scenario keeps HTTP at the browser/API edge and moves customer
work onto local runtime targets.

If you want explicit route and handler wiring instead of annotation-based
adapter help, read:

```text
runtime/scenarios/customer-crud/spring-boot-single-process
```

## Step 6: Cross A Process Or Language Boundary

After the local workflow is clear, run a cross-process customer scenario:

```sh
bash run.sh scenario customer-crud spring-boot-spring-boot dev
bash run.sh scenario customer-crud spring-boot-node dev
bash run.sh scenario customer-crud spring-boot-go dev
```

The web process still owns browser/API HTTP. The store process owns the
customer target. The business call between them stays runtime-only.

## Step 7: Read Production-Facing Notes

After the samples make sense, read:

- [runtime-integration-guide.md](runtime-integration-guide.md)
- [production-readiness.md](production-readiness.md)
- [containerized-runtime.md](containerized-runtime.md)
- [qna.md](qna.md)

Those notes explain runtime start specs, route snapshots, target/source naming,
container identity, and common positioning questions.
