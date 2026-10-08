#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir python)"
package="$(prepare_package python)"
python_source="${package}/python"
require_file "${python_source}/coakka_http/__init__.py"
venv="${work}/venv"
if [[ ! -x "${venv}/bin/python" ]]; then
  python3 -m venv "${venv}"
fi
export PYTHONPATH="${python_source}"
export MYPYPATH="${python_source}"
export PYTHONDONTWRITEBYTECODE=1
export MYPY_CACHE_DIR="${work}/mypy-cache"
export RUFF_CACHE_DIR="${work}/ruff-cache"
unset COAKKA_HTTP_HOST_PATH

case "${command}" in
  smoke)
    "${venv}/bin/python" "${lane_root}/main.py" --smoke --compression --assets "${sample_root}/assets"
    "${venv}/bin/python" "${lane_root}/main.py" --smoke --io-uring --assets "${sample_root}/assets"
    "${venv}/bin/python" "${sample_root}/scripts/smoke-managed.py" \
      --log "${work}/wire.log" --ready-prefix python-sample \
      --check-upload-trailer --upload-abort-prefix python \
      --shutdown-marker python-shutdown=pass --assets "${sample_root}/assets" \
      -- "${venv}/bin/python" -u "${lane_root}/main.py" --compression --assets "${sample_root}/assets"
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    "${venv}/bin/python" "${lane_root}/security.py" --fixtures "${fixtures}"
    ;;
  tuning-smoke)
    "${venv}/bin/python" "${lane_root}/main.py" --smoke --compression --tuning --assets "${sample_root}/assets"
    ;;
  adverse-smoke) "${venv}/bin/python" "${lane_root}/adverse.py" ;;
  http2|http3)
    fixtures="$(prepare_test_certificates)"
    "${venv}/bin/python" "${lane_root}/protocol.py" --protocol "${command}" --fixtures "${fixtures}"
    ;;
  run) "${venv}/bin/python" "${lane_root}/main.py" --assets "${sample_root}/assets" ;;
  check)
    PIP_CACHE_DIR="${work}/pip-cache" "${venv}/bin/python" -m pip install \
      --disable-pip-version-check --quiet 'mypy==1.18.2' 'ruff==0.13.2'
    "${venv}/bin/ruff" check --target-version py311 "${lane_root}"
    "${venv}/bin/mypy" --strict "${lane_root}"
    ;;
  *) printf 'usage: bash python/run.sh [smoke|tuning-smoke|adverse-smoke|security-smoke|http2|http3|run|check]\n' >&2; exit 2 ;;
esac
