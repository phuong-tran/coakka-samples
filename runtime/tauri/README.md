# Tauri Runtime Samples

This private branch is migrating to the exact Tauri source aggregate 2.5.3 over
native Core 2.5.1. It is not a public package/support claim.

- [Intent command](intent-command/README.md) is a standalone terminal-friendly
  Cargo consumer of the frozen candidate, with typed request/reply/deadletter.
- `desktop-intent` is still the legacy scaffold awaiting its own app-host
  async/admission/ACL/CSP/assets/shutdown migration and real-WebView evidence.
  Do not run its old download recipe as a candidate qualification.

Follow the [candidate guide](../../docs/runtime-tauri-candidate.md) for exact
identity, Rust 1.77.2, prepared offline tools and explicit build storage.

```text
WebView intent -> app-owned authorized async command -> dispatcher -> Core target
```

The WebView never owns native handles, transport/engine selection or Core
capability/default probes. The application owns UI permissions, bounded
blocking execution and joined shutdown; the connector projects typed values
and Core-issued outcomes. AUTO/MULTIPLEX remain Core's normal defaults.

Command-only evidence does not prove WebView scheduling, window destruction,
browser-process retirement, background progress or desktop performance. Use
[AI-Assisted Integration](../../docs/ai-assisted-integration.md) before another
feature and keep exact candidate APIs separate from older published packages.
