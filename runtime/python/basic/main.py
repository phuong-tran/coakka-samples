from __future__ import annotations

import json

from coakka_v2_connector import (
    ConnectorOrchestrator,
    ConnectorStartSpec,
    DeliveryHint,
    EndpointFlag,
    EndpointSpec,
    PayloadFormat,
    PayloadIdentity,
    RouteSpec,
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
    # separate_delivered_request_lane=True keeps inbound delivered requests away from
    # response/deadletter matching, which keeps request/reply behavior easy to inspect.
    # generation=1 is the first route-table version; increment it for new route snapshots.
    # EndpointFlag.LOCAL means the target handler is registered in this process.
    start_spec = ConnectorStartSpec(
        system_name="python-runtime-sample",
        node_id="python-runtime-sample-node",
        queue_capacity=128,
        strict_no_drop=True,
        separate_delivered_request_lane=True,
        generation=1,
        routes=[
            RouteSpec(
                target=target,
                endpoints=[
                    EndpointSpec(
                        host="127.0.0.1",
                        port=19311,
                        flags=int(EndpointFlag.LOCAL),
                    )
                ],
            )
        ],
    )

    with ConnectorOrchestrator.start(start_spec=start_spec) as orchestrator:
        info = orchestrator.runtime_info()
        print(
            f"coakka_runtime_info abi={info['abiVersion']} "
            f"version={info['runtimeVersion']} git={info['gitCommit']} "
            f"backend={info['southboundBackend']}"
        )

        def echo_handler(request):
            return orchestrator.client.make_json_reply_from_request_identity(
                request=request,
                source=target,
                payload={"echo": "hello-runtime-python"},
            )

        orchestrator.register_handler(target, echo_handler)

        response = orchestrator.ask_json(
            source="samples-runtime-python-client",
            target=target,
            payload={"message": "hello-runtime-python"},
            payload_identity=request_identity,
            timeout_ms=2000,
            operation="echo",
            delivery_hint=DeliveryHint.ROUTER_DEFAULT,
        )

        print(f"coakka_runtime_response payload={json.dumps(response, separators=(',', ':'))}")

        stats = orchestrator.stats()
        client_stats = orchestrator.client_stats()
        print(
            f"coakka_runtime_stats generation={stats['appliedGeneration']} "
            f"routes={stats['routeCount']} delivered={client_stats.delivered_requests} "
            f"matchedResponses={client_stats.matched_responses}"
        )


if __name__ == "__main__":
    main()
