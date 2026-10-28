#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python_command="${COAKKA_FRAMEWORK_PYTHON:-python3}"
exec "${python_command}" -I -B "${script_dir}/../framework-candidate/sample-runner.py" spring "$@"
