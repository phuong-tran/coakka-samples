# Changelog

This rolling changelog summarizes changes to the public runnable sample
surface. It is not a repository release ledger. Artifact versions, manifests,
and checksums live in the versioned
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish) warehouse.

## 2026-08-12

- Advanced Python samples to registry-published
  `coakka-v2-connector==2.4.0` over native generation `2.4.0+c2f53117`.
  Disposable installs pass request/reply, matched deadletter, route hot reload,
  and local desktop CRUD; local routes use embedded delivery without a TCP
  listener.
- Advanced C# samples to registry-published `CoAkka.Runtime==2.4.0` over
  native generation `2.4.0+c2f53117`. A clean NuGet restore loads the package
  and passes request/reply plus matched deadletter; the Spring Boot-to-C#
  scenario completes live CRUD with explicit network-node listeners.
- Advanced Node.js, Bun, and Electron samples to registry-published npm runtime
  `2.4.0` over native generation `2.4.0+c2f53117`.

## 2026-08-10

- Advanced Python samples to registry-published
  `coakka-v2-connector==2.3.0` over native generation `2.3.0+a83ab412`.
  Clean request/reply, matched deadletter, hot reload, and local desktop CRUD
  pass; the package exposes File Lane and Stream Lane.
- Advanced the C# sample to registry-published `CoAkka.Runtime==2.3.0` over
  native generation `2.3.0+a83ab412`. The repository-signed package preserves
  every staged entry, clean request/reply/deadletter passes, and the package
  exposes File Lane and Stream Lane.
- Advanced Node.js, Bun, and Electron samples to registry-published npm runtime
  `2.3.0` over native generation `2.3.0+a83ab412`. Clean package-manager
  smokes cover Node.js and Bun request/reply, Node.js deadletter, and Electron
  main-process intent. The npm connectors now expose File Lane and Stream Lane.

## 2026-08-09

- Advanced Go and Swift samples to documentation-integrity patches `v1.5.1`
  and `v2.1.1`. Both keep native generation `2.1.0+60ddf70d`, use the
  canonical public file-lane contract, and pass clean consumer request/reply.
- Advanced Python samples to registry-published
  `coakka-v2-connector==2.1.0`; the clean consumer loads native
  `2.1.0+60ddf70d` and completes request/reply.
- Advanced Node.js, Bun, and Electron samples to npm runtime patch `2.1.1`,
  whose README links to the canonical public file-lane contract.
- Advanced C# samples to registry-published `CoAkka.Runtime==2.1.0`; the clean
  consumer loads native `2.1.0+60ddf70d` and passes request/reply/deadletter.
- Advanced the native, JVM/Maven, connector-archive, Go, Swift, coakka-client,
  and coakka-runtime-inspect samples to runtime `2.1.0+60ddf70d`. The Go module
  tag is `v1.5.1` because its established module path has no `/v2` suffix;
  SwiftPM uses `v2.1.1`.
- Added the public C11 file-lane runtime test and detailed file-transfer guide.
  The exact `9 MiB + 731 byte` archive-driven transfer passes with matching
  SHA-256 and durable receiver completion on macOS ARM64, Linux ARM64/x86-64,
  and Windows x86-64 under Windows 11 ARM64 emulation.
- Advanced npm Node.js, Bun, and Electron samples to registry-published
  `2.1.0`; clean Node.js and Bun installs complete native request/reply and
  Electron resolves its exact Node connector dependency.

## 2026-08-08

- Advanced all current runtime samples to the corrective five-platform train:
  native/JVM/source artifacts `1.4.1+9e02a51d`, npm and PyPI `1.4.6`, NuGet
  `1.4.7`, and Go/Swift `v1.4.1`. Native, JVM, Node, Bun, Python, Go, Swift,
  C#, Rust, Tauri, Zig, and Mojo consumer smokes load runtime `1.4.1` on macOS
  ARM64; immutable evidence-runner and Docker-demo lanes retain their own
  independently released versions.
- Added stale-pin checks for the superseded runtime coordinates so sample CI
  rejects future current-version drift.

## 2026-08-03

- Removed the hosted ASan/UBSan lane. Static-analysis and sanitizer controls
  remain in `runtime-test/` for explicit local or evaluator runs; normal sample
  CI keeps only bounded non-sanitized profiles.
- Added configurable public C11 `race` and route-snapshot `hot-reload` modes.
  They cover multi-producer terminal accounting, submit-versus-stop
  convergence, independent runtime lifecycle contention, per-generation
  snapshot observation, and explicit sanitizer scope. Regular CI uses small
  profiles; the same harness remains scalable for external runtime evaluation.
  Windows source builds derive the consumer import library from the checked-in
  public export definition without changing the published runtime DLL.
- Clarified that TLS/mTLS and connection strategies are available through full
  host-language runtime connectors, added Kotlin/JVM startup examples, and
  kept C/C++ snippets explicitly scoped to native and connector-level use.
  Root README and Q&A entrypoints now link directly to those canonical guides,
  and the edge/IoT guide identifies TLS/mTLS for secured network boundaries.
- Advanced the C# runtime samples to registry-verified
  `CoAkka.Runtime==1.4.6`, which fixes the NuGet gallery logo and public C#
  transport documentation link while retaining native runtime generation
  `1.4.0+2cee86bf` for macOS ARM64, Linux ARM64, and Windows x86-64.
- Advanced the Python runtime samples to registry-verified
  `coakka-v2-connector==1.4.5`, which embeds native runtime generation
  `1.4.0+2cee86bf`. Basic, deadletter, hot-reload, and desktop-local samples
  continue to install from PyPI in disposable environments.

## 2026-08-02

- Aligned public artifact pins and runnable archive consumers with runtime
  `1.4.0+2cee86bf`; macOS ARM64 tools select their 1.4.0 bundles while other
  tool platforms retain explicitly reported compatible generations.
- Moved the primary Linux public-surface and sanitizer evidence to Linux ARM64,
  the Linux platform included in the exact 1.4.0 native matrix. The sanitizer
  lane now executes both workload and four-mode connection-strategy contracts.
- Added immutable SHA-256 verification for the Linux x86-64 native compatibility
  package and Windows compatibility evidence runner.
- Refreshed Rust, Tauri, Mojo, and Zig archive samples to the 1.4.0 connector
  generation, including Zig's structured startup result and platform loader
  bridge.
- Promoted the auditable native public-ABI harness to root-level
  `runtime-test/`, kept the historical path as a compatibility wrapper, and
  made correctness and connection-strategy evidence visible before optional
  benchmark tooling.
- Added a Linux CI lane that runs Clang static analysis and the consumer
  harness with ASan/UBSan while preserving Windows, macOS, and Linux
  portability coverage.
- Added the canonical CoAkka guardian logo and brand guide, with shared assets
  synchronized from the runtime documentation source.
- Added canonical ecosystem, connection-strategy, TLS/mTLS, support, and
  troubleshooting guides.
- Made the multi-language and multi-platform ecosystem, verified compatibility
  boundary, and contact paths visible from the root README.
- Kept examples free of unverified benchmark claims.
- Clarified that Windows, macOS, and Linux remain distribution targets while
  bundled bytes, source compilation, and end-to-end execution are reported as
  separate evidence.
- Added strict public C11 connection-strategy evidence for all four modes,
  atomic rejection/rollback, complete runtime lifecycle, and post-start
  immutability, plus Clang static-analysis and consumer ASan/UBSan entrypoints.
- Documented connected `coakka-client runtime-info` snapshots and inspect
  transport diagnostics, including capability truth, connection-policy
  provenance, and non-secret TLS/mTLS state.

## 2026-07-31

- Extended the native public-ABI evidence harness across Linux, macOS, and
  Windows with platform-specific runners and the same JSON/invariant contract.
- Made controlled Linux the preferred deployment-oriented measurement host;
  Docker, CI, UTM, and other VM runs remain portability gates.

## 2026-07-25

- Pointed native `coakka-client`, `coakka-runtime-inspect`, `coakka-client`
  Docker bundle, and Docker Hub sample lanes at the `1.3.1+0da8c2d9`
  stop-backpressure hotfix artifacts/images.
- Refreshed artifact manifest checksum pins used by container rebuild docs and
  sample scripts.

## 2026-07-24

- Documented the public runnable sample surface in a root changelog.
- Added a public sample-lanes map and refreshed root sample tables for
  Bun, Tauri, and Electron logger lanes.
- Moved JavaScript runtime/logger samples to the published npm packages.
- Added a first-reader `New To CoAkka` entrypoint that explains the runtime,
  logger, package install path, and the relationship between `coakka-samples`
  and `coakka-publish`.

## 2026-07-23

- Added Runtime Bun samples that consume the public Bun runtime connector
  package.
- Added Runtime Tauri samples that send application intent to the native host
  boundary.
- Added Runtime Electron samples, including basic and desktop-intent paths.
- Added Logger Bun, Tauri, and Electron sample lanes.
- Added or refreshed walkthrough recordings and GIFs for runtime language
  lanes.
- Expanded desktop documentation with short before/after framing for the
  intent boundary.

## 2026-07-20

- Added `coakka-runtime-inspect` sample coverage across Linux, macOS, and
  Windows archive lanes.
- Added the runtime inspect browser walkthrough.
- Added the runtime inspect Docker wrapper and Docker Hub sample image path.
- Kept native dependency checks generic for inspect sample artifacts.

## 2026-07-19

- Added the `coakka-runtime-inspect` lane.
- Added runtime-client Docker Hub and Docker walkthrough sample paths.
- Embedded the runtime-client walkthrough GIF and clarified the public runtime
  client onboarding path.
- Pointed samples at public GitHub Release assets and manifest-backed artifact
  resolution.

## 2026-07-18

- Promoted runtime samples to the public `1.3.1` artifact train.
- Added runtime-client sample runners, smoke coverage, guide docs, direct
  download links, and automation recording.
- Made `runtime-client` the terminal-first sample path.
- Clarified sample repository boundaries and the public artifact repository
  relationship.

## 2026-07-06

- Promoted runtime samples to the `1.2.1` train.
- Promoted logger samples to the `1.2.1` train.
- Promoted Windows-parity runtime and logger sample coverage.
- Added public Q&A coverage for runtime boundaries, service-mesh boundaries,
  Dapr comparisons, and adoption posture.
- Added community repository templates and contribution/support entrypoints.

## 2026-07-05

- Refreshed samples and containers for updated runtime trains.
- Strengthened logger docs and public Q&A.
- Refreshed container screenshots and publish commands.

## 2026-05

- Opened the public runnable sample surface.
- Added artifact pin and checksum verification against the publish manifest.
- Added runtime native, JVM, Python, Node.js, Go, C#, Rust, Mojo, Zig, and
  native C/C++ sample lanes.
- Added logger JVM, Python, Node.js, Go, C#, Rust, Mojo, Zig, and native sample
  lanes.
- Added container samples, framework samples, cluster routing docs, runtime
  message/routing docs, and benchmark guardrail documentation.
