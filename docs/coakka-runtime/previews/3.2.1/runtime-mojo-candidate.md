# Mojo Runtime Basic Preview

This example consumes the **Mojo 3.2.1 preview source package** with embedded Runtime Core 3.2.
Use Mojo 1.0.0b2.dev2026052115, Python 3.11 or later, and a matching C compiler.
Mojo preview execution was checked on macOS ARM64 and Raspberry Pi 5
(Linux ARM64). The package includes Core images for Linux x86-64 and Windows,
but Mojo execution is unqualified on those targets.

Download the archive and checksum from the [3.2.1 release record](../../releases/2026-11-05-zig-mojo-runtime-3.2.1-source.md).
Run the following from the Samples checkout. Select the archive matching the
[Samples pin](../../../../coakka-runtime/artifacts/runtime-mojo-candidate.json) and a fresh
absolute work path whose parent already exists.

On macOS use the SDK selected by Xcode for the preview build:

~~~sh
export SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
export MACOSX_DEPLOYMENT_TARGET=13.0
~~~

~~~sh
export COAKKA_MOJO_ARCHIVE="/absolute/coakka-runtime-mojo-3.2.1-source.tar.gz"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/existing-parent/mojo-basic-new"
bash run.sh coakka-runtime mojo basic
~~~

The runner checks the archive SHA-256 before unpacking, builds with the
bundled Core, and runs request/reply and route-miss checks. It removes build
staging and retains the installed application and an admission receipt under
the selected work path.

This preview uses a Mojo executable and the packaged C boundary. It does not
provide a stable high-level Mojo API.
