/** Fixed-response framework and CoAkka applications for Node.js and Bun. */
import { Builder, Header, Headers, Limits, Response } from "@coakka/http";

const BODY = Buffer.from("0123456789abcdef0123456789abcdef", "ascii");
const mode = process.argv[2];
const port = Number(process.argv[3]);
if (!Number.isInteger(port) || port < 1 || port > 65_535) {
  throw new RangeError("usage: server.mjs <mode> <port>");
}

function stopSignal() {
  return new Promise((resolve) => {
    process.once("SIGINT", resolve);
    process.once("SIGTERM", resolve);
  });
}

async function coakka() {
  const response = Response.bytes(BODY, {
    headers: new Headers([new Header("content-type", "application/octet-stream")]),
  });
  const service = new Builder()
    .listen("127.0.0.1", port)
    .limits(new Limits({ eventLoopThreads: 1 }))
    .get("/fixed", () => response)
    .start();
  await stopSignal();
  await service.close();
}

async function expressServer() {
  const { default: express } = await import("express");
  const app = express();
  app.disable("x-powered-by");
  app.get("/fixed", (_request, response) => {
    response.type("application/octet-stream").send(BODY);
  });
  const server = app.listen(port, "127.0.0.1");
  await stopSignal();
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}

async function fastifyServer() {
  const { default: Fastify } = await import("fastify");
  const app = Fastify({ logger: false });
  app.get("/fixed", (_request, reply) => reply.type("application/octet-stream").send(BODY));
  await app.listen({ host: "127.0.0.1", port });
  await stopSignal();
  await app.close();
}

async function elysiaServer() {
  const { Elysia } = await import("elysia");
  const app = new Elysia().get(
    "/fixed",
    () => new globalThis.Response(BODY, { headers: { "content-type": "application/octet-stream" } }),
  ).listen({ hostname: "127.0.0.1", port });
  await stopSignal();
  await app.stop();
}

async function honoServer() {
  const { Hono } = await import("hono");
  const app = new Hono();
  app.get("/fixed", (context) => context.body(BODY, 200, { "content-type": "application/octet-stream" }));
  const server = Bun.serve({ hostname: "127.0.0.1", port, fetch: app.fetch });
  await stopSignal();
  server.stop(true);
}

switch (mode) {
  case "coakka": await coakka(); break;
  case "express": await expressServer(); break;
  case "fastify": await fastifyServer(); break;
  case "elysia": await elysiaServer(); break;
  case "hono": await honoServer(); break;
  default: throw new Error(`unsupported JavaScript lane: ${mode}`);
}
