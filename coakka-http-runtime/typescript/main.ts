/**
 * Runnable TypeScript sample shared by Node.js and Bun.
 *
 * The same public package owns networking in both hosts. JavaScript keeps
 * only bounded copied values and synchronous handlers; stream producers use
 * async writes because native queue credit can arrive later.
 */

import {
  BodyDelivery,
  Builder,
  FileResponse,
  Header,
  Headers,
  Limits,
  MonitorCategory,
  MonitorCollection,
  Response,
  RouteControlCode,
  StreamingResponse,
  sse,
  websocket,
  type StreamRequestEvent,
  type Service,
} from "@coakka/http";
import { resolve } from "node:path";

const outboundTarget = "sample.upstream";

/** Parse the intentionally small command line shared by both hosts. */
function options(): Readonly<{ smoke: boolean; assets: string; ioUring: boolean }> {
  const args = process.argv.slice(2);
  const assetIndex = args.indexOf("--assets");
  const assetValue = assetIndex >= 0 ? args[assetIndex + 1] : undefined;
  if (assetIndex >= 0 && assetValue === undefined) {
    throw new Error("--assets requires a directory");
  }
  return Object.freeze({
    smoke: args.includes("--smoke"),
    assets: resolve(assetValue ?? "../assets"),
    ioUring: args.includes("--io-uring"),
  });
}

/** Collect one request stream until its terminal event selects the response. */
function streamedUploadHandler() {
  const chunks = new Map<string, Uint8Array[]>();
  return (event: StreamRequestEvent) => {
    const key = `${event.id.slot}:${event.id.generation}`;
    if (event.kind === "start") {
      chunks.set(key, []);
      return undefined;
    }
    if (event.kind === "data") {
      chunks.get(key)?.push(event.data);
      return undefined;
    }
    if (event.kind === "end") {
      const parts = chunks.get(key) ?? [];
      chunks.delete(key);
      const size = parts.reduce((total, part) => total + part.byteLength, 0);
      const body = new Uint8Array(size);
      let offset = 0;
      for (const part of parts) {
        body.set(part, offset);
        offset += part.byteLength;
      }
      return Response.bytes(body);
    }
    return undefined;
  };
}

/** Read copied query and indexed header projections without reparsing target. */
function helloResponse(request: Readonly<{
  queryParameters: ReadonlyArray<Readonly<{ encodedKey: Uint8Array; encodedValue: Uint8Array | null }>>;
  headers: Headers;
}>): Response {
  const first = request.queryParameters[0];
  const title = first !== undefined && new TextDecoder().decode(first.encodedKey) === "title"
    ? new TextDecoder().decode(first.encodedValue ?? new Uint8Array()) || "hello"
    : "hello";
  return Response.text(`${title} from ${request.headers.get("x-sample-caller") ?? ""}`);
}

/** Start the feature service after the upstream target has a concrete port. */
function createService(assets: string, upstreamPort: number, ioUring: boolean): Service {
  const builder = new Builder()
    .limits(new Limits({ maxHandlerBindings: 16 }))
    .monitor({
      collection: MonitorCollection.AGGREGATES_AND_EVENTS,
      eventCapacity: 32,
      maxEventsPerRead: 8,
      categories: MonitorCategory.LIFECYCLE | MonitorCategory.EXCHANGE,
      signalReserved: true,
    })
    .staticMount({
      urlPrefix: "/app",
      rootPath: assets,
      indexFile: "index.html",
      spaFallbackFile: "index.html",
    })
    .fileAuthority({
      id: 82n,
      rootPath: assets,
      maxActiveFiles: 2,
      maxFileBytes: 1 << 20,
    })
    .outboundTarget({
      name: outboundTarget,
      generation: 1n,
      endpoints: [{
        nodeId: "loopback",
        connectHost: "127.0.0.1",
        connectPort: upstreamPort,
        httpAuthority: `127.0.0.1:${upstreamPort}`,
      }],
    })
    .get("/hello", helloResponse)
    .post("/echo", (request) => Response.bytes(request.bytes(), { status: 201 }))
    .postStream("/upload", streamedUploadHandler())
    .get("/stream", () => new StreamingResponse(async (writer) => {
      await writer.write("stream-");
      await writer.write("ready");
      return new Headers([new Header("x-stream-end", "done")]);
    }, { headers: new Headers([new Header("content-type", "text/plain")]) }), { response: "stream" })
    .get("/events", () => sse([
      { data: "ready", event: "state", id: "1", retry: 1500 },
    ]), { response: "sse" })
    .get("/socket", () => websocket({
      protocol: "coakka.sample",
      open(session) { session.send("welcome"); },
      message(session, message) { session.send(message.data); },
    }), { response: "websocket" })
    .get("/download", () => new FileResponse(82n, "/sample.txt", {
      headers: new Headers([new Header("content-type", "text/plain")]),
    }), { response: "file" })
    .get("/version", () => Response.text("v1"));
  if (ioUring) builder.ioUring();
  return builder.start();
}

/** Activate a prepared callback without rebuilding route structure. */
function activateReplacement(service: Service): void {
  service.prepareHandler(9n, () => Response.text("v2", { status: 201 }));
  const outcome = service.rebindHandler({
    activationId: 1n,
    expectedRouteGeneration: 1n,
    routeId: 8n,
    expectedBindingRevision: 1n,
    newHandlerBindingId: 9n,
  });
  if (outcome.code !== RouteControlCode.APPLIED || !outcome.changed) {
    throw new Error(`handler replacement was rejected: ${outcome.code}`);
  }
}

/** Replace a complete structural generation on an isolated service. */
async function demonstrateRoutePublication(): Promise<void> {
  const service = new Builder()
    .limits(new Limits({ maxHandlerBindings: 3 }))
    .get("/old", () => Response.text("old-generation"))
    .start();
  try {
    service.prepareHandler(2n, () => Response.text("new-generation", { status: 201 }));
    const publication = Object.freeze({
      activationId: 1n,
      expectedRouteGeneration: 1n,
      expectedBindingChangeSequence: 1n,
      routes: Object.freeze([Object.freeze({
        id: 2n,
        handlerBindingId: 2n,
        method: "GET",
        pattern: "/published",
        bodyDelivery: BodyDelivery.INLINE,
        bodyPolicy: Object.freeze({
          enabled: true,
          acceptAbsent: true,
          acceptOther: true,
          maxBodyBytes: 1_048_576,
        }),
      })]),
    });
    const outcome = service.publishRoutes(publication);
    if (outcome.code !== RouteControlCode.APPLIED || !outcome.changed) {
      throw new Error(`route generation was rejected: ${outcome.code}`);
    }
    const response = await fetch(`http://127.0.0.1:${service.port}/published`);
    if (response.status !== 201 || await response.text() !== "new-generation") {
      throw new Error("published route did not select its prepared handler");
    }
  } finally {
    await service.close();
  }
}

/** Submit and consume one request through the runtime-owned outbound lane. */
async function demonstrateOutbound(service: Service): Promise<void> {
  const call = service.outboundSubmit({
    logicalTarget: outboundTarget,
    method: "GET",
    target: "/source",
    timeoutMillis: 3_000,
  });
  const terminal = await service.takeOutbound(5_000);
  if (terminal === null || terminal.call.slot !== call.slot ||
      terminal.responseStatus !== 200 || new TextDecoder().decode(terminal.body) !== "outbound-ready") {
    throw new Error("outbound result did not match the declared target");
  }
}

/** Exercise representative features through the bound loopback listener. */
async function smoke(service: Service): Promise<void> {
  const cases = [
    ["/hello?title=hello", undefined, 200, "hello from smoke"],
    ["/echo", "payload", 201, "payload"],
    ["/upload", "streamed", 200, "streamed"],
    ["/stream", undefined, 200, "stream-ready"],
    ["/events", undefined, 200, "data: ready"],
    ["/download", undefined, 200, "confined application file"],
    ["/app/client/route", undefined, 200, "CoAkka HTTP Runtime"],
    ["/version", undefined, 201, "v2"],
  ] as const;
  for (const [path, body, status, expected] of cases) {
    const headers = new globalThis.Headers();
    if (path.startsWith("/hello?")) headers.set("x-sample-caller", "smoke");
    if (path.startsWith("/app/")) headers.set("accept", "text/html");
    const requestOptions: RequestInit = {
      method: body === undefined ? "GET" : "POST",
      ...(body === undefined ? {} : { body }),
      headers,
    };
    const response = await fetch(
      `http://127.0.0.1:${service.port}${path}`,
      requestOptions,
    );
    const text = await response.text();
    if (response.status !== status || !text.includes(expected)) {
      throw new Error(`${path}: status=${response.status} body=${JSON.stringify(text)}`);
    }
  }
  process.stdout.write("typescript-smoke=pass\n");
}

/** Wait for either termination signal without retaining extra application work. */
function waitForSignal(): Promise<void> {
  return new Promise((resolveSignal) => {
    const finish = () => resolveSignal();
    process.once("SIGINT", finish);
    process.once("SIGTERM", finish);
  });
}

/** Own startup and reverse-order asynchronous shutdown. */
async function main(): Promise<void> {
  const selected = options();
  await demonstrateRoutePublication();
  const upstream = new Builder()
    .get("/source", () => Response.text("outbound-ready"))
    .start();
  let service: Service | undefined;
  try {
    service = createService(selected.assets, upstream.port, selected.ioUring);
    activateReplacement(service);
    await demonstrateOutbound(service);
    const health = service.probeLiveness(1_000);
    const page = service.monitorRead(0n, 8);
    const runtimeInfo = service.runtimeInfo();
    process.stdout.write(
      `ready=${health.ready} monitor-latest=${page.latestSequence} retained=${page.events.length} ` +
      `io-uring-requested=${runtimeInfo.ioUringRequested} io-uring-effective=${runtimeInfo.ioUringEffective}\n`,
    );
    process.stdout.write(`typescript-sample=http://127.0.0.1:${service.port}\n`);
    if (selected.smoke) {
      await smoke(service);
    } else {
      await waitForSignal();
    }
  } finally {
    await service?.close();
    await upstream.close();
  }
}

await main();
