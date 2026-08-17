# JVM Runtime Maven Central 2.4.1

Date: 2026-08-17

CoAkka Runtime for JVM is published to Maven Central as:

```text
io.github.phuong-tran.coakka:runtime:2.4.1
```

Gradle consumers need only Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.4.1")
}
```

Maven consumers use:

```xml
<dependency>
  <groupId>io.github.phuong-tran.coakka</groupId>
  <artifactId>runtime</artifactId>
  <version>2.4.1</version>
</dependency>
```

## Release Identity

- JVM distribution: `2.4.1`
- embedded native generation: `2.4.0+c2f53117`
- connector source commit: `6665d9f5eb7e4366a30c803cfde4443294a50b19`
- connector tag: `coakka-runtime-jvm-v2.4.1`
- Central deployment: `d98c14da-5815-42da-8be2-639aea6679f8`
- signed upload bundle SHA-256:
  `b36a4684ab43c110ef731835361971c9d3b7efe02fad6b789a1d29e8c1111980`
- public runtime jar SHA-256:
  `e541e9e073eb7c2fc29698a30dfe8340a275f53795c83df8807014321dd244ac`
- OpenPGP fingerprint:
  `2FBD20F919F251E8D984A5EBF90740BDDBBE6638`

`RuntimeInfo.runtimeVersion` reports `2.4.0` because it identifies the native
engine generation. Maven version `2.4.1` identifies the immutable JVM
distribution.

## Evidence

- Maven Central accepted and published one user-managed component at the exact
  coordinate above with no validation errors or warnings. The public POM,
  jars, module metadata, and signatures are byte-identical to the signed
  candidate.
- The signed bundle contains the runtime POM, main jar, sources jar, generated
  Javadoc jar, Gradle module metadata, five detached signatures, and checksums.
- All five detached signatures verify against the published OpenPGP key.
- Clean Java 8 and Java 26 consumers load the macOS ARM64 native payload and
  complete embedded request/reply against runtime `2.4.0+c2f53117`.
- Connector CI passes JVM compatibility, Java 8 consumer, framework adapter,
  documentation, and cross-platform source gates at
  [connector-gates run 31985456816](https://github.com/phuong-tran/coakkaJVMConnector/actions/runs/31985456816).
- The jar contains verified native payloads for Linux ARM64/x86-64, macOS
  ARM64, and Windows ARM64/x86-64. Matching-host execution claims remain those
  recorded in
  [Runtime Package And Platform Evidence](../runtime-package-platform-evidence.md).

## Public Links

- [Runnable JVM samples](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/jvm)
- [Artifact catalog and documentation](https://github.com/phuong-tran/coakka-publish)
- [JVM connector source](https://github.com/phuong-tran/coakkaJVMConnector)

This release does not publish `io.github.phuong-tran.coakka:logger`. Logger
remains a separately versioned future Maven Central lane.
