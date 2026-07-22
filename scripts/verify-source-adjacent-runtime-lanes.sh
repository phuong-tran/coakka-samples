#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/sample-utils.sh"
listing_file="$(mktemp "${TMPDIR:-/tmp}/coakka-sample-list.XXXXXX")"
trap 'rm -f "${listing_file}"' EXIT

require_sample_listing() {
  local sample_path="$1"
  if ! grep -Fq "${sample_path}" "${listing_file}"; then
    coakka_die "sample listing does not include ${sample_path}"
  fi
}

echo "[source-adjacent-runtime-lanes] listing"
bash "${repo_root}/run.sh" list >"${listing_file}"
require_sample_listing "runtime/bun/basic"
require_sample_listing "runtime/tauri/intent-command"
require_sample_listing "runtime/tauri/desktop-intent"

echo "[source-adjacent-runtime-lanes] bun"
bash "${repo_root}/run.sh" runtime bun basic

echo "[source-adjacent-runtime-lanes] tauri intent-command"
bash "${repo_root}/run.sh" runtime tauri intent-command

echo "[source-adjacent-runtime-lanes] tauri desktop-intent"
bash "${repo_root}/run.sh" runtime tauri desktop-intent

echo "[source-adjacent-runtime-lanes] artifact pin guards"
bash "${repo_root}/scripts/test-artifact-pins.sh"
bash "${repo_root}/scripts/test-resolve-artifact.sh"

echo "[source-adjacent-runtime-lanes] ok"
