const { contextBridge, ipcRenderer } = require("electron");
const { createCoAkkaLoggerPreloadApi } = require("coakka-logger-electron/preload");

contextBridge.exposeInMainWorld("coakkaLogger", {
  ...createCoAkkaLoggerPreloadApi(ipcRenderer),
  finish(result) {
    return ipcRenderer.invoke("coakka:logger-sample-finish", result);
  },
});
