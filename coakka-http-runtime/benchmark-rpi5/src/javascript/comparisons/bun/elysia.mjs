import { Elysia } from "elysia";
import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const app = new Elysia()
  .get("/fixed", ({ set }) => {
    set.headers["content-type"] = "text/plain";
    return body;
  })
  .listen({ hostname: "127.0.0.1", port: 0 });

ready(app.server.port);
stopOnSignals(() => app.stop(true));
