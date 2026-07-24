# Public Sample Lanes

This page is the quick map of runnable public samples. The root README keeps
the full product story, while each sample directory owns the local command and
expected output details.

## First Runs

| Goal | Command | What it proves |
| --- | --- | --- |
| Verify the published CLI lane | `bash run.sh runtime-client` | Resolves the public `coakka-client` archive, checks its SHA256, and runs the CLI diagnostics. |
| Run a browser-visible container path | `bash run.sh containers node-python` | Starts a Node.js web surface and Python store through the runtime path. |
| Run a same-process JVM runtime path | `bash run.sh runtime jvm basic` | Sends one typed request/reply through a JVM-owned runtime target. |
| Run a Bun runtime path | `bash run.sh runtime bun basic` | Uses the public Bun runtime package with no separate native install. |
| Run a desktop intent path | `bash run.sh runtime tauri intent-command` | Sends an intent-shaped request through the Rust-side command boundary. |
| Run a logger smoke | `bash run.sh logger node basic` | Writes one bounded logger record through a public logger package. |

## Runtime Lanes

| Lane | Status | First command | Notes |
| --- | --- | --- | --- |
| JVM | public | `bash run.sh runtime jvm basic` | Kotlin and Java samples cover basic request/reply and deadletter paths. |
| Python | public | `bash run.sh runtime python basic` | Includes basic, deadletter, and hot-reload samples. |
| Node.js | public | `bash run.sh runtime node basic` | Includes basic and deadletter samples. |
| Bun | public | `bash run.sh runtime bun basic` | Uses the published Bun package. |
| Electron | public | `bash run.sh runtime electron basic` | Renderer sends intent through preload/IPC; Electron main owns runtime execution. |
| Tauri | public source sample | `bash run.sh runtime tauri intent-command` | WebView JavaScript sends intent; Rust owns runtime execution. |
| Go | public | `bash run.sh runtime go basic` | Includes basic and deadletter samples. |
| C# | public | `bash run.sh runtime csharp basic` | Uses the public .NET package. |
| Rust | public | `bash run.sh runtime rust basic` | Uses the public Rust package shape. |
| Mojo | source sample | `bash run.sh runtime mojo basic` | Source connector package over the public native runtime. |
| Zig | source sample | `bash run.sh runtime zig basic` | Source connector package over the public native runtime. |
| Native C/C++ | public | `bash run.sh runtime native basic` | Uses the public C ABI archive. |

## Logger Lanes

| Lane | Status | First command | Notes |
| --- | --- | --- | --- |
| JVM | public | `bash run.sh logger jvm basic` | Kotlin and Java samples cover basic and pressure paths. |
| Python | public | `bash run.sh logger python basic` | Includes basic and pressure samples. |
| Node.js | public | `bash run.sh logger node basic` | Includes basic and pressure samples. |
| Bun | public | `bash run.sh logger bun basic` | Uses the published Bun logger package. |
| Electron | public | `bash run.sh logger electron basic` | Renderer sends log intent through preload/IPC; Electron main owns logging. |
| Tauri | public source sample | `bash run.sh logger tauri basic` | Proves the Rust command boundary used by a Tauri app. |
| Go | public | `bash run.sh logger go basic` | Includes basic and pressure samples. |
| C# | public | `bash run.sh logger csharp basic` | Uses the public .NET logger package. |
| Rust | public | `bash run.sh logger rust basic` | Uses the public Rust logger package shape. |
| Mojo | source sample | `bash run.sh logger mojo basic` | Source connector package over the public logger native package. |
| Zig | source sample | `bash run.sh logger zig basic` | Source connector package over the public logger native package. |
| Native C/C++ | public | `bash run.sh logger native basic` | Uses the public native logger archive. |

## Tool Lanes

| Lane | Status | First command | Notes |
| --- | --- | --- | --- |
| Runtime client | public | `bash run.sh runtime-client` | Native CLI diagnostics and request/reply command surface. |
| Runtime client Docker bundle | public | `bash run.sh runtime-client docker-bundle` | Runs the published Docker Linux bundle path. |
| Runtime client Docker Hub image | public sample image | `bash run.sh runtime-client dockerhub-demo` | Pulls the prebuilt public sample image. |
| Runtime inspect | public | `bash run.sh runtime-inspect check` | Verifies the published inspect archive path for the host. |
| Runtime inspect Docker wrapper | public sample wrapper | `bash run.sh runtime-inspect docker-smoke` | Runs the inspect sample wrapper path. |

## Scenario Lanes

| Lane | Status | First command | Notes |
| --- | --- | --- | --- |
| Node.js -> Python container | public Docker Hub images | `bash run.sh containers node-python` | Browser-visible cross-process runtime sample. |
| Customer CRUD scenarios | public | `bash run.sh scenarios check` | Framework and desktop-local workflow shapes. |
| Two-machine Linux | manual docs | `docs/two-machine-linux.md` | Manual setup path; live two-host capture is still pending. |
| Benchmark smoke | manual workflow | `python3 bench/run_smoke_load.py --profile runtime-native-pressure` | Regression evidence, not the primary product claim. |

## Artifact Rule

Public samples consume artifacts from
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish). The runner
uses a sibling `coakka-publish` checkout when available, then falls back to the
public raw GitHub artifact URL.

Every resolved artifact is checked against
`coakka-publish/artifacts/public-artifacts.tsv` before the sample unpacks or
installs it.

## Boundary Rule

Samples should show the public boundary a user owns:

- app code sends work to a runtime target or logger surface
- desktop frontend code sends intent to the native host boundary
- the host process owns runtime execution, logging, and replies
- deployment policy, public HTTP/gRPC edges, auth, and service discovery stay
  outside these samples unless the sample is specifically about that concern

Do not make a sample depend on a private implementation checkout. If a sample
needs unpublished behavior, keep it out of the public lane until the matching
artifact exists in `coakka-publish`.
