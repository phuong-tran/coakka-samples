# Runtime Release Signing And Platform Trust

Release integrity and operating-system publisher trust are different controls.
Do not treat one as proof of the other.

## Artifact Identification

Every native artifact is identified by its source commit, target platform, and
exact SHA-256 digest. Current package metadata reports publisher signing as
absent unless a release manifest and release note explicitly state otherwise.

Checksums establish byte identity, not publisher identity. Do not describe an
ad-hoc macOS signature, a self-issued Windows certificate, or an unsigned
checksum record as Apple notarization or publicly trusted Authenticode.

## Trust Layers

| Layer | What it proves | What it does not prove |
| --- | --- | --- |
| SHA-256 checksum | Downloaded bytes match a recorded digest | Who approved or published the digest |
| Signed promotion receipt | A trusted CoAkka release key approved the exact manifest and artifact digests | Apple notarization, Windows publisher reputation, or malware-free behavior |
| Apple code signing and notarization | macOS publisher identity and Apple notarization state for exact bytes | Windows trust or cross-platform artifact identity |
| Windows Authenticode | Windows publisher identity and signature integrity for exact bytes | Apple trust or cross-platform artifact identity |
| Package or host policy | The deployment admitted an artifact under local policy | General trust outside that policy domain |

When a release includes a signed promotion receipt, the additive receipt is
signed by a dedicated, project-managed Ed25519 release key. It covers the
manifest identity and exact archive and inner-library digests. The receipt does
not rewrite uploaded binary assets.

This is self-managed release signing: the project controls the key and
publishes or delivers the corresponding trusted public identity. It is not a
claim that an external certificate authority, Apple, or Microsoft endorsed the
binary.

## Current Native-Signing Statement

CoAkka does not currently claim Apple Developer ID signing/notarization for its
macOS runtime library or Microsoft Authenticode signing for its Windows runtime
library unless a specific release manifest and release note explicitly record
that evidence. Absence of a warning in a local development setup is not signing
evidence.

A macOS linker may attach an ad-hoc code directory to a Mach-O library. Tools
then report values such as `adhoc`, `linker-signed`, and no Team ID. This checks
the local code-directory structure but carries no publisher identity and is not
Developer ID signing or notarization. Release metadata may therefore correctly
describe that artifact as unsigned at the publisher-signing layer.

Linux is the primary runtime deployment target. The standard Linux dynamic
loader does not require an Apple- or Microsoft-style publisher signature to
load a `.so`. Verify the source/build identity and checksum, verify a signed
receipt when one is supplied, and apply the deployment's package, filesystem,
container, or host admission policy. Hosts using IMA/EVM, fs-verity, signed
distribution packages, or another enforcement system must also satisfy that
local policy.

An ad-hoc macOS signature or a self-issued Windows certificate can be useful
inside a controlled organization, but it does not create normal public
publisher trust or reputation. It must not be described as Apple notarization
or trusted public Authenticode.

## Why Native Signing Changes Artifact Identity

Apple code signing and Windows Authenticode modify the signed file. Any archive
containing that file also receives a new digest. Therefore native signing,
repacking, timestamping, or notarization-ticket attachment must happen before
the final artifact digests are recorded. Adding it later creates new bytes and
therefore requires new digests plus dependency, export, and consumer
verification.

The detached promotion receipt is different: it is an additive asset that
signs existing digests without changing the candidate binaries.

## Signed Receipt Verification

Use this flow only when a release explicitly supplies and claims a signed
promotion receipt. Otherwise verify the recorded source/build identity and
exact digests without inferring a signature.

1. Obtain the archive, checksum file, promotion receipt, detached signature,
   and trusted release public-key identity through the approved channel.
2. Compute SHA-256 locally and match the exact filename and digest.
3. Verify the detached receipt signature in the CoAkka release namespace.
4. Confirm the receipt names the same release, manifest digest, archive digest,
   inner-library digest, platform, architecture, and release channel.
5. Inspect release notes for any platform-native signing claim. If none is
   present, treat native signing as absent.
6. Apply organization-specific admission or malware-scanning policy before
   deployment.

Example checksum commands:

```sh
# Linux
sha256sum coakka-runtime-archive.tar.gz

# macOS
shasum -a 256 coakka-runtime-archive.tar.gz
```

Never accept a checksum copied from the same untrusted location as the archive
without an authenticated receipt or separately trusted channel.

## Platform Warnings

- A macOS Gatekeeper warning does not prove digest corruption. Check the digest
  and release metadata, then follow organization policy; do not disable
  Gatekeeper globally as a troubleshooting shortcut.
- A Windows SmartScreen or publisher warning does not prove that bytes differ.
  Check the digest and release metadata, then use an organization-approved
  exception only when policy permits it.
- A Linux load failure is usually architecture, dependency, permissions,
  filesystem, or loader-path related. Code signing is not the default first
  diagnosis.

For decision steps and escalation, see [Troubleshooting](troubleshooting.md)
and [Contact And Support](contact-and-support.md).
