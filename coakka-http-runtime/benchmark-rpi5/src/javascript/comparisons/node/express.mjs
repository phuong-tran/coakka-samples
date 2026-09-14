import express from "express";
import { once } from "node:events";
import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const app = express();
app.disable("x-powered-by");
app.get("/fixed", (_request, response) => {
  response.type("text/plain").send(body);
});
const server = app.listen(0, "127.0.0.1");
await once(server, "listening");

ready(server.address().port);
stopOnSignals(async () => {
  server.close();
  server.closeAllConnections();
  await once(server, "close");
});
