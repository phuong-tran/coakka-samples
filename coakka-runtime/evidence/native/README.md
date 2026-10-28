# Native Runtime Test Compatibility Path

The active, auditable harness is at the repository root:
[`runtime-test/`](../../runtime-test/README.md).

Existing commands through `coakka-runtime/evidence/native` remain supported by the
wrapper scripts in this directory, but new documentation and automation should
use `bash run.sh coakka-runtime runtime-test ...`.
