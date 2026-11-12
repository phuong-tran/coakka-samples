# Choose an HTTP version

All four exercises serve `GET /hello` from CoAkka HTTP Runtime and return
`hello`. The listener configuration decides how clients connect. The
application handler stays the same.

| Exercise | What you configure | What curl reports |
| --- | --- | --- |
| [HTTP/1.1](http1.md) | Plain HTTP on loopback over TCP | `200 1.1` |
| [HTTP/2](http2.md) | HTTPS over TCP with HTTP/2 negotiated in the TLS handshake | `200 2` |
| [HTTP/3](http3.md) | HTTPS over QUIC on UDP | `200 3` |
| [HTTP/1.1, HTTP/2 and HTTP/3 on one port](multi.md) | Listener V1 with TLS on TCP and QUIC on UDP | All 3 on the same port |

Use Core SDK 2.2.0 or the matching 2.2.1 connector. The
[language samples](../../../../coakka-http-runtime/protocol-listeners/README.md)
show how to install or select the package and start the same listener
from C, C++, Go, Node.js, Bun, Python, Java and Kotlin.

For the HTTPS exercises, generate a local CA and server identity from the
root of the Samples repository:

```sh
bash coakka-http-runtime/scripts/generate-test-certificates.sh ./http-test-tls
```

The listener loads `server.pem` and `server.key`. The certificate covers
`127.0.0.1` and `localhost`. Pass `ca.pem` to curl to verify the server.
The script generates short-lived credentials for local tests.
