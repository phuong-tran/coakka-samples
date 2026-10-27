# Private Go runtime candidate samples

The basic/deadletter/replica-file-fanout samples on this branch use the exact 66-file private
module in `artifacts/runtime-go-candidate.json`, label
`2.0.0`, bundled Core
`2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2`, ABI 1.
They do not use the older published `v1.8.3` module. Matching targets are
macOS ARM64, Linux ARM64/x64 and native Windows ARM64/x64. The tested compiler
floor is Go 1.22.12, except native Windows ARM64 which requires Go 1.25.5
for cgo. The module coordinate is `github.com/phuong-tran/coakka-runtime-go/v2`.

## Run

Use Go with a working C11 cgo compiler. The matching native Core is embedded
by the module; no separate native runtime library is installed.

Obtain the exact private module directory from the candidate owner, not a
connector source checkout. Choose an existing absolute work directory. On the
development Mac it must be on mounted external APFS storage. From the repository:

```sh
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_GO_CANDIDATE_MODULE="/absolute/private-module"
bash runtime/go/basic/run.sh
bash runtime/go/deadletter/run.sh
bash runtime/go/replica-file-fanout/run.sh
```

The wrappers require those paths and place Go build/temp caches under the work
root unless explicit absolute `GOCACHE`/`GOTMPDIR` are already supplied.
Those shared compiler directories are intentionally retained for reuse; the
runner never deletes them or the caller's work root. Use suitable external
paths for them on the development Mac.

Native Windows can invoke the same standard-library runner directly after
setting absolute Windows paths for those four variables and selecting its
native Go/cgo toolchain:

```text
go run scripts/runtime-go-candidate.go --repo-root C:/path/to/samples basic
go run scripts/runtime-go-candidate.go --repo-root C:/path/to/samples deadletter
go run scripts/runtime-go-candidate.go --repo-root C:/path/to/samples replica-file-fanout
```

Keep `GOTOOLCHAIN=local`, `GOWORK=off`, `GOPROXY=off` and `GOSUMDB=off`
for this offline private candidate workflow. The wrappers set them before the
runner compiles. There are no external Go module dependencies. Windows DLL
execution is tested with native executables, not substituted by WSL.
Do not disable publisher/app protection; allow a trusted test executable
manually if Windows prompts.

## Admission and lifecycle

The trusted repository catalog pins every regular module file's size/hash.
The runner rejects extra/missing/changed files, links, unknown catalog fields,
native/source/loader overrides and foreign `GOFLAGS`. Catalog:1MiB; module:
128files,8MiB each/64MiB total,256tree entries/depth12; app source:128KiB.
Admission completes before unique staging. It copies verified bytes, builds
offline through a local `v2.0.0` replacement (not a published coordinate), then
moves those build inputs before executing the embedded-image consumer.
The catalog is not an authenticity signature; obtain it through the trusted
repository and do not mutate inputs concurrently.

Only the unique `coakka-go-sample-...` child is removed after execution,
failure or handled cancellation. Build/app direct-child bounds are180s/30s
with1s wait-delay cleanup. Go owns its compiler subprocesses; this tool is not
a hostile process-tree sandbox. Forced termination does not promise graceful
Core shutdown; SIGKILL/power loss/unreapable-process cleanup is not guaranteed.
Normal application shutdown always closes the host. These compiler/driver
bounds do not change the2s request or1s observation deadlines.

Basic supplies its application identity/route only, checks a typed JSON echo
and reports Core-issued startup/runtime/counter facts. Inspection errors
propagate, never become zero snapshots. Go's current public startup record
does not expose delivery-mode fields; the sample does not invent them.
Deadletter requires the typed route-miss reason and original target, observes
one bounded event, cancels observation before host close and reports failures
only after deferred cleanup. The one-ask callback never calls process exit.

File fan-out enumerates three exact loopback owner incarnations. Its one
immutable9MiB+731 source is generated through a64KiB application buffer;
Core hashes files, issues actual endpoints/grants and reports both peers'
terminal facts. All three outcomes are independent, including a refused
prepare/control handoff/send. The application never substitutes a load-balanced
service endpoint or guesses native state. It closes every opened lane after
cancel/wait/forget retirement, and reports errors only after that cleanup.
Failed native stop retains the app-owned file directory and reports its path;
it does not delete beneath potentially active I/O. Successful joined shutdown
removes only that unique file directory, not the caller's root or caches.
The local JSON grant roundtrip is not authentication or TLS evidence.
Normal and separate failure-cleanup observation each retain the existing
64-update/30000ms native-wait bounds; cleanup cannot erase the original failure.
The external app bound remains30s. Forced termination cannot promise those
graceful application actions or removal of its file directory.

This runner creates development executables and removes them. Private release
qualification separately exercises real Garble output and artifact containment;
a normal `go build` is not represented as obfuscation. Existing exact-package
resource/shrink/native evidence is reused by identity, not replaced by an echo.
Other replica/cross-language scenarios, other connectors/tools/Android/
camera and legal/signing/coordinated-publication gates remain separate.
