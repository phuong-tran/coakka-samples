import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:net";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { RuntimeHost, outboundOnlyNetwork, DeadletterError } from "coakka-v2-connector-node";
import { openQuoteClient, withQuoteClient, TARGET, MAX_BYTES } from "../quote-client.mjs";
import { readCart } from "../client.mjs";

const root = fileURLToPath(new URL("..", import.meta.url));
const python = process.env.PYTHON || join(root, ".venv", process.platform === "win32" ? "Scripts/python.exe" : "bin/python");
const cart = { cartId: "cart-1001", items: [{ sku: "tea", quantity: 2 }, { sku: "coffee", quantity: 1 }] };

// Only test children and small test files are supervised here. No business transport.
function child(exe, args) {
  const proc = spawn(exe, args, { cwd: root, stdio: ["pipe", "pipe", "pipe"],
    env: { ...process.env, PYTHONDONTWRITEBYTECODE: "1", PYTHONUNBUFFERED: "1" } });
  let output = "", errors = "";
  proc.stdout.setEncoding("utf8"); proc.stderr.setEncoding("utf8");
  proc.stdout.on("data", data => { output = (output + data).slice(-65536); });
  proc.stderr.on("data", data => { errors = (errors + data).slice(-65536); });
  const exited = new Promise((res, rej) => { proc.once("error", rej); proc.once("close", (code, signal) => res({ code, signal })); });
  // Attach immediately so a spawn failure is never an unhandled rejection.
  exited.catch(() => {});
  return { proc, exited, get output() { return output; }, get errors() { return errors; } };
}

async function line(c, text) {
  if (c.output.includes(text)) return;
  await new Promise((res, rej) => {
    const timer = setTimeout(() => done(new Error(`Missing ${text}: ${c.output} ${c.errors}`)), 10000);
    const data = () => { if (c.output.includes(text)) done(); };
    const end = () => done(new Error(`Child exited before ${text}: ${c.output} ${c.errors}`));
    const done = error => { clearTimeout(timer); c.proc.stdout.off("data", data); c.proc.off("close", end); c.proc.off("error", done); error ? rej(error) : res(); };
    c.proc.stdout.on("data", data); c.proc.once("close", end); c.proc.once("error", done);
    data();
  });
}

async function stop(c, force = false) {
  if (c.proc.exitCode !== null || c.proc.signalCode !== null) return c.exited;
  if (force) c.proc.kill("SIGKILL"); else c.proc.stdin.end("\n");
  const timer = setTimeout(() => c.proc.kill("SIGKILL"), 5000);
  try { return await c.exited; } finally { clearTimeout(timer); }
}

async function freePort() {
  const server = createServer();
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  const port = server.address().port;
  await new Promise((res, rej) => server.close(error => error ? rej(error) : res()));
  return port;
}

async function service(t, mode) {
  const port = await freePort();
  const args = mode ? ["tests/service_fixture.py", String(port), mode] : ["pricing_service.py", "--port", String(port)];
  const c = child(python, ["-B", "-Werror", ...args]);
  t.after(() => stop(c));
  await line(c, `Pricing service ready at 127.0.0.1:${port}`);
  return { ...c, child: c, port };
}

function rawHost(port) {
  return RuntimeHost.start({ systemName: "order-quote-test", nodeId: "node-test",
    network: outboundOnlyNetwork(), routes: [{ target: TARGET, endpoints: [{ host: "127.0.0.1", port }] }] });
}

const options = { timeout: 20000 };

test("Python business rules and malformed-input corpus", options, async t => {
  const c = child(python, ["-B", "-Werror", "-m", "unittest", "discover", "-s", "tests", "-p", "pricing_test.py"]);
  t.after(() => stop(c, true));
  assert.equal((await c.exited).code, 0, c.errors);
  assert.match(c.errors, /Ran 7 tests/);
});

test("real CLI: quote, business rejection, expected output and graceful stop", options, async t => {
  const s = await service(t);
  const good = child(process.execPath, ["client.mjs", "--port", String(s.port)]);
  t.after(() => stop(good, true));
  assert.equal((await good.exited).code, 0, good.errors);
  assert.match(good.output, /Quote cart-1001: USD 48.00/);
  await withQuoteClient(s.port, async c => {
    const reply = await c.quote({ ...cart, items: [{ sku: "missing", quantity: 1 }] });
    assert.equal(reply.status, "rejected"); assert.equal(reply.code, "INVALID_CART");
  });
  assert.equal((await stop(s.child)).code, 0, s.child.errors);
  assert.match(s.child.output, /Pricing service stopped/);
});

test("48 concurrent varied carts: correct reply for every caller", options, async t => {
  const s = await service(t);
  await withQuoteClient(s.port, async c => {
    const replies = await Promise.all(Array.from({ length: 48 }, (_, i) => c.quote({ cartId: `cart-${i}`, items: [{ sku: "tea", quantity: i + 1 }] })));
    replies.forEach((q, i) => {
      assert.equal(q.cartId, `cart-${i}`);
      assert.equal(q.totalCents, (i + 1) * 1250 + (i < 3 ? 500 : 0));
    });
  });
});

test("four independent Node processes can request quotes together", options, async t => {
  const s = await service(t);
  const callers = Array.from({ length: 4 }, () => child(process.execPath, ["client.mjs", "--port", String(s.port), "--json"]));
  for (const c of callers) t.after(() => stop(c, true));
  for (const c of callers) {
    assert.equal((await c.exited).code, 0, c.errors);
    assert.equal(JSON.parse(c.output).totalCents, 4800);
  }
});

test("malformed and oversized app requests receive rejection through Runtime", options, async t => {
  const s = await service(t), h = rawHost(s.port); t.after(() => h.close());
  for (const bytes of [Buffer.from("{"), Buffer.from([255]), Buffer.alloc(MAX_BYTES + 1, 32)]) {
    const reply = JSON.parse((await h.askPayload("test", TARGET, bytes, 2000)).toString());
    assert.equal(reply.status, "rejected");
  }
  assert.equal(h.clientStats().pendingRequests, 0);
});

test("route miss stays a native deadletter, with no pending request", options, async t => {
  const h = RuntimeHost.start({ systemName: "quote-test", nodeId: "route-miss", routes: [] });
  t.after(() => h.close());
  await assert.rejects(h.askPayload("test", "no-such-target", Buffer.from("{}"), 1000), DeadletterError);
  assert.equal(h.clientStats().pendingRequests, 0);
});

test("absent pricing service fails; CLI exits with useful guidance", options, async t => {
  const port = await freePort();
  await assert.rejects(withQuoteClient(port, c => c.quote(cart)));
  const c = child(process.execPath, ["client.mjs", "--port", String(port)]);t.after(() => stop(c, true));
  assert.equal((await c.exited).code, 1); assert.match(c.errors, /start Python/);
});

test("expired request fails without retry; a new caller can get a quote", options, async t => {
  const s = await service(t, "delayed");
  await withQuoteClient(s.port, async c => {
    const rejected = assert.rejects(c.quote(cart, 80));
    await line(s.child, "Handler entered.");
    await rejected;
  });
  // A timeout can retire a shared connection and fail other work on it. The CLI
  // closes its one-shot caller on failure; this tests that exact app lifetime.
  await withQuoteClient(s.port, async c => {
    assert.equal((await c.quote({ ...cart, cartId: "after-timeout" })).cartId, "after-timeout");
  });
});

test("disconnect during business work fails without an invented reply", options, async t => {
  const s = await service(t, "delayed");
  await withQuoteClient(s.port, async c => {
    const pending = c.quote(cart);const outcome = assert.rejects(pending);
    await line(s.child, "Handler entered.");
    await stop(s.child, true); await outcome;
  });
});

test("close with a pending request settles it and permits the next host", options, async t => {
  const s = await service(t, "delayed"), c = openQuoteClient(s.port);t.after(() => c.close());
  const rejected = assert.rejects(c.quote(cart));
  await line(s.child, "Handler entered."); await c.close(); await rejected;
  await withQuoteClient(s.port, async next => assert.equal((await next.quote(cart)).totalCents, 4800));
});

test("handler exception stays a failed request; bad replies fail at app boundary", options, async t => {
  for (const mode of ["raises", "malformed", "oversized"]) {
    const s = await service(t, mode);
    await assert.rejects(withQuoteClient(s.port, c => c.quote(cart)));
    assert.equal((await stop(s.child)).code, 0, s.child.errors);
  }
});

test("OS-reserved port is refused without disturbing its owner", options, async t => {
  const port = await freePort();
  // Windows allows shared binds unless the owner requests exclusivity. Exercise
  // actual OS refusal instead of assuming two Runtime listeners must conflict.
  const owner = child(python, ["-B", "tests/port_owner.py", String(port)]);
  t.after(() => stop(owner));
  await line(owner, `Port reserved at 127.0.0.1:${port}`);
  const second = child(python, ["-B", "pricing_service.py", "--port", String(port)]);
  t.after(() => stop(second, true));
  assert.equal((await second.exited).code, 1, second.output + second.errors);
  assert.equal(owner.proc.exitCode, null);
  assert.equal((await stop(owner)).code, 0);
});

test("three service epochs close cleanly and EOF stops the worker", options, async t => {
  for (let i = 0; i < 3; i++) {
    const s = await service(t);
    await withQuoteClient(s.port, async c => assert.equal((await c.quote(cart)).totalCents, 4800));
    s.child.proc.stdin.end(); assert.equal((await s.child.exited).code, 0);
  }
});

test("input limits, CLI arguments and operation/cleanup failures", options, async t => {
  const scratch = await mkdtemp(join(process.env.COAKKA_DEMO_TEST_TMP || tmpdir(), "coakka-quote-"));
  t.after(() => rm(scratch, { recursive: true, force: true }));
  const large = join(scratch, "large.json"); await writeFile(large, Buffer.alloc(MAX_BYTES + 1, 32));
  await assert.rejects(readCart(large), /16 KiB/);
  for (const args of [["--bad"], ["--port", "0"], ["--cart", large]]) {
    const c = child(process.execPath, ["client.mjs", ...args]);t.after(() => stop(c, true));
    assert.equal((await c.exited).code, 1);
  }
  const rejectedFile = join(scratch, "rejected.json");
  await writeFile(rejectedFile, JSON.stringify({ ...cart, items: [{ sku: "missing", quantity: 1 }] }));
  const serviceForCli = await service(t);
  for (const extra of [[], ["--json"]]) {
    const c = child(process.execPath, ["client.mjs", "--port", String(serviceForCli.port), "--cart", rejectedFile, ...extra]);
    t.after(() => stop(c, true));
    assert.equal((await c.exited).code, 2, c.errors);
    if (extra.length) assert.equal(JSON.parse(c.output).status, "rejected");
    else assert.match(c.output, /Cart rejected: Choose tea, coffee or cocoa/);
  }
  assert.throws(() => openQuoteClient(0), RangeError);
  const primary = new Error("business"), cleanup = new Error("close");
  const fake = () => ({ close: async () => { throw cleanup; } });
  await assert.rejects(withQuoteClient(1, async () => { throw primary; }, fake), error => error instanceof AggregateError && error.errors[0] === primary && error.errors[1] === cleanup);
  await assert.rejects(withQuoteClient(1, async () => 1, fake), error => error === cleanup);
});
