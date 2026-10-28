# Go Replica File Fan-Out

Send one immutable9MiB+731 file to three exact loopback File Lane owners.
This is application-level `ALL`, not broadcast or a runtime default.
Use the [private candidate workflow](../../docs/runtime-go-candidate.md)
and its exact local66-file module, not the older public module or a connector
source checkout. Core/native package bytes are unchanged.

From the repository root, with Go1.22.12 or the qualified current Go toolchain
and a working C11 cgo compiler:

```sh
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_GO_CANDIDATE_MODULE="/absolute/private-module"
bash coakka-runtime/go/replica-file-fanout/run.sh
```

Choose existing absolute directories; on the development Mac use mounted
external APFS storage. The shared runner admits exact catalog members before
staging, builds offline and executes the module's normal embedded-image path.
It rejects native/source overrides and has no registry fallback. Native Windows
uses the Go runner directly as described in the candidate guide; WSL is not
substituted for Windows execution. Shared compiler caches are retained.

Expected output, only after successful retirement and joined close:

```text
coakka_replica_file_fanout owners=3 bytes_per_owner=9437915 ok
```

The application chooses three fresh exact owner incarnations and fresh
per-owner transfer IDs/tokens. Core issues each actual bound endpoint and grant;
the local JSON handoff must preserve all those facts. The application checks
sender and receiver terminal success separately and asks Core to verify each
destination identity. It does not replace a Core endpoint with a Service or
derive success from a timeout, enum ordering or local admission flags.

The source is written in bounded64KiB chunks, not allocated as one whole file.
All three owners are processed even after one prepare/handoff/send fails.
Every admitted record is canceled idempotently, observed through Core's update
sequence until terminal, then forgotten; every opened lane is closed. A late
completion remains Core's completion. Operation and cleanup failures propagate
together after cleanup, never through process exit inside an active owner.
Each normal/cleanup observation retains64updates/30000ms per native wait; the
runner still bounds app execution to30s. Cleanup success never turns a failed
normal operation into success.

Only the unique app-owned file directory is removed after every lane confirms
stop. A refused stop retains it and reports its path rather than deleting under
a worker. Forced termination/power loss cannot guarantee graceful stop or file
cleanup. File/queue/I/O tuning stays at Core defaults; this sample adds no
threads, OS capability checks, transport fallback or benchmark claim.

The JSON roundtrip only stands in for an authenticated application control
plane. Tokens are secrets; do not log grants. This protected loopback plaintext
demo is not TLS or untrusted-network qualification. Read
[Runtime Lane Owner Grants](../../../docs/runtime-lane-owner-grants.md) before
adapting it to deployment: `ALL` must enumerate exact process/pod owners;
calling a load-balancing Service three times is not equivalent.
