#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command python3 "Install Python 3.11 or newer, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
site_packages="${tmp_dir}/site-packages"
artifact_rel="runtime/python/releases/0.1.0+0cb644340467/coakka_v2_connector-0.1.0-py3-none-any.whl"
wheel_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/coakka_v2_connector-0.1.0-py3-none-any.whl")"

python3 -m pip install "${wheel_path}" --target "${site_packages}" >/dev/null
PYTHONPATH="${site_packages}" python3 "${script_dir}/main.py"
