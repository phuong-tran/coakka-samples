# Private Swift runtime candidate sample

This sample uses exactly the private `3.0.0` source package and bundled
Core3.0.0+1522b4a9/ABI1, not public2.5.2/2.5.3. The trusted
`artifacts/runtime-swift-candidate.json` pins all57 package files and the
qualified deployment copies of normal language runtime libraries. No separately
installed Core/provider library or native-path override is needed. Targets are
macOS ARM64, Linux ARM64/x64 and native Windows x64 in the selected supported
scope. Windows ARM64 is preview for this3.0.0 wave with no official support
claim; its payload remains available for evaluation. Package presence is not a
wider platform/minimum-OS/signing or publication claim.

SwiftPM manifest/language floor remains6.0. Build profiles use Mac6.2 and
Linux/Windows x64 Swift6.0.3. Windows ARM64 Swift6.4.1 was an attempted
compiler/SDK profile, without a final3.0.0 matching-host execution pass. The
manifest language floor does not establish an older Mac compiler or ARM Windows
OS floor. Use a coherent compiler/SDK profile within the selected support scope. The consumer's build is ordinary
SwiftPM over a local admitted package; WMO/Osize/no-reflection/link stripping
apply to the app and dynamic Swift module. Core native bytes are not rebuilt.

## Build and run

Choose existing absolute candidate, work and cache roots. Large development-Mac
staging/cache trees belong on mounted external APFS storage. The candidate root
contains exactly the57 catalog members, with no Git/build/cache directories.
Keep trusted catalog/toolchain/input/cache trees stable during the build.

```sh
export COAKKA_SWIFT_CANDIDATE_HOME="/absolute/package-3.0.0"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_SWIFT_CACHE_ROOT="/absolute/swift-cache"
# Linux: an admitted Swift6.0.3 deployment directory containing lib/.
export COAKKA_SWIFT_LANGUAGE_HOME="/absolute/swift-deployment"
bash coakka-runtime/swift/basic/run.sh
```

The optional candidate helper uses Python3.11 standard library only for cold
file admission/build orchestration; the installed app never loads Python.
Applications can instead use the checked-in standard Package.swift beside an
already admitted sibling `runtime-candidate` directory. Swift/SDK are build
tools, not an extra Core dependency. This helper refuses another Swift profile
rather than silently linking an incompatible language deployment set.

Native Windows runs native Swift/Python in its supported developer shell. Set
the same variables to absolute Windows paths. `COAKKA_SWIFT_LANGUAGE_HOME`
points to the admitted deployment closure for the selected CPU. Windows x64
uses Swift6.0.3 and `COAKKA_SWIFT_VFS_OVERLAY` for its SDK22621 module maps.
The attempted Windows ARM64 preview profile used Swift6.4.1, SDK26100 and
SwiftPM's native build system, without the x64 overlay. These attempted inputs
do not establish final3.0.0 execution or official Windows ARM64 support. Then:

```text
python -I -X utf8 -B coakka-runtime/scripts/runtime-swift-candidate.py --repo-root C:/path/to/samples
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
Package count57/tree128/depth8 and64MiB per file/192MiB per input table are
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
Android/camera/legal/signing/wider/long-soak and coordinated release
gates remain separate. Nothing here promotes main or a public registry.
