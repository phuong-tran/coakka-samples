# Mojo Private Candidate Sample

This workflow uses the unpublished 3.0.0-source source package and the
exact Core build named in `artifacts/runtime-mojo-candidate.json`. Obtain
the archive through the trusted private candidate channel. The runner verifies
the complete archive before unpacking it; it does not download another version.
Current published package coordinates remain unchanged.

Use **Mojo 1.0.0b2.dev2026052115**, Python 3.11 or later, and a matching C compiler.
These are build tools. Select existing absolute tool/cache directories and a
fresh absolute work path whose parent already exists. On the development Mac,
builds and caches belong on mounted external APFS storage.

```sh
export COAKKA_MOJO_ARCHIVE="/absolute/candidate/coakka-runtime-mojo-3.0.0-source.tar.gz"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/build-storage/mojo-basic-new"
bash run.sh coakka-runtime mojo basic
```

The work path must be new. The sample admits only the fixed catalog identity,
builds from that package, installs its exact native file with the application
and runs the complete request/reply example. Existing work and native-library
overrides are refused. There is no source-project or older public-package
fallback. Checksums establish exact-copy integrity; obtain the archive from a
trusted publisher.

Core chooses CPU AUTO, MULTIPLEXED connections, queue defaults and monitor OFF.
The sample reads accepted settings instead of guessing them from input.
CPU selection can differ by platform; the candidate's native files alone do not
prove that CPU placement was applied. See
[Runtime Monitoring](../../docs/runtime-monitoring.md).

This lane is a small Mojo FFI smoke example around the packaged native adapter.
It does not claim a complete higher-level Mojo application API or a language
performance result. The native adapter retains its request owners for one
runtime lifetime, copies any returned data before releasing native ownership
and closes those owners before the runtime.

After a completed run, the launcher removes its extracted source/build staging
and retains the installed application and admission receipt. Remove only that
owned work path after keeping any evidence you need. An interrupted process or
power loss can leave incomplete work; never treat such a directory as a valid
previous installation.

The package contains five native Core images. This is a file inventory, not a
claim of Mojo compiler or application execution on every listed OS.
Matching compiler/OS execution and remaining release gates are recorded in the
candidate audit. No registry release, public tag, signing or support promotion
occurs in this workflow.

Mac sample builds target macOS 13.0 during compilation and linking. A newer
development Mac therefore does not silently raise the application requirement.
The build checks the resulting application/adapter and bundled Core together;
an incompatible component refuses before the sample runs. These are application
packaging settings, not runtime tuning or a replacement for Core's accepted configuration.
