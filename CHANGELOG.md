# Changelog

This changelog summarizes the public runnable sample surface. Artifact release
details live in
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish).

## 2026-08-02

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
