# Go Logger Samples

Go logger samples consume `github.com/phuong-tran/coakka-logger-go@v1.2.4`.
The package embeds native logger generation `1.2.1+f50756ebff0d` for macOS,
Linux, and Windows.

## New To CoAkka Logger

CoAkka Logger is the native-backed logging side of CoAkka. It gives a host
application a small language-native API while the native core owns queueing,
pressure behavior, drain semantics, and platform library loading.

Use these public repositories to orient first:

- `https://github.com/phuong-tran/coakka-logger-go`
- `https://github.com/phuong-tran/coakka-runtime-go`
- `https://github.com/phuong-tran/coakka-publish`
- `https://github.com/phuong-tran/coakka-samples`

Current samples:

- `basic`: install the public Go module into a temporary workspace, load the
  embedded native logger, emit one record, drain it, and print counters
- `pressure`: install the public Go module, fill a queue with capacity `2`,
  observe rejected writes, drain the accepted records, and print dropped
  counters

Install the package through normal Go module resolution:

```sh
go get github.com/phuong-tran/coakka-logger-go@v1.2.4
```

Run:

```sh
bash logger/go/basic/run.sh
bash logger/go/pressure/run.sh
```
