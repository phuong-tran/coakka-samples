package sample

import coakka.http.CpuPlacement
import coakka.http.CpuPolicy
import coakka.http.MonitorApplyReason
import coakka.http.MonitorCollection
import coakka.http.MonitorLatency
import coakka.http.MonitorNotification
import coakka.http.Service

/** Inspect Core-issued settings; startup affinity is not JVM-wide CPU utilization. */
internal fun verifySettings(service: Service, tuning: Boolean, singleCpu: Boolean) {
    val info = service.runtimeInfo()
    val limits = service.effectiveLimits()
    check(info.serviceStarted && info.execution.observed && info.execution.activeEventLoops > 0)
    check(limits.maxRequestBodyBytes == 65_536L)
    check(limits.headerTimeoutMillis == 5_000L && limits.bodyTimeoutMillis == 5_000L)
    if (tuning) check(info.requestNotificationBatchSize == 1 && info.terminalNotificationBatchSize == 4)
    val cpu = checkNotNull(info.cpu)
    if (singleCpu) check(cpu.requestedPolicy == CpuPolicy.SINGLE &&
        cpu.placement == CpuPlacement.APPLIED && cpu.selectedCpuCount == 1)
    println("core-cpu-placement=${cpu.placement} selected=${cpu.selectedCpuCount} " +
        "ids=${cpu.selectedCpuIds} loops=${info.execution.activeEventLoops} " +
        "request-batch=${info.requestNotificationBatchSize} terminal-batch=${info.terminalNotificationBatchSize}")
}

/** Change collection within reservations, prove refusals are atomic, then restore. */
internal fun demonstrateMonitorReload(service: Service) {
    val initial = service.monitorConfiguration()
    val desired = initial.policy.copy(collection = MonitorCollection.AGGREGATES,
        notification = MonitorNotification.POLL, latency = MonitorLatency.NONE,
        activeEventCapacity = 0, eventCategories = 0)
    val accepted = service.applyMonitorPolicy(initial.generation, desired)
    check(accepted.reason == MonitorApplyReason.APPLIED && accepted.changed)
    check(accepted.effective.policy == desired)
    val stale = service.applyMonitorPolicy(initial.generation, initial.policy)
    check(stale.reason == MonitorApplyReason.GENERATION_CONFLICT && !stale.changed)
    check(stale.effective.generation == accepted.effective.generation && stale.effective.policy == desired)
    check(initial.reservedEventCapacity < Int.MAX_VALUE)
    val oversized = initial.policy.copy(activeEventCapacity = initial.reservedEventCapacity + 1)
    val refused = service.applyMonitorPolicy(accepted.effective.generation, oversized)
    check(refused.reason == MonitorApplyReason.RESOURCE_RESERVATION_EXCEEDED && !refused.changed)
    check(refused.effective.generation == accepted.effective.generation && refused.effective.policy == desired)
    val restored = service.applyMonitorPolicy(accepted.effective.generation, initial.policy)
    check(restored.reason == MonitorApplyReason.APPLIED && restored.changed)
    check(service.monitorConfiguration().policy == initial.policy)
    println("kotlin-monitor-reload=pass")
}
