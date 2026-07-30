# Runtime Logging And Observability

## Short Answer

CoAkka separates runtime evidence from business logs. The runtime reports
delivery facts. The app-host and handlers log business meaning. Observability
pipelines export those facts to the tools the team already operates.

```text
runtime evidence -> delivery facts
business log     -> domain meaning
observability    -> fleet export, dashboards, alerts, traces, retention
```

Log business meaning where business meaning is known. Report runtime evidence
where runtime delivery is decided. Export observability where the organization
already observes systems.

## Three Kinds Of Signals

| Signal | Owner | Examples | Where it belongs |
| --- | --- | --- | --- |
| Runtime evidence | CoAkka Runtime | route miss, timeout, rejection, deadletter, queue pressure, active generation, reply matching | runtime stats, deadletter stream, runtime inspect, CoAkka Logger integration |
| Business log | app-host or handler | order created, payment declined, validation failed, tenant decision, audit record | app logger, audit log, domain event, CoAkka Logger when bounded logging is needed |
| Observability export | platform or app integration | metrics, traces, log shipping, dashboards, alerting | OpenTelemetry, Prometheus, Grafana, Loki, ELK, vendor APM, platform tooling |

The important boundary is meaning. The runtime can say whether delivery was
admitted, routed, replied, rejected, timed out, or deadlettered. The app knows
whether that means "payment declined", "retry later", "show a user message",
"write an audit record", or "compensate a workflow".

## Runtime Evidence

Runtime evidence should explain delivery:

- which source submitted the work;
- which target was called;
- which route snapshot and generation were active;
- whether admission succeeded or failed;
- whether queue pressure affected the request;
- whether the handler replied;
- whether the request timed out;
- whether a route miss, rejection, or deadletter happened;
- whether transport or endpoint state affected delivery.

Example runtime-shaped records:

```text
runtime.ask source=checkout-api target=billing.charge.create payload=billing.charge.v1 generation=12 timeoutMs=750
runtime.route.selected target=billing.charge.create generation=12 endpoint=billing-runtime-b strategy=weighted_round_robin
runtime.pressure target=billing.charge.create node=billing-runtime-b depth=120 capacity=128
runtime.deadletter reason=route_miss target=billing.invoice.issue generation=12 source=checkout-api
```

The exact log schema can differ by connector, logger, or host platform. The
runtime facts should remain attributable.

## Business Logs

Business logs belong where business meaning is known:

- order created;
- payment authorized or declined;
- validation failed;
- tenant policy denied the request;
- customer-visible state changed;
- audit decision made;
- compensation started;
- domain retry scheduled.

Those records usually belong in the app-host, handler, domain event stream, or
audit system. They can use existing framework loggers such as SLF4J, log4j,
logback, Python logging, Go `slog` or `zap`, .NET `ILogger`, Android logging,
or the team's existing platform logger.

CoAkka Runtime should not become the business logger just because it sees the
envelope. The runtime does not own payment meaning, tenant policy, PII masking,
user-facing error text, audit retention, or alert routing.

## Existing Logging Frameworks

Existing logging frameworks remain valid at the app layer. Use them for
application messages, domain events, audit decisions, framework lifecycle, and
operator context.

The discipline is performance and attribution:

- do not log every envelope synchronously on a hot runtime path;
- do not format large strings before checking the log level;
- do not write blocking file, console, or network sinks from latency-sensitive
  runtime paths;
- do not log full payloads by default;
- do not hide dropped or rejected log records;
- do not use logs as the only source of route or control-plane truth.

Logging is work. It can allocate, format, serialize, block, flush, and cross
the network. CoAkka can report pressure honestly, but it cannot make unbounded
synchronous logging free.

## Sources And Sinks

Treat runtime, app-hosts, handlers, and connectors as signal sources. Treat
loggers, metrics exporters, tracing exporters, dashboards, and vendor agents as
sinks or export paths.

Do not move business logging into the core runtime just because the runtime is
near the handoff. The core runtime should emit runtime facts. The app layer
should emit business meaning. A logger or observability adapter can then route
those records to the right sink.

## Where CoAkka Logger Fits

A practical starting point is:

- keep business logs in the app logger the team already uses;
- keep runtime delivery evidence in CoAkka Runtime;
- add CoAkka Logger only when log delivery itself needs a bounded,
  cross-language, pressure-aware boundary.

CoAkka Logger is useful when logging itself needs a bounded operational
boundary:

- bounded admission for log records;
- visible pressure counters;
- emitted, delivered, dropped, rejected, and drained evidence;
- cross-language native-backed shape;
- deterministic drains for samples and embedding tests;
- correlation with runtime target, payload identity, request id, and route
  generation.

It is not a mandatory replacement for every app logger, every sink, or every
observability platform. Keep the host logger when it is already simple,
bounded, and honest under pressure. Use CoAkka Logger when logging behavior is
part of the runtime, performance, or polyglot operational contract.

For teams that already use SLF4J, log4j, logback, Python logging, Go `slog` or
`zap`, .NET `ILogger`, Android logging, or platform logging, CoAkka Logger can
sit beside that stack as the bounded lane for runtime-shaped operational
records: emitted, admitted, dropped, rejected, drained, pressure, deadletter
correlation, request id, target, payload identity, and route generation.

## Observability Export

Observability export belongs above or beside the runtime:

- OpenTelemetry span creation and export;
- Prometheus metrics;
- dashboards and alert rules;
- log shipping to Loki, ELK, or a vendor platform;
- long-term retention;
- fleet-level sampling policy;
- tenant or compliance reporting.

CoAkka should contribute runtime facts that those systems can correlate. The
exporter, retention policy, dashboard, and alert ownership remain with the
app-host, connector, framework adapter, platform team, or observability addon.

## Practical Rule

Use this split:

```text
If it explains delivery, it is runtime evidence.
If it explains business meaning, it is an app or handler log.
If it explains fleet health, export it to observability.
```

Good records carry IDs and facts:

- target;
- payload identity;
- request or correlation id;
- route generation;
- admission outcome;
- reply, timeout, rejection, or deadletter reason;
- queue depth and capacity when pressure matters.

Avoid giant payload dumps, high-cardinality debug spam, blocking sinks, and
logs that hide their own loss. Logging strategy is part of performance design.
