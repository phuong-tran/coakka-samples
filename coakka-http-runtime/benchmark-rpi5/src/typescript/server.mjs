/** Fixed-response framework and CoAkka applications for Node.js and Bun. */

const BODY = Buffer.from("0123456789abcdef0123456789abcdef", "ascii");
const mode = process.argv[2];
const port = Number(process.argv[3]);
if (!Number.isInteger(port) || port < 1 || port > 65_535) {
  throw new RangeError("usage: server.mjs <mode> <port>");
}

/** Register before startup; shutdown completion is checked by the driver. */
function stopSignal() {
  return new Promise((resolve) => {
    process.once("SIGINT", resolve);
    process.once("SIGTERM", resolve);
  });
}
const stopped = stopSignal();

/** The same public host-inlined path and installed native pair as the samples. */
async function coakka() {
  // Framework lanes must not load another server's native library or reserve
  // its state. Module loading is startup work, outside the measured interval.
  const { Builder, CpuPolicy, Header, Headers, Limits, Response } = await import("@coakka/http");
  const intent = process.env.COAKKA_BENCH_CPU_POLICY;
  if (intent !== "single" && intent !== "auto") throw new Error("explicit benchmark CPU intent required");
  const response = Response.bytes(BODY, {
    headers: new Headers([new Header("content-type", "application/octet-stream")]),
  });
  const service = new Builder()
    .listen("127.0.0.1", port)
    .limits(new Limits({ cpuPolicy: intent === "single" ? CpuPolicy.SINGLE : CpuPolicy.AUTO }))
    .get("/fixed", () => response)
    .start();
  try {
    console.log("coakka-runtime-info=" + JSON.stringify(service.runtimeInfo(),
      (_key, value) => typeof value === "bigint" ? value.toString() : value));
    await stopped;
  } finally {
    await service.close();
  }
}

/** Real Express routing; no logging or optional identification header. */
async function expressServer() {
  const { default: express } = await import("express");
  const app = express();
  app.disable("x-powered-by");
  app.get("/fixed", (_request, response) => {
    response.type("application/octet-stream").send(BODY);
  });
  const server = app.listen(port, "127.0.0.1");
  await stopped;
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}

/** Real Fastify routing with logging disabled for the fixed workload. */
async function fastifyServer() {
  const { default: Fastify } = await import("fastify");
  const app = Fastify({ logger: false });
  app.get("/fixed", (_request, reply) => reply.type("application/octet-stream").send(BODY));
  await app.listen({ host: "127.0.0.1", port });
  await stopped;
  await app.close();
}

/** Elysia owns dispatch; the payload matches all other lanes byte for byte. */
async function elysiaServer() {
  const { Elysia } = await import("elysia");
  const app = new Elysia().get(
    "/fixed",
    () => new globalThis.Response(BODY, { headers: { "content-type": "application/octet-stream" } }),
  ).listen({ hostname: "127.0.0.1", port });
  await stopped;
  await app.stop();
}

/** Hono routing on its documented Bun host, not a Bun.serve-only comparison. */
async function honoServer() {
  const { Hono } = await import("hono");
  const app = new Hono();
  app.get("/fixed", (context) => context.body(BODY, 200, { "content-type": "application/octet-stream" }));
  const server = Bun.serve({ hostname: "127.0.0.1", port, fetch: app.fetch });
  await stopped;
  await server.stop(false);
}
switch (mode) {
  case "coakka": await coakka(); break;
  case "express": await expressServer(); break;
  case "fastify": await fastifyServer(); break;
  case "elysia": await elysiaServer(); break;
  case "hono": await honoServer(); break;
  default: throw new Error(`unsupported JavaScript lane: ${mode}`);
}
console.log("benchmark-shutdown=pass");
