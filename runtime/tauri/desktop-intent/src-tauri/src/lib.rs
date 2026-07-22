use std::sync::atomic::{AtomicU16, Ordering};

use coakka_runtime::{ConnectorStartSpec, RouteSpec};
use coakka_tauri_intents::{
    coakka_ask_intent_command, IntentEnvelope, IntentError, IntentResult, RuntimeIntentDispatcher,
};
use serde_json::json;

const TARGET: &str = "samples.tauri.desktop.echo";
static NEXT_LOCAL_PORT: AtomicU16 = AtomicU16::new(19431);

fn create_dispatcher() -> coakka_tauri_intents::Result<RuntimeIntentDispatcher> {
    let mut spec = ConnectorStartSpec::new("tauri-desktop-intent-sample", "tauri-desktop-intent-node");
    spec.queue_capacity = 64;
    let port = NEXT_LOCAL_PORT.fetch_add(1, Ordering::Relaxed);
    spec.routes = vec![RouteSpec::single_local(TARGET, "127.0.0.1", port)];

    let dispatcher = RuntimeIntentDispatcher::start(spec)?;
    dispatcher.register_json_handler(TARGET, |request| {
        Ok(json!({
            "handledBy": "tauri-rust-host",
            "operation": request.operation,
            "echo": request.payload
        }))
    })?;
    Ok(dispatcher)
}

fn ask_intent_with_dispatcher(
    dispatcher: &RuntimeIntentDispatcher,
    intent: IntentEnvelope,
) -> Result<IntentResult, IntentError> {
    coakka_ask_intent_command(dispatcher, intent)
}

#[tauri::command]
fn coakka_ask_intent(
    intent: IntentEnvelope,
    dispatcher: tauri::State<'_, RuntimeIntentDispatcher>,
) -> Result<IntentResult, IntentError> {
    ask_intent_with_dispatcher(&dispatcher, intent)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let dispatcher = create_dispatcher().expect("failed to start CoAkka runtime dispatcher");
    tauri::Builder::default()
        .manage(dispatcher)
        .invoke_handler(tauri::generate_handler![coakka_ask_intent])
        .run(tauri::generate_context!())
        .expect("error while running Tauri application");
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use super::*;
    use coakka_tauri_intents::IntentPayloadIdentity;
    use serde_json::Value;

    #[test]
    fn desktop_command_boundary_roundtrips_intent() {
        let dispatcher = create_dispatcher().expect("create dispatcher");
        let result = ask_intent_with_dispatcher(
            &dispatcher,
            IntentEnvelope {
                intent_id: "desktop-test-intent-1".to_string(),
                source: "tauri-webview".to_string(),
                target: TARGET.to_string(),
                operation: "echo".to_string(),
                payload: json!({"message":"hello-tauri-desktop"}),
                payload_identity: IntentPayloadIdentity::json(
                    "samples.tauri.desktop.echo.request.v1",
                    1,
                ),
                timeout_ms: Some(2_000),
                headers: HashMap::new(),
            },
        )
        .expect("ask intent");

        assert_eq!(result.payload["handledBy"], "tauri-rust-host");
        assert_eq!(result.payload["operation"], "echo");
        assert_eq!(result.payload["echo"]["message"], "hello-tauri-desktop");
    }

    #[test]
    fn desktop_command_rejects_wrong_target() {
        let dispatcher = create_dispatcher().expect("create dispatcher");
        let error = ask_intent_with_dispatcher(
            &dispatcher,
            IntentEnvelope {
                intent_id: "desktop-test-intent-2".to_string(),
                source: "tauri-webview".to_string(),
                target: "samples.tauri.desktop.missing".to_string(),
                operation: "echo".to_string(),
                payload: Value::Null,
                payload_identity: IntentPayloadIdentity::json(
                    "samples.tauri.desktop.echo.request.v1",
                    1,
                ),
                timeout_ms: Some(2_000),
                headers: HashMap::new(),
            },
        )
        .expect_err("missing target should deadletter");

        assert_eq!(error.target.as_deref(), Some("samples.tauri.desktop.missing"));
    }
}
