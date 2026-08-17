# JVM Logger Maven Central 1.2.2

Date: 2026-08-17

CoAkka Logger for JVM is published to Maven Central as:

```text
io.github.phuong-tran.coakka:logger:1.2.2
```

Gradle consumers need only Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.phuong-tran.coakka:logger:1.2.2")
}
```

Maven consumers use:

```xml
<dependency>
  <groupId>io.github.phuong-tran.coakka</groupId>
  <artifactId>logger</artifactId>
  <version>1.2.2</version>
</dependency>
```

## Release Identity

- JVM distribution: `1.2.2`
- embedded native generation: `1.2.1+f50756ebff0d`
- connector source commit: `21b8f1ffda5e68600b86462d2915f9d38473b92b`
- connector tag: `coakka-logger-jvm-v1.2.2`
- Central deployment: `b25be403-0900-4d8b-8e8c-be4cb43cd101`
- signed upload bundle SHA-256:
  `db6a623674d029841e434335bec50ca46afe01f699ec1247bd4d6e387d1f2666`
- public logger jar SHA-256:
  `13ad8527855883e232bf557968a4bfa3763b91728f6de82aa2142c35518650ad`
- OpenPGP fingerprint:
  `2FBD20F919F251E8D984A5EBF90740BDDBBE6638`

`LoggerInfoSnapshot.version` reports `1.2.1` because it identifies the native
logger engine. Maven version `1.2.2` identifies the immutable JVM distribution.

## Evidence

- Maven Central validated and published the user-managed deployment at the
  exact coordinate above.
- Public POM, main jar, sources jar, generated Javadoc jar, and Gradle module
  metadata are byte-identical to the signed candidate.
- The signed single-GAV bundle contains exactly 50 content files, including
  five detached signatures and MD5, SHA-1, SHA-256, and SHA-512 checksums.
- The jar carries one versioned native payload for Linux ARM64/x86-64, macOS
  ARM64, and Windows ARM64/x86-64; every payload matches the frozen staging
  manifest.
- The packaged jar runs on Java 8, 11, 17, 21, 25, and 26. A consumer with
  empty Gradle and Maven-local caches resolves only from Maven Central and
  completes a Java 8 emit/drain flow.
- Connector CI run `31989539107` passes compatibility, Java 8 consumer,
  documentation, framework, and cross-platform source gates.

## Public Links

- [Maven Central artifact](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/logger/1.2.2)
- [Runnable JVM logger samples](https://github.com/phuong-tran/coakka-samples/tree/main/logger/jvm)
- [Artifact catalog and documentation](https://github.com/phuong-tran/coakka-publish)

The older static coordinate
`coakka.logger:coakka-jvm-native-logger:1.2.1-gf50756ebff0d` remains available
for existing consumers. New JVM integrations should use the Maven Central
coordinate above.
