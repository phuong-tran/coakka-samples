# Spring Boot And Quarkus Maven Central 2.5.2

Date: 2026-08-20

The CoAkka framework adapters are published to Maven Central as:

```text
io.github.phuong-tran.coakka:spring-boot-starter:2.5.2
io.github.phuong-tran.coakka:quarkus-extension:2.5.2
```

Both adapters depend only on public CoAkka Runtime `2.5.2`. Applications select
their own Spring Boot or Quarkus platform version; adapter POMs do not import an
application framework BOM.

## Release Identity

- connector source commit:
  `d40ffe8e8212329c3b3ec7ce928c68d33a626655`
- public Apache source commit:
  `83919e21f5ae0a20518783aed65671a3168d1dcf`
- public source tag: `framework-adapters-2.5.2`
- Runtime dependency: `io.github.phuong-tran.coakka:runtime:2.5.2`
- native generation carried by Runtime:
  `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`
- Spring Boot Central deployment:
  `1cc4e567-e950-41ab-9c14-ee463e663770`
- Quarkus Central deployment:
  `16b32b4f-6235-4750-afb7-cb0c256713c0`
- Spring Boot signed bundle SHA-256:
  `0da79883e279dffc9038a5c4ae704a616c9ecde6162ecc918b440d2314ea7295`
- Quarkus signed bundle SHA-256:
  `4e2f3687a9da486c4db676aee9653b8527eb9993f9063cfa4321cbf5f4ac84b3`
- OpenPGP fingerprint:
  `2FBD20F919F251E8D984A5EBF90740BDDBBE6638`

## Public Artifact Hashes

| Artifact | SHA-256 |
| --- | --- |
| Spring Boot POM | `086b96d56ccbc086ad0fce59d21eb40a4560e2bc14a38977caef6824f086d1f6` |
| Spring Boot main jar | `348d532af015e59c6b51bd1fa806994d2d5eca36f19fc773ded9817d22f86f9e` |
| Spring Boot sources jar | `09f63de4b4ce6122f64e491706f315c1676fc9dfc31d131bc90d55c1834aaaaa` |
| Spring Boot Javadoc jar | `1462e403c5018b80083ccf16a43865cc50e77a0cd6f5c8df0d6e20abfd7241b2` |
| Spring Boot Gradle module | `d4e7560d19767fc9cdc8f731208b4b1754afe644a6ac7c6f6a7d38ca11f5e6d5` |
| Quarkus POM | `15db1de1f33e4b908f1c82f4ad8cf7bb56c608ab53d8f7a84a5281ea43db415e` |
| Quarkus main jar | `dd074e89dbf47a230f0d910e4e57f331fa77401cdc760373c51d05c6661ea1d3` |
| Quarkus sources jar | `0d2cb979113b6bba0540894f6452af0866a2a4ea340a89739d8950f01991cf2e` |
| Quarkus Javadoc jar | `f7c7bd941177f23434610a37a5b87a5b0f7c60cf883ab6ab733cecd34fd0ba6f` |
| Quarkus Gradle module | `07b33f398b030457ce78e47de0a42e40330eff44d3f2544915a7b555d90b68ae` |

## Evidence

- Maven Central validated both user-managed deployments with the expected
  component purl and no validation errors before the explicit publish calls.
- Both deployments reached `PUBLISHED`. The ten public base artifacts above
  are byte-identical to the production-signed candidates.
- Each bundle contains exactly 50 files: five base artifacts, five detached
  signatures, and four checksums for every base artifact and signature.
- All ten detached signatures independently resolve to the production OpenPGP
  fingerprint above.
- The public source tag is anonymously reachable. Its source manifest and all
  projected adapter source files are byte-identical to the release checkout.
- Clean staged-coordinate consumers pass Spring Boot `3.2.7`, `3.4.13`, and
  `3.5.16`, plus Quarkus `3.20.4`, `3.27.4`, and `3.35.2`, on Java 17. Each
  consumer verifies Runtime `2.5.2`, native source identity, File Lane, Stream
  Lane, and owner-grant capabilities before completing request/reply.
- Empty-cache public Maven Central consumers complete request/reply with Spring
  Boot `3.5.16` and Quarkus `3.35.2`.

The private connector workflow requested for the release did not receive a
runner because of an account billing restriction; it failed before checkout
and is not counted as test evidence. The complete framework matrices above ran
locally from the clean release commit.

The adapters remain Java 17 application-host glue. This release changes their
Runtime dependency and exposes the already-public owner-aware lane APIs through
the existing orchestrator; it does not change the native ABI, runtime
lifecycle, queues, threads, descriptors, transport, ownership, or shutdown law.

## Public Links

- [Spring Boot artifact](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/spring-boot-starter/2.5.2)
- [Quarkus artifact](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/quarkus-extension/2.5.2)
- [Public adapter source](https://github.com/phuong-tran/coakka-samples/tree/framework-adapters-2.5.2/source-mirrors/framework-adapters/2.5.2)
- [Spring Boot sample](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/scenarios/customer-crud/spring-boot-starter-local)
- [Quarkus sample](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/scenarios/customer-crud/quarkus-local)
