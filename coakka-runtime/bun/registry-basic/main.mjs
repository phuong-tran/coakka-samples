import { Buffer } from "node:buffer";
import { RuntimeHost, localRoute } from "coakka-v2-connector-bun";

const target = "samples.echo";
const runtime = RuntimeHost.start({
  systemName: "bun-first-run",
  nodeId: "bun-first-run-node",
  routes: [localRoute(target)],
});
try {
  runtime.registerPayloadHandler(target, bytes => Buffer.concat([Buffer.from("accepted-"), bytes]));
  const reply = await runtime.askPayload("bun-first-run", target, Buffer.from("hello"), 2000);
  console.log(Buffer.from(reply).toString("utf8"));
} finally {
  await runtime.close();
}
