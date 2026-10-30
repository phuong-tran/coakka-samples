import { Buffer } from "node:buffer";
import { RuntimeHost, localRoute } from "coakka-v2-connector-node";

const target = "samples.echo";
const runtime = RuntimeHost.start({
  systemName: "node-first-run",
  nodeId: "node-first-run-node",
  routes: [localRoute(target)],
});
try {
  runtime.registerPayloadHandler(target, bytes => Buffer.concat([Buffer.from("accepted-"), bytes]));
  const reply = await runtime.askPayload("node-first-run", target, Buffer.from("hello"), 2000);
  console.log(Buffer.from(reply).toString("utf8"));
} finally {
  await runtime.close();
}
