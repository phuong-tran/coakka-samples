# Private Swift runtime candidate sample

This sample uses exactly the private `0.0.0-p453.1` source package and bundled
Core2.5.1+f6b61768/ABI1, not public2.5.2/2.5.3. The trusted
`artifacts/runtime-swift-candidate.json` pins all42 package files and the
qualified deployment copies of normal language runtime libraries. No separately
installed Core/provider library or native-path override is needed. Targets are
macOS ARM64, Linux ARM64 and native Windows x64; package presence is not a wider
platform/minimum-OS/signing or publication claim.

SwiftPM manifest/language floor remains6.0. Qualification profiles are Mac6.2
and Linux/Windows6.0.3, not proof that Mac6.0/minimum OS has executed. Use a
coherent supported compiler/SDK profile. The consumer's build is ordinary
SwiftPM over a local admitted package; WMO/Osize/no-reflection/link stripping
apply to the app and dynamic Swift module. Core native bytes are not rebuilt.

## Build and run

Choose existing absolute candidate, work and cache roots. Large development-Mac
staging/cache trees belong on mounted external APFS storage. The candidate root
contains exactly the42 catalog members, with no Git/build/cache directories.
Keep trusted catalog/toolchain/input/cache trees stable during the build.

```sh
export COAKKA_SWIFT_CANDIDATE_HOME="/absolute/package-p453.1"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_SWIFT_CACHE_ROOT="/absolute/swift-cache"
# Linux: an admitted Swift6.0.3 deployment directory containing lib/.
export COAKKA_SWIFT_LANGUAGE_HOME="/absolute/swift-deployment"
bash runtime/swift/basic/run.sh
```

The optional candidate helper uses Python3.11 standard library only for cold
file admission/build orchestration; the installed app never loads Python.
Applications can instead use the checked-in standard Package.swift beside an
already admitted sibling `runtime-candidate` directory. Swift/SDK are build
tools, not an extra Core dependency. This helper refuses another Swift profile
rather than silently linking an incompatible language deployment set.

Native Windows runs native Swift/Python in its supported developer shell. Set
the same variables to absolute Windows paths. `COAKKA_SWIFT_LANGUAGE_HOME`
points to the qualified Swift6.0.3 runtime DLL directory; set
`COAKKA_SWIFT_VFS_OVERLAY` to the coherent SDK22621 module-map profile. Then:

```text
python -I -X utf8 -B scripts/runtime-swift-candidate.py --repo-root C:/path/to/samples
```

WSL ELF execution is not Windows proof. Build-only SDK/PATH configuration is
not installed-app configuration: the helper bundles admitted language files
and runs with system-only PATH and no SDKROOT or native override. If app
protection prompts for a trusted test executable, allow manually; do not disable
security policy. The Java8 ASCII native-cache limitation is not a Swift
filesystem contract.

The helper admits exact member sets, bounded regular files and streamed64KiB
hash copies into its own stage before invoking SwiftPM. It refuses symlinks,
wrong names/sizes/hashes and native overrides; no runtime registry fallback.
Package count42/tree96/depth8 and64MiB per file/192MiB per input table are
bounded. Hashes are integrity against a trusted catalog, not signatures or a
hostile-filesystem/toolchain sandbox. Build/cache/installed outputs are retained
under the selected roots, including failed stages for diagnosis. No broad shell
cleanup trap removes caller data. Direct children have180s build/30s app bounds;
a forced process stop or power loss is not graceful Core shutdown or process-
tree isolation. Qualification owns and cleans its verified disposable stages.

## Runtime ownership

Business code chooses a host/target/text payload and two-second ask budgets,
not incidental queue, port or delivery hints. Omitted queue submits0; Core
normalizes it and reports accepted capacity/frame bounds. The sample inspects
actual AUTO/MULTIPLEX/monitorOFF/delivery2+2 facts, not an input preference or
an OS capability guess. It checks the matched Core reason code/original target,
not a historical reason-name string or an inferred pending count Swift does not
expose. A small Result/throwing-close scope always attempts cleanup and retains
both operation and cleanup failures. No automatic retry is added.

These source/installed sample checks do not replace the owning memory/race,
finite ARC-resource or cooled Pi cohort evidence. Residency need not be flat;
weak nil is not whole-process leak proof. Remaining ecosystem samples/tools,
Android/camera/legal/signing/minimum/wider/long-soak and coordinated release
gates remain separate. Nothing here promotes main or a public registry.
