# Tauri Runtime Samples

Both samples use the Tauri Intents 3.2.1 aggregate with Core 3.2.
The command and WebView evidence for this aggregate covers macOS ARM64.

- [Intent command](intent-command/README.md) exercises typed request/reply and
  deadletter from the terminal.
- [Desktop intent](desktop-intent/README.md) runs the app-owned WebView
  command, admission policy and joined shutdown.

The [3.2.1 guide](../../docs/coakka-runtime/previews/3.2.1/runtime-tauri-candidate.md)
records the archive pin and toolchain scope.

```text
WebView intent -> app-owned authorized async command -> dispatcher -> Core target
```

The WebView never owns native handles, transport/engine selection or Core
capability/default probes. The application owns UI permissions, bounded
blocking execution and joined shutdown; the connector projects typed values
and Core-issued outcomes. AUTO/MULTIPLEX remain Core's normal defaults.

Command-only evidence does not prove WebView scheduling, window destruction,
browser-process retirement, background progress or desktop performance. Use
[AI-Assisted Integration](../../docs/coakka-runtime/guides/ai-assisted-integration.md) before another
feature and keep the selected package version consistent across the application.
