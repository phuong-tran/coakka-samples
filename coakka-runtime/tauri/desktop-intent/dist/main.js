const button = document.querySelector("#send-intent");
const message = document.querySelector("#message");
const result = document.querySelector("#result");
const facts = document.querySelector("#core-facts");
const invoke = window.__TAURI__.core.invoke;
let nextIntent = 0;

// Display the accepted Core snapshot, never infer native defaults from inputs.
async function loadFacts() {
  try {
    facts.textContent = JSON.stringify(await invoke("coakka_runtime_facts"), null, 2);
    button.disabled = false;
  } catch (error) {
    facts.textContent = JSON.stringify(error, null, 2);
  }
}
void loadFacts();

function writeResult(value) {
  result.textContent = JSON.stringify(value, null, 2);
}

button.addEventListener("click", async () => {
  button.disabled = true;
  try {
    if (nextIntent === Number.MAX_SAFE_INTEGER) {
      throw new Error("Application intent identity exhausted");
    }
    const response = await invoke("coakka_ask_intent", {
      intent: {
        intentId: `desktop-intent-${++nextIntent}`,
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
