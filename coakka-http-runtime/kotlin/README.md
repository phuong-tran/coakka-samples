# Kotlin Sample

Kotlin is the JVM sample language. The application uses builders, data
classes, lambdas, typed response producers, `use`-style stream ownership, and a
JVM shutdown hook rather than translating a Java sample line by line.

## What It Shows

- path/query handling, buffered bodies, and streamed request delivery;
- streamed responses with final trailers, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP;
- health, fresh liveness, and bounded monitor events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- finite close in reverse construction order.

`io_uring` is off unless `--io-uring` is present. The Kotlin builder forwards
the preference; native startup owns capability detection and falls back to
`epoll` when needed. The sample prints both requested and effective states.

`Security.kt` builds a JVM trust store from the test authority and an optional
client key store from the generated identity. It verifies TLS and proves that
mutual TLS refuses an unidentified client.

## Run

```bash
bash coakka-http-runtime/kotlin/run.sh check
bash coakka-http-runtime/kotlin/run.sh smoke
bash coakka-http-runtime/kotlin/run.sh security-smoke
bash coakka-http-runtime/kotlin/run.sh run
```

Gradle and all build output are redirected to the configured external work
directory. The runner builds the sibling connector as a class-only local JAR
and supplies the host and native adapter libraries separately; no Maven
publication is required.

For repeated qualification runs, set `COAKKA_HTTP_SAMPLE_GRADLE_USER_HOME` to
an existing task-owned Gradle cache on that build volume. Connector and sample
compiled outputs still remain in `COAKKA_HTTP_SAMPLE_WORK_ROOT`.
