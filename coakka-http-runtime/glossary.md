# HTTP Runtime glossary and URL guide

Use this page with your language's
[integration guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md#languages).
The terms describe CoAkka HTTP Runtime, not CoAkka Runtime's separate
target/envelope messaging model.

## Contents

- [URL versus route pattern](#url-versus-route-pattern)
- [Route syntax and matching](#route-syntax-and-matching)
- [Path and query values](#path-and-query-values)
- [Building an inbound request](#building-an-inbound-request)
- [Building a response](#building-a-response)
- [Building an outbound request](#building-an-outbound-request)
- [Lifecycle and configuration terms](#lifecycle-and-configuration-terms)

## URL versus route pattern

For a handler registered as `GET /hello/{name}`, a client might send:

```text
http://127.0.0.1:8080/hello/Ada?title=hello&tag=one&tag=two
```

| Value | Example | Where it belongs |
| --- | --- | --- |
| Client URL | `http://127.0.0.1:8080/hello/Ada?title=hello` | curl, browser or HTTP client. |
| Listener | Address `127.0.0.1`, port `8080` | Service startup, not a route declaration. Port 0 asks the OS to assign a port; read the accepted port afterward. |
| HTTP method | `GET` or `POST` | Route registration and request. |
| Route pattern | `/hello/{name}` | Builder/route declaration; no scheme, host, query or fragment. |
| Request path | `/hello/Ada` | Core matches it against the route pattern. |
| Path parameter | Name `name`, encoded value `Ada` | Core-projected request parameters; not a query parameter. |
| Query | `title=hello&tag=one&tag=two` | Ordered request parameters; does not change the route key. |
| Header | `content-type: application/json` | Request/response metadata, separate from URL parameters. |
| Body | UTF-8 JSON or arbitrary binary bytes | Request/response payload; JSON codecs remain application-owned. |
| Fragment | `#section` | Browser-side navigation, not a server route or request target. |

Do not register `http://host/hello/{name}?title={title}`. Register
`/hello/{name}` for the intended method and read `title` from the request.
A route declaration does not contact the network or construct an outbound call.

## Route syntax and matching

| Pattern | Meaning |
| --- | --- |
| `/` | Exact root path. |
| `/health` | Exact static path. |
| `/hello/{name}` | One nonempty whole-segment capture. |
| `/orders/{orderId}/items/{itemId}` | Two captures, in path order. |
| `/orders/{id}/` | Trailing slash is part of this pattern. |

Capture names begin with an ASCII letter or underscore and continue with ASCII
letters, digits, underscores or hyphens. Names may not repeat within a pattern.
Use `{name}`, not Express-style `:name`, regex `(.*)`, `{id:int}`,
optional-segment syntax or a catch-all `*`. Those extensions are not the
route grammar described by this package.

`GET /hello/Ada` matches `GET /hello/{name}`; `/hello/` has no nonempty
name, and `/hello/Ada/Lovelace` has an extra segment. Method comparison is
case-sensitive. Declare the methods you need; do not assume a framework's
implicit method aliases or redirects.

Static segments win over captures at the earliest differing position:
`/users/me` takes precedence over `/users/{id}`.
Same-method patterns with the same structure, such as `/users/{id}` and
`/users/{name}`, are ambiguous and rejected. Do not rely on registration order
to resolve that ambiguity.

`/users` and `/users/` are distinct. Query parameters do not select another
route: `/users/7?view=small` and `/users/7?view=full` select the same
method/path route; the handler chooses the business behavior.

## Path and query values

Core parses and validates the request target; connectors expose its values.
**Encoded does not mean decoded text.** For example:

| Input | Projected meaning |
| --- | --- |
| `/items/a%2Fb` matching `/items/{id}` | Capture is `a%2Fb`; the encoded slash stays inside one segment. |
| `?tag=one&tag=two` | Two ordered entries, not one overwritten map value. |
| `?flag` | Key is present, value is absent. |
| `?flag=` | Key is present, value is the empty value. |
| No `flag` entry | Key itself is absent. |
| `?q=a+b` | Encoded value remains `a+b`; no automatic form-style plus-to-space conversion. |
| `?q=a%26b` | One value containing encoded `&`, not a new query entry. |

Static segments compare after percent decoding, but capture values remain
encoded. Segment boundaries are determined before decoding. Choose percent
decoding, UTF-8 handling and conversion to business types explicitly in the
application; do not decode a capture and then feed it back into path routing.
Converting bytes to a string alone is not percent decoding.

Repeated headers are also meaningful. Use the connector's ordered/all-values
view where duplicates matter instead of collapsing them into a single map.
Header-name lookup is case-insensitive. Do not reflect arbitrary incoming
headers or credentials back to clients.

## Building an inbound request

You do not create an inbound `Request` object in the server handler.
The client sends HTTP; Core parses it and the connector supplies the typed
request to your handler. From the sample repository root, start a language's
server, read its printed port, then:

```sh
PORT=12345 # replace with the actual bound port
curl --max-time 5 -i \
  -H 'content-type: application/json' \
  --data-binary '{"name":"Ada"}' \
  "http://127.0.0.1:$PORT/echo"
```

`--data-binary` selects POST here. The sample returns HTTP201 and the same
bytes. It is a byte echo, not a JSON parser or a promise that the response
content type is JSON. To expose a JSON endpoint, validate/decode the request
in app code and deliberately set the response content type.

For a query whose value needs escaping, use your client's URL encoder rather
than concatenating untrusted text. This native sample example is executable
against the C/C++ server:

```sh
curl --max-time 5 -i --get --data-urlencode 'tag=one & two' \
  "http://127.0.0.1:$PORT/items/7"
```

Buffered handlers read a bounded complete body. Stream handlers consume chunks
incrementally and observe trailers after EOF. Neither shape authorizes
unbounded application retention.

## Building a response

A buffered response consists of status, ordered headers and body bytes.
Use the selected language's response constructor/helper, return it from the
handler, and let Core perform HTTP framing. Native callbacks instead submit
their initialized response through the request API and check the result.

| Desired output | Application responsibility |
| --- | --- |
| Text | Encode text and use the connector's text helper. |
| JSON | Serialize with the language's JSON library and set `content-type: application/json`. A text helper alone is not a JSON codec. |
| Binary | Supply bytes and the appropriate media type. |
| Stream | Produce bounded chunks, respect writable pressure, and finish with optional trailers. |
| SSE | Produce typed SSE fields with the SSE helper; do not assemble HTTP chunk framing. |
| File | Return a confined file authority/path, not arbitrary request-supplied OS paths. |
| WebSocket | Use the upgrade/session response and callbacks, not an ordinary 200 response. |

Avoid hand-writing `Content-Length` or `Transfer-Encoding` as an attempt to
take over Core's framing. Check errors/refusals; do not submit a second response
after cancellation or an already accepted response. Operator diagnostics are
not automatically safe response bodies.

See the request/response section in each guide for exact language identifiers.

## Building an outbound request

Outbound HTTP is different from receiving a request. The sample first declares
logical target `sample.upstream` with a concrete loopback endpoint. A call then
submits the logical target, method, origin-form path/query, optional headers/body,
and a finite deadline.

```text
target declaration: sample.upstream -> 127.0.0.1:<upstream port>
call:              GET /source, target sample.upstream, deadline 3000 ms
result:            matching call identity + typed terminal + optional HTTP response
```

`/source` is a request path, not a route template: replace all variables and
encode their values before submitting. Do not put `{name}` or an arbitrary
full URL in a field whose contract is origin-form path/query.
Connection address, HTTP authority and TLS peer identity are distinct settings.

A successful submit means admitted work, not a received response. Consume the
matching terminal; distinguish a received HTTP404/500 from cancellation,
deadline or transport failure. The read wait and the call deadline are
different budgets. Cancellation is intent until a terminal confirms the outcome.
Application idempotency and retry budgets remain application policy.

## Lifecycle and configuration terms

| Term | Meaning |
| --- | --- |
| Core | Owner of HTTP transport, accepted configuration, bounds, route matching and causal outcomes. |
| Connector | Thin, idiomatic language projection with necessary type, ownership and memory-safety checks. |
| Host-inlined | App-host handlers use the connector in the same application process; this does not mean execution on the HTTP transport thread. |
| HTTP/1.1, HTTP/2, HTTP/3 | Wire protocols selected by listener configuration; a client must support and negotiate the selected protocol. |
| TLS / mTLS | Server-authenticated encrypted transport / transport additionally requiring a verified client identity. Business authorization still belongs to the app. |
| Buffered / streaming body | Complete bounded bytes / incrementally consumed or produced chunks with pressure and cancellation handling. |
| Trailer | A field arriving after body data; do not treat it as an initial header or read it before EOF. |
| SSE | Server-Sent Events: a server-to-client HTTP event stream with data and optional event type, ID and retry fields. |
| WebSocket | An upgraded bidirectional session with typed frame/session events, not an ordinary buffered response. |
| File authority | A bounded application-granted filesystem root for file responses, not unrestricted access to caller-supplied paths. |
| Builder / service | Construction intent / the live owner returned after accepted startup. |
| Effective configuration | Runtime-issued accepted settings, not a copy of builder input. |
| CPU AUTO / SINGLE | Startup placement intent; not a process-wide CPU-time quota. Loop tuning is internal to each connector. |
| Body/header/idle timeout | Budget for a specific transport phase, distinct from handler work and outbound call deadlines. |
| Route ID | Stable identity of a logical route. |
| Handler binding | Prepared application implementation selected by a route revision. |
| Structural generation | Version of the complete accepted route table. |
| Binding revision | Version used for a handler-only compare-and-apply change. |
| Snapshot | Coherent immutable runtime observation, not a reconstructed local declaration list. |
| Pressure | Bounded admission/resource refusal, not permission to queue indefinitely. |
| Monitor policy | Optional bounded collection; startup reservations constrain live changes. |
| Graceful shutdown | Stop admission and complete the owned drain/cleanup contract. A timeout alone is not completion. |
| Borrowed view / lease | Data valid only for the documented scope; release exactly once where required. |

Supported feature, example coverage, platform execution and performance
evidence are distinct. Consult the
[feature index](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/FEATURES.md)
before turning a recipe into a broader support claim.
