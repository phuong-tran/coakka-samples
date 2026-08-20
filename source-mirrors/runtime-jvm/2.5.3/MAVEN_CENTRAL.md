# coakka.runtime Maven Central Release

The Maven Central publication is separate from the existing internal/static
publication so current Spring Boot, Quarkus, and staged consumers keep their
coordinates unchanged.

Central coordinates:

```text
io.github.phuong-tran.coakka:runtime:<major.minor.patch>
```

This coordinate represents the product name `coakka.runtime` while following
Maven's group/artifact naming convention. The future logger coordinate is
reserved as `io.github.phuong-tran.coakka:logger`; this Runtime release does not
publish or reserve a logger version.

Published POM metadata links users to:

- [canonical artifacts and documentation](https://github.com/phuong-tran/coakka-publish)
- [runnable samples and learning material](https://github.com/phuong-tran/coakka-samples)
- [the immutable public Runtime source mirror](https://github.com/phuong-tran/coakka-samples/tree/runtime-jvm-2.5.3/source-mirrors/runtime-jvm/2.5.3)

After the Portal reports the selected version as `PUBLISHED`, Gradle consumers
use:

```kotlin
dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.5.3")
}
```

Maven consumers use:

```xml
<dependency>
  <groupId>io.github.phuong-tran.coakka</groupId>
  <artifactId>runtime</artifactId>
  <version>2.5.3</version>
</dependency>
```

The Central artifact supports Java 8 and newer JVMs. It does not create a
separate artifact for every JVM release. Optional newer-JVM implementations may
be added behind the same API only after workload and compatibility evidence
justify them.

The `2.5.3` Central packaging candidate embeds the exact Core `2.5.1`
generation declared by the release `gradle.properties`. Consequently,
`RuntimeInfo.runtimeVersion` reports `2.5.1`;
the Maven version identifies the immutable JVM distribution, while runtime
identity continues to identify the native engine generation.

The release JAR always requires and embeds the same explicit platform set:
`macos-aarch64`, `linux-aarch64`, `linux-x86_64`, `windows-aarch64`, and
`windows-x86_64`. The build host does not select or replace an advertised
platform. Missing any member fails staging and JAR verification before signing.

## Public Surface

Uploading the bundle publishes the runtime jar, Kotlin sources, generated
Javadoc, dependency metadata, native runtime binaries, and their signatures and
checksums. The JARs carry Apache-2.0 terms for connector material, the CoAkka
Native Artifact License 1.2 for bundled native material, an explicit
`PACKAGE-LICENSE.md` scope map, and `NOTICE`. Maven POM license entries describe
both scopes; they are not alternative licenses for the same files.

Do not upload until all of these external gates are closed:

- `io.github.phuong-tran` is visible as a verified namespace in the Central
  Publisher Portal; the `io.github.phuong-tran.coakka` subgroup is published
  beneath that verified namespace
- the clean release version is final and has never been published
- the source commit and exact native generation are frozen and tagged
- the public `runtime-jvm-2.5.3` source tag is anonymously reachable and its
  complete manifest is byte-identical to the release checkout
- the production PGP public key is distributed to a supported keyserver
- matching-host package/runtime evidence passes for every advertised native
  platform
- a Publisher Portal user token is available only in the release environment

Central releases are immutable. A rejected or incorrect version must be fixed
under a new version, never overwritten.

## Signing Inputs

Use the production secret key through the local GPG agent and OS keychain. The
Runtime release path rejects private-key and passphrase Gradle properties or
environment variables. Export only the terminal binding, unlock the key through
pinentry, and select the complete fingerprint explicitly:

```sh
export GPG_TTY="$(tty)"
printf 'coakka-runtime-signing-unlock' | gpg \
  --local-user 2FBD20F919F251E8D984A5EBF90740BDDBBE6638 \
  --armor --detach-sign >/dev/null
```

The 16-character long key ID is intentionally insufficient. Release commands
must pass the 40-character fingerprint, `coakkaMavenUseGpgAgent=true`, and the
GPG executable. The public key must already be distributed separately; no
bundle task exports or uploads private key material.

## Build And Verify

Candidate `2.5.3` is not published yet. The commands below remain local until
all five matching-host native payloads, the public source tag, signatures, and
final bundle hashes are frozen.

```sh
scripts/test-runtime-jvm-signing-preconditions.sh

./gradlew :v2:jvm:bundleRuntimeJvmForMavenCentral \
  -PcoakkaV2JvmVersion=2.5.3 \
  -PcoakkaMavenCentralNamespaceVerified=true \
  -PcoakkaMavenUseGpgAgent=true \
  -Psigning.gnupg.executable=gpg \
  -Psigning.gnupg.keyName=2FBD20F919F251E8D984A5EBF90740BDDBBE6638
```

The build fails closed for a dirty worktree, a non-clean version, an
unacknowledged namespace, a missing or ambiguous GPG-agent fingerprint, legacy
private-key/passphrase inputs, license drift, incomplete POM metadata, missing
artifacts, missing ASCII-armored signatures, bad checksums, empty
sources/Javadoc, or an invalid package shape.

Expected output:

```text
v2/jvm/build/central/runtime-2.5.3-central-bundle.zip
```

Run the external Java 8 consumer against the exact staged Central publication:

```sh
./gradlew :v2:jvm:verifyRuntimeJvmCentralConsumerJava8 \
  -PcoakkaV2JvmVersion=2.5.3 \
  -PcoakkaMavenCentralNamespaceVerified=true \
  -PcoakkaMavenUseGpgAgent=true \
  -Psigning.gnupg.executable=gpg \
  -Psigning.gnupg.keyName=2FBD20F919F251E8D984A5EBF90740BDDBBE6638
```

Before upload, independently verify all five detached signatures with the
production public key:

```sh
artifact_dir=v2/jvm/build/central/bundle-root/io/github/phuong-tran/coakka/runtime/2.5.3
for signature in "$artifact_dir"/*.asc; do
  gpg --verify "$signature" "${signature%.asc}"
done
```

## Upload

Generate a Central Publisher Portal user token and expose its base64 bearer
value only to the release shell:

```sh
export CENTRAL_PUBLISHER_TOKEN='...'
scripts/upload_maven_central_bundle.sh \
  v2/jvm/build/central/runtime-2.5.3-central-bundle.zip \
  coakka.runtime-2.5.3
```

The script always creates a `USER_MANAGED` deployment. Review Central's
validation result in the Publisher Portal before manually publishing. Do not
switch the first release to automatic publishing.

After publication, resolve the immutable Central coordinate from a clean Maven
cache on Java 8 and a current supported JVM, and archive the Portal deployment
ID, source commit, native generation, key fingerprint, bundle SHA-256, and test
evidence with the release record.
