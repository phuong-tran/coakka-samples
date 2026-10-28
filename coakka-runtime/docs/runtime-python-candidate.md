# Private Python runtime candidate samples

The basic/deadletter/hot-reload apps on this branch use the exact private
`3.0.0` platform wheels pinned in
`artifacts/runtime-python-candidate.json`, not historical public2.5.2.
Each wheel contains22 genuinely minified language modules and one frozen
Core2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2 image, ABI1.
Candidate targets: macOS ARM64, Linux ARM64/x64 and native Windows ARM64/x64.
CPython3.11 is the baseline; existing matching package evidence uses3.11.15
Mac/Pi and3.11.9 Windows. Minimum OS execution is recorded independently from
the language version. No registry publication is opened by this guide.

## Run the exact local candidate

Obtain the trusted catalog and matching wheel from the candidate owner, not
a connector source tree or a separately installed native library. Choose an
existing absolute work directory; on the development Mac it must be on
mounted external APFS storage. From this repository:

```sh
export PYTHON="/absolute/path/to/python3.11"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_PYTHON_CANDIDATE_WHEEL="/absolute/coakka_v2_connector-3.0.0-py3-none-macosx_13_0_arm64.whl"
bash coakka-runtime/python/basic/run.sh
bash coakka-runtime/python/deadletter/run.sh
bash coakka-runtime/python/hot-reload/run.sh
```

Use the matching wheel filename on Linux or Windows. The interpreter needs
its standard `venv` and offline `ensurepip` tools; if a distribution splits
them, install those build tools first. They are not extra Core implementation
dependencies. Native Windows invokes the same Python runner directly:

```text
python -I -X utf8 -B -Werror coakka-runtime/scripts/runtime-python-candidate.py --repo-root C:/path/to/samples basic
python -I -X utf8 -B -Werror coakka-runtime/scripts/runtime-python-candidate.py --repo-root C:/path/to/samples deadletter
python -I -X utf8 -B -Werror coakka-runtime/scripts/runtime-python-candidate.py --repo-root C:/path/to/samples hot-reload
```

Set both variables to absolute Windows paths and use native CPython. WSL ELF
execution is not Windows evidence. Do not weaken app protection; allow trusted
test executables manually if Windows prompts.

## Admission and ownership

The standard-library runner checks exact archive size/hash and all31 regular
members against the canonical catalog before creating unique staging. Catalog:
256KiB; wheel:4MiB;128members maximum,8MiB per member/32MiB total; app:128KiB.
Duplicate/unsafe/linked/encrypted members, wrong target and source/native/
loader overrides refuse. ZIP is parsed only after independently trusted archive
identity matches; the catalog is not a publisher signature. Do not mutate
trusted input trees concurrently. Windows baseline junctions are refused as
reparse points, not mistaken for ordinary directories.

The runner creates a disposable standard virtual environment, bootstraps its
bundled installer offline and uses ordinary pip with `--no-index --no-deps`.
All owned Python commands explicitly select UTF-8 stdio. Baseline ensurepip
does not forward `-X` to its nested pip child, so that single bootstrap phase
uses safe-path/no-user-site flags and four runner-issued Python settings
(UTF-8, safe path, no user site, no bytecode) in an otherwise sanitized
environment. No ambient Python path/home or installer config is accepted.
Ordinary wheel install and application commands retain full `-I` isolation;
no Python/stdlib patch, path transliteration or security-policy change.
No registry/dependency/source-tree fallback. It retires its staged archive path
before running the installed app with isolated interpreter imports. The
package loads its existing installed image directly, not an executable
extraction/cache or guessed system library. Caller roots, original wheels and
shared tools are never deleted. Only the unique `coakka-python-sample-...`
child is removed after its direct child is confirmed reaped; unconfirmed stop
retains it and reports the path.

Each environment/bootstrap/install child is bounded180s, app execution30s;
timeout/interruption kills and reaps the direct child with1s final reap bound.
The trusted Python installer owns its own subprocesses; this is not a hostile
process-tree sandbox. Forced termination, power loss or an unreapable OS child
does not guarantee graceful Core shutdown or directory cleanup. Product asks
remain2s and observation waits1s; those are not widened by installer bounds.

## App behavior

Basic supplies only application identity/route, verifies the JSON echo and
exact one-request counters, and prints Core-issued ABI/version/startup facts:
requested execution intent, effective connection, monitor and both delivery
modes. Inspection errors propagate, never become defaults or zero snapshots.
Deadletter scopes one bounded observation with guaranteed unregister before
host close, validates reason and nullable original target, and requires one
matched pending request. Python's public reason remains an unknown-preserving
number; the sample names the stable public route-miss value without importing
schema/generated internals. Runtime/worker errors are reported after cleanup,
with Python's original operation/cleanup causal chain preserved.

Hot reload deliberately chooses route generations because that is its
business demonstration: stale generation1 must refuse, generation2 replaces
the route table, invalid generation3 must refuse without changing active
state. It consumes `CoreCallError.status`/`CoakkaStatus`, never diagnostic text.
Its generation-wait helper first reads already-applied Core health; that can
succeed with monitor OFF. It neither guesses monitor ON nor claims to have
received a notification. AUTO/MULTIPLEX and incidental tuning remain defaulted.

These are functional consumer examples, not new throughput/resource-neutrality
claims or replacements for exact-package memory/race/resource qualification.
Existing minifier/AST/native/dependency evidence is reused by identity; source
minification is not encryption or native obfuscation. Other language samples,
cross-language scenarios, client/inspect, Android/camera and coordinated legal/
signing/minimum-OS/global documentation/publication gates remain separate.
