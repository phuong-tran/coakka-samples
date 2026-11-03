const { Builder, Response } = await import(
  process.versions.bun ? "coakka-http-runtime-bun" : "coakka-http-runtime-node"
);

const service = new Builder()
  .listen("127.0.0.1", 0)
  .get("/hello", () => Response.text("hello"))
  .start();
try {
  const reply = await fetch(`http://127.0.0.1:${service.port}/hello`, {
    signal: AbortSignal.timeout(5000),
  });
  if (reply.status !== 200 || await reply.text() !== "hello") {
    throw new Error("unexpected HTTP response");
  }
} finally {
  await service.close();
}
console.log("HTTP 200 hello");
