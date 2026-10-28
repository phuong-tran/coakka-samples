//! Standalone command/value boundary; this is not an actual WebView consumer.
use coakka_runtime::{CoAkkaError, ConnectorStartSpec, RouteSpec};
use coakka_tauri_intents::{
    coakka_ask_intent_command, IntentEnvelope, IntentErrorCause, IntentPayloadIdentity,
    RuntimeIntentDispatcher,
};
use serde_json::json;
use std::collections::HashMap;
use std::error::Error;

const TARGET: &str = "samples.tauri.intent.echo";
const MISSING: &str = "samples.tauri.intent.missing";

fn intent(target: &str, id: &str) -> IntentEnvelope {
    IntentEnvelope {
        intent_id: id.into(),
        source: "tauri-command-sample".into(),
        target: target.into(),
        operation: "echo".into(),
        payload: json!({"message": "hello-tauri-intent"}),
        payload_identity: IntentPayloadIdentity::json("samples.tauri.echo.request.v1", 1),
        timeout_ms: Some(2_000),
        // Reserved app-host metadata in this exact adapter, not propagated
        // runtime tracing or idempotency headers.
        headers: HashMap::new(),
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let mut spec = ConnectorStartSpec::new("tauri-command-sample", "tauri-command-node");
    spec.routes = vec![RouteSpec::single_local(TARGET, "local", 0)];
    // This synchronous CLI owns its dispatcher through every `?`. A GUI must
    // use its bounded blocking executor and join accepted work before Drop.
    let dispatcher = RuntimeIntentDispatcher::start(spec)?;
    dispatcher.register_json_handler(TARGET, |request| {
        Ok(json!({"handledBy": "rust-command", "operation": request.operation, "echo": request.payload}))
    })?;
    let response = coakka_ask_intent_command(&dispatcher, intent(TARGET, "sample-intent-1"))?;
    if response.intent_id != "sample-intent-1"
        || response.target != TARGET
        || response.payload["echo"]["message"] != "hello-tauri-intent"
    {
        return Err("unexpected correlated intent reply".into());
    }
    let missing = match coakka_ask_intent_command(&dispatcher, intent(MISSING, "sample-intent-2")) {
        Err(error) => error,
        Ok(_) => return Err("missing target unexpectedly replied".into()),
    };
    match &missing.cause {
        IntentErrorCause::Runtime(error) => match error.as_ref() {
            CoAkkaError::Deadletter(result)
                if result.reason_code == 2 && result.original.target == MISSING => {}
            _ => return Err(missing.into()),
        },
        _ => return Err(missing.into()),
    }
    let info = dispatcher.runtime_info()?;
    let startup = dispatcher.runtime_startup_config()?;
    let delivery = dispatcher.runtime_delivery_config()?;
    let stats = dispatcher.runtime_client_stats();
    if stats.pending_requests != 0 || stats.handler_panics != 0 {
        return Err("intent sample did not reach a quiescent request boundary".into());
    }
    println!(
        "coakka_tauri_intent_result {}",
        serde_json::to_string(&response)?
    );
    println!(
        "coakka_tauri_intent_deadletter {}",
        serde_json::to_string(&missing)?
    );
    println!(
        "runtime={} git={} abi={} cpu_policy={} connection_mode={} monitor={} delivery={}/{} pending={}",
        info.runtime_version, info.git_commit, info.abi_version,
        startup.execution.requested_policy, startup.connection.mode.0,
        startup.monitor.enabled, delivery.request_mode, delivery.response_mode, stats.pending_requests,
    );
    drop(dispatcher);
    println!("CoAkka Tauri command sample ok");
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
    fn ordinary_command_application() {
        run().unwrap();
    }

    #[test]
    fn dispatcher_clones_join_on_error_and_unwind() {
        for phase in 0..4 {
            let capture = Arc::new(());
            let retired = Arc::downgrade(&capture);
            let attempt = || -> Result<(), Box<dyn Error>> {
                let mut spec = ConnectorStartSpec::new("tauri-scope", "tauri-scope-node");
                spec.routes = vec![RouteSpec::single_local(TARGET, "local", 0)];
                let owner = RuntimeIntentDispatcher::start(spec)?;
                owner.register_json_handler(TARGET, move |request| {
                    let _borrow = &capture;
                    Ok(request.payload)
                })?;
                let dispatcher = owner.clone();
                drop(owner);
                assert!(retired.upgrade().is_some(), "clone must retain its host");
                if phase == 0 {
                    return Err("app failure after clone".into());
                }
                let result = coakka_ask_intent_command(
                    &dispatcher,
                    intent(if phase == 1 { MISSING } else { TARGET }, "scope-intent"),
                );
                if phase == 1 {
                    let error = result.unwrap_err();
                    assert!(matches!(error.cause, IntentErrorCause::Runtime(ref cause)
                        if matches!(cause.as_ref(), CoAkkaError::Deadletter(value) if value.reason_code == 2)));
                    return Err(error.into());
                }
                assert_eq!(result?.intent_id, "scope-intent");
                if phase == 2 {
                    return Err("app failure after reply".into());
                }
                panic!("app unwind outside runtime callback")
            };
            if phase == 3 {
                assert!(std::panic::catch_unwind(attempt).is_err());
            } else {
                assert!(attempt().is_err());
            }
            assert!(
                retired.upgrade().is_none(),
                "last clone must retire callback"
            );
            drop(
                RuntimeIntentDispatcher::start(ConnectorStartSpec::new("reopen", "reopen-node"))
                    .unwrap(),
            );
        }
    }
}
