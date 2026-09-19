import { once } from "node:events";
import http from "node:http";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const BODY_LIMIT = 1 << 20;

function loadCore(routes) {
  const path = process.env.COAKKA_HTTP_APPLICATION_CORE;
  if (typeof path !== "string" || path.length === 0) {
    throw new Error("missing CoAkka HTTP application Core");
  }
  const addon = require(path);
  if (typeof addon.createRouter !== "function") {
    throw new Error("incompatible CoAkka HTTP application Core");
  }
  return addon.createRouter(
    routes.map((route, index) => ({
      routeId: BigInt(index + 1),
      method: route.method,
      pattern: route.pattern,
    })),
  );
}

function normalizeRoutes(routes) {
  if (routes.length === 0 || routes.length > 1_024) {
    throw new RangeError("service requires 1 to 1024 routes");
  }
  const seen = new Set();
  return Object.freeze(
    routes.map((route) => {
      const key = `${route.method} ${route.pattern}`;
      if (seen.has(key)) {
        throw new RangeError(`duplicate route ${key}`);
      }
      seen.add(key);
      return Object.freeze({ ...route });
    }),
  );
}

function normalizeResponse(value) {
  if (value === null || typeof value !== "object") {
    throw new TypeError("handler must return a response object");
  }
  const status = value.status ?? 200;
  const body = Buffer.isBuffer(value.body) ? value.body : Buffer.from(value.body ?? "");
  const contentType = value.contentType ?? "application/octet-stream";
  if (!Number.isInteger(status) || status < 100 || status > 599) {
    throw new RangeError("response status is invalid");
  }
  if (body.length > BODY_LIMIT) {
    throw new RangeError("response body exceeds its bound");
  }
  if (typeof contentType !== "string" || /[\r\n\0]/u.test(contentType)) {
    throw new TypeError("response content type is invalid");
  }
  return { status, body, contentType };
}

export const Responses = Object.freeze({
  bytes(body, options = {}) {
    const status = typeof options === "number" ? options : (options.status ?? 200);
    const contentType = typeof options === "number"
      ? "application/octet-stream"
      : (options.contentType ?? "application/octet-stream");
    return Object.freeze({ status, body: Buffer.from(body), contentType });
  },
  text(value, options = {}) {
    const status = typeof options === "number" ? options : (options.status ?? 200);
    return Object.freeze({
      status,
      body: Buffer.from(value, "utf8"),
      contentType: "text/plain; charset=utf-8",
    });
  },
});

export const Response = Responses;

export class Builder {
  #host = "127.0.0.1";
  #port = 0;
  #activeLimit = 256;
  #routes = [];
  #started = false;

  listen(host, port) {
    if (
      this.#started ||
      typeof host !== "string" ||
      host.length === 0 ||
      !Number.isInteger(port) ||
      port < 0 ||
      port > 65_535
    ) {
      throw new RangeError("invalid CoAkka HTTP listener");
    }
    this.#host = host;
    this.#port = port;
    return this;
  }

  concurrency(maxActiveHandlers) {
    if (
      this.#started ||
      !Number.isInteger(maxActiveHandlers) ||
      maxActiveHandlers < 1 ||
      maxActiveHandlers > 16_384
    ) {
      throw new RangeError("invalid CoAkka HTTP active-handler bound");
    }
    this.#activeLimit = maxActiveHandlers;
    return this;
  }

  route(method, pattern, handler) {
    if (
      this.#started ||
      typeof method !== "string" ||
      typeof pattern !== "string" ||
      !pattern.startsWith("/") ||
      typeof handler !== "function"
    ) {
      throw new TypeError("invalid CoAkka HTTP route");
    }
    this.#routes.push({ method: method.toUpperCase(), pattern, handler });
    return this;
  }

  get(pattern, handler) {
    return this.route("GET", pattern, handler);
  }

  post(pattern, handler) {
    return this.route("POST", pattern, handler);
  }

  put(pattern, handler) {
    return this.route("PUT", pattern, handler);
  }

  patch(pattern, handler) {
    return this.route("PATCH", pattern, handler);
  }

  delete(pattern, handler) {
    return this.route("DELETE", pattern, handler);
  }

  async start() {
    if (this.#started) {
      throw new Error("CoAkka HTTP builder can start only once");
    }
    this.#started = true;
    const declaration = normalizeRoutes(this.#routes);
    const core = loadCore(declaration);
    try {
      return await startNode(this.#host, this.#port, this.#activeLimit, declaration, core);
    } catch (error) {
      core.close();
      throw error;
    }
  }
}

function routeTable(routes) {
  return new Map(routes.map((route) => [`${route.method} ${route.pattern}`, route]));
}

async function startNode(host, port, activeLimit, declaration, core) {
  const table = routeTable(declaration);
  let active = 0;
  let closePromise;
  const server = http.createServer((request, response) => {
    const route = table.get(`${request.method} ${request.url}`);
    if (route === undefined) {
      response.writeHead(404, { "content-length": "0" });
      response.end();
      return;
    }
    if (active >= activeLimit) {
      response.writeHead(503, { "content-length": "0" });
      response.end();
      return;
    }
    active += 1;
    try {
      const result = normalizeResponse(route.handler(request));
      response.writeHead(result.status, {
        "content-length": result.body.length,
        "content-type": result.contentType,
      });
      response.end(result.body);
    } catch {
      response.writeHead(500, { "content-length": "0" });
      response.end();
    } finally {
      active -= 1;
    }
  });
  server.listen(port, host);
  await once(server, "listening");
  return Object.freeze({
    port: server.address().port,
    close() {
      closePromise ??= closeNode(server, core);
      return closePromise;
    },
  });
}

async function closeNode(server, core) {
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        server.closeAllConnections();
        reject(new Error("CoAkka HTTP close timed out"));
      }, 5_000);
      server.close((error) => {
        clearTimeout(timer);
        if (error) reject(error);
        else resolve();
      });
      server.closeIdleConnections();
    });
  } finally {
    core.close();
  }
}
