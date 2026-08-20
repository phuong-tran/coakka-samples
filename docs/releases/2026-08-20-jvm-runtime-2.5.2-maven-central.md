# JVM Runtime Maven Central 2.5.2

Status: published to Maven Central and registry-audited on August 20, 2026.

```text
coordinate: io.github.phuong-tran.coakka:runtime:2.5.2
connector source: 3ae74f43d061904d3bdc38a1d84d0479cd6c43bf
annotated tag: coakka-runtime-jvm-v2.5.2
annotated tag object: 5f7f416142178d903ee008727ae750a6972bbe20
native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
Central deployment: 9195e4ef-0c05-443e-be42-ccf754619501
OpenPGP fingerprint: 2FBD20F919F251E8D984A5EBF90740BDDBBE6638
```

Gradle consumers need only Maven Central:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.5.2")
}
```

Maven consumers use:

```xml
<dependency>
  <groupId>io.github.phuong-tran.coakka</groupId>
  <artifactId>runtime</artifactId>
  <version>2.5.2</version>
</dependency>
```

`RuntimeInfo.runtimeVersion` reports `2.5.0` because it identifies the sealed
native engine generation. Maven version `2.5.2` identifies the immutable JVM
connector distribution that exposes the replica-owner File and Stream Lane
surface.

## Candidate Identity

The production-signed Central bundle is 9,529,081 bytes with SHA-256:

```text
581d5ed3ed1ed8b549434f536ac66b478cc9b8c87f6a6345bd2b250f47f36a3d
```

Its five base artifacts have these SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| `runtime-2.5.2.pom` | `42c3eb25faeae3b2b2799078320c05cffe59c3ee0093409e8e525936399644d8` |
| `runtime-2.5.2.jar` | `e745b7ebafebc76cdfea203768bf1025c8839f35f25e6ae8f0fcf915a6f61091` |
| `runtime-2.5.2-sources.jar` | `afb8784e4744e9603b1aee204806c2f2d77d6809a2457d0539190a3a835e7574` |
| `runtime-2.5.2-javadoc.jar` | `cd26d5d000fbdc1935d71cdf9d86a5421ea13e4fccc0b7cefca214f13fbf564c` |
| `runtime-2.5.2.module` | `6de8fad200ad82b8606c09b816e603af28e2d9d930b796972c796ca503b83096` |

The frozen twelve-lane candidate lock is
`releases/runtime-2.5.2.lock.json` at Core release commit
`baa46aa9f7ae5206dda4030033da679a04fd11e5`. Its SHA-256 is
`b53d68c0e63f8d2fb030c24ef6861505aa3ef26f164118778fd1fcd38ad6bf54`.

## Central Audit

- The user-managed deployment reached `VALIDATED` with exact component purl
  `pkg:maven/io.github.phuong-tran.coakka/runtime@2.5.2` and an empty validation
  error map before explicit publication approval.
- After approval, the deployment reached `PUBLISHED`. For this deployment, the
  terminal Portal status response returned `purls=[]` with
  `errors.common=["Deployment components info not found"]`. This observation is
  retained as post-publication component-catalog evidence, not rewritten as a
  validation error. The exact component identity comes from the clean
  `VALIDATED` response and is independently corroborated by the public Maven
  repository audit below.
- The public Maven repository exposes 50 files: five base artifacts, their five
  detached signatures, and four checksum sidecars for each of those ten files.
  All 50 public files are byte-identical to the signed candidate tree.
- All five public detached signatures verify against the production OpenPGP
  key above.
- The runtime jar contains the exact Linux ARM64/x86-64, macOS ARM64, and
  Windows ARM64/x86-64 native payload set declared by the candidate.
- Disposable consumers with an empty Gradle user home resolve only from Maven
  Central and complete request/reply on Java 8 and the current local JVM.
- The packaged JVM compatibility matrix passes Java 8, 11, 17, 21, 25, and 26.
  The 73-test JVM suite includes owner-pinned File receive and Stream publish
  grant integration coverage.

Private connector GitHub Actions run `32364311746` was attempted twice. The run
ID is maintainer-only evidence because the private repository is not publicly
accessible. GitHub blocked all 17 jobs in both attempts before their first step
because of the account payment or spending-limit state. The run therefore
produced no executable CI evidence. This is recorded as blocked external CI,
not as a test failure and not as a passing CI claim. Local release control,
signed-candidate checks, registry-byte and signature audits, and
public-coordinate consumers provide the independent release evidence above.

## Known Metadata Limitation

The immutable `2.5.2` POM records its SCM URL and connection against the private
connector source repository. Anonymous requests to that SCM URL return 404.
The POM project, documentation, sample, and license URLs are public, and the
published sources jar remains available from Maven Central. A future patch must
point SCM metadata at a truthful public source location if that source mirror is
opened; Maven Central does not permit replacing the published `2.5.2` bytes.

## License Scope

The JVM connector, bindings, sources, and documentation are Apache-2.0. The
CoAkka Native Artifact License 1.2 applies only to bundled native Core files.
Application use remains free, including commercial and production use.

## Public Links

- [Maven Central coordinate](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/runtime/2.5.2)
- [Signed public artifact tree](https://repo1.maven.org/maven2/io/github/phuong-tran/coakka/runtime/2.5.2/)
- [Runnable JVM samples](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/jvm)
