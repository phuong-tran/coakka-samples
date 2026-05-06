#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

app_task=":runtime:scenarios:customer-crud:kotlin-desktop-local:customer-desktop"

print_usage() {
  cat <<'EOF'
Kotlin Desktop local customer CRUD

Usage:
  bash run.sh
  bash run.sh check
  bash run.sh dev
  bash run.sh app
  bash run.sh build
  bash run.sh smoke

Default command is 'check'. Use 'app' or 'dev' to open the Swing desktop UI.
EOF
}

require_runtime_commands() {
  coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
  coakka_require_command java "Install JDK 17 or newer, then retry."
}

build_app() {
  require_runtime_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${app_task}:build" --quiet
  coakka_note "check ok: built Kotlin Desktop local runtime customer app"
}

run_app() {
  require_runtime_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${app_task}:run" --quiet
}

smoke() {
  require_runtime_commands
  bash "${repo_root}/gradlew" -p "${repo_root}" "${app_task}:run" --quiet --args='--smoke'
}

case "${1:-}" in
  ""|check|build)
    build_app
    ;;
  app|dev)
    run_app
    ;;
  smoke)
    smoke
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    print_usage
    coakka_die "Unknown command: $1"
    ;;
esac
