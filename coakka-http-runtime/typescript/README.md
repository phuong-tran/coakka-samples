# TypeScript Sample For Node.js And Bun

One strict TypeScript source set runs unchanged on Node.js and Bun. Handlers
use normal functions, Web APIs, async stream writers, `bigint` identities, and
promise-based close.

## What It Shows

- query values, ordered headers, buffered echo, and streamed upload;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP;
- health, fresh liveness, and bounded monitor events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- `SIGINT`/`SIGTERM` waiting and reverse-order asynchronous close.

`io_uring` is off unless `--io-uring` is present. The TypeScript builder
forwards the preference; native startup owns capability detection and falls
back to `epoll` when needed. The sample prints both requested and effective
states on Node.js and Bun.

`security.ts` uses `node:https` on both hosts to verify TLS and mutual TLS. It
also requires an unidentified mutual-TLS client to be rejected.

## Run

```bash
bash coakka-http-runtime/typescript/run.sh check
bash coakka-http-runtime/typescript/run.sh smoke
bash coakka-http-runtime/typescript/run.sh security-smoke
bash coakka-http-runtime/typescript/run.sh run
bash coakka-http-runtime/typescript/run.sh run-bun
```

`tsconfig.json` enables strict checks, exact optional properties, and unchecked
index protection. The runner builds the sibling native addon, installs the
class-and-source package into an external staged application, type-checks it,
and compiles it with `tsc` for Node.js. Bun executes the same TypeScript source
directly. This keeps the Node.js 22 floor independent of optional built-in
TypeScript stripping. No npm publication is required.
