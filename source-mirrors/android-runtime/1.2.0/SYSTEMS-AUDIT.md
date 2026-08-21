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
| Close versus callback | pass | Core stop is the cancellation boundary and runs without the Kotlin lifecycle lock. JNI supplies the exact lane-handle identity to both Kotlin callback mappers; a bounded thread-local guard makes same-lane `close()` fail fast before native stop can join its current worker. Unit tests cover source and consumer paths. A different thread can close, wake native work, wait for callback return, then release refs. |
| Stream callback borrowing | pass | Source and consumer `ByteBuffer` values borrow native frame memory for one callback only. The consumer receives a read-only view. JNI local refs are deleted before return; callback exceptions are cleared and fail the native operation with `IO`. A consumer exception stabilizes as `CONSUMER_ERROR` when its decoded Terminal wins, or `CANCELED_BY_HOST` when peer close makes the local cancel win; both outcomes remain terminal and release callback refs. |
| JNI string borrowing | pass | Seven-string File/Stream config input uses fixed eight-slot local-ref and UTF-char storage, rejects counts outside that bound, and avoids allocator or throwing bounds access across `extern "C"` JNI. Partial acquisition is released by the same stack owner. |
| JNI global refs | pass | One callback and one bridge-class global ref are retained per prepared publisher/subscriber. Acquisition and bounded-map insertion are fail-closed and release partial refs. Core permits forget only after terminal state, so successful Core forget precedes ref release; stop joins workers before destroy releases the remaining map. |
| R8 and JNI names | pass | The AAR exports consumer rules for the statically named `NativeRuntimeBridge` JNI entrypoints and the native-looked-up `NativeStreamCallbacks`, `AndroidStreamSource`, and `AndroidStreamConsumer` descriptors. The packaged-AAR smoke uses a release-minified consumer, rejects R8 mapping drift for every native/callback method, and then executes the same Runtime, File, and Stream paths. |
| Retained callback bound | pass | Kotlin limits Core lane capacity to `64`; JNI independently caps retained callback contexts at `128`, covering both directions without an unbounded map. Duplicate/full insertion retains no new ref. |
| Queues, workers, frames, and files | pass | Queue/capacity values are checked against 32-bit `size_t` for all packaged ABIs. File worker counts are `0..4`; Stream worker counts are `0..4`; Stream capacity is at most `64`; frame/window/timing values fit the exact native integer types; File sizes remain non-negative 64-bit values. Core owns the bounded queues and I/O workers. |
| Strings and grants | pass with limitation | Owner identity/host are visible ASCII with native bounds. Transfer/session IDs and tokens are length-bounded and copied; token-bearing diagnostics are redacted. JNI still uses modified UTF-8 conversion inherited from the Android bridge. ASCII identifiers and ordinary BMP paths interoperate; unrestricted supplementary-Unicode path/identity support is not claimed until a standard UTF-8 conversion test exists. |
| Grant failure cleanup | pass | A JVM allocation failure after native File or Stream prepare cancels and forgets the just-created record where possible, so the caller cannot lose an undiscoverable live capability. |
| Heap churn | pass | Callback contexts live by value in bounded map nodes allocated at session admission. The close guard retains one fixed four-entry stack per participating Core worker and performs no per-frame boxing. Per-frame JNI local wrappers are short-lived boundary allocations; no zero-copy or allocation-free callback claim is made. |
| Threads and wakeups | pass | JNI creates no event loop or worker. It attaches a bounded Core worker only for a Java callback and detaches when the callback returns. Message pipe behavior is unchanged. |

## Senior Systems Review

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| Strict compilation | blocked | The predecessor candidate compiled JNI for four ABIs with C++20, `-Wall -Wextra -Wpedantic -Werror`, and hidden visibility. The exact Core `2.5.1` candidate must repeat that build; predecessor objects are not reusable evidence. |
| Static analysis | blocked | The predecessor four-ABI NDK analyzer run is historical only. Re-run the three JNI translation units from all four exact-Core compile databases, retaining the reviewed frozen-public-ABI padding exclusion and treating every other finding as an error. |
| Pinned compile boundary | pass | Gradle and every shell entrypoint read one `release-identity.properties`; the source gate verifies that its full commit exists and declares the recorded Core version. Build tasks extract that commit before protobuf/JNI work, and generated Java, all four JNI compile databases, and packaged Runtime libraries use only that immutable snapshot. |
| Package provenance boundary | pass | Metadata schema 2 separates the public connector source commit/dirty state from the exact native Core commit and Core checkout dirty state. The aggregate dirty bit fails closed. The Central gate additionally requires the connector commit to equal the peeled public source tag and the clean Core checkout to equal the pinned Core commit. |
| Cacheline and false sharing | not applicable | This slice adds no Core hot-path layout. Callback bookkeeping is an admission/forget mutex-protected map, not a lock-free shared queue. |
| Descriptors and socket ownership | pass | Existing Runtime pipe descriptors remain duplicated/adopted once by Kotlin. File/Stream sockets and files stay inside Core; JNI does not retain raw lane descriptors. |
| Dependency closure | blocked | The predecessor libraries needed Android `libz`, `liblog`, `libm`, `libdl`, and `libc`, while JNI additionally needed only `libcoakka_runtime_v2.so`. Repeat dependency inspection on all exact-Core `2.5.1` libraries before carrying that claim forward. |
| ABI/package shape | blocked | The source still declares `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, but the exact-Core `2.5.1` AAR must be rebuilt and re-inspected for ELF class/machine, lane symbols, and JNI exports. |

## Expert Boundary Review

| Control | Status | Evidence and publication impact |
| --- | --- | --- |
| MMU, DMA, IRQ, driver, and VFS design | not applicable | This connector delegates file, socket, poller, and storage mechanics to the already audited Core and Android runtime. It adds no driver, memory mapping, direct I/O, DMA, or interrupt contract. |
| NUMA and per-core architecture | not applicable | Android connector state is small and process-local; it does not introduce CPU pinning, per-core queues, or a shared-nothing scheduler. |
| Memory ordering | not applicable | Kotlin lifecycle serialization uses `ReentrantLock`; JNI callback bookkeeping uses `std::mutex`. No new atomics or lock-free publication are introduced. |
| Kernel-mechanism substitution | not applicable | There is no justification for `io_uring`, user-space networking, or a new poller at this bridge boundary. Core owns the transport mechanism. |

## Executed Evidence

The prior eight unit tests, Android lint, four-ABI build/analyzer/package
inspection, and API 36 ARM64 installed-AAR File/Stream run belong to Native
Core `2.5.0`. They motivated and protect this source shape but are not evidence
for the `2.5.1` pin. Before promotion, repeat them from the committed public
source tag and a clean checkout of the exact Core commit recorded in
`release-identity.properties`. No exact-Core AAR hash or mirror manifest is
frozen by this source-only update.

## Open Publication Gates

| Gate | Status | Required evidence |
| --- | --- | --- |
| Exact-Core rebuild | blocked | Rebuild unit tests, lint, host JNI profiles, four NDK ABIs/analyzers, dependency/export inspection, and the installed-AAR paths after the final Core `2.5.1` commit is frozen. |
| Clean immutable source | blocked | Commit and project the connector, create and verify public tag `android-runtime-1.2.0`, then build from that tag plus a clean exact-Core checkout. Require connector/Core dirty fields and their aggregate to be false, and require the packaged connector commit to equal the peeled public tag. |
| Maven Central signing and receipt | blocked | After the public source tag exists, create the signed AAR/POM/sources/javadoc/module bundle, verify signatures/checksums, upload only with explicit approval, then record the Central deployment and registry bytes. |
| Other ABI runtime execution | blocked | The predecessor `armeabi-v7a`, `x86`, and `x86_64` files were cross-compiled and package-inspected only. Rebuild them from exact Core `2.5.1`, then execute the tracked translated/x86 workflow without describing translation as matching physical ARMv7 evidence. |
| Sanitizers | blocked | No matching Android ASan/LeakSanitizer, UBSan, or thread-sanitizer run is claimed for the new JNI callback surface. Preserve this as a publication/support gap rather than treating strict compilation as sanitizer evidence. |
| Fault, pressure, and soak | blocked | Allocation failure, callback exception, and cleanup paths are code-reviewed, but device pressure, disconnect/reconnect, cancellation-at-state, process death, and long-running File/Stream soak remain unexecuted. |
| Physical-device lifecycle | blocked | Repeat the predecessor API 36 ARM64 emulator path on the exact-Core candidate through the release-minified packaged-AAR smoke. Even after that pass, an emulator is not foreground-service, Activity recreation, LAN reachability, thermal, or process-kill evidence on physical hardware. |

The pin/provenance source is ready for review, not publication. Any blocked row
that applies to the advertised support claim keeps that claim or release gate
open.
