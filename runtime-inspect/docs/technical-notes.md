trước# CoAkka Runtime Inspect Technical Notes

`coakka-runtime-inspect` sits above `coakka-runtime-core`.

It may own browser presentation, local UI state, route-try forms, response
rendering, and copy-as-CLI snippets. It must not own runtime topology truth,
service discovery, auth policy, mTLS policy, business schema validation, or
runtime queue semantics.

## Runtime Truth

Current inspect snapshots are runtime-owned but local-linked:

```text
inspect process
  -> creates ephemeral native runtime
  -> applies diagnostic route snapshot
  -> reads runtime C ABI snapshots
  -> renders JSON and browser panels
```

The payload explicitly reports:

```text
snapshot_source=local-linked-runtime
remote_runtime=false
```

That is intentional until a dedicated remote read/observe adapter exists.

## Published Archive Status

The current public inspect archive generation is `1.3.1+4ce41f19` for Linux
x86_64/ARM64 and `1.3.1+d7ab7fa` for macOS ARM64 and Windows x86_64/ARM64. The
sample runner verifies those archives through the same manifest/checksum
resolver used by the runtime-client lane.

The browser walkthrough GIF and MP4 were recorded on macOS from the published
macOS ARM64 inspect archive. The recording used two local native runtime
responders on `127.0.0.1:19091` and `127.0.0.1:19092`; it did not use Docker,
UTM, JVM, or a language connector.

## Route Try

When `serve --connect host:port` is provided, the browser Try Route panel uses
the same native request-driving shape as `coakka-client call` / `ask`.

The form supports route, payload, content type, message type, schema version,
timeout, custom headers, mode, and output mode. It also generates an equivalent
`coakka-client` command so browser and terminal usage teach the same runtime
contract.

Unsupported runtime request profiles are reported explicitly instead of being
hidden behind a fake HTTP fallback.
