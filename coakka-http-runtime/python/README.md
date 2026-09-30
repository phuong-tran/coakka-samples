# Python Sample

The Python lane uses typed callables, immutable value objects, context-managed
network responses, `threading.Event`, and normal exceptions. Static checks run
with Ruff and strict mypy.

## What It Shows

- path values, ordered headers, buffered echo, and streamed upload;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP;
- health, fresh liveness, and bounded monitor events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- signal-driven, reverse-order close.

`io_uring` is off unless `--io-uring` is present. The Python builder forwards
the preference; native startup owns capability detection and falls back to
`epoll` when needed. The sample prints both requested and effective states.

`security.py` uses Python's verifying TLS context for server-authenticated TLS
and mutual TLS. Its negative case requires an unidentified client to fail.

## Run

```bash
bash coakka-http-runtime/python/run.sh check
bash coakka-http-runtime/python/run.sh smoke
bash coakka-http-runtime/python/run.sh security-smoke
bash coakka-http-runtime/python/run.sh run
```

The runner creates its virtual environment and dependency cache under the
configured external work directory, imports the sibling connector source, and
loads the separately built host library. It does not contact PyPI for the
CoAkka package.
