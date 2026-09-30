#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir typescript)"
prefix="$(prepare_host_prefix)"
connector_build="${work}/connector-build"
cmake -S "${connector_root}" -B "${connector_build}" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF \
  -DCMAKE_PREFIX_PATH="${prefix}" \
  -DCoAkkaHttpHost_DIR="${prefix}/lib/cmake/CoAkkaHttpHost" >/dev/null
cmake --build "${connector_build}" --target coakka_http_javascript_addon \
  --parallel >/dev/null
addon="${connector_build}/coakka_http_javascript.node"
require_file "${addon}"
app="${work}/app"
mkdir -p "${app}"
cp "${lane_root}/package.json" "${lane_root}/tsconfig.json" \
  "${lane_root}/main.ts" "${lane_root}/security.ts" "${app}/"
npm install --prefix "${app}" --cache "${work}/npm-cache" \
  --no-audit --no-fund --silent "${connector_root}/connectors/javascript/package"
(cd "${app}" && npm exec -- tsc --noEmit)
(cd "${app}" && npm exec -- tsc --outDir dist)
export COAKKA_HTTP_JAVASCRIPT_ADDON="${addon}"
export DYLD_LIBRARY_PATH="${prefix}/lib${DYLD_LIBRARY_PATH:+:${DYLD_LIBRARY_PATH}}"
export LD_LIBRARY_PATH="${prefix}/lib${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

run_node() {
  node "${app}/dist/main.js" "$@" --assets "${sample_root}/assets"
}

run_bun() {
  bun "${app}/main.ts" "$@" --assets "${sample_root}/assets"
}

run_security_node() {
  node "${app}/dist/security.js" --fixtures "$1"
}

run_security_bun() {
  bun "${app}/security.ts" --fixtures "$1"
}

case "${command}" in
  check) ;;
  smoke)
    run_node --smoke
    run_node --smoke --io-uring
    run_bun --smoke
    run_bun --smoke --io-uring
    ;;
  security-smoke)
    fixtures="$(prepare_test_certificates)"
    run_security_node "${fixtures}"
    run_security_bun "${fixtures}"
    ;;
  smoke-node) run_node --smoke ;;
  smoke-bun) run_bun --smoke ;;
  run) run_node ;;
  run-bun) run_bun ;;
  *) printf 'usage: bash typescript/run.sh [check|smoke|security-smoke|smoke-node|smoke-bun|run|run-bun]\n' >&2; exit 2 ;;
esac
