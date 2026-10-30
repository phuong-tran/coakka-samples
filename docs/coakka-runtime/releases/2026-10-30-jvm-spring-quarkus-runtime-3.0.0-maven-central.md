# JVM, Spring Boot and Quarkus Runtime 3.0.0 — Maven Central

Published2026-10-30 under namespace `io.github.phuong-tran.coakka`:

| Artifact | Coordinate | App baseline |
| --- | --- | --- |
| JVM Runtime | `io.github.phuong-tran.coakka:runtime:3.0.0` | Java 8 |
| Spring Boot | `io.github.phuong-tran.coakka:spring-boot-starter:3.0.0` | Java 17 |
| Quarkus CDI adapter | `io.github.phuong-tran.coakka:quarkus-extension:3.0.0` | Java 17 |

Use [the Java/Kotlin first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/jvm/registry-basic).
Framework dependencies use the starter/adapter coordinate above. This 3.0.0 API
requires source migration and recompilation of 2.x callers. Logger, HTTP Runtime,
Android and Mojo keep their own versions/publication scope.

Core is `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456`, ABI1. AUTO, MULTIPLEXED and monitoring off remain Core defaults.
The Runtime JAR includes five Core/bridge pairs: macOS ARM64, Linux ARM64/x64 and
Windows ARM64/x64. Plain JVM retains Java 8/major52; native Windows ARM64 uses an
ARM64 JDK11+. Frameworks add no second native runtime. Quarkus is a CDI/runtime
adapter; this does not establish native-image/AOT support.

Source entered main before the immutable public tags:
[runtime-jvm-3.0.0](https://github.com/phuong-tran/coakka-samples/tree/runtime-jvm-3.0.0/source-mirrors/runtime-jvm/3.0.0)
and [framework-adapters-3.0.0](https://github.com/phuong-tran/coakka-samples/tree/framework-adapters-3.0.0/source-mirrors/framework-adapters/3.0.0).
Both bind source commit `e62fcedf31e618f6108d7b67a4a676843fb54c6b`.
Tagged documentation records the preparation snapshot; this release receipt and
current guides record public availability. Historical2.x packages remain immutable.

The new signing primary fingerprint is `9C46DBC0E3989576C560315379B64BC0696B60D6`;
the [public key is available here](https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x9C46DBC0E3989576C560315379B64BC0696B60D6).
The old signing key remains available for older releases. All15 detached public
signatures verify against the new fingerprint in an independent public-only keyring.

Central deployment `c5870bec-8790-4fa7-8ada-109b3c9280e4` passed validation
before publication. All15 public artifacts,15 signatures and60 checksums match
the reviewed selection. All classfiles, API/keep rules and five native pairs
retain their qualified executable bytes. Packaging updates correct POM generation,
product links and a license-guide hyperlink; license terms remain unchanged.

Fresh Central-only Java 8 Kotlin/Java and POM-only consumers pass on Mac ARM64.
Spring Boot3.5.16 and Quarkus 3.35.2 Java 17 apps pass CRUD, typed route miss,
identity/defaults and zero pending requests. The smoke runner terminates only
its owned process; that is not a new native graceful-shutdown proof.
Earlier exact executable/native matching-host, lifecycle, shrink and resource
qualification remains authoritative; this is not a new post-upload run on all OSes.

| Public file | Bytes | SHA256 |
| --- | ---: | --- |
| runtime-3.0.0.jar | 4928890 | `988c9c864e6d89b5b6316edc02b98119b359cf997882abf40134fe6068c41f12` |
| runtime-3.0.0-sources.jar | 85965 | `8b697377fb7637236aa9fb7a842da809817d35b03ccd83d5e468b6640953fbeb` |
| runtime-3.0.0-javadoc.jar | 1025464 | `8f1d9609e33c6f53f9787ad7293a9dfc81e0a01e61f24681d6bd086d50f4e756` |
| runtime-3.0.0.pom | 3505 | `27d9ee7e7b7b2d7d46b0c64bf2c4e9249fe7379b73c1703ca248a917aa27af30` |
| runtime-3.0.0.module | 3329 | `0a4e2bb17cba2494997be4555ab92dbd64686aeccec0d0d1d05e80d1d7017ca4` |
| spring-boot-starter-3.0.0.jar | 19943 | `59373e7a79df72d02d9aa52a5d75c5385c7f25293876cfd849ea38a487a31c1c` |
| spring-boot-starter-3.0.0-sources.jar | 17512 | `fbc56c96cb483ac247c224595fda56782dc35767d374b96c427729554ec332d0` |
| spring-boot-starter-3.0.0-javadoc.jar | 106148 | `7f2ae377ab79f2deed265a82b9f199647ff2dac91a1ae0f886953d76329f30ca` |
| spring-boot-starter-3.0.0.pom | 2837 | `7d45957605908739a585b61a155bb2097dcd8301ae943f069c0e3619e0cfb3d2` |
| spring-boot-starter-3.0.0.module | 3964 | `6b6dffad4bfabb77bd5f2a63b508aa337cc254e68aff4001196367410d53295f` |
| quarkus-extension-3.0.0.jar | 19387 | `a73b7c8906cf84b9f8bb7599768f80e1c182fb27795daab6a1732971557e25c2` |
| quarkus-extension-3.0.0-sources.jar | 16428 | `a6002a20466143ae748437f5b87b4c174c02197440595e208d81d3f3c9b997d2` |
| quarkus-extension-3.0.0-javadoc.jar | 104599 | `3568aa06f0bd622b1514b99321b40867db78437b135b25e28e12e04c738c7636` |
| quarkus-extension-3.0.0.pom | 2811 | `ac3487804b57af5b095b1b576108c8e6161c211c8c2af8c726e9436ca38086a6` |
| quarkus-extension-3.0.0.module | 3946 | `2eda5732753a395d3f4abc23ff76b7ce2b56e9d0a116bab28fa867c2a87651db` |

[Current packages](../../common/current-packages.md) ·
[Platform evidence](../reference/runtime-package-platform-evidence.md).
