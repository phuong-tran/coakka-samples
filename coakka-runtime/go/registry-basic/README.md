# Go 3.0.0 first run

Use Go 1.22.12+ and a C11 cgo compiler; native Windows ARM64 uses Go 1.25.5
with its matching ARM64 C compiler.

```sh
cd coakka-runtime/go/registry-basic
go run .
```

Expected output: `accepted-hello`. This app installs
`github.com/phuong-tran/coakka-runtime-go/v3@v3.0.0` through the normal module
workflow, starts one host and preserves request/close errors. The module carries
Core and its five platform images. CPU/connection/monitor defaults stay Core-owned.
See [current packages](../../../docs/common/current-packages.md) and
[the integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
