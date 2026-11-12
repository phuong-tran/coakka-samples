# Serve HTTP/1.1, HTTP/2 and HTTP/3 on one port

Listener V1 in Core SDK 2.2.0 enables one fixed profile: HTTPS over
HTTP/1.1, HTTP/2 and HTTP/3. The server opens TCP and UDP on the same
port. TCP clients negotiate HTTP/1.1 or HTTP/2 during TLS. QUIC clients
use UDP for HTTP/3. All 3 can call the same buffered request/reply route.
Listener V1 selects this fixed profile; there is no protocol array to fill in.
The standalone listener API remains supported for one protocol and other
service profiles.

Generate the [local certificates](README.md) and run the [Node.js or Bun
sample](../../../../coakka-http-runtime/protocol-listeners/README.md) in
`multi` mode. The connector calls `multiProtocolListener({...})`.
For C and C++, the native SDK exposes the additive
`coakka_http_host_multi_listener_get_api_v1` table alongside ABI 10.
Go, Python, Java and Kotlin expose standalone protocol selection in 2.2.1.

Leave this one server running and use the same URL in all 3 calls:

```sh
for option in --http1.1 --http2 --http3-only; do
  curl --noproxy '*' "$option" --cacert ./http-test-tls/ca.pem \
    --fail --silent --show-error --max-time 10 \
    --write-out '\n%{http_code} %{http_version}\n' https://127.0.0.1:8443/hello || exit
done
```

Expected output:

```text
hello
200 1.1
hello
200 2
hello
200 3
```

`listenerInfo()` reports the port and the protocols the listener is
admitting. The curl results tell you which protocol each request used.
Clients that discover HTTP/3 through `Alt-Svc` need that advertisement
from your application or deployment. The forced curl probe does not
depend on it.

The V1 profile serves buffered request/reply routes with a server
certificate. Use the standalone listener for features such as streaming,
WebSockets, mTLS or certificate reload. Allow both TCP and UDP through
the firewall if clients connect directly. If a proxy terminates TLS or
QUIC, verify both the client-to-proxy and proxy-to-application hops:
the curl result describes the hop it connects to.
