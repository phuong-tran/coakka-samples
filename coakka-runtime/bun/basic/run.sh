#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
exec bun "${repo_root}/coakka-runtime/scripts/runtime-js-candidate.mjs" bun basic
