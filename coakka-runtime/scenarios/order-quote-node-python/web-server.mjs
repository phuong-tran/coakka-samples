import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { once } from "node:events";
import { MAX_BYTES, openQuoteClient } from "./quote-client.mjs";

const MAX_ACTIVE = 8;
const SECURITY = {
  "Content-Security-Policy": "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Cache-Control": "no-store",
};

class HttpInputError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function readJson(request) {
  const declared = Number(request.headers["content-length"]);
  if (declared > MAX_BYTES) throw new HttpInputError(413, "Cart exceeds 16 KiB.");
  const bytes = Buffer.alloc(MAX_BYTES);
  let size = 0;
  await new Promise((resolve, reject) => {
    let settled = false;
    const finish = error => {
      if (settled) return;
      settled = true;
      request.off("data", data); request.off("end", end); request.off("aborted", aborted);
      error ? reject(error) : resolve();
    };
    const data = chunk => {
      if (size + chunk.length > MAX_BYTES) {
        // Pause instead of destroying the request so the browser receives 413.
        // Every response closes its connection; this body is never reused.
        request.pause(); finish(new HttpInputError(413, "Cart exceeds 16 KiB.")); return;
      }
      chunk.copy(bytes, size); size += chunk.length;
    };
    const end = () => finish();
    const aborted = () => finish(new HttpInputError(400, "Cart upload interrupted."));
    request.on("data", data); request.once("end", end); request.once("aborted", aborted);
    // Keep a one-shot error observer after abort/end too; late socket errors
    // must not become unhandled errors. Its owner is this request object.
    request.once("error", finish);
  });
  try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(0, size))); }
  catch { throw new HttpInputError(400, "Send a valid JSON cart."); }
}

export async function startWebApp({ port = 8080, pricingPort = 19360, openClient = openQuoteClient } = {}) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new RangeError("Invalid web port.");
  const assets = new Map();
  for (const [path, type, file] of [["/", "text/html; charset=utf-8", "index.html"],
    ["/style.css", "text/css; charset=utf-8", "style.css"], ["/app.js", "text/javascript; charset=utf-8", "app.js"], ["/icon.svg", "image/svg+xml", "icon.svg"]]) {
    const body = await readFile(new URL(`./web/${file}`, import.meta.url));
    if (body.length > 65536) throw new Error("Web asset exceeds its sample limit.");
    assets.set(path, { type, body });
  }
  const client = openClient(pricingPort);
  let origin, closing = false, active = 0, closePromise, drained;
  const server = createServer({ maxHeaderSize: 8192, headersTimeout: 5000, requestTimeout: 5000,
    connectionsCheckingInterval: 1000, keepAliveTimeout: 1000 }, (request, response) => {
    const send = (status, value, type = "application/json; charset=utf-8") => {
      if (response.destroyed || response.writableEnded) return;
      const body = Buffer.isBuffer(value) ? value : Buffer.from(JSON.stringify(value));
      response.writeHead(status, { ...SECURITY, "Content-Type": type, "Content-Length": body.length,
        // Closing each small browser response also bounds early-refusal body handling.
        "Connection": "close" });
      response.end(request.method === "HEAD" ? undefined : body);
    };
    const fail = (status, message) => send(status, { status: "unavailable", message });
    async function handle() {
      if (request.headers.host !== new URL(origin).host) return fail(403, "Open the local address shown in the Node terminal.");
      if (request.headers.origin && request.headers.origin !== origin) return fail(403, "Use this demo's own browser page.");
      if (closing) return fail(503, "The web app is stopping.");
      const path = request.url;
      if ((request.method === "GET" || request.method === "HEAD") && assets.has(path)) {
        const asset = assets.get(path); return send(200, asset.body, asset.type);
      }
      if (path !== "/api/quote") return fail(404, "Page not found.");
      if (request.method !== "POST") return fail(405, "Send a cart from the demo page.");
      if (request.headers.origin !== origin) return fail(403, "Use this demo's own browser page.");
      if (request.headers["content-type"]?.split(";")[0].trim().toLowerCase() !== "application/json") {
        return fail(415, "Send the cart as JSON.");
      }
      // This is HTTP-edge admission, not a mirror of Core's request capacity.
      if (active >= MAX_ACTIVE) return fail(429, "Too many quotes are being requested. Try again.");
      active++;
      try {
        const cart = await readJson(request);
        if (response.destroyed) return;
        const reply = await client.quote(cart);
        send(reply.status === "quoted" ? 200 : 422, reply);
      } catch (error) {
        if (error instanceof HttpInputError) fail(error.status, error.message);
        else fail(503, "Quote unavailable. Make sure Python is running, then try again.");
      } finally { active--; if (active === 0) drained?.(); }
    }
    handle().catch(() => fail(500, "The web app could not handle this request."));
  });
  server.maxConnections = 32;
  server.maxHeadersCount = 32;
  server.maxRequestsPerSocket = 100;
  server.setTimeout(5000, socket => socket.destroy());
  // The app does not accept upgrades or tunnelling through its HTTP edge.
  server.on("upgrade", (_request, socket) => socket.destroy());
  server.on("connect", (_request, socket) => socket.destroy());
  try {
    server.listen(port, "127.0.0.1");
    await once(server, "listening");
    origin = `http://127.0.0.1:${server.address().port}`;
  } catch (error) {
    try { await client.close(); } catch (cleanup) { throw new AggregateError([error, cleanup], "Startup and cleanup failed."); }
    throw error;
  }
  return {
    url: origin,
    close() {
      if (!closePromise) {
        closing = true;
        // Stop HTTP admission and drain bounded work before retiring Runtime.
        closePromise = (async () => {
          let failure;
          try { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
          catch (error) { failure = error; }
          if (active > 0) await new Promise(resolve => { drained = resolve; });
          try { await client.close(); } catch (error) {
            if (failure) throw new AggregateError([failure, error], "Shutdown failed.");
            throw error;
          }
          if (failure) throw failure;
        })();
      }
      return closePromise;
    },
  };
}
