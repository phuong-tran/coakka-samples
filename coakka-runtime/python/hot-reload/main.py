from __future__ import annotations

import json
from collections.abc import Callable

from coakka_v2_connector import (
    ConnectorStartSpec,
    CoakkaStatus,
    CoreCallError,
    DeadletterError,
    DeliveryHint,
    EndpointSpec,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    RouteSpec,
    local_route,
)

REQUEST_IDENTITY = PayloadIdentity(
    message_type="samples.runtime.python.hot_reload.request.v1",
    payload_schema_version=1,
    payload_format=PayloadFormat.JSON,
)

CLIENT_TARGET = "samples.runtime.python.hot_reload.client"
V1_TARGET = "samples.runtime.python.hot_reload.v1"
V2_TARGET = "samples.runtime.python.hot_reload.v2"
MISSING_TARGET = "samples.runtime.python.hot_reload.missing"


def ask(runtime: RuntimeHost, target: str, message: str) -> dict:
    return runtime.ask_json(
        source=CLIENT_TARGET,
        target=target,
        payload={"message": message},
        payload_identity=REQUEST_IDENTITY,
        timeout_ms=2000,
        operation=f"ask_{target.rsplit('.', 1)[-1]}",
        delivery_hint=DeliveryHint.ROUTER_DEFAULT,
    )


def expect_route_miss(runtime: RuntimeHost, target: str) -> dict:
    try:
        ask(runtime, target, "route-miss")
    except DeadletterError as error:
        deadletter = error.deadletter
    else:
        raise RuntimeError(f"expected route miss for target={target}")

    if deadletter.reason != 2:
        raise RuntimeError(f"expected route miss reason=2, got {deadletter.reason}")
    if deadletter.original_envelope is None or deadletter.original_envelope.target != target:
        raise RuntimeError("missing or mismatched original deadletter target")
    return {
        "target": deadletter.original_envelope.target,
        "generation": deadletter.active_generation,
        "reason": "DEADLETTER_REASON_ROUTE_MISS",
    }


def expect_control_rejected(label: str, apply_fn: Callable[[], None], expected: CoakkaStatus) -> None:
    try:
        apply_fn()
    except CoreCallError as exc:
        if exc.status != expected:
            raise
        print(f"coakka_runtime_hot_reload_{label}_control_rejected status={exc.status}")
        return
    raise RuntimeError(f"expected {label} control snapshot to be rejected")


def main() -> None:
    start_spec = ConnectorStartSpec(
        system_name="python-hot-reload-sample",
        node_id="python-hot-reload-sample-node",
        generation=1,
        routes=[local_route(V1_TARGET)],
    )

    runtime = RuntimeHost.start(start_spec=start_spec)
    try:
        run_hot_reload(runtime)
    finally:
        if (background_error := runtime.take_background_error()) is not None:
            raise background_error


def run_hot_reload(runtime: RuntimeHost) -> None:
    with runtime:
        info = runtime.runtime_info()
        print(
            f"coakka_runtime_hot_reload_info version={info['runtimeVersion']} "
            f"git={info['gitCommit']}"
        )
        startup = runtime.runtime_startup_config()
        print(f"coakka_runtime_hot_reload_startup monitorEnabled={str(startup['monitor']['enabled']).lower()}")

        def v1_handler(request):
            return runtime.client.make_json_reply_from_request_identity(
                request=request,
                source=V1_TARGET,
                payload={"handledBy": "v1", "deliveryMode": "runtime"},
            )

        def v2_handler(request):
            return runtime.client.make_json_reply_from_request_identity(
                request=request,
                source=V2_TARGET,
                payload={"handledBy": "v2", "deliveryMode": "runtime"},
            )

        runtime.register_handler(V1_TARGET, v1_handler)
        runtime.register_handler(V2_TARGET, v2_handler)

        initial = ask(runtime, V1_TARGET, "before-reload")
        if initial != {"handledBy": "v1", "deliveryMode": "runtime"}:
            raise RuntimeError("unexpected generation-1 response")
        print(f"coakka_runtime_hot_reload_before payload={json.dumps(initial, separators=(',', ':'))}")

        initial_miss = expect_route_miss(runtime, V2_TARGET)
        print(
            "coakka_runtime_hot_reload_initial_miss "
            f"target={initial_miss['target']} generation={initial_miss['generation']}"
        )

        before_stale = runtime.stats()
        expect_control_rejected(
            "stale",
            lambda: runtime.control.apply_snapshot(
                generation=1,
                routes=[local_route(V2_TARGET)],
                source_connector="python-hot-reload-sample",
                seq=2,
            ),
            CoakkaStatus.ERR_STALE_GENERATION,
        )
        after_stale = runtime.stats()
        if after_stale["appliedGeneration"] != 1:
            raise RuntimeError(f"stale snapshot should not advance generation: {after_stale}")
        if after_stale["controlRejectedCount"] <= before_stale["controlRejectedCount"]:
            raise RuntimeError(f"expected stale snapshot rejection counter to advance: {after_stale}")
        print(
            "coakka_runtime_hot_reload_stale_rejected "
            f"appliedGeneration={after_stale['appliedGeneration']} "
            f"controlRejected={after_stale['controlRejectedCount']}"
        )

        runtime.control.apply_snapshot(
            generation=2,
            routes=[local_route(V2_TARGET)],
            source_connector="python-hot-reload-sample",
            seq=3,
        )
        # The helper first reads Core's already-applied health. This can succeed
        # with monitor OFF; it is not evidence that a notification was received.
        monitor = runtime.monitor.await_applied_generation_at_least(2, timeout_ms=1000)
        if monitor is None or monitor.health["appliedGeneration"] < 2:
            raise RuntimeError("generation 2 was not observed in Core health")
        after_reload = runtime.stats()
        if after_reload["appliedGeneration"] != 2 or after_reload["routeCount"] != 1:
            raise RuntimeError(f"expected one generation-2 route, got {after_reload}")
        print(
            "coakka_runtime_hot_reload_applied "
            f"generation={after_reload['appliedGeneration']} routes={after_reload['routeCount']}"
        )

        after = ask(runtime, V2_TARGET, "after-reload")
        if after != {"handledBy": "v2", "deliveryMode": "runtime"}:
            raise RuntimeError("unexpected generation-2 response")
        print(f"coakka_runtime_hot_reload_after payload={json.dumps(after, separators=(',', ':'))}")

        old_target_miss = expect_route_miss(runtime, V1_TARGET)
        print(
            "coakka_runtime_hot_reload_old_target_miss "
            f"target={old_target_miss['target']} generation={old_target_miss['generation']}"
        )

        before_invalid = runtime.stats()
        expect_control_rejected(
            "invalid",
            lambda: runtime.control.apply_snapshot(
                generation=3,
                routes=[RouteSpec(target=MISSING_TARGET, endpoints=[EndpointSpec(host="127.0.0.1", port=0)])],
                source_connector="python-hot-reload-sample",
                seq=4,
            ),
            CoakkaStatus.ERR_INVALID_ARG,
        )
        after_invalid = runtime.stats()
        if after_invalid["appliedGeneration"] != 2:
            raise RuntimeError(f"invalid snapshot should not advance generation: {after_invalid}")
        if after_invalid["controlRejectedCount"] <= before_invalid["controlRejectedCount"]:
            raise RuntimeError(f"expected invalid snapshot rejection counter to advance: {after_invalid}")
        print(
            "coakka_runtime_hot_reload_invalid_rejected "
            f"appliedGeneration={after_invalid['appliedGeneration']} "
            f"controlRejected={after_invalid['controlRejectedCount']}"
        )

        final_miss = expect_route_miss(runtime, MISSING_TARGET)
        final_stats = runtime.stats()
        client_stats = runtime.client_stats()
        if (final_stats["routeMissCount"], final_stats["deadletterCount"], client_stats.matched_deadletters, client_stats.pending_requests) != (3, 3, 3, 0):
            raise RuntimeError("unexpected final hot-reload counters")
        print(
            "coakka_runtime_hot_reload_final "
            f"generation={final_stats['appliedGeneration']} routes={final_stats['routeCount']} "
            f"routeMisses={final_stats['routeMissCount']} deadletters={final_stats['deadletterCount']} "
            f"matchedDeadletters={client_stats.matched_deadletters} "
            f"lastMissingTarget={final_miss['target']}"
        )


if __name__ == "__main__":
    main()
