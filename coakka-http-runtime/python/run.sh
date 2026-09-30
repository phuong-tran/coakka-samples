#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir python)"
prefix="$(prepare_host_prefix)"
python_source="${connector_root}/connectors/python"
require_file "${python_source}/coakka_http/__init__.py"
venv="${work}/venv"
if [[ ! -x "${venv}/bin/python" ]]; then
  python3 -m venv "${venv}"
fi
export PYTHONPATH="${python_source}${PYTHONPATH:+:${PYTHONPATH}}"
export MYPYPATH="${python_source}${MYPYPATH:+:${MYPYPATH}}"
if [[ "$(uname -s)" == Darwin ]]; then
  export COAKKA_HTTP_HOST_PATH="${prefix}/lib/libcoakka_http_host.1.0.0.dylib"
else
  export COAKKA_HTTP_HOST_PATH="${prefix}/lib/libcoakka_http_host.so.1.0.0"
fi

case "${command}" in
  smoke)
    "${venv}/bin/python" "${lane_root}/main.py" --smoke --assets "${sample_root}/assets"
    "${venv}/bin/python" "${lane_root}/main.py" --smoke --io-uring --assets "${sample_root}/assets"
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    "${venv}/bin/python" "${lane_root}/security.py" --fixtures "${fixtures}"
    ;;
  run) "${venv}/bin/python" "${lane_root}/main.py" --assets "${sample_root}/assets" ;;
  check)
    PIP_CACHE_DIR="${work}/pip-cache" "${venv}/bin/python" -m pip install \
      --disable-pip-version-check --quiet 'mypy==1.18.2' 'ruff==0.13.2'
    "${venv}/bin/ruff" check "${lane_root}/main.py" "${lane_root}/security.py"
    "${venv}/bin/mypy" --strict "${lane_root}/main.py" "${lane_root}/security.py"
    ;;
  *) printf 'usage: bash python/run.sh [smoke|security-smoke|run|check]\n' >&2; exit 2 ;;
esac
