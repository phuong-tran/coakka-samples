# JVM Runtime V2 Connector Release Notes

## 2.5.3

Repackages the unchanged public C ABI and wire protocol over exact Core `2.5.1`.
The native patch corrects Stream Lane cancellation/liveness behavior while the
typed File and Stream owner-grant connector API remains source-compatible with
2.5.2. Deploy publisher owners before subscribers during a rolling update and
roll back in the reverse order.

## 2.5.2

Adds typed File and Stream Lane owner grants to the JVM connector, including
validated control-plane reconstruction, exact-owner endpoint pinning, and live
owner-aware transfer/session tests. File grants are scoped to one prepared
transfer and remain usable only for bounded resume and idempotent completed
status while that owner retains the record; Stream grants are single-admission.
The native generation remains
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`.

## 2.5.1

Corrects Maven and registry documentation and adopts the file-scoped
Apache-2.0 and CoAkka Native Artifact License 1.2 package map. Native code, JVM
API, ABI, and the exact native generation are unchanged.

## 2.5.0-g4b65d0b2-f36c396

This candidate preserves the existing JVM runtime, File Lane, and Stream Lane
surface while retargeting all five embedded native payloads to exact Core
generation `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. The native ABI
adds replica-pinned lane owner grants; typed JVM owner-grant projection remains
outside this compatibility retarget and must not be inferred from the bundled
symbol alone.

## Maven Central 2.4.1

This is the first `coakka.runtime` Maven Central packaging release at
`io.github.phuong-tran.coakka:runtime:2.4.1`. It embeds exact native generation
`2.4.0+c2f53117`, so runtime identity remains `2.4.0` even though the immutable
JVM distribution version is `2.4.1`.

## 2.4.0-gc2f53117-0afb5e9

This release adds explicit embedded, outbound-only, and network-node startup
modes. Embedded and outbound-only runtimes do not listen and report local port
`0`. Network nodes own explicit bind and advertise endpoints and fail startup
when binding fails. The Maven artifact embeds exact native generation
`2.4.0+c2f53117` for all five supported platforms.

## 2.3.0-ga83ab412-3a84c7b

This candidate adds the independent `StreamLane` API with borrowed callback
buffers, bounded callback rules, receiver credit, ordered frame metadata,
pressure snapshots, and serialized close/drain ownership. The Maven artifact
embeds exact native generation `2.3.0+a83ab412` for all five supported
platforms.

## 2.1.0-g60ddf70d-4782dcd

This release adds `FileLane`, an independent bulk-transfer resource over the
public C ABI. The JVM surface exposes typed sender/receiver configuration,
SHA-256 calculation, receive preparation, send submission, sequence-based
progress waits, cancellation, retained terminal snapshots, stats, and draining
close ownership. The category label is JVM/JNA/JNI for ecosystem recognition;
the current bridge implementation is JNA.

The Maven artifact embeds exact native generation `2.1.0+60ddf70d` for Linux
ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64. Jar-content verification
requires exactly those five payloads. The 63-test regression suite, packaged
runtime smoke, clean Maven consumer, Spring Boot checks, and Quarkus checks pass
against the release candidate. A multi-quantum file transfer passes with byte
count and SHA-256 equality.

File bytes do not travel in runtime envelopes. Applications exchange the
transfer grant through their authenticated control plane and keep the lane in
long-lived service state. Direct TCP, TLS, and mutual TLS are explicit startup
profiles; secrets and local paths are never projected in snapshots.

## Unreleased

This update adds startup-configured TCP connection strategy, runtime
capability discovery, file-backed TLS/mTLS startup, and atomic same-mode
credential generation reload to the JVM connector. The same typed contract is
available through `RuntimeHandle` and `ConnectorOrchestrator` and is mapped by
the Spring Boot and Quarkus adapters without changing native semantics.

Rejected startup applies throw a connector exception containing the complete
structured native result. Explicit applies return that result directly. A
rejected TLS reload preserves and reports the previously active non-secret
generation and identity metadata; credential paths, PEM bytes, and private keys
are never projected.

Verification in this source slice includes:

- macOS ARM64 baseline ABI/layout, capability, supported-mode, lifecycle, and
  structured rejection tests
- macOS ARM64 full-capability advanced-mode and TLS generation preservation
  tests using the exact native library and verified archive/library
  digests
- the 63-test local-development JVM regression suite, kept separate from the
  strict TCP profile because legacy cases intentionally share one bind port
- Spring Boot and Quarkus property mapping tests
- same-repository adapter compilation against current JVM connector source
- a host-only Maven staging dry-run, packaged-jar native smoke, and external
  consumer smoke against the staged `1.4.1` connector artifact

Exact native evidence used by this update:

- public native generation: `1.4.1+9e02a51d`
- public native archive SHA-256:
  `ef31cd8bc709bd71d62dab0497f2513990f9023bda5e128631842ece5360394f`
- extracted runtime library SHA-256:
  `5935b613a7e9ff3662712d9af1c68d24d34460a58d5900d47d5a12341e754d79`

Linux ARM64/x86-64 and Windows ARM64/x86-64 connector execution is not yet
recorded. Native artifact evidence does not substitute for connector suite
execution on those platforms.

Publisher signing is absent; see the common platform-trust and troubleshooting
documentation for the distinction
between digests, release receipts, Apple/Windows publisher identity, and Linux
loader requirements.
