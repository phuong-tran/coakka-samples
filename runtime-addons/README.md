# Runtime Addon Samples

Runtime addon samples are kept outside the main Runtime sample lane because an
addon is an optional product with its own dependencies, compatibility manifest,
release cadence, and platform evidence. These samples demonstrate composition
with public Runtime features; they do not make the addon part of the default
Runtime package.

## Why Artifact Source Addons Exist

File Lane transfers a stable local file between CoAkka peers. It does not log
in to S3, Hugging Face, GitHub, Google Drive, SFTP, or another external system
to create that local source file.

The released Artifact Source Addons, also described as file acquisition
providers or provider-specific downloaders, fill that upstream gap:

```text
remote immutable file
  -> provider-specific authentication and acquisition
  -> exact size/SHA-256 verification
  -> no-clobber local staging
  -> CoAkka File Lane
  -> destination service
```

For example, an AI worker may need a multi-gigabyte model that exists only at
one pinned Hugging Face commit or S3 object version. The addon acquires and
verifies that exact identity; File Lane then performs the bounded peer transfer.
The same workflow applies to media inputs, checkpoints, firmware, build
artifacts, archived logs, and diagnostic bundles.

This avoids creating a separate internal HTTP file endpoint with custom body
limits, temporary-file handling, digest checks, resume, cancellation, and
receiver-completion semantics for every service. HTTP remains appropriate for
public/browser distribution and CDN-backed objects. Read
[Why These Addons Exist](../docs/runtime-addons.md#why-these-addons-exist) for
the full boundary and comparison.

## Available Releases

| Addon | Workflow | Status |
| --- | --- | --- |
| [HTTPS](artifact-publisher-https/README.md) | Verified immutable HTTPS object. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [S3/MinIO](artifact-publisher-s3/README.md) | Version-pinned S3-compatible GetObject. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [Local Drop](artifact-publisher-local-drop/README.md) | Stable file below an anchored POSIX drop root. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [Azure Blob](artifact-publisher-azure-blob/README.md) | Version-pinned read-only service SAS URL. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [GCS](artifact-publisher-gcs/README.md) | Generation-pinned V4 signed object URL. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [WebDAV](artifact-publisher-webdav/README.md) | Strong-ETag HTTPS resource. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [OCI Registry](artifact-publisher-oci-registry/README.md) | SHA-256 content-addressed registry blob. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [Hugging Face Hub](artifact-publisher-huggingface-hub/README.md) | Commit-pinned Hub file. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [GitHub Release](artifact-publisher-github-release/README.md) | Numeric-ID release asset. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [Google Drive](artifact-publisher-google-drive/README.md) | Retained blob revision media. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [Dropbox](artifact-publisher-dropbox/README.md) | Exact Dropbox `rev:` content. | Native `1.1.0+d1032f6d`; Runtime `2.4.0+`. |
| [SFTP](artifact-publisher-sftp/README.md) | Host-key-pinned SFTP file. | Native `1.2.0+88b9a047`; Runtime `2.3.0+`. |

Run one addon or the full native matrix from the repository root:

```sh
bash run.sh runtime-addons https
bash run.sh runtime-addons all check
bash run.sh runtime-addons all published
```

The deterministic fixtures prove the native sample lifecycle against exact
published packages. They do not claim live cloud-provider certification or a
high-level language connector. These addons are intentionally native-first.
No addon-specific JVM, Python, Node.js, Go, .NET, Swift, or other high-level
connector is currently released. The stable C ABI is ready to wrap without
rewriting each provider engine, but every language still needs lifetime-safe
bindings, packaging, credential handling, failure mapping, and matching-host
tests. Connectors will be added when demonstrated demand justifies that work;
portability readiness is not presented as an already-supported package.

See [Runtime Addons](../docs/runtime-addons.md) for package ownership,
compatibility, and release rules.
