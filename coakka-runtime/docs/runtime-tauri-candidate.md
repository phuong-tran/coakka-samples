# Tauri sample candidate

These are separate private command and desktop sample workflows. There is no
publication, signing or support promotion. The terminal command does not start
a WebView; desktop qualification belongs to the actual app-host boundary.
Use only the exact archive listed in `artifacts/runtime-tauri-candidate.json`
from the trusted candidate channel. Archive generation 2.5.3 contains Rust
crate 2.5.3 and intent crate 0.1.0; that latter Cargo coordinate is not a public
registry selection. Do not substitute the obsolete public 2.5.2 aggregate.

## Command build

Use Rust/Cargo 1.77.2, the matching native linker environment, Python 3.11 or
later at build time, and an existing offline Cargo cache. Select fresh absolute
work on external build storage. There is no default temporary build, package
download, lock regeneration or native rebuild.

```sh
export COAKKA_TAURI_ARCHIVE=/absolute/candidate/coakka-runtime-tauri-intents-2.5.3-source.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/build-storage/tauri-command-new
export COAKKA_RUST_CARGO=/absolute/toolchain/bin/cargo
export COAKKA_RUST_CARGO_HOME=/absolute/offline-cargo-cache
export COAKKA_RUST_TARGET=macos-aarch64
bash run.sh coakka-runtime tauri intent-command
```

Select `linux-aarch64` on Pi. For Windows x64 use native Python in a prepared
x64 compiler environment:

```powershell
python -B coakka-runtime/scripts/runtime-tauri-candidate.py --archive D:/candidate/coakka-runtime-tauri-intents-2.5.3-source.tar.gz --source D:/samples/runtime/tauri/intent-command --work D:/build-storage/tauri-command-new --cargo D:/rust/bin/cargo.exe --cargo-home D:/offline-cargo-cache --target windows-x86_64
& D:/build-storage/tauri-command-new/installed/coakka-sample-tauri-intent-command.exe
```

Only Mac ARM64, Linux ARM64 and Windows x64 are bundled. The SDK's Rust floor
is independent of a desktop application's separately pinned framework/compiler
graph. A command test is not browser or desktop-minimum evidence.

## Deployment and failure law

The Rust and Tauri command entrypoints share one cold Cargo transaction owner.
Fixed artifact profiles select only archive identity, helper location, output
name and nested package layout. They do not contain Core defaults or runtime
policy. The exact archive is admitted before its build helper is loaded; that
existing package helper owns full bounded extraction and native/legal resource
placement. Checksums are integrity, not publisher authentication.

Keep trusted stable non-linked/non-reparse parents. The fresh work reservation
is exclusive; existing work and input/cache overlap refuse. On catchable failure
the build tree and incomplete install retire, with failed receipt/log retained.
A forced termination may leave unadmitted work for inspection. The compiler
child retains its 90-second bound. This is not hostile-filesystem isolation.

Cargo runs offline/locked with size optimization, thin LTO, one codegen unit,
stripped symbols and path remapping. Windows uses static CRT and the compatible
linker bundled with the selected minimum toolchain, without debug-metadata
patching or a release-scanner waiver. Eleven registry dependencies keep the
exact package lock versions/checksums. No helper executes in the installed app.

Move the complete `installed/` directory, including `native/<target>/`, its
native manifest and legal files. The app needs neither Python/Cargo/source
trees nor a separate Core dependency install. Native resolution is executable-
owned; a present invalid override refuses, without fallback or global cache.

## Intent ownership

The sample owns one local route and two explicit two-second intents. It omits
queue/CPU/connection/monitor tuning and reads actual accepted facts from Core.
It checks the typed original Rust deadletter cause, not message text, then
serializes the adapter's causal JSON view without fabricating a fallback.
The original target and wide integer strings survive the boundary.

The dispatcher is an RAII owner. Every `?` and app unwind retires its last
clone, joins the Rust reader and releases callback captures. Handlers do not
capture the host. `IntentEnvelope.headers` is reserved app-host metadata in
this generation, not propagated tracing/idempotency context.

In a real GUI, synchronous waiting belongs on a bounded blocking executor,
not the UI thread. Application admission/authorization, accepted-work joining,
browser retirement, local assets/CSP and window/background policy require
their own real-WebView qualification. The desktop sample uses async commands
and a bounded app owner; do not transplant the synchronous CLI call into a UI
command.

## Desktop build and application ownership

In Samples use `coakka-runtime/tauri/desktop-intent`. It has a separate locked
Tauri 2.11.5/build 2.6.3 application graph and requires stable Rust 1.92 or newer,
including its real Clippy component, plus the native platform's prepared
GUI/linker build tools. This does not raise the independent Rust SDK/command
floor of 1.77.2. No Tauri CLI or runtime package download is part of the recipe.

Select the same exact aggregate, explicit offline Cargo cache, existing target
cache and fresh absolute work. The desktop producer reuses the shared bounded
archive/path/child controls and admitted package helper; it owns only desktop
source projection, compiler/release checks, normal app layout and cleanup. It
never rebuilds Core or discovers runtime capabilities. Build/Clippy/library-test
children each retain a 300-second bound. Source copies get destination-host
timestamps, not earlier cross-host archive timestamps; dependency caches remain
caller-owned and are not deleted. Failed cleanup is a failed transaction with
a retained receipt, not successful promotion.

Build portable Linux desktop artifacts on the prepared ARM64 Debian 12/
GLIBC 2.36 environment and verify the final version requirements are <=2.36.
A newer host can raise the app's libc requirement through link-time versioning
even for weak standard-library symbols. Do not exempt those references from
the gate. This app build baseline never selects or rebuilds Core. Matching
target execution and the normal OS GUI stack are separate requirements.

The Windows release app target explicitly disables CodeView at link time and
retains its exact address map only in the private build cache. The map is not
part of `installed/`; keep it privately when diagnosing that exact build.
No binary metadata patch or scanner exception is used.

The installed Mac output is an ordinary `.app`; Linux/Windows outputs are
native executables with adjacent `native/<target>/` resources and legal files.
Move the complete output, not only the executable. Python, Cargo, extracted
SDK sources and the build tree are not installed runtime dependencies. Private
candidate apps are unsigned; do not disable browser/OS protection to run them.

The app owns command ACL, local CSP/assets/navigation, at most eight accepted
blocking asks and orderly window/browser retirement. Core remains authoritative
for AUTO/MULTIPLEX and accepted effective settings. Browser storage is an app
resource, not a native cache or engine/capability probe. No default hidden-window
JavaScript progress, whole-browser leak-freedom or desktop-throughput guarantee
is implied by this sample.
