import assert from "node:assert/strict";
import test from "node:test";
import { acceptedFrame, createAdmission } from "./lifecycle.mjs";

function gate() {
  let release;
  const promise = new Promise(resolve => { release = resolve; });
  return { promise, release: () => release() };
}

test("eight accepted calls, ninth refused without invoking work", async () => {
  const held = gate(); let calls = 0, closes = 0;
  const owner = createAdmission(async () => { closes++; });
  const active = Array.from({ length: 8 }, () => owner.run(() => { calls++; return held.promise; }));
  await assert.rejects(owner.run(() => { throw new Error("not admitted"); }), /admission refused/);
  assert.equal(calls, 8);
  held.release(); await Promise.all(active); await owner.close(); assert.equal(closes, 1);
});

test("seal joins work before native close; repeated close joins the same result", async () => {
  const held = gate(), native = gate(); const order = [];
  const owner = createAdmission(async () => { order.push("native"); await native.promise; order.push("retired"); });
  const active = owner.run(async () => { await held.promise; order.push("work"); });
  const close = owner.close(); assert.equal(owner.close(), close);
  await assert.rejects(owner.run(() => assert.fail("sealed")), /admission refused/);
  assert.deepEqual(order, []); held.release(); await active;
  await Promise.resolve(); assert.deepEqual(order, ["work", "native"]);
  native.release(); await close; assert.deepEqual(order, ["work", "native", "retired"]);
});

test("throw and rejected work release admission and do not prevent runtime close", async () => {
  let closes = 0;
  const owner = createAdmission(async () => { closes++; });
  for (let i = 0; i < 32; i++) {
    const failure = new Error("work " + i);
    await assert.rejects(owner.run(() => { throw failure; }), error => error === failure);
  }
  assert.equal(await owner.run(() => 42), 42); await owner.close(); assert.equal(closes, 1);
});

test("close retains native failure and never silently retries", async () => {
  const failure = new Error("native close"); let calls = 0;
  const owner = createAdmission(async () => { calls++; throw failure; });
  const close = owner.close(); await assert.rejects(close, error => error === failure);
  assert.equal(owner.close(), close); await assert.rejects(owner.close(), error => error === failure);
  assert.equal(calls, 1); await assert.rejects(owner.run(() => 1), /admission refused/);
});

test("frame authorization uses exact window, current main frame and complete local URL", () => {
  const frame = { url: "file:///sample/index.html" }, contents = { mainFrame: frame };
  const event = { sender: contents, senderFrame: frame };
  assert.equal(acceptedFrame(event, contents, frame.url), true);
  for (const bad of [undefined, {}, { sender: {}, senderFrame: frame },
    { sender: contents, senderFrame: null }, { sender: contents, senderFrame: { url: frame.url } }]) {
    assert.equal(acceptedFrame(bad, contents, frame.url), false);
  }
  const expected = frame.url;
  for (const url of [expected + "#other", expected + "?other", "https://example.invalid/", "file:///sample/other.html"]) {
    frame.url = url; assert.equal(acceptedFrame(event, contents, expected), false);
  }
  frame.url = expected; contents.mainFrame = { url: expected };
  assert.equal(acceptedFrame(event, contents, expected), false);
});
