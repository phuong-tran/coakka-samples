"""Inspect Core-issued startup facts and generation-checked live monitoring."""

from dataclasses import replace

from coakka_http import (
    MonitorApplyReason, MonitorCollection, MonitorLatency, MonitorNotification,
    Service,
)


def verify_settings(service: Service, *, tuning: bool) -> None:
    """Report actual Core observations, never derive affinity from configuration."""
    info, limits = service.runtime_info(), service.effective_limits()
    assert info.execution.observed and info.execution.active_event_loops > 0
    assert limits.header_timeout_ms == limits.body_timeout_ms == 5000
    assert limits.max_request_body_bytes == 65536
    if tuning:
        assert info.request_notification_batch_size == 1
        assert info.terminal_notification_batch_size == 4
    print(f"core-cpu={info.cpu} loops={info.execution.active_event_loops} "
          f"request-batch={info.request_notification_batch_size} "
          f"terminal-batch={info.terminal_notification_batch_size}")


def monitor_reload(service: Service) -> None:
    """Prove accepted reload, stale refusal, reservation refusal and restoration."""
    initial = service.monitor_config()
    desired = replace(initial.policy, collection=MonitorCollection.AGGREGATES,
                      notification=MonitorNotification.POLL, latency=MonitorLatency.NONE,
                      active_event_capacity=0, event_categories=type(initial.policy.event_categories)(0))
    accepted = service.monitor_apply(initial.generation, desired)
    assert accepted.reason is MonitorApplyReason.APPLIED and accepted.changed
    assert accepted.effective.policy == desired
    stale = service.monitor_apply(initial.generation, initial.policy)
    assert stale.reason is MonitorApplyReason.GENERATION_CONFLICT and not stale.changed
    assert stale.effective.policy == desired
    oversized = replace(initial.policy, active_event_capacity=initial.reserved_event_capacity + 1)
    refused = service.monitor_apply(accepted.effective.generation, oversized)
    assert refused.reason is MonitorApplyReason.RESOURCE_RESERVATION_EXCEEDED
    assert not refused.changed and refused.effective.policy == desired
    assert refused.effective.generation == accepted.effective.generation
    restored = service.monitor_apply(accepted.effective.generation, initial.policy)
    assert restored.reason is MonitorApplyReason.APPLIED and restored.changed
    assert service.monitor_config().policy == initial.policy
    print("python-monitor-reload=pass")
