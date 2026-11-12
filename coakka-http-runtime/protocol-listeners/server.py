"""Serve one route with an explicitly selected CoAkka HTTP protocol."""

import argparse
from pathlib import Path
import signal
from threading import Event

from coakka_http import Builder, Listener, ListenerProtocol, Response, TransportSecurity


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("protocol", choices=("http1", "http2", "http3"))
    parser.add_argument("certificates", type=Path)
    args = parser.parse_args()

    if args.protocol == "http1":
        listener = Listener(port=8080)
    else:
        protocol = ListenerProtocol.HTTP_2 if args.protocol == "http2" else ListenerProtocol.HTTP_3
        listener = Listener(
            port=8443,
            protocol=protocol,
            security=TransportSecurity.TLS,
            credential_generation=1,
            credential_id="local-example",
            certificate_chain_file=str(args.certificates / "server.pem"),
            private_key_file=str(args.certificates / "server.key"),
        )

    stopped = Event()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    with Builder().listener(listener).get("/hello", lambda _request: Response.text("hello")).start() as service:
        print(f"listening on 127.0.0.1:{service.port}", flush=True)
        stopped.wait()


if __name__ == "__main__":
    main()
