/** Explicit secure HTTP/2 or HTTP/3 with a normal host-inlined handler. */
import { Builder, ListenerProtocol, Response, TransportSecurity } from "@coakka/http";
import { resolve } from "node:path";

/** Require an explicit recipe and generated development credentials. */
function argument(name: string): string {
  const index = process.argv.indexOf(name);
  const value = index >= 0 ? process.argv[index + 1] : undefined;
  if (value === undefined) throw new Error(`${name} requires a value`);
  return value;
}

const selected = argument("--protocol");
if (selected !== "http2" && selected !== "http3") throw new Error("choose http2 or http3");
const fixtures = resolve(argument("--fixtures"));
const stopped = new Promise<void>((done) => {
  process.once("SIGINT", done);
  process.once("SIGTERM", done);
});
const service = new Builder().listener({
  host: "127.0.0.1",
  protocol: selected === "http2" ? ListenerProtocol.HTTP_2 : ListenerProtocol.HTTP_3,
  security: TransportSecurity.TLS,
  credentialGeneration: 1n, credentialId: "typescript-protocol-sample",
  certificateChainFile: resolve(fixtures, "server.pem"),
  privateKeyFile: resolve(fixtures, "server.key"),
}).get("/protocol", () => {
  console.log("typescript-protocol-requested");
  return Response.text("protocol-ready");
}).start();
try {
  console.log(`typescript-${selected}=https://127.0.0.1:${service.port}`);
  await stopped;
} finally { await service.close(); }
console.log("typescript-protocol-shutdown=pass");
