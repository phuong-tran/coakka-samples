# C and C++ server and response streaming with SDK 1.1.2

Set `COAKKA_HTTP_HOST_PREFIX` to the installed, verified 1.1.2 SDK and
`COAKKA_HTTP_SAMPLE_WORK_ROOT` to an external build directory. Run one
example from the repository root.

```sh
bash coakka-http-runtime/c/run.sh host-server
bash coakka-http-runtime/c/run.sh host-streaming
```

Use `cpp/run.sh` in place of `c/run.sh` to run the C++ versions.
Each command prints `host-server-port=PORT`. Send the matching request from
another terminal before the five-second request wait expires. The buffered
example returns `hello`. The streaming example returns `stream-ready` in
chunks followed by the `x-stream-end: done` trailer.

```sh
curl --max-time 5 "http://127.0.0.1:$PORT/hello"
curl --max-time 5 --http1.1 --raw -i "http://127.0.0.1:$PORT/stream"
```

The [C entry point](../../c/host_server.c) explicitly destroys its service.
The [C++ entry point](../../cpp/host_server.cpp) also ties the service to
an RAII owner and checks close explicitly. The shared HTTP flow
[registers a route and takes one request](example.h), releases each event
lease, and keeps only the exchange identity between events. It waits for
Core's writable event before sending stream data, finishes with a trailer,
and waits for the correlated terminal event before closing. Admission
pressure causes this finite example to exit with an error.

For upload streaming, cancellation, and lifecycle checks, use the
[installed SDK conformance harness](../../runtime-test/README.md).
