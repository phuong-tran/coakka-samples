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
| WebDAV | Passed with Digest SHA-256 | Normal Digest SHA-256 case failed; see the Windows limits below |
| SFTP | Passed with the ECDSA fixture | Normal connection case failed; see the Windows limits below |
| Local Drop | Passed | No Windows package; Linux/macOS only |

The checks acquired a file, verified its size and SHA-256 at staging and the
receiver, rejected invalid content and existing destinations, and exercised
shutdown. WSL2 also ran the complete installed C test scenarios. These are
controlled service tests, not certification of every cloud account or server.

This check does not requalify Linux ARM64, macOS ARM64 or Windows ARM64 with
Runtime 3.0.0. The five Runtime native artifacts remain a separate matrix;
Local Drop retains its three declared Linux/macOS targets. WSL2 uses the Linux
x86-64 artifact and is not a sixth native artifact.

<a id="windows-settings-to-check"></a>

## Windows Limits In The Released Addons

Alternate authentication profiles helped diagnose these Windows limitations.
They are not fixes or recommendations to change your server's authentication
policy. The failing cases need addon updates and must pass again in corrected
builds. No new addon version has been published by this audit.

**WebDAV:** the audited Windows addon did not complete the Digest SHA-256
scenario. A separate Basic-over-HTTPS test passed, but does not close the normal
Digest case. Applications requiring Digest SHA-256 should wait for a validated
Windows addon update. Keep certificate and hostname verification enabled.

**SFTP:** an alternate RSA authentication test passed, while the normal ECDSA
and server-settings case used on Linux failed with the released Windows addon.
The alternate test does not close that limitation. Wait for a validated addon
update for the failing case, and keep server identity verification enabled.

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
still requires the Windows fixes, package cleanup, and remaining platform checks;
this page does not open that release gate.

Continue with [choosing an addon](runtime-addons.md#choose-a-file-acquisition-provider),
the [File Lane guide](runtime-file-transfer.md), or the [current package list](../../common/current-packages.md).
