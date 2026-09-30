# Go Sample

The Go lane uses ordinary functions, structs, errors, contexts, and
`signal.NotifyContext`. It imports the sibling connector source directly rather
than relying on a module proxy.

## What It Shows

- path and query values, ordered headers, buffered echo, and streamed upload;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- a logical-target outbound request with one terminal owner;
- health, fresh liveness, and bounded monitoring events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- reverse-order close and signal-driven service lifetime.

`io_uring` is off unless `--io-uring` is present. The Go builder forwards the
preference; native startup owns capability detection and falls back to `epoll`
when needed. The sample prints both requested and effective states.

`security.go` creates server-authenticated TLS and mutual-TLS services and uses
Go's TLS client to verify the chain and mandatory client identity.

## Run

```bash
bash coakka-http-runtime/go/run.sh check
bash coakka-http-runtime/go/run.sh smoke
bash coakka-http-runtime/go/run.sh security-smoke
bash coakka-http-runtime/go/run.sh run
```

The runner builds the host library into the external work directory, adds a
local `replace` only to the staged `go.mod`, runs `go vet`, and leaves the
public source free of workstation paths.
