//! A standalone, local request/reply application using the admitted package.

use coakka_runtime::{
    CoAkkaError, ConnectorStartSpec, DeliveryHint, PayloadIdentity, RouteSpec, RuntimeHost,
};
use std::error::Error;
use std::time::Duration;

const STORE: &str = "samples.customer.store";
const MISSING: &str = "samples.customer.missing";

fn run() -> Result<(), Box<dyn Error>> {
    let mut intent = ConnectorStartSpec::new("rust-sample", "rust-sample-node");
    intent.routes = vec![RouteSpec::single_local(STORE, "local", 0)];
    // RuntimeHost is the RAII owner. Every `?` below retires it and joins its
    // reader before returning. The callback owns no reference back to its host.
    let runtime = RuntimeHost::start(intent)?;
    runtime.register_handler(STORE, |request| {
        RuntimeHost::make_json_reply_from_request_identity(&request, STORE, r#"{"customer":"Ada"}"#)
            .ok()
    })?;
    let identity = PayloadIdentity::json("samples.customer.create.request.v1", 1);
    let response = runtime.ask_json(
        "samples.customer.frontend",
        STORE,
        r#"{"name":"Ada"}"#,
        identity.clone(),
        Duration::from_secs(2),
        "create_customer",
        DeliveryHint::RouterDefault,
    )?;
    if response.payload_utf8() != r#"{"customer":"Ada"}"# {
        return Err("unexpected customer reply".into());
    }
    let miss = match runtime.ask_json(
        "samples.customer.frontend",
        MISSING,
        r#"{"name":"Ada"}"#,
        identity,
        Duration::from_secs(2),
        "missing_customer",
        DeliveryHint::RouterDefault,
    ) {
        Err(CoAkkaError::Deadletter(value)) => value,
        Err(error) => return Err(error.into()),
        Ok(_) => return Err("missing route unexpectedly replied".into()),
    };
    if miss.reason_code != 2 || miss.original.target != MISSING {
        return Err("unexpected typed route-miss result".into());
    }
    let info = runtime.runtime_info()?;
    let startup = runtime.runtime_startup_config()?;
    let delivery = runtime.runtime_delivery_config()?;
    let stats = runtime.client_stats();
    if stats.pending_requests != 0 || stats.handler_panics != 0 {
        return Err("sample did not reach a quiescent request boundary".into());
    }
    // These are Core-issued facts, not reconstructed input preferences.
    println!(
        "runtime={} git={} abi={} cpu_policy={} connection_mode={} monitor={} delivery={}/{}",
        info.runtime_version,
        info.git_commit,
        info.abi_version,
        startup.execution.requested_policy,
        startup.connection.mode.0,
        startup.monitor.enabled,
        delivery.request_mode,
        delivery.response_mode,
    );
    println!(
        "response={} deadletter={} original={} pending={}",
        response.payload_utf8(),
        miss.reason_code,
        miss.original.target,
        stats.pending_requests
    );
    drop(runtime);
    println!("CoAkka Rust sample ok");
    Ok(())
}

fn main() -> Result<(), Box<dyn Error>> {
    run()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;

    #[test]
    fn ordinary_application() {
        run().unwrap();
    }

    #[test]
    fn scoped_error_paths_retire_callback_and_allow_reopen() {
        for phase in 0..4 {
            let capture = Arc::new(());
            let retired = Arc::downgrade(&capture);
            let attempt = || -> Result<(), Box<dyn Error>> {
                let mut intent = ConnectorStartSpec::new("rust-scope", "rust-scope-node");
                intent.routes = vec![RouteSpec::single_local(STORE, "local", 0)];
                let host = RuntimeHost::start(intent)?;
                host.register_handler(STORE, move |request| {
                    let _borrow = &capture;
                    RuntimeHost::make_json_reply_from_request_identity(
                        &request,
                        STORE,
                        r#"{"customer":"Ada"}"#,
                    )
                    .ok()
                })?;
                if phase == 0 {
                    return Err("application failure after registration".into());
                }
                let outcome = host.ask_json(
                    "scope-control",
                    if phase == 1 { MISSING } else { STORE },
                    "{}",
                    PayloadIdentity::json("sample.scope.v1", 1),
                    Duration::from_secs(2),
                    "scope",
                    DeliveryHint::RouterDefault,
                );
                if phase == 1 {
                    match outcome {
                        Err(CoAkkaError::Deadletter(value)) if value.reason_code == 2 => {}
                        other => panic!("expected typed Core route miss, got {other:?}"),
                    }
                    return Err("application failure after deadletter".into());
                }
                outcome?;
                if phase == 2 {
                    return Err("application failure after response".into());
                }
                panic!("application unwind outside callback/FFI")
            };
            if phase == 3 {
                assert!(std::panic::catch_unwind(attempt).is_err());
            } else {
                assert!(attempt().is_err());
            }
            assert!(
                retired.upgrade().is_none(),
                "callback retained after host Drop"
            );
            let next =
                RuntimeHost::start(ConnectorStartSpec::new("reopen", "reopen-node")).unwrap();
            assert_eq!(next.client_stats().pending_requests, 0);
            drop(next);
        }
    }
}
