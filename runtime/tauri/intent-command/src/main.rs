use std::collections::HashMap;
use std::process;

use coakka_runtime::{ConnectorStartSpec, RouteSpec};
use coakka_tauri_intents::{
    coakka_ask_intent_command, IntentEnvelope, IntentPayloadIdentity, RuntimeIntentDispatcher,
};
use serde_json::json;

fn main() {
    if let Err(error) = run() {
        eprintln!(
            "coakka_tauri_intent_error {}",
            serde_json::to_string(&error).unwrap_or_else(|_| "{\"code\":\"SERIALIZE_ERROR\"}".into())
        );
        process::exit(1);
    }
}

fn run() -> coakka_tauri_intents::Result<()> {
    let target = "samples.tauri.intent.echo";
    let mut spec = ConnectorStartSpec::new("tauri-intent-command-sample", "tauri-intent-command-node");
    spec.queue_capacity = 64;
    spec.routes = vec![RouteSpec::single_local(target, "127.0.0.1", 19421)];

    let dispatcher = RuntimeIntentDispatcher::start(spec)?;
    dispatcher.register_json_handler(target, |request| {
        Ok(json!({
            "handledBy": "rust-command",
            "operation": request.operation,
            "echo": request.payload
        }))
    })?;

    let result = coakka_ask_intent_command(
        &dispatcher,
        IntentEnvelope {
            intent_id: "sample-intent-1".to_string(),
            source: "tauri-webview".to_string(),
            target: target.to_string(),
            operation: "echo".to_string(),
            payload: json!({"message":"hello-tauri-intent"}),
            payload_identity: IntentPayloadIdentity::json("samples.tauri.echo.request.v1", 1),
            timeout_ms: Some(2_000),
            headers: HashMap::new(),
        },
    )?;

    println!(
        "coakka_tauri_intent_result {}",
        serde_json::to_string(&result)
            .unwrap_or_else(|_| "{\"intentId\":\"sample-intent-1\"}".into())
    );
    Ok(())
}
