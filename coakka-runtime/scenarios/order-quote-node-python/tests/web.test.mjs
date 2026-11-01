import assert from "node:assert/strict";
import { request } from "node:http";
import { connect } from "node:net";
import { once } from "node:events";
import { test } from "node:test";
import { startWebApp } from "../web-server.mjs";
import { MAX_BYTES } from "../quote-client.mjs";
import { child, line, stop, freePort, service, python } from "./process-fixture.mjs";

const options = { timeout: 20000 };
const cart = { cartId: "web-1001", items: [{ sku: "tea", quantity: 2 }, { sku: "coffee", quantity: 1 }] };
const quoted = input => ({ status: "quoted", cartId: input.cartId, currency: "USD", items: [], subtotalCents: 4300, shippingCents: 500, totalCents: 4800 });
const post = (app, input = cart, extra = {}) => fetch(app.url + "/api/quote", { method: "POST", headers: { Origin: app.url, "Content-Type": "application/json" }, body: JSON.stringify(input), ...extra });
async function fakeApp(t, quote = async input => quoted(input)) {
  let closes = 0;
  const app = await startWebApp({ port: 0, openClient: () => ({ quote, close: async () => { closes++; } }) });
  t.after(() => app.close());
  return { ...app, get closes() { return closes; } };
}

function raw(app, body, headers = {}, method = "POST", path = "/api/quote") {
  return new Promise((resolve, reject) => {
    const req = request(app.url + path, { method, headers: { Origin: app.url, "Content-Type": "application/json", ...headers } }, res => {
      const parts = [];res.on("data", b => parts.push(b));res.on("end", () => resolve({ status: res.statusCode, body: Buffer.concat(parts).toString(), headers: res.headers }));
    });
    req.on("error", reject); req.end(body);
  });
}

test("web assets, security headers and exact route/method/origin boundaries", options, async t => {
  let calls = 0;
  const app = await fakeApp(t, async input => { calls++; return quoted(input); });
  for (const [path, type] of [["/", "text/html"], ["/style.css", "text/css"], ["/app.js", "text/javascript"]]) {
    const res = await fetch(app.url + path);assert.equal(res.status, 200);assert.match(res.headers.get("content-type"), new RegExp(type));
    assert.match(res.headers.get("content-security-policy"), /frame-ancestors 'none'/);
    assert.equal(res.headers.get("cache-control"), "no-store");assert.equal(res.headers.get("x-content-type-options"), "nosniff");
    assert.ok((await res.text()).length > 0);
  }
  assert.equal((await fetch(app.url, { method: "HEAD" })).status, 200);
  assert.equal((await fetch(app.url + "/../package.json")).status, 404);
  assert.equal((await fetch(app.url + "/api/quote")).status, 405);
  assert.equal((await raw(app, "{}", { Host: "attacker.example" })).status, 403);
  assert.equal((await raw(app, "{}", { Origin: "https://attacker.example" })).status, 403);
  assert.equal((await post(app, cart, { headers: { "Content-Type": "application/json" } })).status, 403);
  assert.equal((await raw(app, "{}", { "Content-Type": "text/plain" })).status, 415);
  assert.equal(calls, 0);
});

test("bounded JSON, malformed bytes and declared/chunked oversize refuse before Runtime", options, async t => {
  let calls = 0;
  const app = await fakeApp(t, async input => { calls++; return quoted(input); });
  for (const bytes of [Buffer.from("{"), Buffer.from([255]), Buffer.from("[".repeat(3000))]) {
    const res = await raw(app, bytes);assert.equal(res.status, 400);
  }
  for (const headers of [{ "Content-Length": MAX_BYTES + 1 }, { "Transfer-Encoding": "chunked" }]) {
    const res = await raw(app, Buffer.alloc(MAX_BYTES + 1, 32), headers);assert.equal(res.status, 413);
  }
  assert.equal(calls, 0);
  const exact = JSON.stringify(cart);
  const res = await raw(app, exact + " ".repeat(MAX_BYTES - Buffer.byteLength(exact)));
  assert.equal(res.status, 200);assert.equal(calls, 1);
});

test("bounded HTTP malformed-input corpus never escapes the app boundary", options, async t => {
  const app = await fakeApp(t, async () => ({ status: "rejected", message: "Rejected test payload." }));
  let seed = 555;
  const next = () => { seed ^= seed << 13;seed ^= seed >>> 17;seed ^= seed << 5;return seed >>> 0; };
  for (let i = 0; i < 128; i++) {
    const data = Buffer.from(Array.from({ length: 1 + next() % 256 }, () => next() & 255));
    const res = await raw(app, data);
    assert.ok([400, 422].includes(res.status), res.status);
    assert.ok(Buffer.byteLength(res.body) < 512);
  }
});

test("HTTP edge refuses a ninth active quote without a queue; drains before one close", options, async t => {
  let enter, release, count = 0, closes = 0;
  const entered = new Promise(resolve => { enter = resolve; });
  const gate = new Promise(resolve => { release = resolve; });
  const app = await startWebApp({ port: 0, openClient: () => ({ quote: async input => {
    if (++count === 8) enter(); await gate; return quoted(input);
  }, close: async () => { closes++; } }) });
  t.after(() => { release(); return app.close(); });
  const pending = Array.from({ length: 8 }, (_, i) => post(app, { ...cart, cartId: `web-${i}` }));
  await entered;
  const refused = await post(app);assert.equal(refused.status, 429);assert.equal(count, 8);
  const close = app.close();assert.equal(app.close(), close);assert.equal(closes, 0);
  release();
  for (const res of await Promise.all(pending)) assert.equal(res.status, 200);
  await close;assert.equal(closes, 1);
});

test("real Python quotes, rejection and concurrent browser requests share one app Runtime", options, async t => {
  const s = await service(t);
  const app = await startWebApp({ port: 0, pricingPort: s.port });t.after(() => app.close());
  const res = await post(app);assert.equal(res.status, 200);const q = await res.json();
  assert.equal(q.cartId, cart.cartId);assert.equal(q.totalCents, 4800);assert.equal(q.items.length, 2);
  const bad = await post(app, { ...cart, items: [{ sku: "missing", quantity: 1 }] });
  assert.equal(bad.status, 422);assert.equal((await bad.json()).code, "INVALID_CART");
  const replies = await Promise.all(Array.from({ length: 4 }, (_, i) => post(app, { cartId: `web-${i}`, items: [{ sku: "tea", quantity: i + 1 }] }).then(r => r.json())));
  replies.forEach((reply, i) => { assert.equal(reply.cartId, `web-${i}`);assert.equal(reply.totalCents, (i + 1) * 1250 + (i < 3 ? 500 : 0)); });
});

test("Python stop and restart is visible; web app never fabricates or retries quotes", options, async t => {
  const s = await service(t);
  const app = await startWebApp({ port: 0, pricingPort: s.port });t.after(() => app.close());
  assert.equal((await post(app)).status, 200);
  await stop(s.child);
  const failed = await post(app);assert.equal(failed.status, 503);assert.equal((await failed.json()).status, "unavailable");
  const restarted = child(python, ["-B", "pricing_service.py", "--port", String(s.port)]);t.after(() => stop(restarted));
  await line(restarted, `Pricing service ready at 127.0.0.1:${s.port}`);
  const next = await post(app);assert.equal(next.status, 200);assert.equal((await next.json()).totalCents, 4800);
});

test("startup rollback, invalid ports and native failure do not leak owners or details", options, async t => {
  await assert.rejects(startWebApp({ port: -1 }), RangeError);
  let closes = 0;
  const first = await fakeApp(t);
  await assert.rejects(startWebApp({ port: Number(new URL(first.url).port), openClient: () => ({ close: async () => { closes++; } }) }));
  assert.equal(closes, 1);
  const fake = await fakeApp(t, async () => { throw new Error("private test diagnostic"); });
  const fail = await post(fake);assert.equal(fail.status, 503);assert.doesNotMatch(await fail.text(), /private test diagnostic/);
});

test("slow uploads are bounded; disconnected browsers do not poison the next quote", options, async t => {
  const s = await service(t);
  const app = await startWebApp({ port: 0, pricingPort: s.port });t.after(() => app.close());
  const address = new URL(app.url);
  const socket = connect(Number(address.port), address.hostname);t.after(() => socket.destroy());await once(socket, "connect");
  socket.on("data", () => {});
  const closed = once(socket, "close");
  socket.write(`POST /api/quote HTTP/1.1\r\nHost: ${address.host}\r\nOrigin: ${app.url}\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{`);
  await closed;
  const next = await post(app);assert.equal(next.status, 200);assert.equal((await next.json()).totalCents, 4800);
});
