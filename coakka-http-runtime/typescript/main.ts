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
  CompressionMode,
  FileResponse,
  Header,
  Headers,
  Limits,
  MonitorCategory,
  MonitorCollection,
  OutboundReason,
  OutboundPhase,
  OutboundCertainty,
  Response,
  RouteControlCode,
  StreamingResponse,
  sse,
  websocket,
  type StreamRequestEvent,
  type Service,
} from "@coakka/http";
import { resolve } from "node:path";
import { get } from "node:http";
import { gunzipSync } from "node:zlib";

const outboundTarget = "sample.upstream";
const uploadLimit = 64 * 1024;

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

/**
 * Collect a body within Core's route byte limit and the service admission bound.
 * END chooses a response; dispose releases retained application data on every
 * exit, including a peer disconnect before END. It is cleanup, not HTTP success.
 */
function streamedUploadHandler() {
  const chunks = new Map<string, { parts: Uint8Array[]; size: number; ended: boolean }>();
  return (event: StreamRequestEvent) => {
    const key = `${event.id.slot}:${event.id.generation}`;
    if (event.kind === "dispose") {
      const state = chunks.get(key);
      // Disposal releases application retention; it is not a wire success ack.
      if (state !== undefined && !state.ended) {
        process.stdout.write(`typescript-upload-read-failed cause=${event.cause}\n`);
      }
      chunks.delete(key);
      return undefined;
    }
    if (event.kind === "start") {
      chunks.set(key, { parts: [], size: 0, ended: false });
      process.stdout.write("typescript-upload-reading\n");
      return undefined;
    }
    if (event.kind === "data") {
      const state = chunks.get(key);
      if (state === undefined) throw new Error("upload data without admitted start");
      // Core enforces its route admission bound. This bounds the sample's own
      // retained representation independently, without reparsing HTTP.
      if (event.data.byteLength > uploadLimit - state.size) {
        throw new Error("upload exceeds application retention bound");
      }
      state.parts.push(event.data);
      state.size += event.data.byteLength;
      return undefined;
    }
    if (event.kind === "end") {
      const state = chunks.get(key);
      if (state === undefined) throw new Error("upload end without admitted start");
      state.ended = true;
      const body = new Uint8Array(state.size);
      let offset = 0;
      for (const part of state.parts) {
        body.set(part, offset);
        offset += part.byteLength;
      }
      return Response.bytes(body, { headers: new Headers([
        new Header("x-upload-observed", event.trailers.get("x-upload-check") ?? "absent"),
      ]) });
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
    // Buffered compression is Core-owned; response streams remain identity.
    .compression({ mode: CompressionMode.GZIP, minimumBodyBytes: 16 })
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
    .postStream("/upload", streamedUploadHandler(), {
      bodyPolicy: { enabled: true, acceptAbsent: true, acceptOther: true, maxBodyBytes: uploadLimit },
    })
    .get("/stream", () => new StreamingResponse(async (writer) => {
      await writer.write("stream-");
      await writer.write("ready");
      return new Headers([new Header("x-stream-end", "done")]);
    }, { headers: new Headers([new Header("content-type", "text/plain")]) }), { response: "stream" })
    .get("/events", () => sse([
      { data: "ready\nsecond line", event: "state", id: "1", retry: 1000 },
    ]), { response: "sse" })
    .get("/socket", () => websocket({
      protocol: "coakka.sample",
      open(session) { session.send("welcome"); },
      message(session, message) { session.send(message.data); },
    }), { response: "websocket" })
    .get("/download", () => new FileResponse(82n, "/sample.txt", {
      headers: new Headers([new Header("content-type", "text/plain")]),
    }), { response: "file" })
    .get("/version", () => Response.text("v1"))
    .get("/gzip", () => Response.text("compressible-sample-".repeat(64)))
    .get("/parameters/{id}", (request) => {
      // Core has parsed capture/query boundaries. Keep encoded values, order,
      // duplicates, absent values and empty values distinct; do not split URL.
      const decode = (bytes: Uint8Array) => new TextDecoder().decode(bytes);
      return Response.text(JSON.stringify({
        path: request.pathParameters.map((item) => ({ name: item.name, encodedValue: decode(item.encodedValue) })),
        query: request.queryParameters.map((item) => ({ encodedKey: decode(item.encodedKey),
          encodedValue: item.encodedValue === null ? null : decode(item.encodedValue) })),
        headers: request.headers.getAll("x-sample-value"),
      }), { headers: new Headers([new Header("content-type", "application/json")]) });
    });
  if (ioUring) builder.ioUring();
  return builder.start();
}

/** Activate a prepared callback without rebuilding route structure. */
function activateReplacement(service: Service): void {
  service.prepareHandler(100n, () => Response.text("v2", { status: 201 }));
  const before = service.routes;
  // Builder assigns stable route IDs in declaration order; /version is eighth.
  // The snapshot supplies effective revisions, not application URL metadata.
  const route = before.routes.find((entry) => entry.routeId === 8n);
  if (route === undefined) throw new Error("Core snapshot omitted version route");
  const outcome = service.rebindHandler({
    activationId: 1n,
    expectedRouteGeneration: before.routeGeneration,
    routeId: route.routeId,
    expectedBindingRevision: route.bindingRevision,
    newHandlerBindingId: 100n,
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
    const before = service.routes;
    service.prepareHandler(2n, () => Response.text("new-generation", { status: 201 }));
    const publication = Object.freeze({
      activationId: 1n,
      expectedRouteGeneration: before.routeGeneration,
      expectedBindingChangeSequence: before.bindingChangeSequence,
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
    const after = service.routes;
    const refused = service.publishRoutes({ ...publication, activationId: 2n });
    if (refused.code !== RouteControlCode.GENERATION_MISMATCH || refused.changed ||
        before.routes[0]?.routeId !== 1n || after.routes[0]?.routeId !== 2n ||
        service.routes.routeGeneration !== after.routeGeneration) {
      throw new Error("Core route snapshot/refusal contract mismatch");
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
      terminal.call.generation !== call.generation ||
      terminal.reason !== OutboundReason.RESPONSE || terminal.phase !== OutboundPhase.RESPONSE ||
      terminal.certainty !== OutboundCertainty.COMPLETE_RESPONSE ||
      terminal.responseStatus !== 200 || new TextDecoder().decode(terminal.body) !== "outbound-ready") {
    throw new Error("outbound result did not match the declared target");
  }
}

/** Exercise representative features through the bound loopback listener. */
async function smoke(service: Service): Promise<void> {
  // fetch transparently decodes content; this finite wire client also verifies
  // that negotiation really produced GZIP rather than merely accepting config.
  await new Promise<void>((resolveResponse, reject) => {
    const request = get({ hostname: "127.0.0.1", port: service.port,
      path: "/gzip", headers: { "accept-encoding": "gzip" } }, (response) => {
      const parts: Buffer[] = [];
      let size = 0;
      response.on("error", reject);
      response.on("data", (chunk: Buffer) => {
        size += chunk.byteLength;
        if (size > 4096) { response.destroy(new Error("encoded response exceeds bound")); return; }
        parts.push(chunk);
      });
      response.on("end", () => {
        try {
          if (response.statusCode !== 200 || response.headers["content-encoding"] !== "gzip" ||
              gunzipSync(Buffer.concat(parts), { maxOutputLength: 4096 }).toString() !==
                "compressible-sample-".repeat(64)) {
            throw new Error("buffered compression wire mismatch");
          }
          resolveResponse();
        } catch (error) { reject(error); }
      });
    });
    request.setTimeout(5000, () => request.destroy(new Error("GZIP peer timeout")));
    request.on("error", reject);
  });
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
  // Register before advertising the listener, so an immediate SIGTERM cannot
  // bypass the checked, reverse-order close path.
  const stopped = selected.smoke ? undefined : waitForSignal();
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
      await stopped;
    }
  } finally {
    const failures: unknown[] = [];
    try { await service?.close(); } catch (error) { failures.push(error); }
    try { await upstream.close(); } catch (error) { failures.push(error); }
    if (failures.length > 0) throw new AggregateError(failures, "sample shutdown failed");
  }
  process.stdout.write(selected.smoke ? "typescript-smoke=pass\n" : "typescript-shutdown=pass\n");
}

await main();
