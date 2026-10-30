// Real sandboxed renderer: values only, no connector/native/OS owner.
async function run() {
  if (typeof process !== "undefined" || typeof require !== "undefined") throw new Error("renderer isolation failed");
  const result = await window.coakka.intent({ intentId: "electron-sample-intent-1",
    source: "electron-renderer", target: "samples.electron.intent.echo", operation: "echo",
    payload: { message: "hello-electron-runtime – tiếng Việt 🌿" },
    payloadIdentity: { messageType: "samples.electron.intent.echo.request.v1", payloadSchemaVersion: 1, payloadFormat: "json" },
    timeoutMs: 2000 });
  document.getElementById("status").textContent = JSON.stringify(result, null, 2);
  await window.coakka.finish(result);
}
void run().catch(error => { document.getElementById("status").textContent = String(error); });
