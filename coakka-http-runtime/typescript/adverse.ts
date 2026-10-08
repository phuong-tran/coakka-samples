/** Finite deadline, overload, cancellation and reuse recipes; not a benchmark. */
import assert from "node:assert/strict";
import { Builder, Limits, OutboundReason, Response, type Service } from "@coakka/http";

/** Explicit application gate: no busy wait and no blocking of the host loop. */
function gate(): { promise: Promise<void>; release: () => void } {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => { release = resolve; });
  return { promise, release };
}

/** Bound fixture synchronization too; always remove its timeout on settlement. */
async function bounded<T>(work: Promise<T>): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([work, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error("sample synchronization timed out")), 5000);
    })]);
  } finally { if (timer !== undefined) clearTimeout(timer); }
}

/** Read a tiny peer response with a finite HTTP deadline. */
async function request(service: Service, path: string): Promise<number> {
  const response = await fetch(`http://127.0.0.1:${service.port}${path}`,
    { signal: AbortSignal.timeout(4000) });
  const body = await response.arrayBuffer();
  assert.ok(body.byteLength <= 4096);
  return response.status;
}

/** Handler deadline is native policy; application work still needs retirement. */
async function deadline(): Promise<void> {
  const entered = gate();
  const held = gate();
  const service = new Builder().limits(new Limits({ requestTimeoutMillis: 250 }))
    .get("/held", async () => { entered.release(); await held.promise; return Response.text("late"); })
    .get("/ready", () => Response.text("ready")).start();
  try {
    const pending = request(service, "/held");
    await bounded(entered.promise);
    assert.equal(await pending, 504);
    held.release();
    assert.equal(await request(service, "/ready"), 200);
  } finally { held.release(); await service.close(); }
  console.log("typescript-handler-deadline=pass");
}

/** Hold the only native exchange slot until pressure has produced a refusal. */
async function pressure(): Promise<void> {
  const entered = gate();
  const held = gate();
  const service = new Builder().limits(new Limits({ maxConnections: 32,
    maxActiveRequests: 1, requestQueueCapacity: 1, responseQueueCapacity: 1,
    requestTimeoutMillis: 5000,
  })).get("/held", async () => { entered.release(); await held.promise; return Response.text("admitted"); })
    .get("/ready", () => Response.text("ready")).start();
  const admitted = request(service, "/held");
  const pending: Promise<number>[] = [];
  try {
    await bounded(entered.promise);
    pending.push(...Array.from({ length: 9 }, () => request(service, "/held")));
    // This assertion happens before release: success cannot be a scheduling
    // accident after the overloaded interval has already disappeared.
    const statuses = await bounded(Promise.all(pending));
    assert.ok(statuses.every((status) => status === 503));
    held.release();
    assert.equal(await admitted, 200);
    assert.equal(await request(service, "/ready"), 200);
  } finally {
    held.release();
    await Promise.allSettled([admitted, ...pending]);
    await service.close();
  }
  console.log("typescript-pressure=pass");
}

/** Consume Core's one terminal cause, release application work, then reuse. */
async function outbound(): Promise<void> {
  let entered = gate();
  let held = gate();
  const upstream = new Builder().limits(new Limits({ requestTimeoutMillis: 5000 }))
    .get("/held", async () => {
      const current = held;
      entered.release();
      await current.promise;
      return Response.text("released");
    }).get("/ready", () => Response.text("ready")).start();
  let client: Service | undefined;
  try {
    client = new Builder().outboundTarget({ name: "adverse", generation: 1n,
      endpoints: [{ nodeId: "loopback", connectHost: "127.0.0.1", connectPort: upstream.port,
        httpAuthority: `127.0.0.1:${upstream.port}` }],
    }).get("/ready", () => Response.text("ready")).start();
    for (const cancel of [true, false]) {
      entered = gate(); held = gate();
      const call = client.outboundSubmit({ logicalTarget: "adverse", method: "GET",
        target: "/held", timeoutMillis: cancel ? 3000 : 800 });
      await bounded(entered.promise);
      if (cancel) client.outboundCancel(call);
      const terminal = await client.takeOutbound(4000);
      assert.ok(terminal !== null);
      assert.deepEqual(terminal.call, call);
      assert.equal(terminal.reason, cancel ? OutboundReason.CANCELLED : OutboundReason.DEADLINE_EXCEEDED);
      held.release();
      assert.equal(await client.takeOutbound(0), null);
      const reused = client.outboundSubmit({ logicalTarget: "adverse", method: "GET",
        target: "/ready", timeoutMillis: 3000 });
      const ready = await client.takeOutbound(4000);
      assert.ok(ready !== null);
      assert.deepEqual(ready.call, reused);
      assert.equal(ready.reason, OutboundReason.RESPONSE);
      assert.equal(ready.responseStatus, 200);
    }
  } finally {
    held.release();
    const failures: unknown[] = [];
    try { await client?.close(); } catch (error) { failures.push(error); }
    try { await upstream.close(); } catch (error) { failures.push(error); }
    if (failures.length) throw new AggregateError(failures, "adverse owner cleanup failed");
  }
  console.log("typescript-outbound-cancel-deadline-reuse=pass");
}

await deadline();
await pressure();
await outbound();
console.log("typescript-adverse=pass");
