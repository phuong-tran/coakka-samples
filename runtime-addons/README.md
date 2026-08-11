# Runtime Addon Samples

Runtime addon samples are kept outside the main Runtime sample lane because an
addon is an optional product with its own dependencies, compatibility manifest,
release cadence, and platform evidence. These samples demonstrate composition
with public Runtime features; they do not make the addon part of the default
Runtime package.

## Available Releases

| Addon | Workflow | Status |
| --- | --- | --- |
| [SFTP artifact publisher](artifact-publisher-sftp/README.md) | Acquire and verify an artifact in Service A, then distribute it to Service B through File Lane. | Native `0.1.0+40810b79` for `macos-aarch64`; Runtime `2.3.0+`. |

Run an addon from its own directory. The root `run.sh` remains the front door
for Runtime and Logger samples and intentionally does not merge independently
released addon commands into the main lane.

See [Runtime Addons](../docs/runtime-addons.md) for package ownership,
compatibility, and release rules.
