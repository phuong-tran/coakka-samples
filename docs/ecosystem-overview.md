# CoAkka Ecosystem Overview

CoAkka is a polyglot, multi-language, multi-platform native-backed runtime
ecosystem for routing application-owned work by stable target name across
process, language, container, and host boundaries. The core owns bounded
execution, request/reply, deadletters, delivery evidence, and transport
mechanics. Connectors adapt host-language objects and lifecycle to the stable
public C ABI without redefining core semantics.

Kubernetes is a first-class deployment lane because its topology, rollout,
policy, and scale deserve detailed operational treatment. It is not a runtime
prerequisite. The same contract applies to standalone services, containers,
VMs, bare-metal hosts, and architecture-matched edge deployments.

## Repository Boundaries

| Surface | Responsibility |
| --- | --- |
| Runtime source | Native runtime behavior, public C ABI, native C++ connector, and canonical common docs |
| `coakka-publish` | Released packages, native archives, checksums, manifests, compatibility matrices, and release notes |
| `coakka-samples` | Runnable consumer examples and integration workflows |
| Language package repositories | Package-manager-specific runtime or logger bindings |

Use the [publish repository](https://github.com/phuong-tran/coakka-publish)
when exact artifact identity matters. Use the
[samples repository](https://github.com/phuong-tran/coakka-samples) when the
goal is to run and inspect an integration.

## Language Surfaces

The ecosystem contains native C/C++, JVM and framework adapters, Node.js and
Bun, Python, Go, C#, Rust, Swift, and source-oriented Zig, Mojo, Tauri, and
Electron lanes. A language name in the ecosystem is not by itself a release or
platform claim. Before adoption, check the exact package version, native
payload, architecture, release channel, and consumer smoke recorded by that
release.

Bindings must preserve the same core rules:

- one explicit lifecycle owner per runtime instance
- startup-configured connection strategy
- atomic configuration publication
- failed TLS credential reload keeps the active generation
- capability discovery before feature use
- stable status and reason values for control flow
- no connector-specific retry after ambiguous delivery

## Platform Vocabulary

| Platform class | What it proves |
| --- | --- |
| Linux | Primary runtime deployment and production-oriented evidence |
| macOS | First-class development, integration, native loading, and connector smoke |
| Windows | First-class portability, development-host, and host-integration evidence |
| Edge and device Linux | Supported only when the release matrix includes the device architecture and the deployment satisfies the same dependency, clock, storage, and network requirements |
| Industrial Android | An integration target that requires release-specific native ABI, packaging, clock, certificate, and lifecycle evidence; it is not inferred from desktop Linux |

Raspberry Pi, BeagleBone, bare metal Linux hosts, LAN services, and industrial
devices are common reasons to use runtime-owned TLS/mTLS instead of depending on
a Kubernetes ingress or service mesh. Their networks are controlled networks,
not automatically secure networks.

## Package Truth

- All connectors use the same public ABI shape and discover effective runtime
  capabilities instead of inferring behavior from a package name.
- Package versions are independent across language ecosystems.
- A package or platform is available only when its exact release metadata and
  compatibility evidence say so.

Start operational work with [Troubleshooting](troubleshooting.md). For private
or sensitive issues, use [Contact And Support](contact-and-support.md).
