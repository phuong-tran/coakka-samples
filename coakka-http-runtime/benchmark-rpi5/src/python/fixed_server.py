#!/usr/bin/env python3
"""Comparable Python HTTP servers for the fixed-response RPi 5 workload."""

from __future__ import annotations

import asyncio
import json
import signal
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Awaitable, Callable

import uvicorn
from coakka_http import Builder, Response
from fastapi import FastAPI, Response as FastApiResponse

BODY = b"0123456789abcdef0123456789abcdef"
CONTENT_TYPE = "application/octet-stream"


def announce(port: int) -> None:
    print(
        json.dumps(
            {"ready": True, "bound_port": port, "application_path": "normal"},
            separators=(",", ":"),
        ),
        flush=True,
    )


def install_stop_handler(stop: Callable[[], None]) -> None:
    signal.signal(signal.SIGINT, lambda _signal, _frame: stop())
    signal.signal(signal.SIGTERM, lambda _signal, _frame: stop())


def run_coakka() -> int:
    return asyncio.run(run_coakka_async())


async def run_coakka_async() -> int:
    response = Response.bytes(BODY)

    async def fixed(_request: object) -> Response:
        return response

    service = await (
        Builder()
        .listen("127.0.0.1", 0)
        .get("/fixed", fixed)
        .start()
    )
    stopped = asyncio.Event()
    install_stop_handler(stopped.set)
    announce(service.port)
    await stopped.wait()
    await service.close()
    return int(service.snapshot().failed != 0)


class FixedHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:  # noqa: N802 - stdlib callback name
        if self.path != "/fixed":
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE)
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)

    def log_message(self, _format: str, *args: object) -> None:
        del args


class FixedHttpServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 256


def run_direct() -> int:
    server = FixedHttpServer(("127.0.0.1", 0), FixedHandler)
    thread = threading.Thread(target=server.serve_forever, name="python-http", daemon=True)
    thread.start()
    stopped = threading.Event()
    install_stop_handler(stopped.set)
    announce(server.server_port)
    stopped.wait()
    server.shutdown()
    server.server_close()
    thread.join(5)
    return int(thread.is_alive())


async def raw_asgi(
    scope: dict[str, object],
    receive: Callable[[], Awaitable[dict[str, object]]],
    send: Callable[[dict[str, object]], Awaitable[None]],
) -> None:
    del receive
    if scope["type"] != "http":
        return
    if scope["method"] != "GET" or scope["path"] != "/fixed":
        await send({"type": "http.response.start", "status": 404, "headers": []})
        await send({"type": "http.response.body", "body": b""})
        return
    await send(
        {
            "type": "http.response.start",
            "status": 200,
            "headers": [
                (b"content-type", b"application/octet-stream"),
                (b"content-length", b"32"),
            ],
        }
    )
    await send({"type": "http.response.body", "body": BODY})


fastapi_app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)


@fastapi_app.get("/fixed")
async def fastapi_fixed() -> FastApiResponse:
    return FastApiResponse(BODY, media_type=CONTENT_TYPE)


def run_uvicorn(application: object) -> int:
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    listener.bind(("127.0.0.1", 0))
    listener.listen(256)
    listener.setblocking(False)
    port = listener.getsockname()[1]
    config = uvicorn.Config(
        application,
        loop="uvloop",
        http="httptools",
        ws="none",
        lifespan="off",
        access_log=False,
        log_level="warning",
        server_header=False,
        date_header=False,
    )
    server = uvicorn.Server(config)
    server.install_signal_handlers = lambda: None
    thread = threading.Thread(
        target=lambda: server.run(sockets=[listener]),
        name="uvicorn",
        daemon=True,
    )
    thread.start()
    deadline = time.monotonic() + 10
    while not server.started and thread.is_alive() and time.monotonic() < deadline:
        time.sleep(0.01)
    if not server.started:
        server.should_exit = True
        thread.join(5)
        listener.close()
        raise RuntimeError("Uvicorn did not start")
    stopped = threading.Event()
    install_stop_handler(stopped.set)
    announce(port)
    stopped.wait()
    server.should_exit = True
    thread.join(10)
    listener.close()
    return int(thread.is_alive())


def main() -> int:
    if len(sys.argv) != 2:
        raise ValueError("usage: fixed_server.py <coakka|direct|uvicorn|fastapi>")
    mode = sys.argv[1]
    if mode == "coakka":
        return run_coakka()
    if mode == "direct":
        return run_direct()
    if mode == "uvicorn":
        return run_uvicorn(raw_asgi)
    if mode == "fastapi":
        return run_uvicorn(fastapi_app)
    raise ValueError(f"unsupported mode: {mode}")


if __name__ == "__main__":
    exit_code = main()
    print(
        json.dumps(
            {"stopped": True, "handler_errors": 0},
            separators=(",", ":"),
        ),
        flush=True,
    )
    raise SystemExit(exit_code)
