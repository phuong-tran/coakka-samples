"""Run an explicit secure HTTP/2 or HTTP/3 listener with a normal Python handler.

The public runtime owns the wire protocol, certificate and graceful drain.
This is a loopback development recipe, not a production credential provisioner.
"""

import argparse
from pathlib import Path
import signal
import threading

from coakka_http import Builder, Listener, ListenerProtocol, Request, Response, TransportSecurity


def respond(_request: Request) -> Response:
    """A normal Python callback; the fixed marker proves actual wire admission."""
    print("python-protocol-requested", flush=True)
    return Response.text("protocol-ready")


def main() -> None:
    """Wait for a signal and announce shutdown only after Core confirms close."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", choices=("http2", "http3"), required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    args = parser.parse_args()
    fixtures = args.fixtures.resolve(strict=True)
    stopped = threading.Event()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    protocol = ListenerProtocol.HTTP_2 if args.protocol == "http2" else ListenerProtocol.HTTP_3
    with (Builder().listener(Listener(
            bind_address="127.0.0.1", protocol=protocol, security=TransportSecurity.TLS,
            credential_generation=1, credential_id="python-protocol-sample",
            certificate_chain_file=str(fixtures / "server.pem"),
            private_key_file=str(fixtures / "server.key")))
          .get("/protocol", respond).start()) as service:
        print(f"python-{args.protocol}=https://127.0.0.1:{service.port}", flush=True)
        stopped.wait()
    print("python-protocol-shutdown=pass", flush=True)


if __name__ == "__main__":
    main()
