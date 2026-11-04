# Zig Runtime Basic

This example consumes the **Zig 3.2.1 source package** with embedded Runtime Core 3.2.
Use Zig 0.16.0, Python 3.11 or later, and a matching C compiler.
Zig 0.16.0 application execution was checked on macOS ARM64, Raspberry Pi 5
(Linux ARM64), WSL2 (Linux x86-64), native Windows x86-64, and Windows ARM64
in UTM. The Windows runs used applications cross-built on macOS.

Run the following from the Samples checkout. Select the archive matching the
[Samples pin](../../../../coakka-runtime/artifacts/runtime-zig-candidate.json) and a fresh
absolute work path whose parent already exists.

~~~sh
export COAKKA_ZIG_ARCHIVE="/absolute/coakka-runtime-zig-3.2.1-source.tar.gz"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/existing-parent/zig-basic-new"
export COAKKA_ZIG_GLOBAL_CACHE_ROOT="/absolute/existing-zig-cache"
bash run.sh coakka-runtime zig basic
~~~

The runner checks the archive SHA-256 before unpacking, builds with the
bundled Core, and runs request/reply and route-miss checks. It removes build
staging and retains the installed application and an admission receipt under
the selected work path.
