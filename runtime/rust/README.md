# Rust Runtime Samples

Rust runtime samples document the `coakka-runtime-rs` spike tarball shape. This
runtime lane consumes the public Rust spike tarball built against native
runtime `0.1.0+a671b3a`.

This lane is intentionally marked as a spike. It proves the runtime shape before
claiming a stable Rust API or crates.io-ready package.

## Run

```sh
bash run.sh runtime rust basic
```

Rust samples expect a working Rust/Cargo toolchain.

## Before: Internal REST

A Rust CRUD service can expose an internal Axum/Actix endpoint just so another
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
    .post("http://customer-store/internal/customers")
    .json(&command)
    .send()
    .await?
    .json::<MutationResponse>()
    .await?;
```

That works, but it adds URL wiring, HTTP parsing, headers, status/error
mapping, timeout policy, and tests before there is a real product boundary.

## After: Runtime Target

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

`samples.customer.store` is the sample's capability target name. In your app,
choose your own target name, then use the same value in the route table, the
local `register_handler(...)`, and every caller target. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

`ask_json(...)` below is a convenience helper for JSON samples. It is not the
runtime saying that only JSON is supported. The runtime contract is an envelope
with target, payload bytes, message type, schema version, and payload format.
The Rust package is still a spike, so treat non-JSON helper ergonomics as
connector API surface work rather than a runtime limitation.

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

- Treat this package as a spike until the Rust API is promoted.
- Keep one active `RuntimeHost` per process.
- Keep queue sizes bounded.
- Treat matched deadletters as route/delivery results.
- Windows support is not claimed.
