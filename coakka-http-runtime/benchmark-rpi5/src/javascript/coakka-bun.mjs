import { Builder, Responses } from "./lib/coakka-service.mjs";
import { ready, stopOnSignals } from "./lib/lifecycle.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const response = Responses.bytes(body);
const service = await new Builder()
  .listen("127.0.0.1", 0)
  .get("/fixed", () => response)
  .start();

ready(service.port, "normal");
stopOnSignals(() => service.close());
