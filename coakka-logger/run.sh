#!/usr/bin/env bash
set -euo pipefail
product_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${product_root}/.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"
source "${repo_root}/scripts/sample-metadata.sh"
case "${1:-all}" in
  all) exec bash "${product_root}/run-all.sh" ;;
  list) coakka_print_product_samples coakka-logger; exit 0 ;;
  help|-h|--help) printf '%s\n' 'Usage: bash run.sh coakka-logger <language> <sample>'; exit 0 ;;
esac
if [[ "$1" == */* && "$#" == 1 ]]; then selected="$1"
elif [[ "$#" == 1 ]]; then selected="jvm/$1"
elif [[ "$#" == 2 ]]; then selected="$1/$2"
else coakka_die 'Usage: coakka-logger <language> <sample>'; fi
[[ "${selected}" =~ ^[a-z0-9-]+/[a-z0-9-]+$ ]] || coakka_die 'Invalid Logger sample path'
entry="${product_root}/${selected}/run.sh"
coakka_require_file "${entry}" "Use 'bash run.sh coakka-logger list'."
exec bash "${entry}"
