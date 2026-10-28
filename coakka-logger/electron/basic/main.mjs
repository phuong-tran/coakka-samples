import { app, BrowserWindow, ipcMain } from "electron";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import {
  ElectronLoggerIntentBridge,
  registerElectronLoggerIpcHandler,
} from "coakka-logger-electron";

const __dirname = dirname(fileURLToPath(import.meta.url));
const bridge = ElectronLoggerIntentBridge.start({
  systemName: "electron-sample-logger",
  queueCapacity: 8,
});

let cleanupLoggerHandler = null;

async function runSample() {
  cleanupLoggerHandler = registerElectronLoggerIpcHandler(ipcMain, bridge);
  const resultPromise = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Electron logger sample timed out")), 10_000);
    ipcMain.handle("coakka:logger-sample-finish", (_event, result) => {
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
  if (result?.record?.category !== "samples.logger.electron.basic") {
    throw new Error(`unexpected Electron logger result=${JSON.stringify(result)}`);
  }

  console.log(
    `coakka_logger_record sequence=${result.record.sequence} ` +
      `level=${result.record.levelName} category=${result.record.category} message=${result.record.message}`,
  );
  console.log(
    `coakka_logger_stats emitted=${result.stats.emittedCount} ` +
      `delivered=${result.stats.deliveredCount} dropped=${result.stats.droppedCount}`,
  );
  window.destroy();
}

app.whenReady()
  .then(runSample)
  .then(() => app.quit())
  .catch((error) => {
    console.error(error);
    app.exit(1);
  });

app.on("window-all-closed", () => {
  bridge.close();
  cleanupLoggerHandler?.();
  ipcMain.removeHandler("coakka:logger-sample-finish");
});
