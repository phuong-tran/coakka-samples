#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command java "Install a JDK, then retry."
bash "${repo_root}/gradlew" -p "${repo_root}" :runtime:jvm:java-deadletter:run --quiet
