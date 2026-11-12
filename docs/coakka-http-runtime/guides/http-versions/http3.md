# Start an HTTP/3 listener

HTTP/3 uses QUIC over UDP. It can carry multiple requests on one connection.
Its transport uses TLS 1.3, so the URL starts with `https://`.

Generate the [local certificates](README.md) and start the language
sample in `http3` mode. It listens on UDP port 8443. Run `curl --version`
and look for `HTTP3` under Features. Check the exact binary you plan to use; for example,
`/usr/bin/curl --version` if multiple copies are installed.

If the binary lacks HTTP/3, use a build from your OS package channel
with that feature. On Debian 13, enable
[official Debian Backports](https://backports.debian.org/Instructions/#add-backports-to-sourceslist)
first, then run:

```sh
sudo apt update
sudo apt install -t trixie-backports curl
/usr/bin/curl --version
```

Look for `HTTP3` in the last command's Features line. For other platforms
see the [official curl HTTP/3 build guide](https://curl.se/docs/http3.html).

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
exercise times out, check that the service started in `http3` or `multi`
mode, that the port matches, and that UDP reaches it. If curl reports a
certificate error, use the generated CA and the host named in the
certificate. Test directly against the server before
adding a proxy. A TCP health check cannot verify HTTP/3.
[HTTP/3 specification](https://www.rfc-editor.org/rfc/rfc9114.html)
