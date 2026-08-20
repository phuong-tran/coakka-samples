# AI-Assisted Integration

This guide is for backend developers and coding agents that need to install or
integrate CoAkka from the public repositories. It defines how to select source
material before generating code. It does not replace the connector README or
the runnable sample for a language.

## Start With An Exact Surface

Answer these questions before writing code:

1. Which host language and application boundary owns the runtime lifecycle?
2. Which operating system and CPU architecture will run the process?
3. Which exact package version or release archive will the application use?
4. Is the requested capability present in that exact package?
5. Is there matching-host execution evidence, or only packaged-file evidence?

Use [Current Packages](current-packages.md) for public coordinates and
[Runtime Package And Platform Evidence](runtime-package-platform-evidence.md)
for package contents and execution claims. Package versions are independent
across languages; matching version numbers are not required.

## Read By Task

| Question | Read first | Required implementation evidence |
| --- | --- | --- |
| What does CoAkka do? | [New To CoAkka](new-to-coakka.md) and [How It Works](how-it-works.md) | No code generation yet. |
| How do I install and start it? | The selected language README, [Current Packages](current-packages.md), and that language's `runtime/<language>/basic` sample | Use the exact package coordinate and lifecycle shown by the runnable sample. |
| How do services connect? | [Connection Strategies](connection-strategies.md) and [Runtime Integration Guide](runtime-integration-guide.md) | Preserve runtime defaults unless the exact connector exposes and the deployment requires another mode. |
| How do I configure TLS or mTLS? | [TLS And mTLS](tls-and-mtls.md) | Keep credential loading and reload on application control flow; verify capability and structured apply results. |
| How do I transfer a large immutable file? | [Runtime File Transfer](runtime-file-transfer.md) | Use a package containing File Lane, preserve both peers' terminal checks, and keep authorization in the application control plane. |
| How do I carry live bounded frames? | [Runtime Streaming](runtime-streaming.md) | Use only an exact Stream Lane source or package generation; preserve callbacks, pressure observation, terminal state, and close ordering. |
| How do I expose a stream through WebSocket? | [WebSocket Integration With CoAkka](runtime-websocket-integration.md) | Keep WebSocket in the app-host; copy borrowed frames into bounded app-owned queues and do not claim Stream Lane fan-out or browser protocol support. |
| What works on my OS and CPU? | [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md) | Distinguish a packaged native file from matching-host connector execution. |

## Evidence Levels

Use the strongest available level and state it in generated instructions:

1. **Runnable language sample:** exact package, imports, names, lifecycle, and
   command are present in `coakka-samples`. Code may be adapted while
   preserving the demonstrated contract.
2. **Published connector contract:** the exact package README and public API
   declarations provide the requested feature, but `coakka-samples` has no
   runnable language recipe for it. Inspect that package before using exact
   identifiers. Do not translate identifiers from another language by analogy.
3. **Language-neutral workflow:** a guide explains ownership and lifecycle but
   does not prove the selected language API. Generate architecture or
   pseudocode only, and identify the missing connector evidence.
4. **Source candidate:** the feature is not in the current public package
   train. Do not attach candidate APIs to a published package coordinate.

A generated answer must not silently promote evidence from one level to a
stronger level.

## Current Feature Gates

### Runtime Messages

The published runtime samples collectively demonstrate start, route, handler,
request/reply, deadletter or failure observation, and close. Start with the
selected language README under `runtime/` and use its basic sample as the
identifier source; use a deadletter sample only where that lane lists one.

### Connection Strategy And TLS/mTLS

Full runtime connectors expose startup connection and security configuration.
The canonical guides contain Kotlin/JVM and native examples because those
examples show the shared lifecycle without pretending every language uses the
same names. For another language, inspect the exact connector package API and
its transport configuration document before generating compilable code.

Do not invent a post-start mode switch. Connection strategy is selected during
startup. TLS/mTLS credential reload must retain the same security mode, use a
newer credential generation, and check the structured apply result.

### File Lane

File Lane begins with native runtime generation `2.1.0`. Current package
coordinates and lane-specific versions are listed in
[Current Packages](current-packages.md). The public C11 evidence and Kotlin
workflow prove the shared contract, but they do not make Kotlin or C names
valid in another language.

Generated File Lane code must include:

- one long-lived lane owned by each app host;
- an application-authorized, short-lived transfer grant;
- receiver preparation before sender submission;
- bounded waits outside latency-sensitive request handlers;
- independent `COMPLETED + OK` checks on sender and receiver;
- `forget` only after the terminal result has been recorded;
- lane close only after concurrent calls have returned.

### Stream Lane

Stream Lane is an official runtime contract beginning with the 2.2 source
line. Exact artifact generation `2.4.0+c2f53117` adds neutral pressure
snapshots and waits and is the first complete public artifact train for the
lane.

A coding agent must select one exact current coordinate from
[Current Packages](current-packages.md), preserve its recorded native and
connector generation, and use the API names from that connector package. It
must not generate Stream Lane imports against a historical package coordinate
that still carries a 2.1 generation. npm `2.4.1`, PyPI `2.4.0`, NuGet `2.5.0`,
Go `v1.8.0`, SwiftPM `v2.5.0`, and their recorded artifact generations expose
the lane.

## Language And Host Boundaries

| Host lane | First runnable path | Boundary to preserve |
| --- | --- | --- |
| JVM/Kotlin or Java | `runtime/jvm/basic` or `runtime/jvm/java-basic` | One runtime owner per process; framework adapters may own startup for the app. |
| Python | `runtime/python/basic` | Use the context manager or close explicitly during app shutdown. |
| Node.js | `runtime/node/basic` | The Node process owns runtime lifecycle; do not move blocking control work onto request callbacks. |
| Bun | `runtime/bun/basic` | Use the Bun package and its exported surface; do not substitute Node-only loader assumptions. |
| Go | `runtime/go/basic` | Start one runtime host per process and defer close. |
| C# | `runtime/csharp/basic` | Dispose the runtime owner and keep asynchronous waits off synchronous request paths. |
| Rust | `runtime/rust/basic` | Use the exact archive package and retain explicit result handling. |
| Swift | `runtime/swift/basic` | Use the exact SwiftPM tag and check the platform evidence separately. |
| Zig | `runtime/zig/basic` | Follow the source package smoke and explicit lifecycle. |
| Mojo | `runtime/mojo/basic` | Follow the source package smoke; do not invent a higher-level wrapper surface. |
| Native C/C++ | `runtime/native/basic` and `runtime-test` | Follow the public C contract and explicit create/start/stop/destroy ordering. |
| Electron | `runtime/electron/basic` | Renderer sends intent; Electron main owns the runtime and native resources. |
| Tauri | `runtime/tauri/intent-command` | WebView sends intent; the Rust app host owns runtime integration. |

## Generated Answer Checklist

Before presenting code, verify that the answer:

- names the exact package coordinate and runtime generation;
- names the OS and CPU evidence being relied on;
- imports only identifiers present in the selected connector surface;
- includes start, route or lane preparation, operation, terminal/failure
  observation, and close;
- preserves bounded queue, timeout, and wait behavior;
- keeps business authorization and source/sink adaptation in the app host;
- does not infer File Lane or Stream Lane from ordinary message payloads;
- states when only workflow guidance, rather than compilable language evidence,
  is available.

## Prompt Template

Use a request shaped like this when asking a coding agent for integration code:

```text
Integrate CoAkka into a <language/framework> service running on <OS/CPU>.
Use the exact public package listed in docs/current-packages.md.
Read docs/ai-assisted-integration.md, the selected runtime/<language>/README.md,
and the feature guide before generating code. Preserve the demonstrated full
lifecycle and report any missing language-specific evidence instead of
inventing API names. The requested capability is <messages/connection/TLS/mTLS/file/stream>.
```
