import { Hono } from "hono";
import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const app = new Hono();
app.get("/fixed", (context) =>
  context.body(body, 200, { "content-type": "text/plain" }),
);
const server = Bun.serve({
  hostname: "127.0.0.1",
  port: 0,
  development: false,
  fetch: app.fetch,
});

ready(server.port);
stopOnSignals(() => server.stop(true));
