# Start an HTTP/3 listener

HTTP/3 uses QUIC over UDP. It can carry multiple requests on one connection.
Its transport uses TLS 1.3, so the URL starts with `https://`.

Generate the [local certificates](README.md) and start the language
sample in `http3` mode. It listens on UDP port 8443. Run `curl --version`
and look for `HTTP3` under Features. Use your distribution's package
manager to install a build that includes HTTP/3, then check the exact
executable you will run. On Debian 13 with the official `trixie-backports`
repository configured, use `sudo apt install -t trixie-backports curl`.
The curl project also documents how to build it with HTTP/3 support.
[Official curl HTTP/3 guide](https://curl.se/docs/http3.html)

```sh
curl --noproxy '*' --http3-only --cacert ./http-test-tls/ca.pem \
  --fail --silent --show-error --max-time 10 \
  --write-out '\n%{http_code} %{http_version}\n' https://127.0.0.1:8443/hello
```

Expected output:

```text
hello
200 3
```

`--http3-only` makes a failed QUIC connection visible. With `--http3`,
curl may fall back and return HTTP 200 over another protocol. If this
exercise times out, confirm that the process started and that the firewall
or proxy allows UDP to the port. Test directly against the server before
adding a proxy. A TCP health check cannot verify HTTP/3.
[HTTP/3 specification](https://www.rfc-editor.org/rfc/rfc9114.html)
