# CoAkka Troubleshooting

Use this guide as the common first stop for runtime, connector, sample, and
artifact problems. Keep secrets out of commands, logs, and public reports.

## Collect Facts First

Record:

- CoAkka package and native runtime versions
- release channel and exact archive or library SHA-256
- operating system, version, CPU architecture, and container or VM boundary
- connector language and framework version
- runtime ABI, build ID, compiled capabilities, and active connection/security
  snapshots from public introspection
- stable numeric status, apply reason, validation code, and deadletter or
  transport-failure facts

Do not diagnose from an error string alone when structured fields are
available.

## Startup And Configuration

| Symptom | Likely cause | Action |
| --- | --- | --- |
| `COAKKA_V2_ERR_BAD_STATE` while applying connection options | Runtime already started or is terminally stopped | Apply connection strategy in `CREATED`; create a new runtime for a live strategy change |
| `COAKKA_V2_ERR_FEATURE_UNAVAILABLE` | Mode, TLS source, or tuning is not compiled in this profile | Query capabilities and use a supported mode; do not infer capability from edition name alone |
| `COAKKA_V2_ERR_FEATURE_NOT_ENTITLED` | Compiled feature is not entitled for the active distribution | Check release and license metadata; do not retry as a transient transport error |
| `changed == 0` after apply | Rejected attempt or already-effective configuration | Inspect `apply_status`, `reason`, `validation`, and the returned active/effective snapshot |
| Startup fails without an active route snapshot or exported handles | Lifecycle prerequisites are incomplete | Export requested host lanes, apply an initial valid control snapshot, then start |
| Stale control generation | Generation is not strictly newer | Serialize control ownership and submit a higher monotonic generation |

Configuration apply is atomic. A rejected connection or security apply keeps
the previous active configuration; do not issue a follow-up getter and assume
it describes the failed proposal.

## Connection Strategy

- Confirm the mode exists in the runtime capability snapshot.
- Remember that Community bounded pool uses fixed defaults and rejects numeric
  tuning.
- Do not change strategy after startup. Use a new runtime and an
  application-owned cutover.
- Do not automatically replay after an ambiguous write or connection-scoped
  multiplexing failure.

See [Runtime Connection Strategies](connection-strategies.md) for defaults,
availability, and code examples.

## TLS And mTLS

| Symptom | Check |
| --- | --- |
| Credential reload rejected | Generation must be strictly newer, mode must match the active TLS/mTLS mode, and all required file fields must be present |
| Active generation did not change | Inspect `reason` and `validation`; the previous generation remains active by design |
| Unknown CA or untrusted peer | Verify the intended CA bundle on both peers and the complete presented chain |
| Hostname or IP mismatch | Certificate identities must match the exact endpoint used by the connector |
| Client certificate required | mTLS server requires a valid client identity and trust chain |
| Certificate appears expired or not yet valid | Verify host wall clock and certificate validity; edge devices need an explicit time-readiness gate |
| Old connection remains briefly after drain reload | Drain is bounded retirement, not a synchronous barrier; an in-flight exchange may finish on its captured generation |

Runtime introspection never returns private keys, PEM bytes, or credential file
paths. Inspect those through the host secret-management boundary without
copying them into application logs.

See [Runtime TLS And mTLS](tls-and-mtls.md) for parameter, ownership, rotation,
and deployment guidance.

## Network Placement

A private address, LAN, VLAN, Kubernetes cluster network, or device subnet is a
controlled network, not an automatically secure network. Decide explicitly
where peer identity, encryption, certificate rotation, and authorization live.

- For traffic between CoAkka participants, use runtime TLS/mTLS for direct
  end-to-end peer identity and encryption; a service-mesh data plane is not
  required.
- In Kubernetes, keep certificate issuance and secret distribution in the
  platform when appropriate, but let CoAkka terminate its own runtime TLS/mTLS.
  Keep public ingress TLS at the public edge.
- On Raspberry Pi, BeagleBone, bare metal, edge Linux, and industrial Android,
  verify architecture, clock readiness, CA provisioning, key permissions, and
  release evidence separately.

## Artifact Integrity And Platform Warnings

First compare the archive SHA-256 and, after extraction, the inner runtime
library SHA-256 with release metadata. Also confirm the release channel,
edition/profile, OS, and CPU architecture before loading the library.

The ecosystem distribution keeps target-specific runtime artifacts for
Windows, macOS, and Linux. A connector validation run may cover fewer
OS/architecture lanes than the distribution matrix; bundled bytes, source
compilation, and end-to-end execution are reported as separate evidence. Do not
silently remove another operating system from the package. Always select the
archive matching both OS and CPU, then read the exact compatibility/evidence
row before diagnosing the loader.

Do not confuse signed release receipts with Apple code signing/notarization or
Windows Authenticode. CoAkka's current native-signing status and verification
flow are documented in
[Runtime Release Signing And Platform Trust](runtime-release-signing-and-platform-trust.md).

For Linux load failures, check in this order:

1. CPU architecture and binary format.
2. Archive and inner-library digest.
3. Execute/read permissions and mount policy.
4. Loader search path and required native dependencies.
5. C/C++ runtime and OpenSSL compatibility recorded by the release.
6. Organization-specific integrity enforcement.

Do not treat every system-runtime dependency as a bundled CoAkka
implementation dependency. Read the exact release evidence, then use the OS
vendor's package channel:

- On Linux, `ldd` or `objdump -p` shows the required SONAMEs. If the loader
  reports `libz.so.1` or `libzstd.so.1` missing, install the architecture-
  matched zlib or zstd runtime package from the distribution repository. Do
  not create an unversioned compatibility symlink.
- On Windows, missing `MSVCP140*.dll` or `VCRUNTIME140*.dll` requires the
  official architecture-matched Microsoft Visual C++ 2015-2022
  Redistributable. Do not download individual DLL files from third-party
  sites.
- On macOS, verify `otool -L` dependencies against system frameworks and
  `/usr/lib` libraries recorded for that release. A missing non-system path is
  a packaging defect and should be reported.

For macOS Gatekeeper or Windows publisher warnings, verify the bytes and follow
local policy. Do not disable system-wide security controls as a generic fix.

### macOS sanitizer process does not reach `main`

Some AppleClang and macOS combinations can stall while AddressSanitizer is
initializing shadow memory, before any consumer or CoAkka code executes. A run
that never reaches `main` is a toolchain failure, not a passing sanitizer gate
and not by itself evidence of a runtime defect.

Confirm the boundary with a zero-work ASan probe or a debugger breakpoint at
`main`. If the probe also stalls before `main`, use a current Homebrew LLVM
Clang while retaining Xcode's archive tools for Mach-O builds:

```bash
LLVM_PREFIX="$(brew --prefix llvm)"
APPLE_AR="$(xcrun --find ar)"
APPLE_RANLIB="$(xcrun --find ranlib)"

cmake -S runtime-test -B build/native-evidence-sanitized \
  -DCMAKE_C_COMPILER="$LLVM_PREFIX/bin/clang" \
  -DCMAKE_AR="$APPLE_AR" \
  -DCMAKE_RANLIB="$APPLE_RANLIB" \
  -DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON
cmake --build build/native-evidence-sanitized
```

Run with `ASAN_OPTIONS=halt_on_error=1:abort_on_error=1:detect_leaks=1` and
`UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1`. Record the compiler version,
runtime generation, and whether the tested runtime library itself was
sanitizer-instrumented. Instrumenting only `runtime-test` proves the public
consumer harness; it does not convert an ordinary release library into a
sanitized core build. Linux remains the leak-detection authority.

The same boundary applies to ThreadSanitizer. Some AppleClang/macOS
combinations fail in the TSan `dyld` shared-cache initializer before `main`,
even when a minimal threaded program succeeds. Confirm with a debugger stack:
if every frame belongs to the sanitizer runtime, `dyld`, or loader
initialization and no CoAkka frame was entered, record the host toolchain
failure and run the source-instrumented gate on Linux. Do not report that run as
a passing TSan result, and do not attribute it to the runtime without a frame or
race report involving runtime code.

If `codesign -d --verbose=4` reports `adhoc` or `linker-signed` with no Team ID,
the Mach-O file has no Apple publisher identity. That output does not conflict
with release metadata that says publisher signing is absent. On Windows, check
the PE certificate table or an approved Authenticode verifier; a zero-sized
certificate table means no embedded Authenticode signature. Neither result is
a Linux loader requirement.

## Escalation

If the issue remains reproducible, send the collected facts and the smallest
reproduction through the correct issue tracker. Report vulnerabilities,
credential exposure, and sensitive artifact-integrity concerns privately.
See [Contact And Support](contact-and-support.md).
