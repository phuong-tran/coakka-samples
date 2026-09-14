import { ready, stopOnSignals } from "../../lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const server = Bun.serve({
  hostname: "127.0.0.1",
  port: 0,
  development: false,
  routes: {
    "/fixed": {
      GET: () => new Response(body, { headers: { "content-type": "text/plain" } }),
    },
  },
});

ready(server.port);
stopOnSignals(() => server.stop(true));
