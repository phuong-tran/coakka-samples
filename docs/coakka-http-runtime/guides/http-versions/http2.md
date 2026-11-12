# Start an HTTP/2 listener

HTTP/2 can carry multiple requests on one TCP connection. This exercise
uses HTTPS. During the TLS handshake, the client and server agree on HTTP/2.

Generate the [local certificates](README.md) and start the language
sample in `http2` mode. It selects HTTP/2, TLS and TCP port 8443.
Check that `curl --version` lists `HTTP2` under Features.

```sh
curl --noproxy '*' --http2 --cacert ./http-test-tls/ca.pem \
  --fail --silent --show-error --max-time 5 \
  --write-out '\n%{http_code} %{http_version}\n' https://127.0.0.1:8443/hello
```

Expected output:

```text
hello
200 2
```

An HTTP 200 alone does not confirm HTTP/2. Read the last number, especially
if you are testing behind a proxy that terminates TLS. If certificate
verification fails, check the local CA file and the host in the URL.
[HTTP/2 specification](https://www.rfc-editor.org/rfc/rfc9113.html)
