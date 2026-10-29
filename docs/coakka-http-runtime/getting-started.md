# Start With CoAkka HTTP Runtime

Choose your language in [HTTP Runtime Samples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md).
Follow that language's prerequisites and package instructions, then run its
server and send one request. Each sample includes the expected response and
how to close the service.

For example, the [Go sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/go/README.md)
uses Go 1.23 or newer plus a C compiler. From the Samples checkout:

```sh
bash coakka-http-runtime/go/run.sh run
```

Follow the Go README's warehouse/archive setup before running. Its commands
load the exact 1.0.0 archive from Publish; they do not compile a private Core.
Only prepare Go's tools for this route. Other languages have their own guides.

After one successful response, use the [feature guides](guides/README.md) or
[monitoring and operations](operations/README.md). The all-language verify
suite is for checking changes and requires all corresponding toolchains.

[HTTP Runtime documentation](README.md).
