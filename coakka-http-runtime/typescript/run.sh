#!/usr/bin/env bash
# Shared paths are deliberately defined by the sourced sample helper.
# shellcheck disable=SC1091,SC2154
set -euo pipefail

lane_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../scripts/common.sh
source "${lane_root}/../scripts/common.sh"

command="${1:-smoke}"
work="$(prepare_work_dir typescript)"
package="$(prepare_package javascript)"
app="${work}/app"
mkdir -p "${app}"
cp "${lane_root}/package.json" "${lane_root}/tsconfig.json" \
  "${lane_root}/main.ts" "${lane_root}/security.ts" \
  "${lane_root}/control.ts" "${lane_root}/protocol.ts" "${lane_root}/adverse.ts" "${app}/"
npm install --prefix "${app}" --cache "${work}/npm-cache" \
  --ignore-scripts --install-links --no-audit --no-fund "${package}"
(cd "${app}" && npm exec -- tsc --noEmit)
(cd "${app}" && npm exec -- tsc --outDir dist)
unset COAKKA_HTTP_JAVASCRIPT_ADDON COAKKA_HTTP_HOST_PATH

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
  tuning-smoke)
    node "${app}/dist/control.js"
    bun "${app}/control.ts"
    ;;
  adverse-smoke)
    node "${app}/dist/adverse.js"
    bun "${app}/adverse.ts"
    ;;
  http2-node|http3-node|http2-bun|http3-bun)
    fixtures="$(prepare_test_certificates)"
    protocol="${command%-*}"
    if [[ "${command}" == *-node ]]; then
      node "${app}/dist/protocol.js" --protocol "${protocol}" --fixtures "${fixtures}"
    else
      bun "${app}/protocol.ts" --protocol "${protocol}" --fixtures "${fixtures}"
    fi
    ;;
  run) run_node ;;
  run-bun) run_bun ;;
  *) printf 'usage: bash typescript/run.sh [check|smoke|security-smoke|tuning-smoke|adverse-smoke|smoke-node|smoke-bun|http2-node|http3-node|http2-bun|http3-bun|run|run-bun]\n' >&2; exit 2 ;;
esac
