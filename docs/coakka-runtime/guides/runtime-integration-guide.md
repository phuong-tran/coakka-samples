# Runtime Integration Guide

**Keep your application logic. Add named work, request/reply and a clear lifetime.**

Run [one first example in your language](../getting-started.md), then bring the
same steps into your application. You do not need to learn another language's
API or choose a deployment topology first.

## Contents

- [Start with your language's code](#language-recipes).
- [Configure this application](#start-spec).
- [Name the work and write its handler](#target-design).
- [Call it and handle failures](#caller-pattern).
- [Close it deliberately](#shutdown).
- [Add another process when needed](#before-and-after-route-knowledge).

## Published Public Transport

Install the connector for your application host from the
[published package ledger](../../common/current-packages.md). Use its matching
versioned examples and package components together. The normal package includes
Runtime; a separate Core installation is not needed.

For a business example, start with [examples and use cases](examples-and-use-cases.md).
A web application keeps its HTTP endpoints, authentication, validation and
response handling. Runtime connects the application work behind those endpoints.

## Start Spec

Create Runtime for the lifetime of your application or service. The startup
type and convenience functions differ by language; use your host's first-run
source rather than copying field names from another host.

Choose your application's name and the work it owns. A local first example
declares one target and registers one handler. CPU use, connection strategy and
monitoring can stay omitted: AUTO, MULTIPLEXED and monitoring-off are the defaults.

Read [defaults and monitoring](../operations/runtime-monitoring.md) when you
need to inspect accepted settings or request extra diagnostics.

### Reading The Runtime Types

These are concepts, not a list of identically named classes in every language:

| Concept | Application question |
| --- | --- |
| Startup settings | What application instance is starting? |
| Target | What work can a caller ask for? |
| Route | Where can that work be handled? |
| Handler | Which application function performs it? |
| Request and result | What information goes in, and what comes back? |

### Field Details

Use a stable logical application/service name and a distinct instance identity
where several instances run. Keep advanced queue, delivery and routing settings
at their normal defaults until your workload shows a need to change them.

When you supply a setting, inspect what Runtime accepted through your language's
supported inspection API. [Configuration and reference guides](../reference/README.md)
cover the detailed options.

### Before And After Route Knowledge

Start locally. When a different process owns the handler, configure that
process's endpoint and the required network participation using your selected
package's API. The target name and business request can remain the same.

A target is not an HTTP URL. A route supplies the location for that named work.
Keep the location in application/deployment configuration rather than spreading
it through business handlers.

See [the routing model](../explanation/runtime-message-and-routing-model.md)
and [network modes](../reference/runtime-network-modes.md) when adding a peer.
[Process and language examples](examples-and-use-cases.md#work-across-processes-and-languages)
identify each demonstration's exact package generation.

## Source And Target

The target names the work you want performed, such as orders.price.
The source identifies the caller. Choose names that describe application
responsibilities and keep them stable across normal restarts.

A process registers handlers for work it owns. A caller can ask for local work
or work owned by another process.

## Request Parameters

Put business arguments in the request data. Use the installed connector's
supported request functions and its documented result/error model. Choose a
timeout that fits the application's operation; small sample timeouts are
teaching values, not production recommendations.

If callers and handlers are maintained separately, agree on the request and
reply shape. Keep application context, credentials and business payloads out
of diagnostic messages unless explicitly needed and safe to record.

## Target Design

Name a useful operation or service responsibility, then keep validation and
business decisions in its application handler. For example, an order-pricing
handler can validate quantities and calculate a quote; Runtime delivers the
request and returns its result.

Use [the customer walkthrough](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/guides/first-npm-smoke.md)
to see one ordinary business operation in Node.js.

## Endpoint Flags

Local and remote routes have different ownership. Prefer your language's route
helpers; follow the selected version's documented endpoint flags when configuring
routes explicitly. [Network modes](../reference/runtime-network-modes.md) and
[the routing model](../explanation/runtime-message-and-routing-model.md) explain
the advanced fields.

## Route Resolution Strategy

Start with the simplest route that names the actual owner of the work.
Change routing policy only for an application need such as equivalent workers
or keyed ownership. [Cluster routing](runtime-cluster-routing.md) explains
those choices and their operational implications.

## Payload Contract

Callers and handlers need to agree on data and versioning. A small application
can start with the language's normal text, byte or JSON APIs. Use the supported
typed request/reply functions when your application needs explicit message types.

Version shared business contracts deliberately. Keep one definition of the
meaning and required fields; each language can represent that data naturally.
A convenient host API does not remove the need to validate untrusted input.

## Handler Pattern

A handler receives a request, validates its business input, performs the local
operation and returns the documented result. Keep database writes, calculations,
authorization decisions and business retries in application code.

Use the connector's reply helpers so replies remain associated with their
requests. Follow that language API's rules for data that remains in use while
a request or callback is pending.

## Caller Pattern

Handle both the reply and the ways an operation can fail:

| Outcome | What the application should decide |
| --- | --- |
| Reply | Validate/use the returned business result. |
| Missing or unreachable target | Report a delivery failure; inspect the route and owner. Some failures are reported as a deadletter after work was accepted. |
| Work refused | Respond to overload or invalid input rather than retrying without a limit. |
| Timeout or cancellation | Decide whether business retries are safe. A timeout does not prove that a handler made no change. |
| Close failure | Preserve it alongside an earlier operation failure. |

Use the installed language's typed results/exceptions. Avoid guessing native
state from message text or from how much time passed.

## Queue And Failure Policy

Leave queue sizing at the normal default for the first integration. Measure
actual pressure before changing it, and keep any application retry/backlog
bounded. A larger queue cannot make a persistently slow handler faster.

Use [monitoring](../operations/runtime-monitoring.md) and
[production readiness](../operations/production-readiness.md) for a measured
operating plan.

## Shutdown

Give Runtime an application lifetime. Stop submitting new work, let active
work finish or follow the documented cancellation path, and await close.
Use your language's normal cleanup pattern shown in the first-run source.

Framework applications should follow their framework lifecycle. Go and Swift
examples preserve operation and close errors; Python uses a context manager,
C# uses await-using, and Node/Bun await close in a finally block.

## Language Recipes

| Host | Runnable source and instructions |
| --- | --- |
| Node.js | [Node first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/node/registry-basic) |
| Bun | [Bun first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/bun/registry-basic) |
| Electron | [Desktop first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic) |
| Python | [Python first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic) |
| C# | [C# first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic) |
| Kotlin / Java | [Separate Kotlin and Java commands](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/jvm/registry-basic) |
| Go | [Go first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/go/registry-basic) |
| Swift | [Swift first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/swift/registry-basic) |
| Spring Boot / Quarkus | [Framework customer application](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/framework-registry) |

For other hosts, use the [sample index](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md#other-languages-and-evaluations)
and follow the selected package/version's own API. Then use
[the learning path](integration-path.md) to choose the next level of example.
