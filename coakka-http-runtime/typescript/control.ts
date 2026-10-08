/** Core-issued startup observations and generation-checked monitor reload. */
import assert from "node:assert/strict";
import {
  Builder, CpuPolicy, CpuPlacement, Limits, MonitorCategory, MonitorCollection,
  MonitorLatency, MonitorNotification, NotificationProfile, Response, type Service,
} from "@coakka/http";

/** Narrow the advanced public record without inventing absent native facts. */
function record(value: unknown): Readonly<Record<string, unknown>> {
  assert.ok(value !== null && typeof value === "object" && !Array.isArray(value));
  return value as Readonly<Record<string, unknown>>;
}

/** Accept one policy, reject stale/over-reservation edits, then restore it. */
function reload(service: Service): void {
  const initial = service.monitorConfig();
  assert.equal(typeof initial.generation, "bigint");
  assert.equal(typeof initial.reservedEventCapacity, "number");
  const original = record(initial.policy);
  const desired = { ...original, collection: MonitorCollection.AGGREGATES,
    notification: MonitorNotification.POLL, latency: MonitorLatency.NONE,
    activeEventCapacity: 0, eventCategories: 0 };
  const accepted = service.monitorApply(initial.generation as bigint, desired);
  assert.equal(accepted.changed, true);
  const effective = record(accepted.effective);
  assert.equal(typeof effective.generation, "bigint");
  assert.deepEqual(effective.policy, desired);
  const stale = service.monitorApply(initial.generation as bigint, original);
  assert.equal(stale.changed, false);
  assert.deepEqual(stale.effective, effective);
  const oversized = { ...original, activeEventCapacity: (initial.reservedEventCapacity as number) + 1 };
  const refused = service.monitorApply(effective.generation as bigint, oversized);
  assert.equal(refused.changed, false);
  assert.deepEqual(refused.effective, effective);
  const restored = service.monitorApply(effective.generation as bigint, original);
  assert.equal(restored.changed, true);
  assert.deepEqual(service.monitorConfig().policy, original);
  // Keep native refusal reasons visible; never infer a cause from a message.
  console.log(`monitor-reload=pass stale-reason=${stale.reason} reservation-reason=${refused.reason}`);
}

/** One owner at a time: CPU placement is a shared process scope, not per-route. */
async function settings(single: boolean): Promise<boolean> {
  const service = new Builder().limits(new Limits({
    cpuPolicy: single ? CpuPolicy.SINGLE : CpuPolicy.AUTO,
    requestNotificationProfile: NotificationProfile.SMALL,
    terminalNotificationProfile: NotificationProfile.MEDIUM,
    headerTimeoutMillis: 5000, bodyTimeoutMillis: 5000,
    maxRequestBodyBytes: 65536,
  })).monitor({ collection: MonitorCollection.AGGREGATES_AND_EVENTS,
    eventCapacity: 32, maxEventsPerRead: 8,
    categories: MonitorCategory.LIFECYCLE | MonitorCategory.EXCHANGE,
    signalReserved: true,
  }).get("/", () => Response.text("control-ready")).start();
  try {
    const info = service.runtimeInfo();
    assert.ok(info.execution.observed && info.execution.activeEventLoops > 0);
    assert.equal(info.limits.headerTimeoutMillis, 5000n);
    assert.equal(info.limits.bodyTimeoutMillis, 5000n);
    assert.equal(info.limits.maxRequestBodyBytes, 65536n);
    assert.equal(info.requestNotificationBatchSize, 1);
    assert.equal(info.terminalNotificationBatchSize, 4);
    if (single) assert.equal(info.cpu.selectedCpuCount, 1);
    console.log("Core CPU", info.cpu, "execution", info.execution);
    reload(service);
    return info.cpu.placement !== CpuPlacement.UNSUPPORTED;
  } finally { await service.close(); }
}

if (await settings(false)) await settings(true);
else console.log("single-placement=unsupported (Core observation; no CPU count inferred)");
console.log("typescript-control=pass");
