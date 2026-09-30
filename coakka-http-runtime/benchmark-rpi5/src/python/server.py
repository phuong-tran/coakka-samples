"""Fixed-response CoAkka, FastAPI, and Starlette benchmark applications."""

from __future__ import annotations

import signal
import sys
import threading

from coakka_http import Builder, Header, Headers, Response
from fastapi import FastAPI
from fastapi.responses import Response as FastApiResponse
from starlette.applications import Starlette
from starlette.responses import Response as StarletteResponse
from starlette.routing import Route

BODY = b"0123456789abcdef0123456789abcdef"
CONTENT_TYPE = "application/octet-stream"
COAKKA_HEADERS = Headers((Header("content-type", CONTENT_TYPE),))

fastapi_app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)


@fastapi_app.get("/fixed")
async def fastapi_fixed() -> FastApiResponse:
    """Return the shared immutable payload without JSON encoding."""
    return FastApiResponse(BODY, media_type=CONTENT_TYPE)


async def starlette_fixed(_request: object) -> StarletteResponse:
    """Return the same payload through the Starlette routing surface."""
    return StarletteResponse(BODY, media_type=CONTENT_TYPE)


starlette_app = Starlette(routes=[Route("/fixed", starlette_fixed)])


def run_coakka(port: int) -> None:
    """Run the host-inlined Python callback until a process signal arrives."""
    service = (
        Builder()
        .listen("127.0.0.1", port)
        .concurrency(3)
        .get("/fixed", lambda _request: Response(headers=COAKKA_HEADERS, body=BODY))
        .start()
    )
    stopped = threading.Event()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    while not stopped.wait(0.1):
        error = service.poll_error()
        if error is not None:
            print(f"CoAkka service error: {error}", file=sys.stderr, flush=True)
            try:
                health = service.health()
                print(
                    "CoAkka service health: "
                    f"lifecycle={health.lifecycle} "
                    f"failed_components={health.failed_components} "
                    f"admission_open={health.admission_open}",
                    file=sys.stderr,
                    flush=True,
                )
            except RuntimeError as health_error:
                print(
                    f"CoAkka health query failed: {health_error}",
                    file=sys.stderr,
                    flush=True,
                )
    service.close()


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "coakka":
        raise SystemExit("usage: server.py coakka <port>")
    run_coakka(int(sys.argv[2]))
