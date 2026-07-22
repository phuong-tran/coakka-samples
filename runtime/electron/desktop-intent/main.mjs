import { app, BrowserWindow, ipcMain } from "electron";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import {
  ElectronRuntimeIntentBridge,
  registerElectronIntentIpcHandler,
} from "coakka-v2-connector-electron";

const __dirname = dirname(fileURLToPath(import.meta.url));
const target = "samples.electron.intent.echo";
const bridge = ElectronRuntimeIntentBridge.start({
  systemName: "electron-runtime-sample",
  nodeId: "electron-runtime-sample-main",
  defaultTarget: target,
  routePort: 19483,
});

let cleanupIntentHandler = null;

bridge.registerJsonIntentHandler(target, async (intent) => ({
  handledBy: "electron-main",
  echo: intent.payload,
}));

async function runSmoke() {
  cleanupIntentHandler = registerElectronIntentIpcHandler(ipcMain, bridge);
  const resultPromise = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Electron runtime sample timed out")), 10_000);
    ipcMain.handle("coakka:sample-finish", (_event, result) => {
      clearTimeout(timeout);
      resolve(result);
    });
  });

  const window = new BrowserWindow({
    show: false,
    webPreferences: {
      preload: join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  await window.loadFile(join(__dirname, "index.html"));
  const result = await resultPromise;
  if (result?.payload?.handledBy !== "electron-main") {
    throw new Error(`unexpected Electron intent result=${JSON.stringify(result)}`);
  }
  if (result.payload.echo?.message !== "hello-electron-runtime") {
    throw new Error(`unexpected Electron echo payload=${JSON.stringify(result.payload)}`);
  }
  console.log(
    `coakka_electron_runtime_response payload=${JSON.stringify(result.payload)} ` +
      `delivered=${result.runtime.delivered} matchedResponses=${result.runtime.matchedResponses}`,
  );
  window.destroy();
}

app.whenReady()
  .then(runSmoke)
  .then(() => app.quit())
  .catch((error) => {
    console.error(error);
    app.exit(1);
  });

app.on("window-all-closed", () => {
  bridge.close();
  cleanupIntentHandler?.();
  ipcMain.removeHandler("coakka:sample-finish");
});
