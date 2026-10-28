#!/usr/bin/env bash
set -euo pipefail
product_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${product_root}/.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"
source "${repo_root}/scripts/sample-metadata.sh"
run_path() {
  local selected="$1"; shift
  # Accept product-relative commands only, never arbitrary filesystem paths.
  [[ "${selected}" =~ ^[a-z0-9-]+(/[a-z0-9-]+)*$ ]] || coakka_die 'Invalid Runtime sample path'
  local entry="${product_root}/${selected}/run.sh"
  coakka_require_file "${entry}" "Use 'bash run.sh coakka-runtime list'."
  bash "${entry}" "$@"
}
run_addon() {
  local addon="$1" command="${2:-published}"
  [[ "${addon}" =~ ^[a-z0-9-]+$ ]] || coakka_die 'Invalid Runtime addon'
  run_path "addons/artifact-publisher-${addon}/native" "${command}"
}
list_addons() {
  local row addon summary
  for row in "${COAKKA_ADDON_ROWS[@]}"; do
    IFS='|' read -r addon summary <<<"${row}"
    printf '  %-40s %s\n' "coakka-runtime/addons/${addon}" "${summary}"
  done
}
command="${1:-all}"
[[ "$#" == 0 ]] || shift
case "${command}" in
  all) exec bash "${product_root}/run-all.sh" "$@" ;;
  list)
    coakka_print_product_samples coakka-runtime
    printf '  %s\n' coakka-runtime/runtime-test coakka-runtime/tools/coakka-client coakka-runtime/tools/coakka-runtime-inspect coakka-runtime/demos/rpi-camera
    list_addons; coakka_print_scenarios; coakka_print_containers ;;
  test|runtime-test) exec bash "${product_root}/runtime-test/run.sh" "$@" ;;
  client|coakka-client|runtime-client) exec bash "${product_root}/tools/coakka-client/run.sh" "$@" ;;
  inspect|coakka-runtime-inspect|runtime-inspect) exec bash "${product_root}/tools/coakka-runtime-inspect/run.sh" "$@" ;;
  addons|runtime-addons)
    if [[ "$#" == 0 ]]; then list_addons
    elif [[ "$1" == all ]]; then
      shift
      for row in "${COAKKA_ADDON_ROWS[@]}"; do
        IFS='|' read -r addon _ <<<"${row}"
        run_addon "${addon}" "${1:-published}"
      done
    else run_addon "$@"; fi ;;
  addons/artifact-publisher-*/native) run_path "${command}" "$@" ;;
  runtime-addons/*|addons/*) run_addon "${command#*/}" "${1:-published}" ;;
  runtime-client/*|client/*) run_path tools/coakka-client "${command#*/}" "$@" ;;
  runtime-inspect/*|inspect/*) run_path tools/coakka-runtime-inspect "${command#*/}" "$@" ;;
  containers)
    if [[ "$#" == 0 ]]; then coakka_print_containers
    elif [[ "$1" == down || "$1" == stop ]]; then
      for row in "${COAKKA_CONTAINER_ROWS[@]}"; do
        IFS='|' read -r sample _ <<<"${row}"
        if run_path "containers/${sample}" down >/dev/null 2>&1; then
          coakka_note "containers/${sample}: down ok"
        else
          coakka_note "containers/${sample}: already stopped or not present"
        fi
      done
    else sample="$1"; shift; run_path "containers/${sample}" "$@"; fi ;;
  scenarios)
    if [[ "$#" == 0 ]]; then coakka_print_scenarios
    elif [[ "$#" == 1 && "$1" == check ]]; then
      for row in "${COAKKA_SCENARIO_ROWS[@]}"; do
        IFS='|' read -r track topology _ <<<"${row}"
        run_path "scenarios/${track}/${topology}" check
      done
    else coakka_die 'Usage: coakka-runtime scenarios [check]'; fi ;;
  scenario)
    [[ "$#" -ge 2 ]] || coakka_die 'Usage: coakka-runtime scenario <track> <topology> [command]'
    track="$1"; topology="$2"; shift 2
    run_path "scenarios/${track}/${topology}" "$@" ;;
  help|-h|--help)
    printf '%s\n' 'Usage: bash run.sh coakka-runtime <language> <sample>' \
      '  coakka-runtime [list|all|runtime-test|client|inspect|addons|containers|scenarios|scenario] ...' ;;
  */*) run_path "${command}" "$@" ;;
  *)
    if [[ "$#" == 0 ]]; then run_path "jvm/${command}"
    elif [[ "$#" == 1 ]]; then run_path "${command}/$1"
    else coakka_die 'Usage: coakka-runtime <language> <sample>'; fi ;;
esac
