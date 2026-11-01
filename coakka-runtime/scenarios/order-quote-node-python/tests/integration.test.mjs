import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { RuntimeHost, outboundOnlyNetwork, DeadletterError } from "coakka-v2-connector-node";
import { openQuoteClient, withQuoteClient, TARGET, MAX_BYTES } from "../quote-client.mjs";
import { readCart } from "../client.mjs";

import { child, line, stop, freePort, service, python } from "./process-fixture.mjs";
const cart = { cartId: "cart-1001", items: [{ sku: "tea", quantity: 2 }, { sku: "coffee", quantity: 1 }] };

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
