#!/usr/bin/env bash

coakka_resolve_artifact() {
  if [[ "$#" -ne 3 ]]; then
    echo "usage: coakka_resolve_artifact <publish-root> <relative-path> <download-target>" >&2
    return 2
  fi

  local publish_root="$1"
  local relative_path="$2"
  local download_target="$3"
  local local_path="${publish_root}/${relative_path}"

  if [[ -f "${local_path}" ]]; then
    echo "[coakka-samples] using local artifact: ${local_path}" >&2
    printf '%s\n' "${local_path}"
    return 0
  fi

  local raw_base="${COAKKA_PUBLISH_RAW_BASE:-https://raw.githubusercontent.com/phuong-tran/coakka-publish/main}"
  local url="${raw_base%/}/${relative_path}"

  mkdir -p "$(dirname "${download_target}")"
  if ! command -v curl >/dev/null 2>&1; then
    echo "[coakka-samples] curl is required to download ${url}" >&2
    return 1
  fi

  echo "[coakka-samples] downloading public artifact: ${url}" >&2
  curl -fsSL "${url}" -o "${download_target}"
  printf '%s\n' "${download_target}"
}
