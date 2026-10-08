"""Fixed-response CoAkka, FastAPI, and Starlette benchmark applications."""

from __future__ import annotations

import signal
import os
import json
from dataclasses import asdict
import sys
import threading

BODY = b"0123456789abcdef0123456789abcdef"
CONTENT_TYPE = "application/octet-stream"


def __getattr__(name: str) -> object:
    """Build only Uvicorn's selected app; exclude unrelated server imports/RSS."""
    if name == "fastapi_app":
        from fastapi import FastAPI
        from fastapi.responses import Response as FrameworkResponse

        app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

        @app.get("/fixed", response_model=None)
        async def fixed() -> object:
            """Send immutable bytes without JSON serialization."""
            return FrameworkResponse(BODY, media_type=CONTENT_TYPE)

        return app
    if name == "starlette_app":
        from starlette.applications import Starlette
        from starlette.responses import Response as FrameworkResponse
        from starlette.routing import Route

        async def fixed(_request: object) -> object:
            """Send the identical body through real Starlette routing."""
            return FrameworkResponse(BODY, media_type=CONTENT_TYPE)

        return Starlette(routes=[Route("/fixed", fixed)])
    raise AttributeError(name)


def run_coakka(port: int) -> None:
    """Run the host-inlined Python callback until a process signal arrives."""
    from coakka_http import Builder, CpuPolicy, Header, Headers, Response

    headers = Headers((Header("content-type", CONTENT_TYPE),))
    intent = os.environ.get("COAKKA_BENCH_CPU_POLICY")
    if intent not in ("single", "auto"):
        raise ValueError("explicit benchmark CPU intent required")
    stopped = threading.Event()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    service = (
        Builder()
        .listen("127.0.0.1", port)
        .cpu(CpuPolicy.SINGLE if intent == "single" else CpuPolicy.AUTO)
        .get("/fixed", lambda _request: Response(headers=headers, body=BODY))
        .start()
    )
    try:
        info = service.runtime_info()
        if info.cpu is None:
            raise RuntimeError("Core CPU observation unavailable")
        print("coakka-runtime-info=" + json.dumps({
            "cpu": {"requestedPolicy": info.cpu.requested_policy,
                    "placement": info.cpu.placement, "selectedCpuCount": info.cpu.selected_cpu_count,
                    "selectedCpuIds": info.cpu.selected_cpu_ids},
            "execution": {"observed": info.execution.observed,
                          "configuredEventLoops": info.execution.configured_event_loops,
                          "activeEventLoops": info.execution.active_event_loops},
            "limits": asdict(service.effective_limits()),
            "ioUringEffective": info.io_uring_effective,
            "requestNotificationBatchSize": info.request_notification_batch_size,
            "terminalNotificationBatchSize": info.terminal_notification_batch_size,
        }), flush=True)
        # The controller owns process health checks. No periodic native query
        # or timer wakeup is added to only one measured implementation.
        stopped.wait()
        error = service.poll_error()
        if error is not None:
            raise RuntimeError("benchmark handler diagnostic") from error
    finally:
        service.close()
    print("benchmark-shutdown=pass", flush=True)


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "coakka":
        raise SystemExit("usage: server.py coakka <port>")
    run_coakka(int(sys.argv[2]))
