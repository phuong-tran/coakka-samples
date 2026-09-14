#!/usr/bin/env python3
"""CoAkka Core HTTP/2 TLS benchmark with a Python-owned handler loop."""

from __future__ import annotations

import json
import os
import queue
import signal
import sys
import threading

from coakka_http import (
    BodyDelivery,
    Configuration,
    EventKind,
    IoBackend,
    Listener,
    ListenerProtocol,
    Response,
    Route,
    TransportSecurity,
    runtime_info,
)

BODY = b"0123456789abcdef0123456789abcdef"


def required_environment(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"missing {name}")
    return value


def parse_backend(value: str) -> tuple[IoBackend, str]:
    if value == "platform-default":
        return IoBackend.PLATFORM_DEFAULT, value
    if value == "io-uring":
        return IoBackend.IO_URING, value
    raise ValueError(f"unsupported I/O backend: {value}")


def main() -> int:
    if len(sys.argv) != 2:
        raise ValueError("usage: coakka_core.py <platform-default|io-uring>")
    backend, backend_name = parse_backend(sys.argv[1])
    host_runtime = runtime_info()
    if backend is IoBackend.IO_URING and not host_runtime.io_uring_supported:
        raise RuntimeError(
            "io_uring benchmark stopped: Core reports unsupported "
            f"(compiled={host_runtime.io_uring_compiled} "
            f"probe_error={host_runtime.io_uring_probe_error})"
        )
    configuration = Configuration()
    try:
        configuration.add_listener(
            Listener(
                listener_id=1,
                bind_address="127.0.0.1",
                port=0,
                protocol=ListenerProtocol.HTTP_2,
                security=TransportSecurity.TLS,
                credential_generation=1,
                credential_id="benchmark-server",
                certificate_chain_file=required_environment(
                    "COAKKA_BENCHMARK_TLS_CERT"
                ),
                private_key_file=required_environment("COAKKA_BENCHMARK_TLS_KEY"),
            )
        )
        configuration.add_route(
            Route(
                1,
                "GET",
                "/fixed",
                handler_binding_id=1,
                body_delivery=BodyDelivery.INLINE,
            )
        )
        configuration.set_io_backend(backend)
        core = configuration.create_core()
    finally:
        configuration.close()

    core.start()
    effective_runtime = core.runtime_info()
    active = effective_runtime.effective_io_backend == IoBackend.IO_URING
    if effective_runtime.effective_io_backend != backend:
        core.close()
        raise RuntimeError(
            "backend benchmark stopped: "
            f"requested={backend_name} "
            f"effective={effective_runtime.effective_io_backend} "
            f"fallback_reason={effective_runtime.fallback_reason}"
        )

    shutdown_requested = threading.Event()
    reader_stopping = threading.Event()
    failures: queue.Queue[BaseException] = queue.Queue(maxsize=1)
    response = Response(status=200, body=BODY)

    def read_events() -> None:
        try:
            while not reader_stopping.is_set():
                lease = core.take_event(100)
                if lease is None:
                    continue
                exchange = None
                with lease:
                    if lease.kind is EventKind.REQUEST:
                        exchange = lease.exchange
                if exchange is not None:
                    core.respond(exchange, response)
        except BaseException as error:
            if not reader_stopping.is_set():
                try:
                    failures.put_nowait(error)
                except queue.Full:
                    pass
                shutdown_requested.set()

    reader = threading.Thread(target=read_events, name="coakka-benchmark-events")
    reader.start()

    def request_stop(_signal: int, _frame: object) -> None:
        shutdown_requested.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    print(
        json.dumps(
            {
                "ready": True,
                "bound_port": core.port,
                "application_path": "normal",
                "application_protocol": 2,
                "transport_security_mode": 2,
                "io_backend": backend_name,
                "io_uring_active": active,
                "io_uring_supported": host_runtime.io_uring_supported,
                "requested_io_backend": effective_runtime.requested_io_backend,
                "effective_io_backend": effective_runtime.effective_io_backend,
                "io_backend_fallback_reason": int(effective_runtime.fallback_reason),
            },
            separators=(",", ":"),
        ),
        flush=True,
    )

    while not shutdown_requested.wait(0.1):
        pass
    error: BaseException | None = None
    try:
        error = failures.get_nowait()
    except queue.Empty:
        pass
    try:
        core.drain()
    except BaseException as caught:
        error = error or caught
    reader_stopping.set()
    try:
        core.interrupt()
    except BaseException:
        # drain() may have already closed the reader; join below is the proof.
        pass
    reader.join(5)
    if reader.is_alive():
        error = error or RuntimeError("Python event reader did not stop")
    if not reader.is_alive():
        try:
            core.stop()
            core.close()
        except BaseException as caught:
            error = error or caught
    print(
        json.dumps(
            {"stopped": True, "handler_errors": int(error is not None)},
            separators=(",", ":"),
        ),
        flush=True,
    )
    if error is not None:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
