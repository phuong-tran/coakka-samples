from __future__ import annotations

import json

from coakka_v2_connector import (
    ConnectorStartSpec,
    DeliveryHint,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    local_route,
)


def main() -> None:
    target = "samples.runtime.python.echo"
    request_identity = PayloadIdentity(
        message_type="samples.runtime.python.echo.request.v1",
        payload_schema_version=1,
        payload_format=PayloadFormat.JSON,
    )
    # Minimal single-process runtime configuration.
    #
    # system_name groups diagnostics for one logical runtime participant.
    # node_id identifies this concrete process in logs and snapshots.
    # queue_capacity=128 is bounded but roomy enough for a sample.
    # strict_no_drop=True makes overload visible instead of silently dropping messages.
    # The delivered-request lane is enabled by default for request/reply hosts.
    # generation=1 is the first route-table version; increment it for new route snapshots.
    # local_route(...) hides host/port placeholders for same-process targets.
    start_spec = ConnectorStartSpec(
        system_name="python-runtime-sample",
        node_id="python-runtime-sample-node",
        queue_capacity=128,
        strict_no_drop=True,
        generation=1,
        routes=[local_route(target, 19311)],
    )

    with RuntimeHost.start(start_spec=start_spec) as runtime:
        info = runtime.runtime_info()
        print(
            f"coakka_runtime_info abi={info['abiVersion']} "
            f"version={info['runtimeVersion']} git={info['gitCommit']}"
        )

        def echo_handler(request):
            return runtime.client.make_json_reply_from_request_identity(
                request=request,
                source=target,
                payload={"echo": "hello-runtime-python"},
            )

        runtime.register_handler(target, echo_handler)

        response = runtime.ask_json(
            source="samples-runtime-python-client",
            target=target,
            payload={"message": "hello-runtime-python"},
            payload_identity=request_identity,
            timeout_ms=2000,
            operation="echo",
            delivery_hint=DeliveryHint.ROUTER_DEFAULT,
        )

        print(f"coakka_runtime_response payload={json.dumps(response, separators=(',', ':'))}")

        stats = runtime.stats()
        client_stats = runtime.client_stats()
        print(
            f"coakka_runtime_stats generation={stats['appliedGeneration']} "
            f"routes={stats['routeCount']} delivered={client_stats.delivered_requests} "
            f"matchedResponses={client_stats.matched_responses}"
        )


if __name__ == "__main__":
    main()
