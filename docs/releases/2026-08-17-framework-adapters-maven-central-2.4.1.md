# Spring Boot And Quarkus Maven Central 2.4.1

Date: 2026-08-17

The CoAkka framework adapters are published to Maven Central as:

```text
io.github.phuong-tran.coakka:spring-boot-starter:2.4.1
io.github.phuong-tran.coakka:quarkus-extension:2.4.1
```

Gradle consumers need only Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.phuong-tran.coakka:spring-boot-starter:2.4.1")
    // Or, for a Quarkus application:
    implementation("io.github.phuong-tran.coakka:quarkus-extension:2.4.1")
}
```

Applications select their own Spring Boot or Quarkus platform version. The
adapter POMs do not import either framework BOM and depend on public CoAkka
Runtime `2.4.1`.

## Release Identity

- connector source commit:
  `f3a5d91bfaa13933b07b047548e3211e02a66f24`
- Spring Boot Central deployment:
  `75a0f1f3-774d-4ab6-94e9-86e205993e80`
- Quarkus Central deployment:
  `175d1886-f094-4c3a-bb26-b36f82d9bc06`
- Spring Boot signed bundle SHA-256:
  `96867ffc8bb38b4c8af341bdecc8d960e3ef92155a5f2fb030a82b0ecbe6fe59`
- Quarkus signed bundle SHA-256:
  `9e0a42e3bc170d52c3f2203669b89e7bd4427d4fc857eac70b1e06b0e40ca8b6`
- OpenPGP fingerprint:
  `2FBD20F919F251E8D984A5EBF90740BDDBBE6638`

## Public Artifact Hashes

| Artifact | SHA-256 |
| --- | --- |
| Spring Boot Javadoc jar | `47e8987e1b14807b23e08a98c26f6e50aed6ee13c8566a584b82ca8d6bc92087` |
| Spring Boot sources jar | `538e869bbedde17cedb6fa3ad4b2a81d9e04d2392a8d45efe032b57ad0d23102` |
| Spring Boot main jar | `0cefb5f2d9e7dde23e0f48d8fdc2f18ba6898a1ca6698df86f47a78fabf2d379` |
| Spring Boot Gradle module | `666e1870fbaeac9bc9f6e3eb12565bd58f80925c6df05ad5f2e580863899d074` |
| Spring Boot POM | `dd1c2e0195dd239b9c2c0ce2d06902dc5491ba7871acdf421d89d1b3a544d317` |
| Quarkus Javadoc jar | `a2da676aafb7d65de99f28af908d497f33745c7c85d1e5f19a6d6fc46750926b` |
| Quarkus sources jar | `9308fee7dc7880baf02995aa37346e494070be1e44b721e99922977e79a4eb7f` |
| Quarkus main jar | `b844e47b851c8b25a08a1774aa95b012f8d5297cf4170639e6122b413867adc3` |
| Quarkus Gradle module | `efb6c3016b6ad8731e6f8a704615cdb1b5a0de80816a4bab401e3f4dcbf9af5f` |
| Quarkus POM | `121f232c65c29508d2fd1adeeaa6f14b440614a42b38ebe5a06aed6dec80ffb5` |

## Evidence

- Maven Central validated both user-managed deployments before the explicit
  publish calls. Both deployments reached `PUBLISHED` without validation
  errors.
- Each production bundle contains exactly 50 files: POM, main jar, sources
  jar, generated Javadoc jar, Gradle module metadata, five detached
  signatures, and four checksums for each signed file.
- All ten detached signatures resolve independently to the production OpenPGP
  fingerprint above.
- All ten public base artifacts are byte-identical to the immutable signed
  candidates.
- An empty-cache Maven Central consumer completes request/reply with Spring
  Boot `3.5.16` on Java 17.
- An independent empty-cache Maven Central consumer builds a Quarkus `3.35.2`
  fast-jar and completes HTTP-to-runtime request/reply on Java 17.
- Connector CI run `32030679227` passes Spring Boot `3.2.7`, `3.4.13`, and
  `3.5.16`; Quarkus `3.20.4`,
  `3.27.4`, and `3.35.2`; the complete connector gate; and platform-source
  jobs on macOS, Linux, and Windows.

The adapters remain Java 17 application-host glue over public Runtime `2.4.1`.
This release changes packaging and distribution only; it does not change the
native ABI, runtime lifecycle, queues, threads, descriptors, transport,
ownership, or shutdown law.

## Public Links

- [Spring Boot artifact](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/spring-boot-starter/2.4.1)
- [Quarkus artifact](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/quarkus-extension/2.4.1)
- [Spring Boot sample](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/scenarios/customer-crud/spring-boot-starter-local)
- [Quarkus sample](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/scenarios/customer-crud/quarkus-local)
