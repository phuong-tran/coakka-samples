# Serve HTTP/1.1, HTTP/2 and HTTP/3 on one port

The shared listener in Core SDK 2.3.0 enables HTTPS over
HTTP/1.1, HTTP/2 and HTTP/3. The server opens TCP and UDP on the same
port. TCP clients negotiate HTTP/1.1 or HTTP/2 during TLS. QUIC clients
use UDP for HTTP/3. All 3 can call the same buffered request/reply route.
Selecting this profile enables all 3 versions together.
The standalone listener API remains supported for one protocol and other
service profiles.

Generate the [local certificates](README.md#prepare-local-tls-files) and run
the [sample for your language](../../../../coakka-http-runtime/protocol-listeners/README.md)
in `multi` mode with connector 2.3.1.

| Language | API | Complete README example |
| --- | --- | --- |
| Go | `MultiProtocolListener(...)` | [Go](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/go/README.md#serve-all-3-http-versions-on-one-port) |
| Node.js and Bun | `multiProtocolListener(...)` | [Node.js and Bun](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/javascript/README.md#choose-an-http-version) |
| Java and Kotlin | `multiProtocolListener(...)` | [Java and Kotlin](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/jvm/README.md#serve-all-3-http-versions-on-one-port) |
| Python | `multi_protocol_listener(...)` | [Python](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/python/README.md#run-http11-http2-and-http3-on-one-port) |
| C# | `HttpListenerOptions.MultiProtocolListener(...)` | [C#](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/csharp/README.md#serve-all-3-protocols-on-one-port) |
| Swift | `HttpListener.multiProtocolListener(tls:address:port:)` | [Swift](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/swift/README.md#serve-all-3-protocols-on-one-port) |

For C and C++, the native SDK exposes the optional
`coakka_http_host_multi_listener_get_api_v1` table alongside ABI 10.

Check that `curl --version` lists both `HTTP2` and `HTTP3` in its features.
See the [HTTP/3 client setup](http3.md) if your curl lacks HTTP/3. Leave
one server running and use the same URL in all 3 calls. On PowerShell,
run each flag with `curl.exe`.

```sh
for option in --http1.1 --http2 --http3-only; do
  curl --noproxy '*' "$option" --cacert ./http-test-tls/ca.pem \
    --fail --silent --show-error --max-time 10 \
    --write-out '\n%{http_code} %{http_version}\n' https://127.0.0.1:8443/hello || exit
done
```

All 3 requests return the same body and show their negotiated protocol.

```text
hello
200 1.1
hello
200 2
hello
200 3
```

The connector's listener inspection API reports the port and the protocols the listener is
admitting. The curl results tell you which protocol each request used.
Clients that discover HTTP/3 through `Alt-Svc` need that advertisement
from your application or deployment. The forced curl probe does not
depend on it.

The shared profile serves buffered request/reply routes with a server
certificate. If the second request reports `200 1.1`, inspect the TLS
negotiation. If only the third times out, check UDP reachability to the
same port. If all three fail certificate verification, check the CA and
the address on the certificate. Use the standalone listener for features such as streaming,
WebSockets, mTLS or certificate reload. Allow both TCP and UDP through
the firewall if clients connect directly. If a proxy terminates TLS or
QUIC, verify both the client-to-proxy and proxy-to-application hops:
the curl result describes the hop it connects to.
