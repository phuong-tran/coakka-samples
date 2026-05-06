#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/scripts/sample-utils.sh"
source "${script_dir}/scripts/sample-metadata.sh"

print_usage() {
  cat <<'EOF'
coakka-samples

Usage:
  bash run.sh
  bash run.sh quickstart
  bash run.sh list
  bash run.sh doctor
  bash run.sh all
  bash run.sh logger
  bash run.sh runtime
  bash run.sh scenarios
  bash run.sh scenarios check
  bash run.sh <lane> <sample>
  bash run.sh <lane> <language> <sample>
  bash run.sh scenario <track> <topology> [command]
  bash run.sh <lane>/<language>/<sample>
  bash run.sh runtime/scenarios/<track>/<topology> <command>

Examples:
  bash run.sh
  bash run.sh runtime basic
  bash run.sh logger basic
  bash run.sh logger/python/pressure
  bash run.sh runtime node basic
  bash run.sh runtime/go/deadletter
  bash run.sh scenarios
  bash run.sh scenarios check
  bash run.sh scenario customer-crud spring-boot-go check
  bash run.sh runtime/scenarios/customer-crud/spring-boot-node smoke

Lanes:
  logger
  runtime

Logger languages:
  jvm
  python
  node
  go
  native

Runtime languages:
  jvm
  python
  node
  go
EOF
}

print_samples() {
  coakka_print_samples
}

run_sample_path() {
  local sample_path="$1"
  shift || true
  local sample_script="${script_dir}/${sample_path}/run.sh"
  coakka_require_file "${sample_script}" "Use 'bash run.sh list' to see available samples."
  coakka_note "running ${sample_path}"
  bash "${sample_script}" "$@"
}

run_quickstart() {
  echo "coakka-samples quickstart"
  echo
  bash "${script_dir}/scripts/doctor.sh"
  echo
  echo "[quickstart] runtime jvm basic"
  run_sample_path "runtime/jvm/basic"
  echo
  echo "[quickstart] logger jvm basic"
  run_sample_path "logger/jvm/basic"
}

run_scenario() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "Usage: bash run.sh scenario <track> <topology> [command]"
  fi

  local track="$1"
  local topology="$2"
  shift 2
  run_sample_path "runtime/scenarios/${track}/${topology}" "$@"
}

run_scenario_checks() {
  local scenario_path
  for scenario_path in \
    "runtime/scenarios/customer-crud/spring-boot-single-process" \
    "runtime/scenarios/customer-crud/kotlin-desktop-local" \
    "runtime/scenarios/customer-crud/python-desktop-local" \
    "runtime/scenarios/customer-crud/spring-boot-spring-boot" \
    "runtime/scenarios/customer-crud/spring-boot-node" \
    "runtime/scenarios/customer-crud/spring-boot-go" \
    "runtime/scenarios/customer-crud/spring-boot-nodes"; do
    echo "[scenarios/check] ${scenario_path}"
    run_sample_path "${scenario_path}" check
  done
}

if [[ "$#" -eq 0 ]]; then
  run_quickstart
  exit 0
fi

case "$1" in
  quickstart|start|try)
    run_quickstart
    exit 0
    ;;
  list)
    print_samples
    exit 0
    ;;
  scenarios)
    if [[ "$#" -eq 1 ]]; then
      echo "Available scenarios:"
      coakka_print_scenarios
    elif [[ "$#" -eq 2 && "$2" == "check" ]]; then
      run_scenario_checks
    else
      coakka_die "Usage: bash run.sh scenarios [check]"
    fi
    exit 0
    ;;
  doctor)
    bash "${script_dir}/scripts/doctor.sh"
    exit 0
    ;;
  help|-h|--help)
    print_usage
    exit 0
    ;;
  all)
    bash "${script_dir}/logger/run-all.sh"
    bash "${script_dir}/runtime/run-all.sh"
    exit 0
    ;;
  logger)
    if [[ "$#" -eq 1 || "${2:-}" == "all" ]]; then
      bash "${script_dir}/logger/run-all.sh"
    elif [[ "$#" -eq 2 ]]; then
      run_sample_path "logger/jvm/$2"
    elif [[ "$#" -eq 3 ]]; then
      run_sample_path "logger/$2/$3"
    else
      coakka_die "Usage: bash run.sh logger [<jvm|python|node|go|native>] <sample>"
    fi
    ;;
  runtime)
    if [[ "$#" -eq 1 || "${2:-}" == "all" ]]; then
      bash "${script_dir}/runtime/run-all.sh"
    elif [[ "$#" -eq 2 ]]; then
      run_sample_path "runtime/jvm/$2"
    elif [[ "$#" -eq 3 ]]; then
      run_sample_path "runtime/$2/$3"
    else
      coakka_die "Usage: bash run.sh runtime [<jvm|python|node|go|native>] <sample>"
    fi
    ;;
  scenario)
    shift
    run_scenario "$@"
    ;;
  logger/*|runtime/*)
    sample_path="$1"
    shift
    run_sample_path "${sample_path}" "$@"
    ;;
  *)
    print_usage
    coakka_die "Unknown command or sample path: $1"
    ;;
esac
