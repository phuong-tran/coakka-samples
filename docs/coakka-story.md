# The CoAkka Story

What if internal capabilities stopped pretending to be public APIs?

CoAkka did not start as a product category. It started as an uncomfortable
question during an ordinary technical conversation.

## Chapter 1: Coffee, AI, And A Familiar Demo

One day, over coffee, two friends were talking about engineering, AI, and how
quickly modern tools can help developers build software. The topic was not only
"AI can write code faster." It was also what happens when developers can create
architecture faster than they can question it.

One friend demoed a project with a familiar microservice shape:

```text
Service A -> HTTP -> Service B
```

The demo worked. That was exactly what made the question uncomfortable. Go made
the service code feel light. HTTP made the handoff easy to explain. The
deployment model looked familiar enough that most teams would accept it without
much debate.

The reaction did not come from deep backend or DevOps habit. It came from a
different sensitivity: when a system puts a decision in the wrong layer, the
shape feels off even before the tool names matter.

But one question kept standing out:

```text
Why do Service A and Service B know so much about each other?
```

If Service A only needs Service B to perform an internal capability, why does
that capability have to pretend to be a public HTTP product API? Why does a
function-like internal handoff become a URL, a client wrapper, status-code
mapping, retry policy, timeout policy, observability convention, and deployment
contract?

That question became the first shape of CoAkka.

It did not become a clean product idea immediately. For a while it was only a
stubborn discomfort: the system worked, the tools were accepted, and yet the
boundary still felt misplaced.

## Chapter 2: Microservices Are Simple Until The Boundary Lies

Microservices are simple when described honestly:

```text
one capability asks another capability to do work
```

The complexity appears when that capability call is modeled as an HTTP-shaped
internal boundary with public-API semantics:

```text
app work -> private URL -> HTTP status -> client wrapper -> retry guess
```

The problem is not that HTTP is bad. Public HTTP, gRPC, ingress, auth,
gateways, and product APIs are real system boundaries. They belong where users,
systems, teams, security policy, and public contracts meet.

The problem is using public network vocabulary for work that is still
application-owned.

Once the boundary is named incorrectly, the rest of the system starts paying
for it:

- `4xx` and `5xx` become too vague to explain what actually failed.
- the caller cannot tell whether the target was missing, overloaded, stale, or
  rejected.
- retry policy moves away from the code that understands idempotency and
  business meaning.
- tracing has to reconstruct a story the runtime should have known directly.
- teams add more infrastructure to compensate for a contract that was placed
  at the wrong layer.

The uncomfortable part is that this can look professional. There may be
dashboards, sidecars, certificates, retries, traces, and many YAML files. But a
clean-looking infrastructure story can still be hiding a simple modeling error:

```text
an internal capability was published as if it were an external API
```

## Chapter 3: The Wrong Layer Becomes Expensive

Once HTTP-shaped internal boundaries spread, the ecosystem around them starts
to grow. Service mesh rules appear. Sidecars appear. mTLS is pushed deeper into
places where the real trust boundary may already be better expressed at
ingress, gateway, identity, or deployment policy. Automatic retries appear in
layers that cannot know whether the operation is business-safe to replay.

The issue is not whether Istio, mTLS, or Feign can be made to work. The issue is
paying for a generic proxy and HTTP-client layer after application-owned work
was placed at the wrong boundary. CoAkka provides mTLS and runtime delivery
policy directly; it does not need Istio sidecars to make its internal path
operable.

The uncomfortable feeling comes from placement. A platform layer can see
packets, connections, certificates, response codes, and timing. It usually
cannot see the business promise behind a command.

The problem is letting a layer without that context guess application meaning.

If a payment was submitted, an order was reserved, inventory was decremented,
or a customer record was created, the application knows the semantics. The
sidecar does not. The generic HTTP client does not. A retry rule cannot decide
business idempotency just because a transport event looked retryable.

Feign-style clients can be convenient for real HTTP services. They become
awkward when the endpoint exists only to call work that the application already
owns. At that point the team is maintaining an HTTP facade for something that
should have been a runtime target.

The cost is not only latency. It is engineering attention:

- more infrastructure to operate
- more traces to interpret
- more ambiguous failures
- more hidden coupling between services
- more places where business behavior can be guessed outside the app
- more money spent patching a boundary that should have been named differently

This is why CoAkka starts by stepping back one level. Before adding another
mesh rule, retry policy, client wrapper, or trace convention, ask:

```text
What is the real boundary?
```

If the answer is "an application capability is asking another application
capability to do work," then the contract should say that directly.

## Chapter 4: The Missing Vocabulary

This pain was hard to discuss because it did not have a good shared name.

Teams could say "microservice," "internal API," "backend endpoint," "service
mesh," "client SDK," "distributed tracing," or "sidecar," but those words often
describe the workaround rather than the cause.

That is a lonely kind of problem. When a pattern is common enough, people stop
seeing it as a choice. HTTP-shaped internal boundaries become the default
answer, so the discomfort sounds abstract until there is a better vocabulary
for it.

CoAkka came from staying with that discomfort instead of dismissing it. The
hard part was not only writing runtime code. The hard part was compressing an
unnamed architectural pain into a small set of words that could travel across
teams, languages, and platforms.

CoAkka names the boundary in runtime terms:

```text
caller -> target -> handler -> reply or deadletter
```

The target is the capability name. The active route snapshot records current
delivery ownership. The envelope carries the request. A reply is a runtime
outcome. A deadletter is not a vague timeout or generic `5xx`; it is delivery
failure evidence with source, target, route generation, and reason.

That vocabulary matters because it is not tied to one web framework, one cloud
stack, or one language ecosystem. It can describe the same boundary whether the
caller is JavaScript, Python, Go, Swift, C#, JVM code, native code, a desktop
app, or a constrained edge process.

## Chapter 5: Why The Name Is CoAkka

The `Co` part comes from coroutine-first thinking: small units of work,
structured execution, and application code that stays readable in the host
language.

The `Akka` part is a tribute to actor-system vocabulary, especially envelope
and deadletter concepts from the Akka and Erlang/Elixir lineage.

CoAkka borrows vocabulary from systems that proved the value of message
ownership, delivery semantics, and failure visibility.

But CoAkka does not marry the JVM. It does not marry Erlang. It does not ask a
team to move into one runtime, one VM, one language, or one deployment culture
just to get a better internal boundary.

The name keeps the inspiration. The architecture takes the harder path:

```text
one vocabulary, many host languages, many platforms
```

## Chapter 6: From Runtime To Ecosystem

After the first familiar lanes such as native code, JVM code, and Python, the
question became bigger than any one package.

Then the question changed:

```text
Can CoAkka become an ecosystem?
```

If the boundary is correct, it should not stop at one backend language. It
should fit:

- native hosts
- JVM services and framework adapters
- Python workers
- JavaScript and desktop-adjacent runtimes
- Go services
- C# applications
- Swift packages
- desktop apps
- edge and IoT processes with smaller CPU, memory, and wakeup budgets

That ecosystem includes runtime delivery and the logger that records what the
host saw under pressure. The hard part is not only shipping libraries. The hard
part is keeping the public contract stable across all of them:

```text
target, route snapshot, envelope, reply, deadletter, pressure, diagnostics
```

Those words should mean the same thing everywhere.

## Chapter 7: What Happens When The Boundary Is Right

If CoAkka is used correctly, the benefit is not magic throughput and not a
claim that every network tool disappears. The benefit is that the system stops
manufacturing accidental work around the wrong boundary.

When the internal boundary is a runtime target:

- the application owns business meaning instead of delegating it to transport
  conventions.
- missing capability ownership becomes delivery evidence, not a mystery `404`
  or vague timeout.
- overload and bounded admission can be surfaced as runtime pressure, not
  hidden queue growth.
- traces explain target ownership and route generation instead of only showing
  URLs.
- deadletters give the log and trace story a concrete runtime fact: source,
  target, route generation, and reason.
- legacy public HTTP and gRPC edges can stay intact while one internal handoff
  moves to a better model.

Runtime evidence becomes much more useful when it is paired with a logger that
follows the same discipline across languages. CoAkka Logger is that companion:
bounded, explicit about accepted, delivered, dropped, and rejected records, and
able to use the same operational vocabulary as the runtime.

That combination is the point. The runtime can say where capability delivery
failed. The logger can record that evidence without hiding pressure behind an
unsafe background queue. Together they make debugging less dependent on
guessing from generic status codes, scattered logs, and transport-shaped trace
fragments.

This is the cost argument behind CoAkka. If a system stops manufacturing
HTTP-shaped internal boundaries, it needs fewer patches around those
boundaries. Fewer patches mean less infrastructure to operate, less debugging
ceremony, fewer ambiguous failures, and less money spent explaining something
the runtime contract should have made obvious.

## Chapter 8: CoAkka's Position

CoAkka is not a replacement for public APIs and it is not a way to avoid
security. It is a service-mesh alternative for CoAkka runtime traffic: built-in
TLS/mTLS, connection strategies, target-aware routing, bounded failover, route
generations, and delivery evidence remove the need for a sidecar data plane.
Ingress, API gateways, public APIs, certificate issuance, firewalls, and
observability backends remain at their real boundaries without requiring a
proxy beside every application process.

CoAkka is a claim about placement:

```text
put the boundary where the ownership actually lives
```

If the boundary is public, use public API tools. If the boundary is deployment
or identity, use infrastructure tools. If the boundary is an application
capability asking another application capability to do work, use an
application-owned runtime contract.

That is the idea CoAkka is built around.

Do not make every internal function pretend to be the internet. Do not ask
infrastructure to guess business behavior. Do not force every language to
invent a different vocabulary for the same runtime failure.

Name the target. Route it. Carry the envelope. Return the reply. Report the
deadletter. Emit the log record. Report pressure honestly. Keep the contract
portable.

That is CoAkka.
