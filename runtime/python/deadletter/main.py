from __future__ import annotations

from coakka_v2_connector import (
    ConnectorStartSpec,
    DeadletterError,
    DeliveryHint,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    local_route,
)

ROUTE_MISS_REASON = 2


def main() -> None:
    live_target = "samples.runtime.python.deadletter.live"
    missing_target = "samples.runtime.python.deadletter.missing"
    request_identity = PayloadIdentity(
        message_type="samples.runtime.python.deadletter.request.v1",
        payload_schema_version=1,
        payload_format=PayloadFormat.JSON,
    )
    start_spec = ConnectorStartSpec(
        system_name="python-deadletter-sample",
        node_id="python-deadletter-sample-node",
        queue_capacity=128,
        strict_no_drop=True,
        generation=1,
        routes=[local_route(live_target, 19411)],
    )

    with RuntimeHost.start(start_spec=start_spec) as runtime:
        observed_deadletters = runtime.deadletters(buffer_capacity=1)
        try:
            runtime.ask_json(
                source="samples-runtime-python-deadletter-client",
                target=missing_target,
                payload={"message": "route-miss"},
                payload_identity=request_identity,
                timeout_ms=2000,
                operation="route-miss",
                delivery_hint=DeliveryHint.ROUTER_DEFAULT,
            )
            raise RuntimeError("expected route miss deadletter")
        except DeadletterError as error:
            deadletter = error.deadletter

        stats = runtime.stats()
        client_stats = runtime.client_stats()

        if deadletter.reason != ROUTE_MISS_REASON:
            raise RuntimeError(f"expected route miss reason={ROUTE_MISS_REASON}, got {deadletter.reason}")
        if deadletter.original_envelope.target != missing_target:
            raise RuntimeError(f"expected target={missing_target}, got {deadletter.original_envelope.target}")
        if stats["routeMissCount"] != 1 or stats["deadletterCount"] != 1:
            raise RuntimeError(f"expected routeMissCount=1 deadletterCount=1, got {stats}")
        if client_stats.matched_deadletters != 1:
            raise RuntimeError(f"expected matched_deadletters=1, got {client_stats.matched_deadletters}")
        observed = observed_deadletters.get(timeout_s=1.0)
        observed_deadletters.close()
        if observed is None:
            raise RuntimeError("expected observed deadletter")
        if not observed.matched_pending_request:
            raise RuntimeError("expected observed deadletter to match pending request")
        if observed.deadletter.original_envelope.target != missing_target:
            raise RuntimeError(
                f"expected observed target={missing_target}, got {observed.deadletter.original_envelope.target}"
            )

        print(
            "coakka_runtime_deadletter "
            f"reason=DEADLETTER_REASON_ROUTE_MISS target={deadletter.original_envelope.target} "
            f"generation={deadletter.active_generation}"
        )
        print(
            "coakka_runtime_deadletter_observed "
            f"matchedPending={str(observed.matched_pending_request).lower()} "
            f"target={observed.deadletter.original_envelope.target}"
        )
        print(
            f"coakka_runtime_stats routeMisses={stats['routeMissCount']} "
            f"deadletters={stats['deadletterCount']} matchedDeadletters={client_stats.matched_deadletters}"
        )


if __name__ == "__main__":
    main()
