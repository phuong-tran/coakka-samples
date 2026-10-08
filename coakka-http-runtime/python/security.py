"""Runnable TLS and mutual-TLS listener sample."""

from __future__ import annotations

import argparse
import ssl
import urllib.error
import urllib.request
from collections.abc import Callable
from pathlib import Path

from coakka_http import (
    Builder,
    Listener,
    ListenerProtocol,
    OutboundEndpoint,
    OutboundReason,
    OutboundRequest,
    OutboundTarget,
    Request,
    Response,
    TransportSecurity,
)


def response_for(body: bytes) -> Callable[[Request], Response]:
    """Capture immutable response bytes in a fully typed request callback."""

    def handle(_request: Request) -> Response:
        return Response(body=body)

    return handle


def arguments() -> argparse.Namespace:
    """Read the generated local identity directory."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixtures", type=Path, required=True)
    return parser.parse_args()


def request(port: int, fixtures: Path, *, with_identity: bool) -> tuple[int, bytes]:
    """Perform one authenticated loopback request with certificate checking."""
    context = ssl.create_default_context(cafile=str(fixtures / "ca.pem"))
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    if with_identity:
        context.load_cert_chain(
            str(fixtures / "client.pem"), str(fixtures / "client.key")
        )
    with urllib.request.urlopen(
        f"https://localhost:{port}/secure", context=context, timeout=5
    ) as response:
        body = response.read(4097)
        if len(body) > 4096:
            raise RuntimeError("secure sample response exceeded its bound")
        return int(response.status), body


def listener(
    fixtures: Path, security: TransportSecurity, generation: int
) -> Listener:
    """Build one copied listener declaration with a rotation generation."""
    return Listener(
        bind_address="127.0.0.1",
        protocol=ListenerProtocol.HTTP_1_1,
        security=security,
        credential_generation=generation,
        credential_id="python-sample-server",
        certificate_chain_file=str(fixtures / "server.pem"),
        private_key_file=str(fixtures / "server.key"),
        trust_roots_file=(
            str(fixtures / "ca.pem")
            if security is TransportSecurity.MUTUAL_TLS
            else ""
        ),
    )


def main() -> None:
    """Exercise server identity and mandatory client identity end to end."""
    fixtures = arguments().fixtures.resolve(strict=True)
    cases = (
        (TransportSecurity.TLS, 7, b"tls-ready"),
        (TransportSecurity.MUTUAL_TLS, 9, b"mtls-ready"),
    )
    for security, generation, body in cases:
        service = (
            Builder()
            .listener(listener(fixtures, security, generation))
            .get("/secure", response_for(body))
            .start()
        )
        try:
            if security is TransportSecurity.MUTUAL_TLS:
                try:
                    request(service.port, fixtures, with_identity=False)
                except (OSError, urllib.error.URLError):
                    pass
                else:
                    raise RuntimeError(
                        "mutual TLS accepted a client without an identity"
                    )
            expected = (200, body)
            observed = request(
                service.port,
                fixtures,
                with_identity=security is TransportSecurity.MUTUAL_TLS,
            )
            if observed != expected:
                raise RuntimeError(f"secure response mismatch: {observed!r}")
            # Core owns the outbound verifying transport too. The application
            # supplies identity generations; it never substitutes ssl sockets
            # for the runtime's outbound lane. Python ssl above is only a peer.
            endpoint = OutboundEndpoint(
                "secure-loopback", "127.0.0.1", service.port,
                f"localhost:{service.port}", security=security,
                tls_peer_identity="localhost", tls_trust_generation=11,
                tls_client_identity_generation=12 if security is TransportSecurity.MUTUAL_TLS else 0)
            builder = (Builder().outbound_trust(11, (fixtures / "ca.pem").read_bytes())
                       .outbound_target(OutboundTarget("secure", 3, (endpoint,)))
                       .get("/ready", response_for(b"ready")))
            if security is TransportSecurity.MUTUAL_TLS:
                builder.outbound_identity(12, (fixtures / "client.pem").read_bytes(),
                                          (fixtures / "client.key").read_bytes())
            with builder.start() as client:
                call = client.outbound_submit(OutboundRequest("secure", "GET", "/secure", timeout_ms=3000))
                terminal = client.take_outbound(4000)
                if (terminal is None or terminal.call != call
                        or terminal.reason is not OutboundReason.RESPONSE
                        or terminal.target_generation != 3
                        or terminal.response_status != 200 or terminal.response_body != body):
                    raise RuntimeError("Core outbound TLS identity/result mismatch")
        finally:
            service.close()
    print("python-security-smoke=pass")


if __name__ == "__main__":
    main()
