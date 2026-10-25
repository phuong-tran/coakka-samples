from __future__ import annotations

import json

from coakka_v2_connector import (
    ConnectorStartSpec,
    DeliveryHint,
    Message,
    PayloadFormat,
    PayloadIdentity,
    RuntimeHost,
    local_route,
)


def check_response(response: object) -> None:
    if response != {"echo": "hello-runtime-python"}:
        raise RuntimeError("unexpected echo response")


def main() -> None:
    target = "samples.runtime.python.echo"
    identity = PayloadIdentity("samples.runtime.python.echo.request.v1", 1, PayloadFormat.JSON)
    # Only application identity/routing is chosen here; Core resolves tuning.
    spec = ConnectorStartSpec(
        system_name="python-runtime-sample",
        node_id="python-runtime-sample-node",
        routes=[local_route(target)],
    )
    runtime = RuntimeHost.start(start_spec=spec)
    try:
        with runtime:
            info = runtime.runtime_info()
            startup = runtime.runtime_startup_config()
            print(f"coakka_runtime_info abi={info['abiVersion']} version={info['runtimeVersion']} git={info['gitCommit']}")
            print(
                f"coakka_runtime_startup cpuPolicy={startup['execution']['requestedPolicy']} "
                f"connectionMode={int(startup['connection'].mode)} "
                f"monitorEnabled={str(startup['monitor']['enabled']).lower()} "
                f"requestMode={startup['delivery']['requestMode']} responseMode={startup['delivery']['responseMode']}"
            )

            def echo_handler(request: Message) -> Message:
                return runtime.client.make_json_reply_from_request_identity(
                    request=request, source=target, payload={"echo": "hello-runtime-python"},
                )

            runtime.register_handler(target, echo_handler)
            response = runtime.ask_json(
                source="samples-runtime-python-client", target=target,
                payload={"message": "hello-runtime-python"}, payload_identity=identity,
                timeout_ms=2000, operation="echo", delivery_hint=DeliveryHint.ROUTER_DEFAULT,
            )
            check_response(response)
            print(f"coakka_runtime_response payload={json.dumps(response, separators=(',', ':'))}")
            stats, client = runtime.stats(), runtime.client_stats()
            if (stats["routeCount"], client.delivered_requests, client.matched_responses, client.pending_requests) != (1, 1, 1, 0):
                raise RuntimeError("unexpected echo counters")
            print(
                f"coakka_runtime_stats generation={stats['appliedGeneration']} routes={stats['routeCount']} "
                f"delivered={client.delivered_requests} matchedResponses={client.matched_responses}"
            )
    finally:
        # Public diagnostics remain readable after close. A worker failure must
        # not disappear behind an ask timeout; Python retains any active cause.
        if (error := runtime.take_background_error()) is not None:
            raise error


if __name__ == "__main__":
    main()
