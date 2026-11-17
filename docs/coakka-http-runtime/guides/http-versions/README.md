# Choose an HTTP version

Use connector 2.3.1 or Core SDK 2.3.0 for these exercises. In the
connector version, `2.3` identifies the Core generation and the final `1`
is the connector's first revision on that Core.
[Version and SDK compatibility](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/versions.md)

All four exercises serve `GET /hello` from CoAkka HTTP Runtime and return
`hello`. The listener configuration decides how clients connect. The
application handler stays the same.

| Exercise | What you configure | What curl reports |
| --- | --- | --- |
| [HTTP/1.1](http1.md) | Plain HTTP on loopback over TCP | `200 1.1` |
| [HTTP/2](http2.md) | HTTPS over TCP with HTTP/2 negotiated in the TLS handshake | `200 2` |
| [HTTP/3](http3.md) | HTTPS over QUIC on UDP | `200 3` |
| [HTTP/1.1, HTTP/2 and HTTP/3 on one port](multi.md) | Shared listener with TLS on TCP and QUIC on UDP | All 3 on the same port |

The [language samples](../../../../coakka-http-runtime/protocol-listeners/README.md)
show how to install or select the package and start the same listener
from C, C++, Go, Node.js, Bun, Python, Java, Kotlin, C# and Swift.

Go, Node.js, Bun, Python, Java, Kotlin, C# and Swift expose standalone selection
and the shared listener. The C and C++ exercises show standalone selection.
Native applications can also use the shared listener through the optional
ABI 10 extension. See the
[feature and profile map](../../reference/FEATURES.md#what-you-can-use-in-231)
before combining protocol settings with advanced features.

## Prepare local TLS files

For the HTTPS exercises, generate a local CA and server identity from the
root of the Samples repository:

```sh
bash coakka-http-runtime/scripts/generate-test-certificates.sh ./http-test-tls
```

The listener loads `server.pem` and `server.key`. The certificate covers
`127.0.0.1` and `localhost`. Pass `ca.pem` to curl to verify the server.
The script generates short-lived credentials for local tests. Copy
`http-test-tls` into the application's working directory if using the
README's relative-path examples. Deploy with a certificate chain for your
actual host and keep its private key outside source control. On Windows,
use `curl.exe` and run the certificate script with Bash and OpenSSL.
