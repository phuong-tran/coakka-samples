import { resolve } from "node:path";

const packageName = process.versions.bun
  ? "coakka-http-runtime-bun"
  : "coakka-http-runtime-node";
const { Builder, ListenerProtocol, Response, TransportSecurity } = await import(packageName);

const [mode, certificates] = process.argv.slice(2);
if (!["http1", "http2", "http3", "multi"].includes(mode) || !certificates) {
  throw new Error("usage: server.mjs http1|http2|http3|multi certificate-directory");
}

const builder = new Builder();
if (mode === "http1") {
  builder.listen("127.0.0.1", 8080);
} else {
  const tls = {
    host: "127.0.0.1",
    port: 8443,
    credentialId: "local-example",
    credentialGeneration: 1n,
    certificateChainFile: resolve(certificates, "server.pem"),
    privateKeyFile: resolve(certificates, "server.key"),
  };
  if (mode === "multi") {
    builder.multiProtocolListener(tls);
  } else {
    builder.listener({
      ...tls,
      protocol: mode === "http2" ? ListenerProtocol.HTTP_2 : ListenerProtocol.HTTP_3,
      security: TransportSecurity.TLS,
    });
  }
}

const stopped = new Promise((done) => {
  process.once("SIGINT", done);
  process.once("SIGTERM", done);
});
const service = builder.get("/hello", () => Response.text("hello")).start();
try {
  console.log(`listening on 127.0.0.1:${service.port}`);
  if (mode === "multi") console.log(service.listenerInfo());
  await stopped;
} finally {
  await service.close();
}
