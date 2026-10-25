from __future__ import annotations

from contextlib import closing

from coakka_v2_connector import (
    ConnectorStartSpec,
    Deadletter,
    DeadletterError,
    DeliveryHint,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    local_route,
)

# Public reason is an unknown-preserving integer, not a schema-generated enum.
ROUTE_MISS_REASON = 2


def check_deadletter(deadletter: Deadletter, target: str) -> None:
    if deadletter.reason != ROUTE_MISS_REASON:
        raise RuntimeError(f"expected route-miss reason, got {deadletter.reason}")
    if deadletter.original_envelope is None or deadletter.original_envelope.target != target:
        raise RuntimeError("missing or mismatched original deadletter target")


def main() -> None:
    live_target = "samples.runtime.python.deadletter.live"
    missing_target = "samples.runtime.python.deadletter.missing"
    identity = PayloadIdentity("samples.runtime.python.deadletter.request.v1", 1, PayloadFormat.JSON)
    spec = ConnectorStartSpec(
        system_name="python-deadletter-sample", node_id="python-deadletter-sample-node",
        routes=[local_route(live_target)],
    )
    runtime = RuntimeHost.start(start_spec=spec)
    try:
        with runtime:
            # Unregister this one-item observation on every body failure, before
            # host close. Never exit the process from an active owner scope.
            with closing(runtime.deadletters(buffer_capacity=1)) as observations:
                try:
                    runtime.ask_json(
                        source="samples-runtime-python-deadletter-client", target=missing_target,
                        payload={"message": "route-miss"}, payload_identity=identity,
                        timeout_ms=2000, operation="route-miss", delivery_hint=DeliveryHint.ROUTER_DEFAULT,
                    )
                except DeadletterError as error:
                    deadletter = error.deadletter
                else:
                    raise RuntimeError("expected route-miss deadletter")
                check_deadletter(deadletter, missing_target)
                observed = observations.get(timeout_s=1.0)
                if observed is None or not observed.matched_pending_request:
                    raise RuntimeError("expected one matching observed deadletter")
                check_deadletter(observed.deadletter, missing_target)

                stats, client = runtime.stats(), runtime.client_stats()
                if (stats["routeMissCount"], stats["deadletterCount"], client.matched_deadletters, client.pending_requests) != (1, 1, 1, 0):
                    raise RuntimeError("unexpected route-miss counters")
                print(f"coakka_runtime_deadletter reason=ROUTE_MISS target={missing_target} generation={deadletter.active_generation}")
                print(f"coakka_runtime_deadletter_observed matchedPending=true target={missing_target}")
                print(
                    f"coakka_runtime_stats routeMisses={stats['routeMissCount']} "
                    f"deadletters={stats['deadletterCount']} matchedDeadletters={client.matched_deadletters}"
                )
    finally:
        if (background_error := runtime.take_background_error()) is not None:
            raise background_error


if __name__ == "__main__":
    main()
