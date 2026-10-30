import assert from "node:assert/strict";
import { app, BrowserWindow, ipcMain } from "electron";
import { dirname, isAbsolute, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ElectronRuntimeIntentBridge, localRoute, registerElectronIntentIpcHandler } from "coakka-v2-connector-electron";
import { acceptedFrame, createAdmission } from "./lifecycle.mjs";

const root = dirname(fileURLToPath(import.meta.url));
const target = "samples.electron.intent.echo";
const entry = pathToFileURL(join(root, "index.html")).href;
let owner, window, unregister, timer, rejectReport;
let allowClose = false, finished = false, failed = false, shutdown;

// Native Core configuration is not read from argv. This optional path belongs
// only to the sample's browser cache and must never select a native library.
function configureStorage() {
  const args = process.argv.slice(2);
  if (args.length === 0) return;
  if (args.length !== 2 || args[0] !== "--sample-data-dir" || !isAbsolute(args[1])) {
    throw new Error("expected --sample-data-dir <absolute browser storage directory>");
  }
  app.setPath("userData", args[1]); app.setPath("sessionData", args[1]);
}

function fail(error) {
  failed = true;
  console.error(error instanceof Error ? error.stack : error);
  rejectReport?.(error);
}

function stop() {
  if (shutdown) return shutdown;
  shutdown = (async () => {
    clearTimeout(timer);
    // Refuse later IPC first; the owner then joins all actual accepted calls.
    try { unregister?.(); ipcMain.removeHandler("coakka:sample-finish"); }
    catch (error) { fail(error); }
    try { await owner?.close(); }
    catch (error) { fail(error); }
    allowClose = true;
    if (window && !window.isDestroyed()) {
      const closed = new Promise(resolve => window.once("closed", resolve));
      window.close(); await closed;
    }
    // Failure exit is explicit only after app IPC/Core/window ownership retires.
    // This is framework process exit, not an OS kill or native-close substitute.
    if (failed) app.exit(1); else app.quit();
  })();
  return shutdown;
}

async function run() {
  configureStorage();
  await app.whenReady();
  if (shutdown) throw new Error("sample startup already sealed");
  const bridge = ElectronRuntimeIntentBridge.start({ systemName: "electron-runtime-sample",
    nodeId: "electron-runtime-sample-main", routes: [localRoute(target)] });
  owner = createAdmission(() => bridge.close());
  const info = bridge.runtimeInfo(), startup = bridge.runtimeStartupConfig();
  bridge.registerJsonIntentHandler(target, intent => ({ handledBy: "electron-main", echo: intent.payload }));
  window = new BrowserWindow({ width: 900, height: 680, show: true, webPreferences: {
    preload: join(root, "preload.bundle.cjs"), contextIsolation: true,
    nodeIntegration: false, sandbox: true, devTools: false,
  } });
  window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  window.webContents.on("will-navigate", event => event.preventDefault());
  window.on("close", event => {
    if (allowClose) return;
    event.preventDefault();
    if (!finished) fail(new Error("sample window closed before its verified reply"));
    void stop();
  });
  window.webContents.on("render-process-gone", (_event, detail) => fail(new Error(JSON.stringify(detail))));
  window.webContents.on("preload-error", (_event, _path, error) => fail(error));
  const ipc = {
    handle(channel, listener) { ipcMain.handle(channel, (event, payload) => owner.run(() => listener(event, payload))); },
    removeHandler(channel) { ipcMain.removeHandler(channel); },
  };
  const authorized = event => acceptedFrame(event, window.webContents, entry);
  unregister = registerElectronIntentIpcHandler(ipc, bridge, { authorize: authorized });
  const reported = new Promise((resolve, reject) => {
    rejectReport = reject;
    timer = setTimeout(() => reject(new Error("Electron runtime sample timed out")), 10_000);
    ipc.handle("coakka:sample-finish", (event, result) => {
      if (!authorized(event) || finished) throw new Error("sample report refused");
      assert.equal(result?.intentId, "electron-sample-intent-1");
      assert.equal(result.target, target); assert.equal(result.operation, "echo");
      assert.equal(result.payload?.handledBy, "electron-main");
      assert.deepEqual(result.payload.echo, { message: "hello-electron-runtime – tiếng Việt 🌿" });
      assert.equal(result.payloadIdentity?.payloadFormat, "json");
      assert.equal(result.payloadIdentity.payloadSchemaVersion, 1);
      assert.equal(result.payloadIdentity.messageType, "samples.electron.intent.echo.request.v1");
      finished = true; resolve(result); return true;
    });
  });
  // Observe report rejection before loading. ESM evaluation must finish before
  // the ready event: never top-level-await app.whenReady() in this entrypoint.
  await Promise.all([reported, window.loadURL(entry)]);
  console.log(JSON.stringify({ sample: "electron-desktop-intent", state: "pass", info, startup,
    connectorStats: bridge.connectorStats(), versions: process.versions,
    platform: process.platform, architecture: process.arch, sandbox: true }));
}

app.on("window-all-closed", () => {});
app.on("before-quit", event => {
  if (allowClose) return;
  event.preventDefault();
  if (!finished) fail(new Error("sample quit before its verified reply"));
  void stop();
});
void run().catch(fail).finally(stop);
