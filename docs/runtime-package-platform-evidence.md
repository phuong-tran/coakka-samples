# Runtime Package And Platform Evidence

This page states exactly what the current public runtime packages contain and
where their connector paths have executed. It is written for application
developers and AI code generators that need a direct answer without inferring
support from archive contents.

Current package-manager coordinates use native runtime generation
`1.4.1+9e02a51d`. The native source generation and the connector/package
version are separate identities.

## Evidence Terms

| Term | Exact meaning |
| --- | --- |
| Published | The named immutable version is available from its registry, module tag, or public artifact path. |
| Contains | The package contains a native payload for that OS and CPU. |
| Verified | Format, architecture, exports, dependency policy, and digest gates pass for that payload. |
| Executed | The named package was installed or unpacked on a matching host and completed the stated connector workload. |
| Source-fixed | The connector source contains a tested correction that is newer than the named public package. The public package does not inherit that result. |

`Contains` and `Verified` never imply `Executed`. An AI-generated integration
must preserve that distinction when selecting a package for a deployment.

## Current Runtime Package Matrix

| Surface | Published coordinate | Native payloads | Matching-host execution evidence |
| --- | --- | --- | --- |
| Native C ABI | `1.4.1+9e02a51d` archive | Linux ARM64/x86-64, macOS ARM64, Windows ARM64/x86-64 | Native runtime execution passes on Linux ARM64/x86-64, macOS ARM64, and Windows 11 ARM64. Windows x86-64 has format, architecture, export, and dependency verification. |
| JVM/JNA/JNI | `coakka.v2:coakka-jvm-native-runtime-v2:1.4.1-g9e02a51d-4e7cda4` | All five targets | Request/reply executes on macOS ARM64 and Linux ARM64. Windows 11 ARM64 request/deadletter execution is recorded for the exact runtime train. The Linux x86-64 guest crashes inside OpenJDK 21/25 before the sample reaches CoAkka, so this ledger makes no JVM connector execution claim there. The implementation uses JNA over the public C ABI; JNI names the JVM native-bridge category. |
| Python/PyPI | `coakka-v2-connector==1.4.6` | All five targets | Registry wheel request/reply executes on macOS ARM64 and Linux ARM64/x86-64. On Windows 11 ARM64 the wheel loads the exact runtime, but its published Python reader uses `select()` on CRT pipes and request/reply times out. The connector source has a Windows pipe-waiter fix that passes the same request/reply sample; that fix requires the next Python package release. Windows x86-64 connector execution is not claimed. |
| Go module | `github.com/phuong-tran/coakka-runtime-go@v1.4.1` | All five targets | Module request/reply executes on macOS ARM64 and Linux ARM64/x86-64. Windows payloads are verified package contents; this module version has no Windows Go execution record. |
| C#/NuGet | `CoAkka.Runtime==1.4.7` | `linux-arm64`, `linux-x64`, `osx-arm64`, `win-arm64`, `win-x64` | Registry package request/reply and route-miss deadletter execute on macOS ARM64, Linux ARM64/x86-64, and Windows 11 ARM64. Windows x86-64 has verified package/RID evidence without a matching-host connector run. |
| SwiftPM | `coakka-runtime-swift` exact `1.4.1` | All five targets | Swift request/reply, transport, TLS reload, and consumer tests execute on macOS ARM64. Linux payloads pass ELF, architecture, digest, and C-bridge compile gates; the Linux guests do not contain a Swift toolchain. Windows payloads pass PE, architecture, and digest gates. |
| Node.js/npm | `coakka-v2-connector-node@1.4.6` | All five targets | Registry package request/reply executes on macOS ARM64 and Linux ARM64/x86-64. Windows native payloads are verified; this package version has no Windows Node.js connector run. |
| Rust archive | `coakka-runtime-rs` `1.4.1` | All five targets | macOS ARM64 execution passes. The public `1.4.1` archive fails native loading on Linux because that release encoded the macOS `RTLD_LOCAL` value for Linux. Current connector source corrects the Linux constant and passes native Linux execution; use the next corrected package for Linux consumers. |

The public package matrix is intentionally direct about known failures. A
payload can remain useful for build, inspection, or a later corrected
connector without being presented as a working application path today.

## Reproducing The Linux Package Runs

On an architecture-matched Linux host with the required language toolchain:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples

bash run.sh runtime python basic
bash run.sh runtime go basic
bash run.sh runtime csharp basic
bash run.sh runtime node basic
```

The Python sample installs PyPI `1.4.6`, the Go sample resolves module
`v1.4.1`, the C# sample installs NuGet `1.4.7`, and the Node.js sample installs
npm `1.4.6`. Each prints runtime version `1.4.1` and native source commit
`9e02a51d7f0e4a231e2f71fe6d19ce02724277c9` before reporting its result.

Run the JVM command on Linux ARM64. The current Linux x86-64 guest is excluded
from JVM execution evidence because both installed OpenJDK versions crash
inside generated JVM code before the sample enters CoAkka.

## Code Generation Rule

When generating integration code from CoAkka docs:

1. Name the exact package coordinate and native generation.
2. Select only a payload matching the deployment OS and CPU.
3. Distinguish package presence from matching-host execution.
4. Preserve the lifecycle shown by the connector README and sample.
5. Keep target, payload identity, timeout, deadletter, close, and ownership
   semantics visible in generated code.
6. Do not generate file-lane calls against the public `1.4.1` packages; the
   file-lane ABI starts with native source generation `2.1.0`.

For release coordinates, use [Current Packages](current-packages.md). For the
full feature/platform view, use the public compatibility matrix. For runtime
behavior, use the connector source documentation and runnable samples.
