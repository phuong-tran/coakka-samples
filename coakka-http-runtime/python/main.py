"""Runnable CoAkka HTTP Runtime feature sample for Python.

The application uses only the public ``coakka_http`` package surface. It keeps
worker count, dispatch retention, file access, monitoring, outbound calls, and
shutdown finite so the example is a safe starting point rather than a toy
server with hidden unbounded state.
"""

from __future__ import annotations

import argparse
import signal
import threading
import urllib.request
import zlib
from pathlib import Path

from coakka_http import (
    BodyPolicy,
    Builder,
    Compression,
    CompressionMode,
    CpuPolicy,
    FileAuthority,
    FileResponse,
    Header,
    Headers,
    IoBackend,
    Limits,
    MonitorCategory,
    MonitorCollection,
    MonitorOptions,
    NotificationProfile,
    OutboundEndpoint,
    OutboundRequest,
    OutboundReason,
    OutboundTarget,
    Request,
    Response,
    ResponseStreamWriter,
    Route,
    RoutePublication,
    RoutePublicationCode,
    RouteRebind,
    RouteRebindCode,
    ServerSentEventsResponse,
    ServerSentEventsWriter,
    Service,
    SseEvent,
    StaticMount,
    StreamingResponse,
    WebSocketEvent,
    WebSocketEventKind,
    WebSocketResponse,
    WebSocketSession,
)
from control import monitor_reload, verify_settings

OUTBOUND_TARGET = "sample.upstream"


def parse_args() -> argparse.Namespace:
    """Parse the deliberate sample choices."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, default=Path("../assets"))
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--compression", action="store_true")
    parser.add_argument("--tuning", action="store_true")
    parser.add_argument("--single-cpu", action="store_true")
    parser.add_argument(
        "--io-uring",
        action="store_true",
        help="request io_uring and allow native fallback to epoll",
    )
    return parser.parse_args()


def streaming_response(_request: Request) -> StreamingResponse:
    """Return two credited chunks and one final response trailer."""

    def produce(writer: ResponseStreamWriter) -> Headers:
        writer.write(b"stream-")
        writer.write(b"ready")
        return Headers((Header("x-stream-end", "done"),))

    return StreamingResponse(
        produce,
        headers=Headers((Header("content-type", "text/plain"),)),
    )


def event_response(_request: Request) -> ServerSentEventsResponse:
    """Write one typed SSE event through the bounded response stream."""

    def produce(writer: ServerSentEventsWriter) -> None:
        writer.write(SseEvent(b"ready\nsecond line", event_type=b"state", id=b"1", retry_ms=1000))

    return ServerSentEventsResponse(produce)


def socket_response(_request: Request) -> WebSocketResponse:
    """Echo text frames while the service owns the ordered session callback."""

    def on_event(session: WebSocketSession, event: WebSocketEvent) -> None:
        kind = event.kind  # The event value is copied before this callback runs.
        if kind is WebSocketEventKind.OPEN:
            session.send_text("welcome")
        elif kind is WebSocketEventKind.TEXT:
            session.send_text(event.data.decode("utf-8"))
        elif kind is WebSocketEventKind.BINARY:
            session.send_bytes(event.data)

    return WebSocketResponse(on_event, "coakka.sample")


def hello_response(request: Request) -> Response:
    """Read copied path, ordered query, and indexed header projections."""
    name = request.path_parameters[0].encoded_value
    title = "hello"
    if request.query_parameters:
        first = request.query_parameters[0]
        if first.encoded_key == "title" and first.has_value:
            title = first.encoded_value
    caller = request.headers.get("x-sample-caller", "")
    return Response.text(f"{title} {name} from {caller}")


def upload_response(request: Request) -> Response:
    """Read at most 64 KiB, then inspect trailers only after observing EOF.

    The fixed non-secret markers let the wire recipe distinguish an actual
    disconnected reader from a client that merely closed its socket.
    """
    print("python-upload-reading", flush=True)
    try:
        body = request.body_reader.read(65537)
    except Exception:
        print("python-upload-read-failed", flush=True)
        raise
    if len(body) > 65536:
        return Response.text("upload too large", status=413)
    return Response(body=body, headers=Headers((
        Header("x-upload-observed", request.trailers.get("x-upload-check") or "absent"),)))


def create_service(assets: Path, upstream_port: int, *, io_uring: bool,
                   compression: bool = False, tuning: bool = False,
                   single_cpu: bool = False) -> Service:
    """Declare and start one service after validating all application roots."""
    root = str(assets.resolve(strict=True))
    endpoint = OutboundEndpoint(
        "loopback",
        "127.0.0.1",
        upstream_port,
        f"127.0.0.1:{upstream_port}",
    )
    builder = (
        Builder()
        .concurrency(2)
        .dispatch_queue(64)
        .dispatch_bytes(8 << 20)
        .handler_bindings(16)
        .limits(Limits(max_request_body_bytes=65536, header_timeout_ms=5000,
                       body_timeout_ms=5000, handler_timeout_ms=5000,
                       request_notification_profile=NotificationProfile.SMALL if tuning else NotificationProfile.AUTO,
                       terminal_notification_profile=NotificationProfile.MEDIUM if tuning else NotificationProfile.AUTO))
        .monitor(
            MonitorOptions(
                collection=MonitorCollection.AGGREGATES_AND_EVENTS,
                event_capacity=32,
                max_events_per_read=8,
                categories=(MonitorCategory.LIFECYCLE | MonitorCategory.EXCHANGE),
                signal_reserved=True,
            )
        )
        .static_mount(
            StaticMount(
                "/app",
                root,
                index_file="index.html",
                spa_fallback_file="index.html",
            )
        )
        .file_authority(FileAuthority(82, root, 1 << 20, 2))
        .outbound_target(OutboundTarget(OUTBOUND_TARGET, 1, (endpoint,)))
        .get(
            "/hello/{name}",
            hello_response,
        )
        .post("/echo", lambda request: Response(status=201, body=request.body))
        .post_stream(
            "/upload",
            upload_response,
        )
        .get("/stream", streaming_response)
        .get("/events", event_response)
        .websocket("/socket", socket_response)
        .get(
            "/download",
            lambda _request: FileResponse(
                82,
                "/sample.txt",
                headers=Headers((Header("content-type", "text/plain"),)),
            ),
        )
        .get("/version", lambda _request: Response.text("v1"))
        .get("/compressed", lambda _request: Response.text("compressible-body-" * 128))
    )
    if io_uring:
        builder.io_backend(IoBackend.IO_URING)
    if compression:
        builder.compression(Compression(CompressionMode.GZIP))
    if single_cpu:
        builder.cpu(CpuPolicy.SINGLE)
    return builder.start()


def activate_replacement(service: Service) -> None:
    """Activate one prepared handler without rebuilding the route table."""
    service.prepare_handler(100, lambda _request: Response.text("v2", status=201))
    # Core returns the bounded control outcome synchronously. There is no
    # separate caller timeout that could cancel an accepted publication.
    snapshot = service.routes
    version = next(route for route in snapshot.routes if route.route_id == 8)
    outcome = service.rebind_handler(RouteRebind(
        1, snapshot.identity.route_generation, version.route_id,
        version.binding_revision, 100))
    if outcome.code is not RouteRebindCode.APPLIED or not outcome.changed:
        raise RuntimeError(f"handler replacement was rejected: {outcome.code.name}")


def demonstrate_route_publication() -> None:
    """Replace one complete structural generation on an isolated service."""
    service = (
        Builder()
        .handler_bindings(3)
        .get("/old", lambda _request: Response.text("old-generation"))
        .start()
    )
    try:
        before = service.routes
        service.prepare_handler(
            2, lambda _request: Response.text("new-generation", status=201)
        )
        publication = RoutePublication(
            activation_id=1,
            expected_route_generation=before.identity.route_generation,
            expected_binding_change_sequence=before.identity.binding_change_sequence,
            routes=(
                Route(
                    2,
                    "GET",
                    "/published",
                    handler_binding_id=2,
                    body_policy=BodyPolicy(
                        enabled=True,
                        accept_absent=True,
                        accept_other=True,
                        max_body_bytes=1 << 20,
                    ),
                ),
            ),
        )
        outcome = service.publish_routes(publication)
        if outcome.code is not RoutePublicationCode.APPLIED or not outcome.changed:
            raise RuntimeError(
                f"route generation was rejected: {outcome.code.name}"
            )
        if read_url(service.port, "/published") != (201, b"new-generation"):
            raise RuntimeError("published route did not select its prepared handler")
        after = service.routes
        if len(after.routes) != 1 or after.routes[0].route_id != 2:
            raise RuntimeError("Core route snapshot did not reflect publication")
        stale = service.publish_routes(RoutePublication(
            2, before.identity.route_generation, before.identity.binding_change_sequence, ()))
        if stale.code is not RoutePublicationCode.GENERATION_MISMATCH or service.routes != after:
            raise RuntimeError("stale publication changed the effective route cut")
    finally:
        service.close()


def demonstrate_outbound(service: Service) -> None:
    """Submit and consume one call on the sole-reader outbound lane."""
    call = service.outbound_submit(
        OutboundRequest(OUTBOUND_TARGET, "GET", "/source", timeout_ms=3_000)
    )
    terminal = service.take_outbound(5_000)
    if (
        terminal is None
        or terminal.reason is not OutboundReason.RESPONSE
        or terminal.call != call
        or terminal.response_status != 200
        or terminal.response_body != b"outbound-ready"
    ):
        raise RuntimeError("outbound result did not match the declared target")


def read_url(port: int, path: str, *, data: bytes | None = None) -> tuple[int, bytes]:
    """Read one loopback response for the self-contained smoke command."""
    headers = {"accept": "text/html"} if path.startswith("/app/") else {}
    if data is not None:
        # urllib otherwise labels byte payloads as form data. These sample
        # routes accept an ordinary opaque body, so declare that explicitly.
        headers["content-type"] = "application/octet-stream"
    if path.startswith("/hello/"):
        headers["x-sample-caller"] = "smoke"
    request = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}", data=data, headers=headers
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        body = response.read(65537)
        if len(body) > 65536:
            raise RuntimeError("sample client response exceeded 64 KiB")
        return response.status, body


def smoke(service: Service) -> None:
    """Validate representative public features over the real listener."""
    cases = (
        ("/hello/reader?title=hello", None, 200, b"hello reader from smoke"),
        ("/echo", b"payload", 201, b"payload"),
        ("/upload", b"streamed", 200, b"streamed"),
        ("/stream", None, 200, b"stream-ready"),
        ("/events", None, 200, b"data: ready"),
        ("/download", None, 200, b"confined application file"),
        ("/app/client/route", None, 200, b"CoAkka HTTP Runtime"),
        ("/version", None, 201, b"v2"),
    )
    for path, data, expected_status, expected_body in cases:
        status, body = read_url(service.port, path, data=data)
        if status != expected_status or expected_body not in body:
            raise RuntimeError(f"{path}: status={status} body={body!r}")
    print("python-smoke=pass")


def main() -> None:
    """Own startup, optional smoke validation, signal wait, and shutdown."""
    args = parse_args()
    stopped = threading.Event()
    # Install before announcing readiness so a supervisor cannot race startup.
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    demonstrate_route_publication()
    upstream = Builder().get(
        "/source", lambda _request: Response.text("outbound-ready")
    ).start()
    service = None
    try:
        service = create_service(args.assets, upstream.port, io_uring=args.io_uring,
                                 compression=args.compression, tuning=args.tuning,
                                 single_cpu=args.single_cpu)
        verify_settings(service, tuning=args.tuning)
        monitor_reload(service)
        activate_replacement(service)
        demonstrate_outbound(service)
        health = service.probe_liveness(1_000)
        page = service.monitor_read(0, 8)
        runtime_info = service.runtime_info()
        print(
            f"ready={health.ready} monitor-latest={page.latest_sequence} "
            f"retained={len(page.events)} "
            f"io-uring-requested={runtime_info.io_uring_requested} "
            f"io-uring-effective={runtime_info.io_uring_effective}"
        )
        print(f"python-sample=http://127.0.0.1:{service.port}", flush=True)
        if args.smoke:
            smoke(service)
            if args.compression:
                request = urllib.request.Request(
                    f"http://127.0.0.1:{service.port}/compressed",
                    headers={"Accept-Encoding": "gzip"})
                with urllib.request.urlopen(request, timeout=5) as response:
                    encoded = response.read(4097)
                    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
                    decoded = decoder.decompress(encoded, 4097)
                    if (response.headers.get("Content-Encoding") != "gzip"
                            or len(encoded) > 4096
                            or not decoder.eof or decoder.unused_data
                            or decoded != b"compressible-body-" * 128):
                        raise RuntimeError("buffered GZIP response mismatch")
            return

        stopped.wait()
    finally:
        # Both owners must be attempted, retaining independent close failures.
        failures: list[Exception] = []
        for owner in (service, upstream):
            if owner is not None:
                try:
                    owner.close()
                except Exception as failure:
                    failures.append(failure)
        if failures:
            raise ExceptionGroup("sample shutdown failed", failures)
        print("python-shutdown=pass", flush=True)


if __name__ == "__main__":
    main()
