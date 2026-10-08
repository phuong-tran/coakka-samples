"""Finite deadline, pressure and outbound cancellation recipes using real HTTP.

Core owns deadline and terminal causes. Events only coordinate application work
for a reproducible example; an HTTP status is not used to guess a native cause.
"""

from concurrent.futures import ThreadPoolExecutor, as_completed
import threading
import urllib.error
import urllib.request

from coakka_http import (
    Builder, Limits, OutboundEndpoint, OutboundReason, OutboundRequest,
    OutboundTarget, Request, Response,
)


def status(port: int, path: str) -> int:
    """Complete one bounded loopback request, including ordinary HTTP errors."""
    try:
        response = urllib.request.urlopen(f"http://127.0.0.1:{port}{path}", timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        if len(response.read(4097)) > 4096:
            raise RuntimeError("adverse response exceeded its bound")
        return int(response.status)


def handler_deadline() -> None:
    """A Core timeout completes the client but cannot forcibly stop Python code."""
    entered, release = threading.Event(), threading.Event()

    def held(_request: Request) -> Response:
        entered.set()
        if not release.wait(3):
            raise RuntimeError("application hold exceeded its bound")
        return Response.text("late")

    with (Builder().concurrency(1).limits(Limits(handler_timeout_ms=250))
          .get("/held", held).get("/ready", lambda _: Response.text("ready")).start()) as service:
        with ThreadPoolExecutor(max_workers=1) as clients:
            pending = clients.submit(status, service.port, "/held")
            try:
                assert entered.wait(2)
                assert pending.result(timeout=2) == 504
            finally:
                release.set()
        assert status(service.port, "/ready") == 200
        assert service.poll_error() is None


def pressure() -> None:
    """One held worker and one queued item must produce bounded overload refusal."""
    entered, release = threading.Event(), threading.Event()

    def held(_request: Request) -> Response:
        entered.set()
        if not release.wait(5):
            raise RuntimeError("pressure fixture exceeded its bound")
        return Response.text("released")

    with (Builder().concurrency(1).dispatch_queue(1)
          .limits(Limits(handler_timeout_ms=10000)).get("/held", held)
          .get("/ready", lambda _: Response.text("ready")).start()) as service:
        with ThreadPoolExecutor(max_workers=9) as clients:
            pending = [clients.submit(status, service.port, "/held")]
            try:
                assert entered.wait(2)
                pending.extend(clients.submit(status, service.port, "/held") for _ in range(8))
                first = next(as_completed(pending, timeout=3)).result()
                assert first == 503  # Assert wire refusal, not its hidden producer.
            finally:
                release.set()
            assert all(item.result(timeout=5) in (200, 503) for item in pending)
        assert status(service.port, "/ready") == 200
        # Saturation also emits bounded local-dispatch diagnostics. Keep these
        # visible; do not reinterpret their text as a native outcome or claim
        # an empty diagnostic stream during deliberate overload.
        for _ in range(16):
            error = service.poll_error()
            if error is None:
                break
            print(f"pressure-diagnostic={error!r}")
        else:
            raise RuntimeError("pressure diagnostics exceeded the fixture bound")


def outbound_terminal(cancel: bool) -> None:
    """Consume the exact Core cancellation/deadline result, then reuse the lane."""
    entered, release = threading.Event(), threading.Event()

    def held(_request: Request) -> Response:
        entered.set()
        if not release.wait(5):
            raise RuntimeError("outbound fixture exceeded its bound")
        return Response.text("late")

    with (Builder().get("/held", held)
          .get("/ready", lambda _: Response.text("ready")).start()) as upstream:
        endpoint = OutboundEndpoint("loopback", "127.0.0.1", upstream.port,
                                    f"127.0.0.1:{upstream.port}")
        with (Builder().outbound_target(OutboundTarget("upstream", 1, (endpoint,)))
              .get("/ready", lambda _: Response.text("ready")).start()) as service:
            call = service.outbound_submit(OutboundRequest(
                "upstream", "GET", "/held", timeout_ms=3000 if cancel else 800))
            try:
                assert entered.wait(2)
                if cancel:
                    service.outbound_cancel(call)
                terminal = service.take_outbound(3000)
                expected = OutboundReason.CANCELLED if cancel else OutboundReason.DEADLINE_EXCEEDED
                assert terminal is not None and terminal.call == call and terminal.reason is expected
            finally:
                release.set()
            reuse = service.outbound_submit(OutboundRequest("upstream", "GET", "/ready", timeout_ms=3000))
            terminal = service.take_outbound(4000)
            assert terminal is not None and terminal.call == reuse
            assert terminal.reason is OutboundReason.RESPONSE and terminal.response_body == b"ready"
            assert service.take_outbound(0) is None


def main() -> None:
    """Run all cases once; a gate may repeat this bounded command independently."""
    handler_deadline()
    pressure()
    outbound_terminal(True)
    outbound_terminal(False)
    print("python-adverse=pass")


if __name__ == "__main__":
    main()
