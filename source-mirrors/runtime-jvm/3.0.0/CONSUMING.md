# Consuming The JVM Connector

Version3.0.0 uses Maven coordinate `io.github.phuong-tran.coakka:runtime` and
requires source migration/recompilation from2.x. Older published coordinates
remain immutable. Source version does not establish registry availability;
use the complete artifact selected for the release channel.

The shrunk JAR embeds Core `3.0.0`, commit
`1522b4a9f30da1039ef8c7d198bbecfc749a2456`, ABI1 and five host bridge pairs.
The managed minimum is Java8/major52. Native Windows ARM64 uses ARM64 JDK11
or newer; x64 Java8 on an ARM machine uses the x64 pair. Exact archive and
member checksums belong to the selected candidate catalog, independently of
the version string. Other JVM/platform combinations require their own evidence.

## Ordinary Loading

The JAR verifies both native members' recorded size/digest, installs a complete
immutable pair in its loader-owned cache, and loads the runtime before its
bridge. Consumers do not install a separate native runtime or implementation
dependency. Keep the normal managed dependencies with the JAR. Do not unpack
natives beside the JAR or select a library from an unrelated build tree.

The default cache is `.coakka-runtime-native-v1` under the JVM user's home.
Choose deployment storage before first load with
`-Dcoakka.runtime.nativeDir=/trusted/writable/cache` if needed. Relative values
resolve against the process working directory, not the JAR directory. An
explicit blank value fails. The loader never changes the application's home or
silently chooses another directory when admission/loading fails.

Cache admission is bounded: eight retained pair slots, a five-second admission
budget and member integrity checks. Incomplete, foreign, linked or mismatched
entries are not silently repaired. Stop every JVM using the cache before
operator cleanup; loaded images are not request-owned temporary files.
Directory/ownership security checks remain enabled.

## Windows Java8 Path Limit

The tested Windows Java8 native loader requires an ASCII native-cache path.
For example, configure `-Dcoakka.runtime.nativeDir=D:/CoAkka/native-cache` when
the default home/cache contains non-ASCII characters. This is an explicit
deployment choice, not an automatic fallback or a restriction on message text.
Unicode values are independently tested. Do not generalize this observed JVM
loading limit to Windows filesystem support.

## Application Ownership

Follow the current [README](README.md) for actual Kotlin/Java identifiers.
Own one runtime participant per process, retain callbacks until accepted work
retires, and await the documented asynchronous shutdown. Closing a view or
abandoning a future does not prove native cancellation.

Leave advanced groups absent and inspect Core's accepted startup/network/
delivery facts. AUTO/MULTIPLEX and delivery modes are Core facts, not connector
tables or platform guesses. The connector owns JVM values, FFI safety and
loading; Core owns validation, probes, defaults and outcomes.

## Shrinking And Verification

Native entrypoints bind through a fixed private bridge without reflection.
Keep only required native/callback lookup names under the real shrinker, not
the whole connector package. Execute the actual shrunk JAR, not source-build
production classes. Qualified Java8 classes are major52. Loader refusal,
cold/reused/relative caches, pairing, typed failures, File/Stream and lifecycle
controls remain artifact admission requirements.

Finite results do not certify publication, newer JVMs, whole-process memory or
wider platforms. See [Release qualification](RELEASE.md) and
[JVM compatibility](JVM_COMPATIBILITY.md) before expanding support.
