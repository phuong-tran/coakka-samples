# Native Addons With Runtime 3.0.0

Use a file acquisition addon when your application needs to obtain a file from
an external source before sending it through File Lane. If the file already
exists locally, use File Lane directly. These addons currently provide a native
C API; installing a Runtime language connector does not install an addon binding.

## What Was Checked

The 2026-11-02 check combined the unchanged published addon archives with the
released Runtime 3.0.0 Core. No addon was re-released or silently replaced.

| Addon | Linux x86-64 in WSL2 | Native Windows x86-64 |
| --- | --- | --- |
| HTTPS, S3/MinIO, Azure Blob, GCS | Passed | Passed |
| OCI Registry, Hugging Face Hub, GitHub Release | Passed | Passed |
| Google Drive, Dropbox | Passed | Passed |
| WebDAV | Passed with Digest SHA-256 | Passed with explicitly selected Basic over HTTPS; see below |
| SFTP | Passed with the ECDSA fixture | Passed with the RSA profile below |
| Local Drop | Passed | No Windows package; Linux/macOS only |

The checks acquired a file, verified its size and SHA-256 at staging and the
receiver, rejected invalid content and existing destinations, and exercised
shutdown. WSL2 also ran the complete installed C test scenarios. These are
controlled service tests, not certification of every cloud account or server.

This check does not requalify Linux ARM64, macOS ARM64 or Windows ARM64 with
Runtime 3.0.0. The five Runtime native artifacts remain a separate matrix;
Local Drop retains its three declared Linux/macOS targets. WSL2 uses the Linux
x86-64 artifact and is not a sixth native artifact.

## Windows Settings To Check

**WebDAV:** the audited Windows addon did not complete the Digest SHA-256
scenario. Explicit Basic authentication over verified HTTPS passed. Choose that
mode only if your server and application policy permit it; there is no automatic
fallback. Keep certificate and hostname verification enabled. Applications that
require Digest SHA-256 should wait for a validated addon update on Windows.

**SFTP:** the passing Windows profile used RSA 3072-bit keys, RSA/SHA-2
authentication, a PEM private key with its matching `.pub` file alongside it,
and a server permitting `diffie-hellman-group16-sha512`. Keep the expected host
key SHA-256 pin. The ECDSA/default-server scenario used on Linux did not pass on
the audited Windows package; check your server's permitted settings before
deployment. Do not enable legacy SHA-1 algorithms to reproduce this profile.

## Versions And Application Ownership

The eleven non-SFTP providers retain native `1.1.0+d1032f6d`; SFTP retains
`1.2.0+88b9a047`. Addon versions advance independently of Runtime. The old
manifests and archives remain unchanged; their compatibility fields alone do
not establish Runtime 3.0.0 behavior.

Your application owns credentials, the expected file identity, destination
authorization, and the decision to use the received file. Keep the sender lane
alive until the addon has stopped and been destroyed. Accept the file only after
the receiver reports `COMPLETED + OK`.

The compatibility check is complete for the scopes above. A new addon release
still requires package cleanup and the remaining platform and release checks;
this page does not open that release gate.

Continue with [choosing an addon](runtime-addons.md#choose-a-file-acquisition-provider),
the [File Lane guide](runtime-file-transfer.md), or the [current package list](../../common/current-packages.md).
