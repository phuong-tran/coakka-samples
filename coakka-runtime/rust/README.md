# Rust Runtime Samples

The Rust 3.2.1 source archive embeds Core 3.2. The
[3.2.1 guide](../../docs/coakka-runtime/previews/3.2.1/runtime-rust-candidate.md)
records the matching-host evidence and how to select the pinned archive.

The [basic application](basic/README.md) is a separate Cargo consumer, not the
package's smoke executable. It owns a local customer capability, sends a typed
request/reply and handles a typed route-miss deadletter. Core remains the source
of truth for defaults, effective configuration and causal runtime outcomes.

```text
caller -> target samples.customer.store -> Rust handler -> owned reply
```

Choose the target to name an application capability. Keep the same target in
route intent, handler registration and callers. HTTP can remain at a genuine
external/legacy boundary; an internal runtime target is not an HTTP URL.

The sample leaves execution, connection, queues and monitor policy to Core.
The normal defaults are AUTO/MULTIPLEX; the app reads accepted facts from Core
rather than reconstructing them from its submitted intent. No OS probe,
reflection, guessed fallback or separate native dependency installation is
needed. The current engine uses Core-owned delivery resources; response-pipe
handling belongs to the old generation and is not sample code to preserve.

Rust's `RuntimeHost` is an RAII owner. A callback returns promptly and holds no
strong reference to its host. A fallible app operation may use `?`: the owner
retires and joins its reader on exit. Keep the installed executable and its
`native/` resources/legal files together when relocating the app.

The archive contains five native images. The basic sample runner targets
macOS ARM64, Linux ARM64 and Windows x64. Source, application and broader
ecosystem release readiness are separate gates. Follow
[AI-Assisted Integration](../../docs/coakka-runtime/guides/ai-assisted-integration.md) before using
another feature; do not infer File/Stream/TLS APIs from a different connector
or mix the APIs of different package versions.
