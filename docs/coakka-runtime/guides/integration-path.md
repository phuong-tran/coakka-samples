# CoAkka Sample Rollout Path

Start with one language and one reply. Add a business workflow, another process
or deployment tools when they help answer your next question.

## Step 1: See The Runtime Path

[Choose your language's published first-run example](../getting-started.md).
It starts one local Runtime, registers an application handler, sends a request,
checks its reply and closes.

No container setup is needed for this step. The point is to recognize your own
application code before introducing a larger deployment.

## Step 2: Read One Basic Sample

Read the source named by that example's README. Find:

| Piece | Question |
| --- | --- |
| Start | Where does this application create Runtime? |
| Target and handler | What work is named, and which function owns it? |
| Request and reply | What does the caller submit and receive? |
| Failure handling | What happens if the call or close fails? |
| Close | Where does the application stop its Runtime? |

Use the host language's normal lifecycle. Python's context manager, C#'s
await-using, Node/Bun's awaited close and Go/Swift error handling express the
same responsibilities differently.

## Step 3: Observe Failure Semantics

A successful reply is only the first case. In your application, decide how to
report a missing target, a timeout, refused work and shutdown.

The [integration guide](runtime-integration-guide.md) explains those choices.
Older deadletter wrappers and local evaluation scripts retain their own package
requirements; they are not automatically a published 3.0.0 recipe.

## Step 4: Understand Route Snapshots

A target names work; routes say where it can be handled. Begin with one local
handler. When another process owns it, configure that process's endpoint and
keep the application request/reply contract explicit.

Use [the message and routing guide](../explanation/runtime-message-and-routing-model.md)
when that change becomes relevant. Route reload, custom policies and
deployment topology can wait until you need them.

## Step 5: Move To A Real Workflow

[Choose an example by use case](examples-and-use-cases.md). The Node.js customer
walkthrough and Spring Boot/Quarkus registry build use released 3.0.0 packages.
The framework application lets you create, list, update and delete customers.

## Step 6: Add Processes And Deployment

[Process and language examples](examples-and-use-cases.md#work-across-processes-and-languages)
state their exact versions and prerequisites. The existing Node-to-Python
container demonstration is a historical 1.x example, not the current 3.0.0
installation path.

Read [incremental adoption](../explanation/incremental-adoption.md) and
[production readiness](../operations/production-readiness.md) when planning
deployment. A sample's happy-path result is not a production capacity claim.

[Back to first run](../getting-started.md).
