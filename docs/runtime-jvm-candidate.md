# Private JVM runtime candidate samples

Four Kotlin/Java examples use the exact real ProGuard candidate
`3.0.0` in `artifacts/runtime-jvm-candidate.properties`, not the
historical public2.5.2 artifact. The runtime JAR bundles Core2.5.1+f6b61768 /
ABI1 and five admitted Core/JNI pairs. Four ordinary public managed runtime
dependencies accompany it; no separate Core/provider library is installed.
Candidate targets are macOS ARM64, Linux ARM64/x64 and native Windows ARM64/x64.

Java8 is the consumer baseline. Build JDK17+ runs Gradle/Kotlin2.3.10 only;
Java sources use--release8, Kotlin targets1.8/Java8 APIs and owned classes must
be major52. Qualification uses actual Java8u504 on four matching targets;
native Windows ARM64 uses Microsoft ARM64 JDK11. Minimum OS execution and
registry/main promotion are recorded separately.

## Exact local inputs / separate build

Obtain the trusted candidate directory containing runtime.jar and its four
lib/ JARs. The properties catalog pins all five names, sizes and hashes.
Choose existing absolute work and Gradle cache directories; on the development
Mac both belong on mounted external APFS storage. Select a Java8 runtime home
separately from the build JAVA_HOME. For example:

```sh
export JAVA_HOME="/absolute/build-jdk17"
export GRADLE_USER_HOME="/absolute/gradle-cache"
export COAKKA_JVM_CANDIDATE_HOME="/absolute/admitted-jvm-candidate"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_SAMPLE_JAVA_HOME="/absolute/consumer-jdk8"
export COAKKA_SAMPLE_JVM_NATIVE_DIR="/absolute/work/native-cache"
bash runtime/jvm/basic/run.sh
bash runtime/jvm/deadletter/run.sh
bash runtime/jvm/java-basic/run.sh
bash runtime/jvm/java-deadletter/run.sh
```

The native cache path itself must not be pre-created; its parent exists and
the package loader creates/admit its private storage. It may be reused with
the same verified pair. Windows Java8 requires this native cache path to be
ASCII; other source/package/work/CWD paths need not be transliterated. This is
the approved Java8 loading limitation, not a Core filesystem restriction or
permission to use reflection, change user.home or fall back after refusal.

Java8's Windows launcher can also lose characters outside the active code
page in absolute command-line classpath entries, before application main.
For the Gradle workflow below, use ASCII source/work/candidate roots on
Windows. Direct installed consumers can use explicit relative classpath
entries from their application directory; that shape is verified with a
Unicode application directory without renaming it. This does not claim
arbitrary Unicode launcher arguments or Unicode native-cache support.
Do not change system locale, private encoding properties or security policy.

The dedicated Gradle build is runtime/jvm, not the unrelated root logger/
Spring/Quarkus graph. Run the wrappers above or, with the same environment:

```sh
bash gradlew -p runtime/jvm :basic:run --offline --no-daemon
```

Native Windows uses its native build JDK/Gradle wrapper in PowerShell after
setting the same variables to absolute Windows paths:

```text
.\gradlew.bat -p runtime/jvm :java-basic:run --offline --no-daemon
.\gradlew.bat -p runtime/jvm :java-deadletter:run --offline --no-daemon
```

The basic/deadletter task names run Kotlin. WSL ELF execution is not Windows
proof. If app protection prompts for a trusted test executable, allow it
manually; do not disable trust/security controls.

## Admission, lifecycle and limits

One shared cold build owner reads a bounded duplicate-safe Properties catalog,
admits exactly five regular JAR inputs by size/hash and uses only their explicit
classpath. No old public-coordinate/native-path/source fallback, registry
runtime resolution or unknown artifact selection. Input trees/catalog must be
trusted and stable during the build; hashes are integrity, not publisher
signatures or protection from a hostile toolchain/filesystem. Standard Gradle/
Kotlin build tools must already be available in the selected external cache;
--offline refuses missing plugin inputs rather than downloading product bytes.
The wrapper distribution is a build tool, not an embedded-Core download.

Build outputs, validated Gradle caches and native cache are deliberately retained
under the chosen roots for reuse, not deleted by an unsafe shell EXIT trap.
Gradle owns its compiler/application subprocess lifecycle. Qualification
separately bounds/reaps real matching-host consumers and records residue;
forced termination/power loss is not a graceful Core-stop guarantee or hostile
process-tree sandbox. Build worker/heap limits do not tune the consumer VM.

Kotlin basic keeps the beginner CoAkka.local/use path and its1s ask, checks the
text response and does not inspect hidden facade state. Other examples use
public orchestrator surfaces, keep2s asks,3s Java future/shutdown and1s Kotlin
observation. Java callback observation has its own1s bound, independent of the
unchanged request/future/shutdown limits. Core owns effective facts and typed causal reasons; samples
omit incidental queue/port/monitor/generation tuning and validate nullable
original envelopes. A coroutine observation is canceled/joined before shutdown;
Java code observes the actual callback, not AtomicReference timing after ask.
Operation and cleanup errors remain visible. Direct orchestrator.close is not
the SDK shutdown contract: use suspend/future shutdown, never reflection.

These examples do not rebuild/re-shrink Core/JNI/the runtime JAR, tune the JVM
for a benchmark or establish performance/resource neutrality. Exact native,
real ProGuard, memory/race and finite resource evidence is reused by identity,
not replaced by a single echo. Other samples/scenarios/client/inspect/Android/
camera/legal/signing/global/coordinated-publication controls remain separate.
