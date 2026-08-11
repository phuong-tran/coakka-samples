# Runtime Addon Samples

Runtime addon samples are kept outside the main Runtime sample lane because an
addon is an optional product with its own dependencies, compatibility manifest,
release cadence, and platform evidence. These samples demonstrate composition
with public Runtime features; they do not make the addon part of the default
Runtime package.

## Available Source Candidates

| Addon | Workflow | Status |
| --- | --- | --- |
| [SFTP artifact publisher](artifact-publisher-sftp/README.md) | Acquire and verify an artifact in Service A, then distribute it to Service B through File Lane. | Native source-candidate sample; no public addon archive yet. |

Run a source candidate from its own directory. The root `run.sh` remains the
front door for promoted Runtime and Logger samples and intentionally does not
advertise an unpublished addon coordinate.

See [Runtime Addons](../docs/runtime-addons.md) for package ownership,
compatibility, and release rules.
