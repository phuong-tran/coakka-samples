# JVM 3.0.0 release qualification

3.0.0 is the new managed API baseline at
`io.github.phuong-tran.coakka:runtime`. Historical2.x artifacts remain immutable;
migrate and recompile callers. In particular, Core uint32 fields use Long,
startup/delivery facts use grouped Core-issued snapshots, and retired startup
accessors are removed. Core remains2.5.1/f6b61768, ABI1.

Freeze one connector source, complete five-target Core/JNI pair set and public
runtime dependencies. Use the real ProGuard shrinker, narrow JNI/callback keep
rules, Java8 APIs and major52 classes. Verify deterministic archive order/time,
exact pair manifests, JNI_OnLoad-only exports, system dependency closure and
absence of private paths/debug/schema material. Retain private symbols outside
consumer artifacts. Core must not be rebuilt by a historical wrapper.

Execute the exact shrunk JAR from an installed public consumer and unrelated
working directory on every packaged target. Include checked-JNI owner tests,
Runtime/File/Stream, callbacks/GC, cancellation/close, pressure, finite resource
trends, cold/reused/relative caches and damaged/foreign/linked-input refusals.
The managed floor is Java8. Native Windows ARM64 requires ARM64 JDK11 or newer;
an x64 Java8 process instead loads the x64 pair. Windows Java8 retains the
explicit ASCII native-cache limitation. A newer JVM is not floor evidence.

Run strict native warnings/static analysis and separate full-owned JNI/Core
ASan+LSan+UBSan and TSan diagnostics. Public VM/GC evidence does not establish
whole-VM native leak freedom or indefinite resource bounds. Keep tool failures
and finite measurement limits explicit; benchmarks cannot waive quality gates.

Prepare the runtime, sources, Javadoc and POM/dependency artifacts with exact
hashes. Canonical documentation and generated sample/catalog copies must agree.
Central publication requires the authorized source tag/mirror, PGP signatures,
namespace and registry checks at release time; no token or upload is needed to
qualify the private candidate. No public source/tag/upload occurs before the
user's release command. Detached signatures do not change the sealed JAR bytes.
