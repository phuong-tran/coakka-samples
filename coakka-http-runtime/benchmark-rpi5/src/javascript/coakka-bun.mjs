import { developmentService } from "@coakka/http/host-inline";
import { ready, stopOnSignals } from "./lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const service = developmentService({
  name: "coakka-bun-fixed",
  host: "127.0.0.1",
  port: 0,
})
  .get(
    "/fixed",
    () => new Response(body, { headers: { "content-type": "text/plain" } }),
  )
  .start();

ready(service.port, "normal");
stopOnSignals(() => service.stop());
