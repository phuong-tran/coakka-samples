#!/usr/bin/env bash
# Variables are the sourced manifest API consumed by repository/product callers.
# shellcheck disable=SC2034
# Product manifests own their samples and published coordinates; this file only
# aggregates repository discovery. Artifact paths are not local sample paths.
coakka_metadata_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COAKKA_PUBLISH_RAW_BASE_DEFAULT="https://raw.githubusercontent.com/phuong-tran/coakka-publish/9429dd78fee2127e3aacbf0e753ec1a7bc141f6b"
source "${coakka_metadata_root}/coakka-logger/scripts/sample-metadata.sh"
source "${coakka_metadata_root}/coakka-runtime/scripts/sample-metadata.sh"
COAKKA_SAMPLE_ROWS=("${COAKKA_LOGGER_SAMPLE_ROWS[@]}" "${COAKKA_RUNTIME_SAMPLE_ROWS[@]}")
COAKKA_ARTIFACT_ROWS=("${COAKKA_LOGGER_ARTIFACT_ROWS[@]}" "${COAKKA_RUNTIME_ARTIFACT_ROWS[@]}")

coakka_default_publish_root() {
  printf '%s\n' "${COAKKA_PUBLISH_ROOT:-$1/../coakka-publish}"
}

coakka_print_product_samples() {
  local product="$1" row lane language sample summary
  for row in "${COAKKA_SAMPLE_ROWS[@]}"; do
    IFS='|' read -r lane language sample summary <<<"${row}"
    [[ "coakka-${lane}" == "${product}" ]] || continue
    printf '  %-55s %s\n' "${product}/${language}/${sample}" "${summary}"
  done
}

coakka_print_scenarios() {
  local row track topology summary
  for row in "${COAKKA_SCENARIO_ROWS[@]}"; do
    IFS='|' read -r track topology summary <<<"${row}"
    printf '  %-70s %s\n' "coakka-runtime/scenarios/${track}/${topology}" "${summary}"
  done
}

coakka_print_containers() {
  local row sample summary
  for row in "${COAKKA_CONTAINER_ROWS[@]}"; do
    IFS='|' read -r sample summary <<<"${row}"
    printf '  %-55s %s\n' "coakka-runtime/containers/${sample}" "${summary}"
  done
}

coakka_print_samples() {
  echo 'Products: coakka-runtime, coakka-http-runtime, coakka-logger'
  coakka_print_product_samples coakka-runtime
  coakka_print_product_samples coakka-logger
  printf '  %s\n' coakka-runtime/runtime-test coakka-runtime/tools/coakka-client coakka-runtime/tools/coakka-runtime-inspect coakka-runtime/addons coakka-runtime/demos/rpi-camera coakka-http-runtime/verify
  coakka_print_scenarios
  coakka_print_containers
}
