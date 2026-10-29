# Rust sample candidate

This is a private source-candidate workflow, not a published crates.io version
or a release/support claim. Use the exact archive in
`artifacts/runtime-rust-candidate.json`, obtained through the trusted candidate
channel. The runner refuses different bytes; it does not download or silently
fall back to an older public package. Checksums establish exact-copy integrity,
not publisher authentication.

The sample uses Rust/Cargo 1.77.2 (edition 2021). Its selected private archive
bundles Core 3.0.0 for five targets: Mac ARM64, Linux ARM64/x64 and Windows
ARM64/x64. This sample runner remains qualified for Mac ARM64, Linux ARM64 and
Windows x64 only. More bundled files do not extend that sample execution scope.
Compiler target selection is a build/deployment choice, not a runtime capability
probe. The source archive and installed application are different artifacts.

## Build and run

Use a prepared matching-host compiler/linker, an existing offline Cargo cache
and Python 3.11 or later at build time. Select fresh absolute work on external
build storage; there is no default temporary build or dependency download.
The application itself needs neither Python, Cargo nor a separate Core install.

```sh
export COAKKA_RUST_ARCHIVE=/absolute/candidate/coakka-runtime-rs-3.0.0.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/build-storage/rust-basic-new
export COAKKA_RUST_CARGO=/absolute/toolchain/bin/cargo
export COAKKA_RUST_CARGO_HOME=/absolute/offline-cargo-cache
export COAKKA_RUST_TARGET=macos-aarch64
bash run.sh coakka-runtime rust basic
```

Use `linux-aarch64` on Pi. On Windows x64, run native Python in an x64 MSVC
developer environment and invoke `coakka-runtime/scripts/runtime-rust-candidate.py` directly:

```powershell
python -B coakka-runtime/scripts/runtime-rust-candidate.py --archive D:/candidate/coakka-runtime-rs-3.0.0.tar.gz --source D:/samples/coakka-runtime/rust/basic --work D:/build-storage/rust-basic-new --cargo D:/rust/bin/cargo.exe --cargo-home D:/offline-cargo-cache --target windows-x86_64
& D:/build-storage/rust-basic-new/installed/coakka-runtime-sample.exe
```

Python's options are build-only. All source, archive, work and cache paths must
have stable trusted, non-linked/non-reparse parents. Do not assemble resources
while an application is running. The runner is not a hostile-filesystem sandbox.

The runner validates the frozen archive before loading its exact build helper,
then delegates full bounded extraction and resource placement to that helper.
It builds this sample as a separate Cargo app with its checked-in lockfile,
offline/locked, release size optimization, thin LTO, one codegen unit, stripped
symbols, source/cache path remapping and Windows static CRT. The Windows build
selects Rust 1.77.2's bundled compatible linker so the final executable does not
retain the default platform linker's POGO debug payload. Missing tools refuse
before output; no post-link byte patch or metadata waiver is used. No native rebuild,
lock regeneration, runtime extraction or global native cache is performed.

On Mac the prepared Apple strip/sign tools finalize the copied application;
compiler build tools keep their local signatures while building. This local
ad-hoc application signature enables normal development execution; it is not
publisher authentication, notarization or a release signature. The bundled
Core image is copied byte-for-byte and is never re-signed by this sample.

Only `installed/`, the bounded build/platform-tool logs and `receipt.json`
remain in the fresh work root.
The entire installed directory may move together: the executable resolves
`native/<target>/<product image>` beside itself, not from source or current
working directory. Keep the copied legal files and native manifest with it.
An invalid present native override fails closed; it is not a recovery option.

On a catchable build/install failure, only the newly reserved build transaction
and incomplete installed tree are retired. Existing work is never overwritten.
Logs and a failed receipt remain; forced termination may leave an unadmitted
work directory, which must be inspected rather than reused. Explicitly delete
only that task's work after retaining required private evidence.

## Runtime ownership

The app submits one local route and omits advanced tuning. Core supplies the
accepted execution/connection/monitor/delivery facts; the connector does not
probe the OS or infer native state. Two-second requests retain typed terminal
errors, including the original target in a route-miss deadletter. Handler
construction returns an owned reply or no reply; no failure text is parsed and
no native outcome is fabricated.

`RuntimeHost` is an RAII owner. External Drop seals admission, joins its reader
and retires native attachments. Every `?` returns through that owner; the
callback does not capture its host. Do not introduce strong reference cycles
or block a reader callback. Drop has no fallible close return value: unexpected
Core stop diagnostics remain diagnostics, not a fabricated successful close.

Qualification results and remaining ecosystem/signing/soak gates are recorded
separately. This guide does not promote older public versions or infer targets
not present in the exact candidate.

Mac sample builds target macOS 13.0 during compilation and linking. A newer
development Mac therefore does not silently raise the application requirement.
The build checks the resulting application/adapter and bundled Core together;
an incompatible component refuses before the sample runs. These are application
packaging settings, not runtime tuning or a replacement for Core's accepted configuration.
