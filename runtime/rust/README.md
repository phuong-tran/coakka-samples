# Rust Runtime Samples

Rust runtime samples document the published `coakka-runtime-rs` archive shape.
This runtime lane consumes source version `2.4.0`, built against native runtime
generation `2.4.0+c2f53117` from connector generation `0afb5e9`.

## Run

```sh
bash run.sh runtime rust basic
```

Watch the Rust runtime walkthrough:

![CoAkka Runtime Rust walkthrough](../../docs/assets/coakka-runtime-rust.gif)

Full recording: [coakka-runtime-rust.mp4](../../docs/assets/coakka-runtime-rust.mp4)

Rust samples expect a working Rust/Cargo toolchain.

## Before: Backend HTTP

A Rust CRUD service can expose a backend Axum/Actix endpoint just so another
process can call a store capability:

```rust
async fn create_customer(
    State(store): State<CustomerStore>,
    Json(command): Json<CustomerDraft>,
) -> Json<MutationResponse> {
    Json(store.create(command))
}
```

The caller then forwards business work through HTTP:

```rust
let customer = client
    .post("http://customer-store/backend/customers")
    .json(&command)
    .send()
    .await?
    .json::<MutationResponse>()
    .await?;
```

That works, but it adds URL wiring, HTTP parsing, headers, status/error
mapping, timeout policy, and tests before there is a real product boundary.

## After: Runtime Target

Read the address change like this:

```text
Before backend HTTP:
  POST /backend/customers -> Axum/Actix handler

After CoAkka:
  target = "samples.customer.store" -> registered handler
```

The target plays a similar addressing role to a backend HTTP path, but it is
runtime routing vocabulary, not an HTTP URL.

`samples.customer.store` is the sample's capability target name. In your app,
choose your own target name, then use the same value in the route table, the
process-owned `register_handler(...)`, and every caller target. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

The same string appears again in the reply helper as `source`: the response is
coming from the target handler that produced it.

This sample registers one handler to stay small. A real app-host can register
multiple handlers if it owns multiple targets, such as
`samples.customer.create`, `samples.customer.update`, and
`samples.customer.list`. The route table and caller target must use the same
names.

With CoAkka, the store is a runtime target owned by the Rust process:

```rust
runtime.register_handler("samples.customer.store", |request| {
    RuntimeHost::make_json_reply_from_request_identity(
        &request,
        "samples.customer.store",
        store.create_json(request.payload_utf8()),
    )
    .ok()
})?;
```

`ask_json(...)` below is a convenience helper for JSON samples. It is not the
runtime saying that only JSON is supported. The runtime contract is an envelope
with target, payload bytes, message type, schema version, and payload format.
The Rust package currently ships as an archive package, so treat non-JSON
helper ergonomics as connector API surface work rather than a runtime
limitation.

The caller sends one typed runtime request:

```rust
let response = runtime.ask_json(
    "samples.customer.frontend",
    "samples.customer.store",
    command_json,
    PayloadIdentity::json("samples.customer.create.request.v1", 1),
    Duration::from_secs(5),
    "create_customer",
    DeliveryHint::RouterDefault,
)?;
```

HTTP stays at real client-facing or legacy boundaries. Internal Rust work can
stay as a runtime target with request/reply and deadletter semantics.

## Production Notes

- Treat `coakka-runtime-rs-2.1.0.tar.gz` as the pinned published Rust archive
  line for this sample.
- Keep one active `RuntimeHost` per process.
- Keep queue sizes bounded.
- Treat matched deadletters as route/delivery results.
- Treat packaged Windows payloads separately from matching-host Rust execution
  evidence.
- Use Linux validation before presenting this lane as a deployment path.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by the `2.4.0` archive when
integrating either lane.
