# JVM Compatibility And Evidence Scope

Java8 remains the managed minimum for the main runtime connector. Native
Windows ARM64 uses ARM64 JDK11 or newer; x64 Java8 on that physical CPU uses
the x64 native pair. Other packaged targets retain the Java8 floor. A newer compiler,
Gradle launcher or framework does not raise that consumer requirement.
Production Kotlin is restricted to Java8 APIs and JVM1.8 output; Java sources
use `--release 8`. Packaged connector classes must verify as major52.

The private ProGuard candidate executes on actual Java8u504 on macOS ARM64,
Linux ARM64 and physical Windows x64. Its native generation is Core
`1522b4a9f30da1039ef8c7d198bbecfc749a2456`, ABI1. The Windows Java8 ASCII
cache requirement is in [Consuming](CONSUMING.md). WSL controls the Windows
test; it is not Windows PE execution.

## Independent Evidence Axes

JVM version, OS version, CPU architecture, native pair and shrunk JAR identity
are separate axes. A successful Java8 Mac run does not certify another target;
a newer build JVM does not prove consumer execution. Historical public
Java11/17/21/25/latest-feature results belong to their released artifact, not
automatically to this private native migration. Each advertised JVM/platform
pair needs its exact candidate, loading policy and ordinary diagnostics tested.

The main artifact keeps one Java8-compatible surface. Higher-JVM facilities
may be optional only when a concrete capability needs them and ownership,
lifecycle, memory and performance evidence supports that boundary. Do not
select implementations implicitly by reflection or JVM number. No virtual-thread
or FFM extension is certified by these candidate tests.

## Native And Shrinker Boundary

The bridge registers a fixed private surface at load. The connector does not
use reflection, a separate implementation binding dependency or a second native
policy engine. Pair integrity and callback lifetimes remain required under
shrink/obfuscation. Narrow keep rules retain necessary lookup names; keeping
the whole package is not an acceptable shrink test.

The actual ProGuard JAR passes installed Java8 Runtime/File/Stream lifecycle
and finite-resource consumers on the three named targets. Scoped native memory
and race evidence is separate; collectibility of wrappers/callbacks does not
prove a whole-VM allocation bound or indefinite leak freedom.

Framework adapters retain their owning framework's minimum and lifecycle law.
Java8 connector qualification does not certify Spring/Quarkus or raise/lower
their baseline. Promotion must reconcile those consumers, minimum OS/wider
targets, legal/signing and coordinated downstream evidence.
