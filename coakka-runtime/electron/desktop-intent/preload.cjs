const { contextBridge, ipcRenderer } = require("electron");
const { createCoAkkaPreloadApi } = require("coakka-v2-connector-electron/preload");
const api = createCoAkkaPreloadApi(ipcRenderer);

contextBridge.exposeInMainWorld("coakka", {
  // This sample needs one intent, not arbitrary IPC or native resources.
  intent: api.intent,
  finish(result) {
    return ipcRenderer.invoke("coakka:sample-finish", result);
  },
});
