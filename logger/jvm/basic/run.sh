#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_file "${repo_root}/gradlew" "Run from a full coakka-samples checkout."
coakka_require_command java "Install JDK 17 or newer, then retry."
bash "${repo_root}/gradlew" -p "${repo_root}" :logger:jvm:basic:run --quiet
