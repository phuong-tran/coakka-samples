# Start an HTTP/1.1 listener

HTTP/1.1 uses TCP and is widely supported. Start here to confirm that the
listener, route and handler work before setting up HTTPS.

Run the [language sample](README.md) in `http1` mode. It binds to
`127.0.0.1:8080` using plain HTTP. In most connectors,
`listen("127.0.0.1", 8080)` selects HTTP/1.1 with plain transport.

In another terminal, run:

```sh
curl --noproxy '*' --http1.1 --fail --silent --show-error --max-time 5 \
  --write-out '\n%{http_code} %{http_version}\n' http://127.0.0.1:8080/hello
```

Expected output:

```text
hello
200 1.1
```

If curl reports connection refused, check the server startup output and
the TCP port. Use the `http://` URL for this plain listener.
[HTTP/1.1 specification](https://www.rfc-editor.org/rfc/rfc9112.html)
