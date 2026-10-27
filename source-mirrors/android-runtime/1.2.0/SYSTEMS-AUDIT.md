# CoAkka Runtime Android 1.2.0 Systems Audit

This record covers the Kotlin/JNI connector and Android AAR packaging over
Native Core `2.5.1` at the exact `core.commit` in
`release-identity.properties`. That file is the single machine-readable source
for connector version, Core version, full Core commit, and the four Android
ABIs. This record does not replace the Core audit for that native generation.

## Ownership And Bounds

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| Native handle ownership | pass | Each Kotlin lane owns one opaque JNI handle. `nativeCall` borrows it under an active-call count. `close` changes admission to closing, calls native stop outside the Kotlin lock, waits for borrowed calls to drain, destroys once, and wakes concurrent closers. JNI create cleanup uses one audited stop-before-destroy helper for start, allocation, and output-status failure paths. |
| Startup ownership | pass | JNI submits only Android's explicit monitor-collection requirement to native construction. Execution, connection, and I/O groups remain absent; Core resolves and reports CPU `AUTO`, persistent `MULTIPLEXED`, and I/O OFF. `startupConfig()` returns one bounded pointer-free copy of Core's accepted snapshot under lifecycle serialization and refuses malformed counts or unknown enum values instead of mirroring defaults. |
| Runtime delivery ownership | pass | Android retains only request/control writers and the monitor doorbell as descriptors. Delivered requests use Core's opaque request reader; reply-capable outbound work uses Core ask tickets. Core alone selects owned queue/doorbell or framed fallback, matches response/deadletter terminals, and owns queue credit. The connector has no delivery-mode table, raw output-FD reader, or fallback heuristic. |
| Blocking-call close law | pass | Kotlin closes admission before signaling the native handle, then waits for already-admitted JNI calls. Native request/ask waits recheck close at one 50 ms monotonic slice, retire owned frames/tickets, and decrement a condition-variable-tracked borrow before reader/client destruction and Runtime stop. The second native active-call count keeps direct JNI misuse from racing destruction; no unbounded close wait or per-call thread is introduced. |
| Close versus callback | pass | Core stop is the cancellation boundary and runs without the Kotlin lifecycle lock. JNI supplies the exact lane-handle identity to both Kotlin callback mappers; a bounded thread-local guard makes same-lane `close()` fail fast before native stop can join its current worker. Unit tests cover source and consumer paths. A different thread can close, wake native work, wait for callback return, then release refs. |
| Stream callback borrowing | pass | Source and consumer `ByteBuffer` values borrow native frame memory for one callback only. The consumer receives a read-only view. JNI local refs are deleted before return; callback exceptions are cleared and fail the native operation with `IO`. A consumer exception stabilizes as `CONSUMER_ERROR` when its decoded Terminal wins, or `CANCELED_BY_HOST` when peer close makes the local cancel win; both outcomes remain terminal and release callback refs. |
| JNI string borrowing | pass | Seven-string File/Stream config input uses fixed eight-slot local-ref and UTF-char storage, rejects counts outside that bound, and avoids allocator or throwing bounds access across `extern "C"` JNI. Partial acquisition is released by the same stack owner. |
| JNI global refs | pass | One callback and one bridge-class global ref are retained per prepared publisher/subscriber. Acquisition and bounded-map insertion are fail-closed and release partial refs. Core permits forget only after terminal state, so successful Core forget precedes ref release; stop joins workers before destroy releases the remaining map. |
| R8 and JNI names | pass | The AAR exports consumer rules for the statically named `NativeRuntimeBridge` JNI entrypoints and the native-looked-up `NativeStreamCallbacks`, `AndroidStreamSource`, and `AndroidStreamConsumer` descriptors. The release-minified smoke checks those four exact JNI identities plus seven startup/ask/error types shared by the independently minified target/test APKs, verifies 38 native plus two callback members and descriptors in the final APK DEX, and then executes the same Runtime, File, and Stream paths. |
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
| Descriptors and socket ownership | pass | Kotlin adopts only the runtime request/control writers and monitor doorbell. JNI retains the three native output descriptors for the opaque Core reader/ask-client lifetime, closes them through `coakka_v2_host_handles_close`, and never exposes them to Kotlin or adopts one twice. File/Stream sockets and files stay inside Core. |
| Dependency closure | pass | Exact-Core Runtime libraries require only Android `liblog`, `libm`, `libdl`, and `libc`; JNI additionally requires `libcoakka_runtime_v2.so`. The final AAR inspection checks this exact closure for every ABI. |
| Artifact disclosure and stripping | pass | Candidate builds statically absorb the sealed native message implementation, hide archive symbols, enforce exact Core/JNI export maps, strip debug and ordinary symbol tables, remove compiler-comment sections, remap producer paths, and run the canonical Core binary-identity scanner over all eight packaged images. Any private vocabulary, producer path, unexpected export, section, or native library fails the AAR gate. |
| ABI/package shape | pass | The exact-Core AAR contains Runtime and JNI libraries for every Android NDK ABI: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Each ABI contains both Runtime and JNI images; ELF class/machine, exact 160-symbol Core surface, and exact 38-symbol JNI surface are inspected before device execution. |

## Expert Boundary Review

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| MMU, DMA, IRQ, driver, and VFS design | not applicable | This connector delegates file, socket, poller, and storage mechanics to the already audited Core and Android runtime. It adds no driver, memory mapping, direct I/O, DMA, or interrupt contract. |
| NUMA and per-core architecture | not applicable | Android connector state is small and process-local; it does not introduce CPU pinning, per-core queues, or a shared-nothing scheduler. |
| Memory ordering | not applicable | Kotlin lifecycle serialization uses `ReentrantLock`; JNI callback bookkeeping uses `std::mutex`. No new atomics or lock-free publication are introduced. |
| Kernel-mechanism substitution | not applicable | There is no justification for `io_uring`, user-space networking, or a new poller at this bridge boundary. Core owns the transport mechanism. |

## Executed Evidence

The private candidate pipeline over clean connector checkpoint
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

## Open Publication Gates

| Gate | Status | Required evidence |
| --- | --- | --- |
| Exact-Core private candidate | pass | The clean commit rebuild passes unit tests, strict four-ABI build, lint, exact export/dependency/architecture and disclosure gates, negative controls, R8 verification, and the installed-AAR Runtime, request/reply, File, and Stream paths. Static analysis and sanitizers remain separately classified below. |
| Public immutable source | blocked | A private candidate commit is not the annotated public tag `android-runtime-1.2.0`. Publication requires a separate clean rebuild whose packaged connector commit equals the peeled public tag and whose Core checkout equals the pinned Core commit. |
| Maven Central signing and receipt | blocked | After the public source tag exists, create the signed AAR/POM/sources/javadoc/module bundle, verify signatures/checksums, upload only with explicit approval, then record the Central deployment and registry bytes. |
| Other ABI runtime execution | blocked | x86_64 now executes natively on the Windows AVD. `arm64-v8a` there is translated and `armeabi-v7a`/`x86` remain package-inspected only; none is relabelled as matching physical-device evidence. |
| Sanitizers | blocked for publication | WSL/Linux x86_64 strict, ASan, and UBSan each pass all six host-JNI tests. LSan and TSan prove their runtimes but stop on unsuppressed HotSpot allocation/race diagnostics in `libjvm.so`; no matching Android sanitizer run is claimed. Preserve both limitations instead of treating host execution or strict compilation as device sanitizer evidence. |
| Fault, pressure, and soak | blocked | Allocation failure, callback exception, and cleanup paths are code-reviewed, but device pressure, disconnect/reconnect, cancellation-at-state, process death, and long-running File/Stream soak remain unexecuted. |
| Physical-device lifecycle | blocked | The exact-Core release-minified packaged-AAR path passes on the named API 36 ARM64 emulator. An emulator is not foreground-service, Activity recreation, LAN reachability, thermal, or process-kill evidence on physical hardware. |

The private source, package, provenance, and native x86_64 emulator gates are
complete. Public source/tag, Maven Central, and all publication actions remain
closed. Other blocked rows constrain the advertised support claim and are not
silently converted into physical-device or production-soak evidence.
