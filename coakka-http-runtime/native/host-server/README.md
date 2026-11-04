# Serve HTTP from C and C++ with SDK 2.0.1

Download and verify the [native SDK 2.0.1](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native/releases/2.0.1).
Set `COAKKA_HTTP_HOST_PREFIX` to the extracted SDK. Set
`COAKKA_HTTP_SAMPLE_WORK_ROOT` to a build directory outside the source tree.

From the repository root, choose a buffered or streaming response.

```sh
bash coakka-http-runtime/c/run.sh host-server
bash coakka-http-runtime/c/run.sh host-streaming
```

Use `coakka-http-runtime/cpp/run.sh` to try the same flows in C++.
Each server prints `host-server-port=PORT`. Send the matching request
before its five-second request wait expires.

```sh
curl --max-time 5 "http://127.0.0.1:$PORT/hello"
curl --max-time 5 --http1.1 --raw -i "http://127.0.0.1:$PORT/stream"
```

The buffered response contains `hello`. The stream sends `stream-` and `ready`
as separate chunks, then finishes with the `x-stream-end: done` trailer.

The [C entry point](../../c/host_server.c) destroys the service explicitly.
The [C++ entry point](../../cpp/host_server.cpp) checks close and uses a
scope-bound owner. The [shared flow](example.h) releases the event lease
after taking its exchange identity. It waits for a writable event before
sending stream data and waits for the terminal event before closing.

The [installed SDK tests](../../runtime-test/README.md) cover request streams,
cancellation, and lifecycle.
