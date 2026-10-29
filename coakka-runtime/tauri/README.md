# Tauri Runtime Samples

This private branch is migrating to the exact Tauri source aggregate 3.0.0 over
native Core 3.0.0. It is not a public package/support claim.

- [Intent command](intent-command/README.md) is a standalone terminal-friendly
  Cargo consumer of the frozen candidate, with typed request/reply/deadletter.
- [Desktop intent](desktop-intent/README.md) uses the same candidate through
  an app-owned authorized async command, bounded admission, local CSP/assets,
  joined shutdown and a normal desktop application layout.

Follow the [candidate guide](../docs/runtime-tauri-candidate.md) for exact
identity, prepared offline tools and explicit build storage. Command/SDK Rust
1.77.2 and desktop app Rust 1.92 are separate build baselines.

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
