# CoAkka Framework Adapters 2.5.3 Source Mirror

This tagged directory is the public Apache-2.0 source projection for:

```text
io.github.phuong-tran.coakka:spring-boot-starter:2.5.3
io.github.phuong-tran.coakka:quarkus-extension:2.5.3
```

**Free for application use, including commercial and production use.** The
adapter files in this mirror are Apache-2.0. The separately resolved Runtime
package carries its own file-scoped Native Artifact License; `LICENSE`,
`NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and `NOTICE` keep that mapping
available offline without treating the two licenses as alternatives.

The adapter source, resources, and tests are projected byte-for-byte from the
release source. `SOURCE-MANIFEST.sha256` records every projected source file.
The standalone build resolves
`io.github.phuong-tran.coakka:runtime:2.5.3` from Maven Central; it does not use
the historical checked-in Maven mirror or a local connector project.

This directory contains no native library. Runtime `2.5.3` is a separately
licensed package carrying the exact Core `2.5.1` generation recorded in this
mirror's `gradle.properties`.

From the `coakka-samples` repository root, build and test both adapters with:

```sh
./gradlew -p source-mirrors/framework-adapters/2.5.3 clean build --no-daemon
```

The build uses Java 17, treats compiler and Javadoc warnings as errors, checks
the exact Runtime dependency, verifies adapter manifest identities, and rejects
embedded native files. The source mirror contains no publication task or
registry credential path.
