# CoAkka Native Artifact License 1.2

Effective: 2026-08-20

This license applies only to CoAkka Native Artifacts that include or explicitly
reference version 1.2 of these terms. It does not replace the terms shipped
with an older release unless that release explicitly adopts version 1.2.

This is a public-use native artifact license, not an OSI-approved open source
license. Connector source, generated language bindings, type declarations,
sample source, package documentation, and public documentation may be provided
separately under the Apache License, Version 2.0. A package can therefore be a
file-scoped multi-license distribution: Apache-2.0 for its language-level
material and this license for its bundled Native Artifacts.

## Definitions

`Licensor` means the copyright owner or entity authorized to distribute the
Native Artifacts under this license.

`Native Artifacts` means compiled CoAkka native libraries and executables,
native headers required to consume them, native symbol or debug files, and
native-only provenance, manifest, checksum, or signature material that includes
or explicitly references this license. Connector source, generated language
bindings, type declarations, examples, and documentation are not Native
Artifacts merely because they are distributed in the same package.

`Application` means a product or service that uses the Native Artifacts as an
internal component to provide functionality independent from CoAkka. CoAkka is
not the primary or substantial product offered to the application's users, and
those users are not given a hosted CoAkka runtime or substantially equivalent
CoAkka service.

`Managed CoAkka Service` means a product or service offered to third parties
whose primary or substantial value is providing hosted access to CoAkka's
runtime, connector, routing, delivery, management, or substantially equivalent
functionality. This includes a hosted CoAkka runtime, a CoAkka control plane, a
service through which customers deploy or operate workloads directly against
CoAkka, and an appliance or cloud image whose primary purpose is to provide
CoAkka functionality.

## Free Application Use

Subject to this license, the Licensor grants you a non-exclusive, worldwide,
royalty-free license to download, copy, and use the Native Artifacts, and to
redistribute unmodified Native Artifacts as part of an Application.

You may, without a separate agreement:

- use every capability included in a Native Artifact in development, test, CI,
  evaluation, proof-of-concept, and production environments
- use the Native Artifacts for internal business operations and commercial workloads
- build, operate, sell, and distribute Applications that use or bundle
  unmodified Native Artifacts
- operate a SaaS, hosted application, or customer-facing service that uses the
  Native Artifacts as an internal component, provided the offering is not a
  Managed CoAkka Service
- reproduce and cache unmodified Native Artifacts inside your organization and
  its controlled build, deployment, and support environments
- run and internally cache official CoAkka sample images
- provide integration, consulting, and support services for Applications that
  use CoAkka, provided those services do not offer a Managed CoAkka Service

These permissions apply equally to individuals and organizations regardless of
their size or industry. A cloud provider or other infrastructure company may
use CoAkka internally and in its independent Applications on the same terms as
any other user. The restriction below is based on what is offered to third
parties, not on the identity of the user.

When redistributing a Native Artifact with an Application, you must preserve
the applicable copyright, license, trademark, checksum, and provenance notices
and make a copy of this license available with the redistributed Native
Artifact.

## Reserved Uses

The following uses require a separate written agreement with the Licensor:

- selling, hosting, or offering a Managed CoAkka Service
- selling, licensing, renting, or redistributing the Native Artifacts as a
  standalone product or where they provide the primary or substantial value of
  the offering
- publishing an appliance, container image, cloud image, runtime platform, or
  marketplace offering whose primary purpose is to provide CoAkka functionality
- presenting modified, repackaged, or third-party artifacts as official CoAkka
  Native Artifacts

You may not:

- remove or obscure copyright, license, trademark, checksum, or provenance
  notices
- reverse engineer or modify the Native Artifacts except where that restriction
  is prohibited by applicable law
- use the CoAkka name, package names, artifact names, image names, or other
  project identifiers in a way that implies endorsement of an unofficial fork,
  hosted service, or product

## No Runtime Activation Gate

This license states legal permissions and restrictions; it is not a runtime
activation mechanism. Publisher signing, platform trust, checksums, release
receipts, capability introspection, and runtime license-status fields do not
create an additional feature or production-use fee for a Native Artifact
distributed under this license. Local operating-system or organization
security policy may still require its own signing, integrity, or admission
checks.

## No Warranty

The Native Artifacts are provided as-is, without warranties or conditions of
any kind, to the maximum extent permitted by applicable law.

## Limitation Of Liability

To the maximum extent permitted by applicable law, the project contributors
and artifact publishers are not liable for any direct, indirect, incidental,
special, consequential, exemplary, or other damages arising from use of the
Native Artifacts.

## No Patent Or Trademark Grant

These terms do not grant patent rights, trademark ownership, or rights to use
the CoAkka name beyond truthful reference to the project, compatible
integrations, and unmodified official Native Artifacts. Trademark use is
governed by the CoAkka trademark guidance distributed with the applicable
package or public documentation.

## Questions

For questions about whether an offering is a Managed CoAkka Service or requires
a separate agreement, use the public support path in the `coakka-samples`
repository.

## Legal Notice

This file is not legal advice. If intended use depends on legal interpretation
of these terms, consult qualified counsel or request a separate written
agreement.
