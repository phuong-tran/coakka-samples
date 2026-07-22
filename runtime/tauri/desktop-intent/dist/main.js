const button = document.querySelector("#send-intent");
const message = document.querySelector("#message");
const result = document.querySelector("#result");

function writeResult(value) {
  result.textContent = JSON.stringify(value, null, 2);
}

button.addEventListener("click", async () => {
  button.disabled = true;
  try {
    const invoke = window.__TAURI__.core.invoke;
    const response = await invoke("coakka_ask_intent", {
      intent: {
        intentId: `desktop-intent-${Date.now()}`,
        source: "tauri-webview",
        target: "samples.tauri.desktop.echo",
        operation: "echo",
        payload: { message: message.value },
        payloadIdentity: {
          messageType: "samples.tauri.desktop.echo.request.v1",
          schemaVersion: 1,
          format: "json",
        },
        timeoutMs: 2000,
        headers: {},
      },
    });
    writeResult(response);
  } catch (error) {
    writeResult(error);
  } finally {
    button.disabled = false;
  }
});
