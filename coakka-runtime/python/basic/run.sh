#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
: "${COAKKA_SAMPLE_WORK_ROOT:?Set an existing absolute work directory}"
: "${COAKKA_PYTHON_CANDIDATE_WHEEL:?Set the exact matching private wheel}"
exec "${PYTHON:-python3}" -I -X utf8 -B -Werror "${repo_root}/coakka-runtime/scripts/runtime-python-candidate.py" --repo-root "${repo_root}" basic
