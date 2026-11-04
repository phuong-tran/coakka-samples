# CoAkka Runtime Inspect Technical Notes

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

Download [coakka-runtime-inspect 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-runtime-inspect/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) for the native explorer. See the
[archive checksums and verification](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/releases/2026-11-05-runtime-tools-3.2.1.md). The sample runner, Docker image, and browser recordings
describe the pinned 2.5.1 walkthrough.

## Route Try

When `serve --connect host:port` is provided, the browser Try Route panel uses
the same native request-driving shape as `coakka-client call` / `ask`.

The form supports route, payload, content type, message type, schema version,
timeout, custom headers, mode, and output mode. It also generates an equivalent
`coakka-client` command so browser and terminal usage teach the same runtime
contract.

Unsupported runtime request profiles are reported explicitly instead of being
hidden behind a fake HTTP fallback.
