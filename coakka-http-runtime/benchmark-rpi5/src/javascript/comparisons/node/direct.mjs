import http from "node:http";
import { once } from "node:events";
import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const server = http.createServer((request, response) => {
  if (request.method !== "GET" || request.url !== "/fixed") {
    response.writeHead(404, { "content-length": "0" });
    response.end();
    return;
  }
  response.writeHead(200, {
    "content-length": body.length,
    "content-type": "text/plain",
  });
  response.end(body);
});
server.listen(0, "127.0.0.1");
await once(server, "listening");

ready(server.address().port);
stopOnSignals(async () => {
  server.close();
  server.closeAllConnections();
  await once(server, "close");
});
