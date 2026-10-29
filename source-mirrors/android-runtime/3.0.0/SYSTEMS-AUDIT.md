# CoAkka Runtime Android Private Candidate Systems Audit

This record covers the Kotlin/JNI connector and Android AAR packaging over
Native Core `3.0.0` at the exact `core.commit` in
`release-identity.properties`. That file is the single machine-readable source
for connector version, Core version, full Core commit, and the four Android
ABIs. This record does not replace the Core audit for that native generation.

## Ownership And Bounds

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| Native handle ownership | pass | Each Kotlin lane owns one opaque JNI handle. `nativeCall` borrows it under an active-call count. `close` changes admission to closing, calls native stop outside the Kotlin lock, waits for borrowed calls to drain, destroys once, and wakes concurrent closers. JNI create cleanup uses one audited stop-before-destroy helper for start, allocation, and output-status failure paths. |
| Startup ownership | pass | JNI submits a nullable monitor group (absent / explicit OFF / explicit ON); queue capacity zero delegates to Core and negative values are refused. Execution, connection, and I/O groups remain absent; Core resolves and reports CPU `AUTO`, persistent `MULTIPLEXED`, and I/O OFF. `startupConfig()` returns one bounded pointer-free copy of Core's accepted snapshot under lifecycle serialization and refuses malformed counts or unknown enum values instead of mirroring defaults. |
| Runtime delivery ownership | pass | Android retains only request/control writers and an optional Core-accepted monitor handle as descriptors. Delivered requests use Core's opaque request reader; reply-capable outbound work uses Core ask tickets. Core alone selects owned queue/doorbell or framed fallback, matches response/deadletter terminals, and owns queue credit. The connector has no delivery-mode table, raw output-FD reader, or fallback heuristic. |
| Blocking-call close law | pass | Kotlin closes admission before signaling the native handle, then waits for already-admitted JNI calls. Native request/ask waits recheck close at one 50 ms monotonic slice, retire owned frames/tickets, and decrement a condition-variable-tracked borrow before reader/client destruction and Runtime stop. The second native active-call count keeps direct JNI misuse from racing destruction; no unbounded close wait or per-call thread is introduced. |
| Close versus callback | pass | Core stop is the cancellation boundary and runs without the Kotlin lifecycle lock. JNI supplies the exact lane-handle identity to both Kotlin callback mappers; a bounded thread-local guard makes same-lane `close()` fail fast before native stop can join its current worker. Unit tests cover source and consumer paths. A different thread can close, wake native work, wait for callback return, then release refs. |
| Stream callback borrowing | pass | Source and consumer `ByteBuffer` values borrow native frame memory for one callback only. The consumer receives a read-only view. JNI local refs are deleted before return; callback exceptions are cleared and fail the native operation with `IO`. A consumer exception stabilizes as `CONSUMER_ERROR` when its decoded Terminal wins, or `CANCELED_BY_HOST` when peer close makes the local cancel win; both outcomes remain terminal and release callback refs. |
| JNI string borrowing | pass | Seven-string File/Stream config input uses fixed eight-slot local-ref and UTF-char storage, rejects counts outside that bound, and avoids allocator or throwing bounds access across `extern "C"` JNI. Partial acquisition is released by the same stack owner. |
| JNI global refs | pass | One callback and one bridge-class global ref are retained per prepared publisher/subscriber. Acquisition and bounded-map insertion are fail-closed and release partial refs. Core permits forget only after terminal state, so successful Core forget precedes ref release; stop joins workers before destroy releases the remaining map. |
| R8 and JNI names | pass | The AAR exports consumer rules for the statically named `NativeRuntimeBridge` JNI entrypoints and the native-looked-up `NativeStreamCallbacks`, `AndroidStreamSource`, and `AndroidStreamConsumer` descriptors. The release-minified smoke checks those exact JNI class/constructor identities and ten startup/ask/error/monitor/network types shared by the independently minified target/test APKs, verifies 43 native plus two callback members and descriptors in the final APK DEX, and then executes the same Runtime, File, and Stream paths. |
| Retained callback bound | pass | Kotlin limits Core lane capacity to `64`; JNI independently caps retained callback contexts at `128`, covering both directions without an unbounded map. Duplicate/full insertion retains no new ref. |
| Queues, workers, frames, and files | pass | Queue/capacity values are checked against 32-bit `size_t` for all packaged ABIs. File worker counts are `0..4`; Stream worker counts are `0..4`; Stream capacity is at most `64`; frame/window/timing values fit the exact native integer types; File sizes remain non-negative 64-bit values. Core owns the bounded queues and I/O workers. |
| Strings and grants | pass with limitation | Owner identity/host are visible ASCII with native bounds. Transfer/session IDs and tokens are length-bounded and copied; token-bearing diagnostics are redacted. JNI still uses modified UTF-8 conversion inherited from the Android bridge. ASCII identifiers and ordinary BMP paths interoperate; unrestricted supplementary-Unicode path/identity support is not claimed until a standard UTF-8 conversion test exists. |
| Grant failure cleanup | pass | A JVM allocation failure after native File or Stream prepare cancels and forgets the just-created record where possible, so the caller cannot lose an undiscoverable live capability. |
| Heap churn | pass | Callback contexts live by value in bounded map nodes allocated at session admission. The close guard retains one fixed four-entry stack per participating Core worker and performs no per-frame boxing. Per-frame JNI local wrappers are short-lived boundary allocations; no zero-copy or allocation-free callback claim is made. |
| Threads and wakeups | pass | JNI creates no event loop or worker. Runtime reply matching reuses Core's bounded ask-client workers; request/ask close observation uses a 50 ms slice only while an application call is blocked. Owned-queue credit and doorbells stay Core-owned. File/Stream attaches a bounded Core worker only for a Java callback and detaches when the callback returns. |

## Senior Systems Review

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| Strict compilation | pass | The clean private-checkpoint rebuild compiles JNI for all four ABIs with NDK Clang 21, C++20, `-Wall -Wextra -Wpedantic -Werror`, and hidden visibility. Third-party protobuf diagnostics remain outside the connector JNI warning-as-error boundary. |
| Static analysis | pass | NDK Clang 21 analyzes the three JNI translation units from all four exact-Core compile databases: 12 scoped analyzer commands with the `core`, `unix`, `deadcode`, and `cplusplus` checkers complete without a finding. Pinned third-party sources are not redundantly analyzed. |
| Pinned compile boundary | pass | Gradle and every shell entrypoint read one `release-identity.properties`; the source gate verifies that its full commit exists and declares the recorded Core version. Build tasks extract that commit before protobuf/JNI work, and generated Java, all four JNI compile databases, and packaged Runtime libraries use only that immutable snapshot. |
| Package provenance boundary | pass | Metadata schema 2 separates the public connector source commit/dirty state from the exact native Core commit and Core checkout dirty state. The aggregate dirty bit fails closed. The Central gate additionally requires the connector commit to equal the peeled public source tag and the clean Core checkout to equal the pinned Core commit. |
| Cacheline and false sharing | not applicable | This slice adds no Core hot-path layout. Callback bookkeeping is an admission/forget mutex-protected map, not a lock-free shared queue. |
| Descriptors and socket ownership | pass | Kotlin adopts only the runtime request/control writers and an optional monitor handle after Core accepts monitoring. JNI retains the three native output descriptors for the opaque Core reader/ask-client lifetime, closes them through `coakka_v2_host_handles_close`, and never exposes them to Kotlin or adopts one twice. File/Stream sockets and files stay inside Core. |
| Dependency closure | pass | Exact-Core Runtime libraries require only Android `liblog`, `libm`, `libdl`, and `libc`; JNI additionally requires `libcoakka_runtime_v2.so`. The final AAR inspection checks this exact closure for every ABI. |
| Artifact disclosure and stripping | pass | Candidate builds statically absorb the sealed native message implementation, hide archive symbols, enforce exact Core/JNI export maps, strip debug and ordinary symbol tables, remove compiler-comment sections, remap producer paths, and run the canonical Core binary-identity scanner over all eight packaged images. Any private vocabulary, producer path, unexpected export, section, or native library fails the AAR gate. |
| ABI/package shape | pass | The exact-Core AAR contains Runtime and JNI libraries for every Android NDK ABI: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Each ABI contains both Runtime and JNI images; ELF class/machine, exact 160-symbol Core surface, and exact 43-symbol JNI surface are inspected before device execution. |

## Expert Boundary Review

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| MMU, DMA, IRQ, driver, and VFS design | not applicable | This connector delegates file, socket, poller, and storage mechanics to the already audited Core and Android runtime. It adds no driver, memory mapping, direct I/O, DMA, or interrupt contract. |
| NUMA and per-core architecture | not applicable | Android connector state is small and process-local; it does not introduce CPU pinning, per-core queues, or a shared-nothing scheduler. |
| Memory ordering | not applicable | Kotlin lifecycle serialization uses `ReentrantLock`; JNI callback bookkeeping uses `std::mutex`. No new atomics or lock-free publication are introduced. |
| Kernel-mechanism substitution | not applicable | There is no justification for `io_uring`, user-space networking, or a new poller at this bridge boundary. Core owns the transport mechanism. |

## Initial P528 Startup Verification (38-Symbol Candidate)

This preparatory slice keeps Core f6 immutable. It removes the connector's
forced monitor-ON request and fixed queue default. Java/Kotlin submit absence,
OFF or ON without a local default table; handle export follows Core's accepted
snapshot. The existing nullable monitor lane returns zero/null when Core
accepts OFF. No new worker, queue, polling loop, per-request allocation or
transport policy is introduced. Core constructor/startup refusals retain their exact status through the existing
`CoAkkaNativeException`. Invalid JNI inputs are Java argument errors; local
owner/JNI publication allocation is `OutOfMemoryError`; pending VM exceptions
are preserved. Unpublished native owners are retired before error publication;
a snapshot-publication failure retains a caller-owned live runtime, while
`open` destroys its unpublished owner on any failure. A host-only counter
proves that failed public `open` attempts return to the owner baseline. No
failure is guessed from error text. This is a correctness/default-convergence
change, not a measured throughput improvement.

The source boundary is verified with Kotlin 2.2.10/API 2.2/JVM 11 warnings as
errors, Java 11 `-Xlint:all -Werror`, strict C++20 warnings as errors and 22
JUnit tests against the exact f6 desktop SDK (13 projection/bounds/control tests
including Java construction, plus nine real host-JNI startup/File/Stream tests).
Host JNI sanitizer instrumentation covers the owned adapter only; the linked
Core SDK is frozen and uninstrumented. Strict and UBSan profiles each pass all
22 tests on macOS arm64; a deliberate UBSan fault and JVM support probe pass
before the instrumented run. The generated diagnostic Java launcher uses the
same JDK modules with an ad-hoc local signature, allowing sanitizer loading
without modifying the installed vendor JDK. Private P528 evidence records each
command, source/image hash and outcome. The prebuilt producer path additionally
requires an exact AAR SHA, full native inventory/identity verification and exact
Git source before copying only Core images; 15 focused tests exercise refusal,
extent bounds, input mutation, symlink handling and output ownership. Ruff,
mypy and sourced ShellCheck checks pass. This avoids rebuilding frozen Core
dependencies.

The first P528 monitor/default/causal AAR was built from clean connector checkpoint
`f2a9e9f8e6585d40e3bf6b64b134e2130e8b3298` and the clean exact f6 Core
checkout. It is 3,020,363 bytes with SHA-256
`bae0744b1120d2b6ddbbad18c3136e56062c4862d23a8d7bbff6eb1c6b6b9d0e`.
All four Core images match sealed P500 SHA-256 `64e3a4b3...` byte-for-byte;
only the JNI adapter is rebuilt. Unit tests, strict four-ABI JNI build, release
lint, eight-image hardening/identity checks and all three negative controls
pass. Twelve scoped NDK analyzer invocations from the actual package compile
databases complete without a finding. No Core/dependency source graph is
rebuilt or reanalyzed. Private unstripped JNI companions for all four ABIs
reproduce the packaged image hashes exactly after the release stripping steps;
they are retained separately and never enter the AAR.

The exact selected library AAR is consumed by real R8 target and independently minified
instrumentation builds. The production consumer rule alone retains the JNI
exception constructor; final DEX verifies its exact descriptor alongside all
38 native and two callback members. The installed Windows API 37 native
x86_64 test passes monitoring absent/OFF/ON provenance, OFF zero/null calls,
Core-owned request/reply, close interruption, File and Stream paths. Pulled
installed APK hashes equal the built hashes, and their native images and
metadata equal the selected AAR. The temporary read-only/no-snapshot-save AVD
session is closed back to the initial stopped state.

Physical-Windows WSL/Linux x86_64 strict, ASan and UBSan each pass all 22 current
tests. JNI alone is instrumented; the f6 SDK image is frozen and uninstrumented.
Fresh LSan/TSan support probes work, but product-clean evidence remains blocked:
TSan stops on an unsuppressed HotSpot `libjvm.so` race; LSan reports VM-owned
allocations, including method-ID metadata reached through JNI lookup. Full
diagnostics are preserved without suppression. macOS strict/UBSan also pass
22 tests; its ASan support probe times out, TSan probe crashes and LSan reports
unsupported. These are blocked runners, not passing sanitizer evidence.

The first clean producer attempt stopped before JNI compilation because Bash
3.2 treats an empty array as unbound under `set -u`. The follow-up checkpoint
uses one nonempty command array. Both shell modes have a regression control,
and a separate real `--build` fixture verifies all four exact Core images with
no old JNI copied. Failed and successful receipts remain distinct.

The historical public tag/source mirror/signature is immutable and must not be
used as provenance for this private candidate. Other ABI execution, minimum-API
execution, physical-device lifecycle, device pressure/soak and publication gates
remain open; no performance improvement is claimed.

## Historical P528 Network Presence Follow-Up (39-Symbol Candidate)

The first `bae0744b...` AAR and all of its receipts remain immutable. The
network follow-up removes duplicated mode/wildcard policy and silent loss of
non-node fields. Network intent is nullable; port presence is represented
separately from the zero value. JNI uses Core's field constants, forwards every
provided value independently of mode, and preserves Core's apply status.
Only Java/JNI integer representation, array extent and lifetime checks remain.
Node advertised ports are explicit, never derived by the connector.

One cold `networkSnapshot()` call copies Core's mode, explicit-policy fact,
ports and two optional hosts while the existing native lifecycle lock protects
its borrowed strings. Core's accepted strings have the bounded extent enforced
by the frozen owning Core validator; JNI retains no pointer or reference after
the synchronous copies. JVM publication failure propagates the pending Java
exception and retains the caller's runtime. Invalid apply during public `open`
retires the unpublished owner. The source tests exercise actual Core refusal,
not guessed validation text or a connector-side default table.

The new selected AAR is 3,026,710 bytes, SHA-256
`1ec164434373b378071c46e9040807889149b15ea59c9c2f283a8f6081c296c3`,
from clean source `655fcf27dd84eaeb25d654371568084b79a69427` and clean
Core f6. Its four Core images are identical to the sealed input. Unit tests,
strict four-ABI JNI build, lint, export/dependency/architecture/privacy checks,
three verifier negative controls and 12 actual compile-database analyzer runs
pass. Private unstripped JNI companions reproduce all four packaged JNI hashes
after release stripping.

The output JNI surface is now 39 exact symbols. The sealed Core input keeps
its independent 38-symbol historical JNI input map and requires an exact trusted
SHA before the full eight-image verifier runs. Normal output verification
requires 39 and rejects the old 38-symbol AAR; input mode rejects a wrong digest.
Only Core images are adopted, never historical JNI. Core160/ABI1 remains frozen.

macOS strict and UBSan pass 28 current unit/host tests, including omitted and
explicit network facts, all three active modes, wildcard/zero/missing/non-node
refusal, copy lifetime, output allocation failure and failed-open owner cleanup.
All four ABI strict checks and 12 scoped analyzer commands pass. The new 39-symbol AAR passes real R8-minified application/instrumentation
builds and installed native x86_64 execution on Windows API 37 (1.389s test body).
It covers absent/defaulted and explicit network modes, a real local node
listener, copied Core facts, wildcard/zero/non-node refusal, monitoring and the
existing request/File/Stream paths. Installed APK hashes match the built bytes;
native images and metadata match the selected AAR. Prior 38-symbol evidence
remains separate. WSL strict/ASan/UBSan each pass all 28 current tests with only
the owned JNI adapter instrumented; existing unsuppressed JVM LSan/TSan and
matching Android sanitizer blockers remain open.

A separate ARM64-filtered R8 application/instrumentation pair from the same
exact R2 AAR passes the same installed API 36 native ARM64 emulator test on the
ARM64 Mac/Hypervisor.Framework (0.901s body). Installed APK digests and AAR
native-image/metadata binding match. A disposable external APFS profile and thin
QCOW overlays use the original SDK/data/encryption images as read-only backing;
no 9.35GB base copy is made. `lsof` proves all writable image descriptors are
external. The SDK's forced-readonly mode first used internal temporary overlays
despite TMPDIR; that attempt was refused, stopped and its exact owned temporary
files removed before qualification. The accepted run uses writable external
top overlays with read-only backing and snapshots disabled. Original base-file
sizes/mtimes/small-file hashes remain unchanged; the temporary guest is stopped
and its external tree is removed. This is native ARM64 emulator evidence, not
physical-device or minimum-API qualification.

The historical P528 AAR was not pre-obfuscated. Its consumer-only R8 proof did
not close library-distribution hardening. P529 now processes the library itself;
its product message boundary and matching final package remain separately
qualified below. No publication or production-support promotion is claimed.

## P529 Product Message Boundary Preparation

The processed library distribution keeps generated codec types private. The
application-facing boundary is `AndroidRuntimeMessages`: request/reply frames,
request target/payload/one-way values, and response payload copies. The four
stateless JNI methods call only the frozen Core `client.h` builders and copy
projections; they add no runtime owner, routing, matcher, thread, queue or
semantic fallback. Public Android apps and the normal device fixture require
no private codec. The historical 3c616a90 artifact is a diagnostic baseline,
not a promotable application package: its missing product message API was
identified before promotion.

Kotlin performs strict UTF-8 conversion and rejects embedded NUL. JNI verifies
array extents, one terminal NUL and signed-to-unsigned representability, then
borrows storage only for the synchronous Core call. Core validates semantic
fields and supplies exact statuses; nonempty replyTo for one-way intent is
refused rather than normalized. Native outputs are released on every path,
including Java array/publication failure. Returned arrays are mutable app-owned
copies; a delivered request frame stays app-owned and unchanged until reply
building returns. No native view outlives a call. A host-only seam counts array
borrows and Core outputs and injects Java OOM at publication boundaries; it is
absent from release code. The neutral `runtime_ask` operation label preserves
status without inventing whether begin or await produced it.

The native change remains in one cohesive message-projection translation unit;
its helpers have no cross-module lifetime and do not need a new private header.
The shared header adds instrumentation-only atomics; production owner layout,
cachelines and heap state stay unchanged. Senior and expert review scope is
representation bounds, output ownership and exception preservation. Kernel,
IRQ, DMA and additional scheduling mechanisms are not applicable. Final JNI43
image identities, analyzer, real packaged API/second-R8/ART execution, private
companions and sanitizer evidence remain candidate-specific gates.

## Historical P500 Executed Evidence

The historical P500 private candidate pipeline over clean connector checkpoint
`073df27ab0404764f0303008b9c3b16b197b1bc7` and clean Core commit
`f6b61768f60a08d81b1dc06625a211ffca8e89e2` runs source-manifest
verification, unit tests, strict four-ABI Core/JNI compilation, release lint,
package metadata, exact export/dependency/architecture inspection, the
canonical binary-identity scan, and three fail-closed verifier controls. The
3,016,513-byte AAR has SHA-256 `64e3a4b3...`, 160 Core exports and 38 JNI
exports. NDK Clang 21 also completes 12 scoped analyzer commands without a
finding.

The release-minified exact-AAR app then runs on the Windows API 37 x86_64 AVD
after the R8 mapping and final-DEX JNI gates. It covers startup
AUTO/MULTIPLEXED, owned Runtime request/reply, close interruption of a pending
ask, and owner-pinned File and Stream paths. The AVD advertises a translated
ARM64 ABI through `libndk_translation.so`; only its native x86_64 execution is
claimed. No public tag, registry upload, physical-device, lifecycle-restart,
pressure, or soak claim is inferred from this candidate evidence.

The integrated startup/delivery source also runs the same six host-JNI
lifecycle, File and Stream tests on physical-Windows WSL2/Linux x86_64 with
Temurin 21.0.13 and Clang 21.1.8. Strict, ASan and UBSan profiles pass all six
tests separately;
the sanitizer support probes first prove that each runtime detects its intended
fault. LeakSanitizer and ThreadSanitizer remain blocked evidence: their proof
probes work, but a preloaded HotSpot process reports process-lifetime JVM
allocations and `libjvm.so` races, including with interpreted execution. Those
diagnostics are preserved without suppression and are not reported as Android
device or product-clean sanitizer evidence.

## Historical P528 Publication-Gate Snapshot

| Gate | Status | Required evidence |
| --- | --- | --- |
| P528 exact-Core private candidate | pass | Current clean `655fcf27` AAR `1ec16443...`, output 160/39: unit tests, strict four-ABI JNI build, lint, actual compile-database analyzer, exact hardening/identity, negative controls, R8 and installed native x86_64 execution pass. Four Core image hashes are unchanged; no support promotion follows for other targets. |
| Historical P500 exact-Core private candidate | pass | The clean commit rebuild passes unit tests, strict four-ABI build, lint, exact export/dependency/architecture and disclosure gates, negative controls, R8 verification, and the installed-AAR Runtime, request/reply, File, and Stream paths. Static analysis and sanitizers remain separately classified below. |
| Library managed-code hardening | blocked in P528 | That historical AAR was not pre-obfuscated; only consuming application/instrumentation R8 was proved. |
| Public immutable source | blocked | The existing historical `android-runtime-1.2.0` tag identifies different source/native bytes and remains immutable. This private candidate requires a new reviewed version/tag and source projection. Publication requires a separate clean rebuild whose packaged connector commit equals the peeled public tag and whose Core checkout equals the pinned Core commit. |
| Maven Central signing and receipt | blocked | After the public source tag exists, create the signed AAR/POM/sources/javadoc/module bundle, verify signatures/checksums, upload only with explicit approval, then record the Central deployment and registry bytes. |
| Other ABI runtime execution | blocked for remaining targets | Current R2 executes natively on Windows API37 x86_64 and a separate Mac API36 ARM64 emulator. `armeabi-v7a`/`x86` remain package-inspected only. No emulator is relabelled as physical-device evidence. |
| Sanitizers | blocked for publication | Current WSL/Linux x86_64 strict, ASan, and UBSan each pass all 28 unit/host-JNI tests with only JNI instrumented. LSan and TSan prove their runtimes but stop on unsuppressed HotSpot allocation/race diagnostics in `libjvm.so`; no matching Android sanitizer run is claimed. Preserve both limitations instead of treating host execution or strict compilation as device sanitizer evidence. |
| Fault, pressure, and soak | blocked | Allocation failure, callback exception, and cleanup paths are code-reviewed, but device pressure, disconnect/reconnect, cancellation-at-state, process death, and long-running File/Stream soak remain unexecuted. |
| Physical-device lifecycle | blocked | The exact-Core release-minified packaged-AAR path passes on the Windows API 37 x86_64 AVD. An emulator is not foreground-service, Activity recreation, LAN reachability, thermal, process-kill, or physical ARM64/32-bit evidence. |

P528 private package, provenance and native x86_64/ARM64 emulator gates are complete
for the exact `1ec16443...` AAR (consumer APK R8 scope). Historical P500 evidence remains tied to its
own immutable bytes. Public source/tag, Maven Central, and all publication actions remain
closed. Other blocked rows constrain the advertised support claim and are not
silently converted into physical-device or production-soak evidence.


## Current P529 Qualification Scope

This table supersedes the historical snapshot for the user's present private
candidate. The selected AAR is 3,000,839 bytes, SHA-256
`a839037be3683649e306cdbef1b780a25b5267598c0172c58e0cc2663bb33ee1`,
built from clean source `3a046e6c9ebd67934c954e07255ce1fdc5078656` and Core f6.
Later qualification text does not change that compiled source identity.

| Gate | Current state | Scope |
| --- | --- | --- |
| Core identity | pass | Frozen f6/ABI1/160 exports and all four native Core images remain unchanged; only the Android adapter adds four stateless message projections. |
| Managed library and public API | pass | Real library R8, private dependency absorption, Kotlin-only POM, 86 intentional public types and 671 Kotlin contracts. Old 84 type contracts remain intact; two new product types are explicitly admitted. The managed JAR independently reproduces as `f75fd617...`; private mapping and native companions remain separate. |
| Owned native boundary | pass, scoped | Four ABI strict/analyzer checks and 35 real Java/Kotlin host tests in strict and separately verified UBSan profiles; Core is the exact uninstrumented installed image, not rebuilt. The isolated owner fixture separately passes ASan/LSan/UBSan and TSan with genuine positive controls. This is not whole-VM leak/race certification. |
| Final no-codec app consumer | pass | Installed minified apps create requests, decode delivery, build correlated replies, read payloads and observe actual response/deadletter/refusal/close outcomes using only product APIs. Unicode, binary/empty payloads, ownership copies and one-way delivery/refusal are exercised. No private codec substitutes for normal app functionality. |
| Minimum/normal Android | pass | Exact selected AAR and APK hashes execute on native Mac ARM64 emulators at API24 and API36. Earlier `3c616a90...` evidence remains diagnostic only. |
| Android memory instrumentation and bounded campaign | pass, scoped | All four actual adapter translation units run under HWASan on API36; the deliberate overflow control reports tag mismatch, then full codec-free Runtime/File/Stream and fresh-process recovery pass. Release Core remains exact and uninstrumented. Both normal targets pass 23 lifecycle cycles, 30 seconds of concurrent correlated work, queue-pressure stop and process restart with fd/thread retirement and sampled RSS. Classic ASan startup failures remain retained, not relabelled as passes. |
| Physical Android execution | not applicable | Explicitly excluded by the user for this acceptance. No physical-device or foreground-service production qualification is claimed. |
| 32-bit Android execution | not applicable | Explicitly excluded by the user. All four ABI payloads and their static architecture/export/dependency inspection remain required; no 32-bit runtime claim follows. |
| Public source/tag and registry actions | held by user | The historical source mirror remains immutable. A new reviewed source projection/version, signing and registry receipt are separate publication steps; nothing is published in this task. |

## P533 Runtime 3.0.0 Candidate Boundary

The release identity now selects connector and Core `3.0.0`. Core's version-only
source baseline preserves the accepted processing, ABI, configuration defaults,
ownership and failure law. The candidate must rebuild all four native Core
images from its pinned source; changing an old package label does not satisfy
that gate. The source projection is `android-runtime/3.0.0`; historical
`1.2.0` source and package identities remain immutable.

The scoped review introduces no buffer, allocation, thread, queue, callback,
wakeup, atomic, or kernel mechanism. Prior lifetime, race, fuzz, pressure and
sanitizer evidence remains limited to its exact source and flags. New release
bytes require fresh strict native builds, export/import and disclosure scans,
real R8 output admission, managed consumers, and a functional smoke of the
final AAR on one existing Mac emulator. Android is explicitly preview; no
official Android platform support is claimed. The user excludes extra minimum
OS campaigns and an ARM/x86 execution matrix, as well as physical-device and
32-bit execution. The existing four-ABI package and static checks remain
required. A build is not target execution; the final functional observation below closes
only the accepted private preview smoke scope.


### Final P533 preview observation

The final AAR SHA-256
`3aa24aafb3838c328823e6fee9a54eed50f8d3a075ce883fcd6513a9e1d1ea06`
from connector source `97659069795a701d6e38ea8868282191ab13f1a9`
and Core `1522b4a9f30da1039ef8c7d198bbecfc749a2456` passes the accepted
functional smoke on one existing Mac emulator. The observed guest reports
API24/arm64-v8a with no native bridge. This observation is preview evidence,
not an official minimum-OS or architecture support claim. No second emulator,
physical-device or32-bit execution was added; Windows remains an accepted
fallback rather than an additional required run.

The minified installed target/test APKs match their built SHA-256 identities
and verify all43 JNI entries before executing public Runtime request/reply,
File and Stream paths. The already-running bounded checks complete23 lifecycle
cycles and30 seconds with556,770 correct replies,22ms close, descriptors38 to38
and threads14 to14; no performance claim is made. Force-stop and full package
reexecution pass. The private `android/platforms/PREVIEW-RESULT.json` names the
exact package receipt, installed APK proof, preserved evidence capsules and
final cleanup receipt. Prior P529 memory/race evidence remains source/flag
qualified rather than relabeled as a new sanitizer run. Publication and
broader platform support remain closed.
