# Runtime Release Signing And Platform Trust

Release integrity and operating-system publisher trust are different controls.
Do not treat one as proof of the other.

## Current Project Stage

CoAkka is currently in a pre-commercial development and preview stage. Source
commit, build profile, target platform, and exact SHA-256 digests are sufficient
to distinguish internal builds while the team controls their distribution.
Neither an OS-native publisher signature nor a signed promotion receipt is a
gate for connector development, local qualification, or internal dry-runs.

The immutable-receipt tooling remains available for a later distribution stage.
A reviewed release identity should be enrolled when the project actually needs
authenticated promotion across a wider trust boundary. Do not create or present
an informal signature merely to make an internal build look commercially
signed.

## Trust Layers

| Layer | What it proves | What it does not prove |
| --- | --- | --- |
| SHA-256 checksum | Downloaded bytes match a recorded digest | Who approved or published the digest |
| Signed promotion receipt | A trusted CoAkka release key approved the exact manifest and artifact digests | Apple notarization, Windows publisher reputation, or malware-free behavior |
| Apple code signing and notarization | macOS publisher identity and Apple notarization state for exact bytes | CoAkka promotion approval on another platform |
| Windows Authenticode | Windows publisher identity and signature integrity for exact bytes | CoAkka promotion approval on another platform |
| Package or host policy | The deployment admitted an artifact under local policy | General trust outside that policy domain |

When signed promotion is enabled, CoAkka runtime v2 uses an additive receipt
signed by a dedicated, project-managed Ed25519 release key. The receipt covers
the candidate manifest identity and exact archive and inner-library digests.
Candidate manifests and uploaded binary assets are not rewritten to change
lifecycle state.

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
load a `.so`. For controlled internal builds, verify the source/build identity
and checksum. When signed promotion is enabled, verify its receipt as well,
then apply the deployment's package, filesystem, container, or host admission
policy. Hosts using IMA/EVM, fs-verity, signed distribution packages, or
another enforcement system must also satisfy that local policy.

An ad-hoc macOS signature or a self-issued Windows certificate can be useful
inside a controlled organization, but it does not create normal public
publisher trust or reputation. It must not be described as Apple notarization
or trusted public Authenticode.

## Why Native Signing Requires A New Candidate

Apple code signing and Windows Authenticode modify the signed file. Any archive
containing that file also receives a new digest. Therefore native signing,
repacking, timestamping, or notarization-ticket attachment must happen before
the final candidate digests are recorded. Adding it later requires a new
candidate identity and a complete digest, dependency, export, consumer, and
promotion verification pass.

The detached promotion receipt is different: it is an additive asset that
signs existing digests without changing the candidate binaries.

## Signed-Promotion Verification Flow

This flow applies only to a release that claims a signed promotion receipt.
Internal preview builds that make no such claim use recorded source/build
identity plus exact digests.

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
