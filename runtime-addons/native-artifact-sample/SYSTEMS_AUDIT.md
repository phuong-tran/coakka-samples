# Native Artifact Sample Systems Audit

## Boundary

The sample exposes no library ABI. It consumes the released Runtime and addon
C ABIs as an external CMake project. Protocol workers, staging, integrity, and
File Lane mechanics remain inside those packages. The Python TLS server and
shell orchestration are test fixtures in separate processes.

Senior systems review classifies this as ordinary bounded userspace C11
integration. Expert review found no kernel-adjacent, NUMA, DMA, IRQ, io_uring,
or custom memory-ordering mechanism warranted for this sample.

## Ownership And Lifecycle

- Service B owns its receiver lane and installs one receive grant before
  readiness. It stops and destroys the lane after the wait call has returned.
- Service A owns its sender lane and opaque publisher. The publisher borrows
  the started lane, so teardown is publisher stop/destroy followed by lane
  stop/destroy.
- Submit arguments, target entries, strings, and digest arrays are stack-backed
  and borrowed only for the synchronous submit call. Accepted work is copied
  by the published addon contract.
- Terminal records are forgotten only after both aggregate and target outcomes
  are recorded. Error cleanup requests cancellation before stopping.
- The shell owner traps every receiver/server child and removes its private
  workspace. No background process is intentionally left running.

## Bounds And Failure Law

- one publisher worker inherited from the addon, one sender lane, one receiver
  lane, one fixture server process, and one artifact job;
- publisher queue `2`, retention `8`, lane queues `4`, and exactly one target;
- fixture payload is about 320 KiB and the server rejects files above 4 MiB;
- acquisition and File Lane timeouts are 15 seconds; sequence waits have a
  separate 60-second application deadline; readiness waits are 15 seconds;
- every required numeric environment value is parsed with full-string and
  range checks before resource creation;
- Service A requires publisher `COMPLETED + OK` and sender target File Lane
  `COMPLETED + OK`; Service B independently requires receiver
  `COMPLETED + OK`, exact byte count, and exact SHA-256;
- staging and receiver destinations are new paths inside a private workspace;
  the addon and File Lane retain their no-clobber behavior;
- source URLs and credentials are fixed fixture configuration. The server
  suppresses request logging so bearer and signed-query values are not emitted.

## Source Organization

Shared parsing, receiver behavior, and lifecycle live in focused files because
their ownership is identical across the addon family. Addon-specific config
and publish-spec construction remain together in `service_a.c`; splitting each
small preprocessor branch into another file would add navigation without
creating an ownership boundary. The compile-time adapter preserves concrete
types and makes ABI drift a compiler error.

## Evidence

- all 11 shared sample variants compile as C11 with
  `-Wall -Wextra -Wpedantic -Werror` against their exact public headers;
- the HTTPS consumer path passes halt-on-error UBSan for both sample
  executables on macOS ARM64;
- CMake builds consume the exact extracted Runtime and addon imported targets;
- HTTPS, S3/MinIO, Local Drop, Azure Blob, GCS, WebDAV, OCI Registry,
  Hugging Face Hub, GitHub Release, Google Drive, Dropbox, and SFTP complete
  end to end on macOS ARM64 through their published archives;
- the artifact-pin gate includes all 12 addon coordinates.

The Core repositories already own sanitizer, static-analysis, fault, race,
stress, resource-recovery, and matching-host package evidence for the released
implementations. This sample slice intentionally rechecks the external consumer
lifecycle rather than duplicating those internal test suites.

## Limitations

- the shared shell fixture runs on supported macOS and Linux hosts; Windows
  package support does not imply this Bash/OpenSSL fixture runs unchanged;
- deterministic protocol-shaped fixtures are not live provider certification;
- no high-level language addon connector or binding is present;
- consumer ASan does not reach receiver readiness with the prebuilt Runtime on
  this Apple host, and Apple ASan reports that leak detection is unsupported;
  neither ASan nor LSan is claimed by this sample slice;
- credentials, endpoint allowlists, retry policy, rollout, observability, and
  artifact activation remain application/operator responsibilities.
