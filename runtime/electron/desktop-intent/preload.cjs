const { contextBridge, ipcRenderer } = require("electron");
const { createCoAkkaPreloadApi } = require("coakka-v2-connector-electron/preload");

contextBridge.exposeInMainWorld("coakka", {
  ...createCoAkkaPreloadApi(ipcRenderer),
  finish(result) {
    return ipcRenderer.invoke("coakka:sample-finish", result);
  },
});
