#!/usr/bin/env bash

coakka_artifact_sha256() {
  if [[ "$#" -ne 1 ]]; then
    echo "usage: coakka_artifact_sha256 <path>" >&2
    return 2
  fi

  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
    return 0
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
    return 0
  fi

  echo "[coakka-samples] shasum or sha256sum is required to verify artifact checksums" >&2
  return 1
}

coakka_manifest_sha256() {
  if [[ "$#" -ne 2 ]]; then
    echo "usage: coakka_manifest_sha256 <manifest> <relative-path>" >&2
    return 2
  fi

  local manifest="$1"
  local relative_path="$2"
  local status label manifest_path expected_sha extra

  while IFS=$'\t' read -r status label manifest_path expected_sha extra || [[ -n "${status:-}" ]]; do
    [[ -z "${status:-}" || "${status}" == \#* ]] && continue
    if [[ "${status}" == "public" && "${manifest_path}" == "${relative_path}" ]]; then
      printf '%s\n' "${expected_sha}"
      return 0
    fi
  done <"${manifest}"

  return 1
}

coakka_verify_artifact_sha256() {
  if [[ "$#" -ne 3 ]]; then
    echo "usage: coakka_verify_artifact_sha256 <path> <relative-path> <expected-sha256>" >&2
    return 2
  fi

  local path="$1"
  local relative_path="$2"
  local expected_sha="$3"
  local actual_sha

  actual_sha="$(coakka_artifact_sha256 "${path}")" || return 1
  if [[ "${actual_sha}" != "${expected_sha}" ]]; then
    echo "[coakka-samples] artifact checksum mismatch for ${relative_path}" >&2
    echo "[coakka-samples] expected ${expected_sha}" >&2
    echo "[coakka-samples] actual   ${actual_sha}" >&2
    return 1
  fi
}

coakka_resolve_artifact() {
  if [[ "$#" -ne 3 ]]; then
    echo "usage: coakka_resolve_artifact <publish-root> <relative-path> <download-target>" >&2
    return 2
  fi

  local publish_root="$1"
  local relative_path="$2"
  local download_target="$3"
  local local_path="${publish_root}/${relative_path}"
  local manifest_path="${publish_root}/artifacts/public-artifacts.tsv"
  local expected_sha manifest_tmp

  if [[ -f "${local_path}" ]]; then
    if [[ -f "${manifest_path}" ]]; then
      if expected_sha="$(coakka_manifest_sha256 "${manifest_path}" "${relative_path}")"; then
        coakka_verify_artifact_sha256 "${local_path}" "${relative_path}" "${expected_sha}" || return 1
      else
        echo "[coakka-samples] local public artifact manifest does not list ${relative_path}" >&2
        return 1
      fi
    fi
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
  manifest_tmp="$(mktemp "${TMPDIR:-/tmp}/coakka-public-artifacts.XXXXXX")"
  if ! curl -fsSL "${raw_base%/}/artifacts/public-artifacts.tsv" -o "${manifest_tmp}"; then
    rm -f "${manifest_tmp}"
    echo "[coakka-samples] public artifact manifest is required to verify ${relative_path}" >&2
    return 1
  fi
  if ! expected_sha="$(coakka_manifest_sha256 "${manifest_tmp}" "${relative_path}")"; then
    rm -f "${manifest_tmp}"
    echo "[coakka-samples] public artifact manifest does not list ${relative_path}" >&2
    return 1
  fi
  rm -f "${manifest_tmp}"
  coakka_verify_artifact_sha256 "${download_target}" "${relative_path}" "${expected_sha}" || return 1
  printf '%s\n' "${download_target}"
}
