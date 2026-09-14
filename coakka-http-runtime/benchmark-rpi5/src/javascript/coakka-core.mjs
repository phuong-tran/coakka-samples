import {
  BodyDelivery,
  EventKind,
  IoBackend,
  ListenerProtocol,
  TransportSecurity,
  createCore,
  runtimeInfo,
} from "../../build/javascript-http2-package/src/index.mjs";

const body = Buffer.from("0123456789abcdef0123456789abcdef");
const backendName = process.argv[2];
const backend = backendName === "platform-default"
  ? IoBackend.PLATFORM_DEFAULT
  : backendName === "io-uring"
    ? IoBackend.IO_URING
    : undefined;
if (backend === undefined) {
  throw new Error("usage: coakka-core.mjs <platform-default|io-uring>");
}

function requiredEnvironment(name) {
  const value = process.env[name];
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`missing ${name}`);
  }
  return value;
}

const hostRuntime = runtimeInfo();
if (backend === IoBackend.IO_URING && !hostRuntime.ioUringSupported) {
  throw new Error(
    `io_uring benchmark stopped: Core reports unsupported `
      + `(compiled=${hostRuntime.ioUringCompiled} probe_error=${hostRuntime.ioUringProbeError})`,
  );
}
const core = createCore({
  listener: {
    id: 1n,
    host: "127.0.0.1",
    port: 0,
    protocol: ListenerProtocol.HTTP_2,
    security: TransportSecurity.TLS,
    credentialGeneration: 1n,
    credentialId: "benchmark-server",
    certificateChainFile: requiredEnvironment("COAKKA_BENCHMARK_TLS_CERT"),
    privateKeyFile: requiredEnvironment("COAKKA_BENCHMARK_TLS_KEY"),
  },
  routes: [{
    id: 1n,
    handlerBindingId: 1n,
    method: "GET",
    pattern: "/fixed",
    bodyDelivery: BodyDelivery.INLINE,
  }],
  ioBackend: backend,
});

core.start();
const effectiveRuntime = core.runtimeInfo();
const ioUringActive = effectiveRuntime.effectiveIoBackend === IoBackend.IO_URING;
if (effectiveRuntime.effectiveIoBackend !== backend) {
  core.close();
  throw new Error(
    `backend benchmark stopped: requested=${backendName} `
      + `effective=${effectiveRuntime.effectiveIoBackend} `
      + `fallback_reason=${effectiveRuntime.fallbackReason}`,
  );
}

let shutdownRequested = false;
let stopping = false;
let errors = 0;
let scheduled;
let scheduledKind;
let drainDeadline = 0;

function schedulePump() {
  scheduledKind = "immediate";
  scheduled = setImmediate(pump);
}

function finishShutdown() {
  if (stopping) return;
  stopping = true;
  if (scheduled !== undefined) {
    if (scheduledKind === "timeout") clearTimeout(scheduled);
    if (scheduledKind === "immediate") clearImmediate(scheduled);
    scheduled = undefined;
    scheduledKind = undefined;
  }
  try {
    core.stop();
    core.close();
  } catch (error) {
    errors += 1;
    console.error(error);
  }
  console.log(JSON.stringify({ stopped: true, handler_errors: errors }));
  process.exitCode = errors === 0 ? 0 : 1;
}

function pump() {
  scheduled = undefined;
  scheduledKind = undefined;
  if (stopping) return;
  let consumed = 0;
  try {
    while (consumed < 256) {
      const event = core.takeEvent(consumed === 0 ? 100 : 0);
      if (event === null) break;
      consumed += 1;
      if (event.kind === EventKind.REQUEST) {
        core.respond(event.exchange, { status: 200, body });
      }
    }
  } catch (error) {
    if (shutdownRequested && error?.code === 6) {
      finishShutdown();
      return;
    }
    errors += 1;
    console.error(error);
    shutdown();
    return;
  }
  if (shutdownRequested) {
    const activeExchanges = Number(core.health().serverActiveExchanges);
    if (activeExchanges === 0) {
      finishShutdown();
      return;
    }
    if (Date.now() >= drainDeadline) {
      errors += 1;
      console.error(`drain deadline expired with ${activeExchanges} active exchanges`);
      finishShutdown();
      return;
    }
  }
  schedulePump();
}

function shutdown() {
  if (shutdownRequested || stopping) return;
  shutdownRequested = true;
  drainDeadline = Date.now() + 5_000;
  try {
    core.drain();
  } catch (error) {
    errors += 1;
    console.error(error);
    finishShutdown();
    return;
  }
  if (scheduled === undefined) schedulePump();
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);
console.log(JSON.stringify({
  ready: true,
  bound_port: core.port(),
  application_path: "normal",
  application_protocol: 2,
  transport_security_mode: 2,
  io_backend: backendName,
  io_uring_active: ioUringActive,
  io_uring_supported: hostRuntime.ioUringSupported,
  requested_io_backend: effectiveRuntime.requestedIoBackend,
  effective_io_backend: effectiveRuntime.effectiveIoBackend,
  io_backend_fallback_reason: effectiveRuntime.fallbackReason,
}));
pump();
