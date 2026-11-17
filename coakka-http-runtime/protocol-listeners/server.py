"""Serve one route with an explicitly selected CoAkka HTTP protocol."""

import argparse
from pathlib import Path
import signal
from threading import Event

from coakka_http import Builder, Listener, ListenerProtocol, MultiProtocolListener, Response, TransportSecurity


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("protocol", choices=("http1", "http2", "http3", "multi"))
    parser.add_argument("certificates", type=Path)
    args = parser.parse_args()

    builder = Builder()
    if args.protocol == "http1":
        builder.listener(Listener(port=8080))
    else:
        tls = dict(
            port=8443,
            credential_generation=1,
            credential_id="local-example",
            certificate_chain_file=str(args.certificates / "server.pem"),
            private_key_file=str(args.certificates / "server.key"),
        )
        if args.protocol == "multi":
            builder.multi_protocol_listener(MultiProtocolListener(**tls))
        else:
            protocol = ListenerProtocol.HTTP_2 if args.protocol == "http2" else ListenerProtocol.HTTP_3
            builder.listener(Listener(protocol=protocol, security=TransportSecurity.TLS, **tls))

    stopped = Event()
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    with builder.get("/hello", lambda _request: Response.text("hello")).start() as service:
        print(f"listening on 127.0.0.1:{service.port}", flush=True)
        stopped.wait()


if __name__ == "__main__":
    main()
