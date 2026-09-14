import Fastify from "fastify";
import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const app = Fastify({ logger: false });
app.get("/fixed", async (_request, reply) => {
  reply.type("text/plain");
  return body;
});
await app.listen({ host: "127.0.0.1", port: 0 });

ready(app.server.address().port);
stopOnSignals(() => app.close());
