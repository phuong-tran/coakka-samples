# Native SFTP To File Lane Walkthrough

The native sample proves the complete package and runtime boundary:

1. resolve the exact promoted native Runtime and SFTP addon archives;
2. verify both archives against `artifacts/public-artifacts.tsv`;
3. build Service A and Service B through `find_package` imported targets;
4. acquire a deterministic file from a loopback OpenSSH SFTP server;
5. distribute it through File Lane and compare the receiver bytes.

```mermaid
sequenceDiagram
    participant HostA as Service A host
    participant SFTP as SFTP addon
    participant Source as OpenSSH SFTP
    participant LaneA as Sender File Lane
    participant LaneB as Service B receiver

    HostA->>LaneA: create + start
    HostA->>SFTP: create(sender lane) + start
    LaneB->>LaneB: prepareReceive(id, token, size, SHA-256)
    HostA->>SFTP: submit(job, source, target grant)
    SFTP->>Source: authenticate + verify pinned host key
    Source-->>SFTP: artifact bytes
    SFTP->>SFTP: verify size/SHA-256 + publish staging file
    SFTP->>LaneA: submit verified file
    LaneA->>LaneB: bounded File Lane transfer
    LaneB-->>SFTP: completed terminal outcome
    SFTP-->>HostA: job completed
    HostA->>SFTP: forget + stop + destroy
    HostA->>LaneA: stop + destroy
```

## Commands

From this directory:

```sh
bash run.sh published
bash run.sh check
bash run.sh source-candidate
```

`published` is the default and consumes addon `0.2.0+c5656cc8` plus Runtime
`2.3.0`. The full fixture runs on supported macOS and Linux hosts. `check`
compiles against sibling Core source headers. `source-candidate` builds the
next addon candidate from a sibling Core checkout. Override workspace discovery
with `COAKKA_CORE_ROOT` and `COAKKA_PUBLISH_ROOT` when needed.

The published path requires CMake 3.20+, a C11 compiler, OpenSSH, OpenSSL
command-line tools, and `xxd`; it does not require a libssh2 development
package. The source-candidate path additionally requires a C++ compiler,
pkg-config, and static libssh2 `1.11.1` plus its crypto closure.

## Windows Build

On Windows 11 ARM64 or x86-64, extract the matching Runtime `2.3.0` archive and
the SFTP addon `0.2.0` archive. The current package carries GNU-compatible
import libraries and is verified with Zig's `windows-gnu` target. Configure the
same two C applications from PowerShell with the included toolchain file:

```powershell
cmake -S . -B build -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE=cmake\windows-zig-aarch64.cmake `
  -DCoAkkaRuntimeNativeV2_DIR=C:\coakka\runtime\cmake `
  -DCoAkkaRuntimeAddonArtifactPublisherSftp_DIR=C:\coakka\sftp-addon\cmake
cmake --build build
```

Use the x86-64 Zig toolchain file on x86-64. Put the matching Runtime and addon DLLs beside
`coakka_sftp_sample_service_a.exe`; put the Runtime DLL beside
`coakka_sftp_sample_service_b.exe`. Service configuration uses the
`COAKKA_SAMPLE_*` environment variables read in `service_a.c` and
`service_b.c`. Windows private-key authentication requires both the private key
and its companion `<private-key>.pub` file.

The published DLLs are not Authenticode-signed. Verify the release SHA-256
before use; a managed Windows code-signing policy may additionally require the
organization to sign the verified DLLs.

## Consumer Targets

The CMake project keeps the dependency boundary explicit:

```cmake
find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)
find_package(CoAkkaRuntimeAddonArtifactPublisherSftp CONFIG REQUIRED)

target_link_libraries(coakka_sftp_sample_service_a PRIVATE
  CoAkkaRuntimeAddonArtifactPublisherSftp::artifact_publisher_sftp)
target_link_libraries(coakka_sftp_sample_service_b PRIVATE
  CoAkkaRuntimeNativeV2::runtime_v2)
```

Service B does not include the addon header or link its library. The addon is a
Service A deployment choice, not a cluster-wide Runtime dependency.

## Lifecycle And Ownership

Service A starts its sender lane before the addon and stops the addon before
the lane. The publish spec borrows strings, digest arrays, and target entries
only for synchronous admission; the addon copies accepted request data into
bounded owned state. The host retains and then explicitly forgets terminal job
records.

Service B prepares an exact transfer ID, one-use token, size, digest, and
destination before it announces readiness. It verifies the terminal snapshot
and hashes the destination again before forgetting the receive record. Both
processes use bounded capacities and bounded monotonic waits; neither polls with
an unbounded sleep loop.

## Production Changes

The fixture binds both protocols to loopback and uses direct File Lane
transport so it remains self-contained. A real cross-host deployment should:

- select File Lane TLS or mTLS, or place the lane on an equivalently protected
  private network;
- load credentials and transfer grants from the app host's secret and policy
  systems instead of environment variables;
- persist business rollout state outside the addon's bounded terminal cache;
- choose timeouts, queue capacities, staging limits, and fan-out policy from a
  measured resource budget;
- activate a received model only after Service B's own validation and rollout
  policy succeeds.

The sample does not update Go, Swift, npm, NuGet, Python, or other connector
packages. The addon remains an independent native release lane that composes
with the Runtime File Lane contract.
