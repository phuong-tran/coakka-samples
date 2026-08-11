#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/sample-metadata.sh"

public_manifest_path="artifacts/public-artifacts.tsv"
tmp_files=()

cleanup() {
  if [[ "${#tmp_files[@]}" -gt 0 ]]; then
    rm -f "${tmp_files[@]}"
  fi
}
trap cleanup EXIT

required_rows=("${COAKKA_ARTIFACT_ROWS[@]}")
compatibility_rows=("${COAKKA_COMPATIBILITY_ARTIFACT_ROWS[@]-}")

stale_patterns=(
  "0.1.0+5e25""dda67597"
  "0.1.0-g5e25""dda67597"
  "0.1.0-g5e25""dda67597-a21e1ad"
  "0.1.0+2eae""d9a043ca"
  "0.1.0+22f""571fd955c"
  "0.1.0-g22f""571fd955c"
  "0.1.0-g0cb""644340467-e4a8""ed3"
  "0.1.0+0cb""644340467"
  "0.1.0-g0cb""644340467-cfb8ee4"
  "runtime/jvm/releases/0.1.0+0cb""644340467/coakka-jvm-native-runtime-v2-0.1.0-g0cb644""340467.jar"
  "0.1.0+22f""571fd955c"
  "0.1.1-g22f""571fd955c"
  "0.1.0-g432""bd75d3e4b"
  "0.1.0-g26e""e0819dc3d"
  "runtime/jvm/releases/0.2.0+94a5729-5ab812f/coakka-jvm-native-runtime-v2-0.2.0-g94a5729-5ab""812f.jar"
  "maven/coakka/spring/coakka-spring-boot-starter/0.2.0-g5ab""812f/coakka-spring-boot-starter-0.2.0-g5ab812f.jar"
  "maven/coakka/quarkus/coakka-quarkus-extension/0.2.0-g5ab""812f/coakka-quarkus-extension-0.2.0-g5ab812f.jar"
  "1.4.0+2cee""86bf"
  "1.4.0-g2cee""86bf"
  "coakka-v2-connector-node@1.4.""5"
  "coakka-v2-connector-bun@1.4.""5"
  "coakka-v2-connector-electron@1.4.""5"
  "coakka-v2-connector==1.4.""5"
  "CoAkka.Runtime==1.4.""6"
  "--version 1.4.""6"
  "coakka-runtime-go@v1.4.""0"
  "coakka-runtime-swift@v1.4.""0"
  "coakka-runtime-go@v1.5.""0"
  "require \${module_path} v1.5.""0"
  "coakka-runtime-swift@2.1.""0"
  "exact: \"2.1.""0\""
)

fail() {
  printf '[fail] %s\n' "$*" >&2
  exit 1
}

validate_manifest_rows() {
  local manifest="$1"
  local source_name="$2"
  local line_no=0
  local public_rows=0
  local seen_paths=$'\n'
  local seen_labels=$'\n'
  local status label relative_path expected_sha extra

  while IFS=$'\t' read -r status label relative_path expected_sha extra || [[ -n "${status:-}" ]]; do
    line_no=$((line_no + 1))
    [[ -z "${status:-}" || "${status}" == \#* ]] && continue

    if [[ -n "${extra:-}" || -z "${label:-}" || -z "${relative_path:-}" || -z "${expected_sha:-}" ]]; then
      fail "${source_name} manifest has invalid row ${line_no}"
    fi
    if [[ "${status}" != "public" ]]; then
      fail "${source_name} manifest has unsupported status '${status}' on row ${line_no}"
    fi
    if [[ "${relative_path}" == /* || "${relative_path}" == *".."* ]]; then
      fail "${source_name} manifest has unsafe path on row ${line_no}: ${relative_path}"
    fi
    case "${relative_path}" in
      logger/*/releases/*|runtime/*/releases/*|runtime-addons/*/native/releases/*|runtime-inspect/native/releases/*|maven/coakka/*/*/*/*.jar|cli/releases/*|demo/coakka-client/releases/*|coakka-tools/*/releases/*|coakka-tools/*/*/releases/*)
        ;;
      *)
        fail "${source_name} manifest has path outside the published artifact surface on row ${line_no}: ${relative_path}"
        ;;
    esac
    if [[ "${seen_paths}" == *$'\n'"${relative_path}"$'\n'* ]]; then
      fail "${source_name} manifest has duplicate artifact path on row ${line_no}: ${relative_path}"
    fi
    if [[ "${seen_labels}" == *$'\n'"${label}"$'\n'* ]]; then
      fail "${source_name} manifest has duplicate artifact label on row ${line_no}: ${label}"
    fi
    if [[ "${#expected_sha}" -ne 64 || "${expected_sha}" == *[!0-9a-f]* ]]; then
      fail "${source_name} manifest has invalid sha256 on row ${line_no}"
    fi
    seen_paths+="${relative_path}"$'\n'
    seen_labels+="${label}"$'\n'
    public_rows=$((public_rows + 1))
  done <"${manifest}"

  [[ "${public_rows}" -gt 0 ]] || fail "${source_name} manifest has no public artifact rows"
}

manifest_row_exists() {
  local manifest="$1"
  local needle="$2"
  local status label relative_path expected_sha extra

  while IFS=$'\t' read -r status label relative_path expected_sha extra || [[ -n "${status:-}" ]]; do
    [[ -z "${status:-}" || "${status}" == \#* ]] && continue
    if [[ "${status}" == "public" && "${label}|${relative_path}" == "${needle}" ]]; then
      return 0
    fi
  done <"${manifest}"

  return 1
}

check_manifest_required_rows() {
  local manifest="$1"
  local source_name="$2"
  local row

  validate_manifest_rows "${manifest}" "${source_name}"
  for row in "${required_rows[@]}"; do
    manifest_row_exists "${manifest}" "${row}" ||
      fail "${source_name} manifest is missing public artifact row: ${row}"
  done
}

tracked_source_files() {
  git -C "${repo_root}" ls-files \
    ':!:*.lock' \
    ':!:CHANGELOG.md' \
    ':!:.gradle/**' \
    ':!:build/**' \
    ':!:**/build/**' \
    ':!:.idea/**'
}

check_metadata_rows() {
  local row label relative_path extra
  for row in "${required_rows[@]}"; do
    IFS='|' read -r label relative_path extra <<<"${row}"
    [[ -n "${label}" && -n "${relative_path}" && -z "${extra:-}" ]] ||
      fail "sample-metadata.sh has an invalid public artifact row: ${row}"
  done
}

check_stale_patterns() {
  local pattern matches
  for pattern in "${stale_patterns[@]}"; do
    matches="$(
      tracked_source_files |
        xargs grep -n -F -- "${pattern}" 2>/dev/null || true
    )"
    if [[ -n "${matches}" ]]; then
      printf '%s\n' "${matches}" >&2
      fail "stale artifact pin found: ${pattern}"
    fi
  done
}

check_local_artifacts() {
  local publish_root manifest row label relative_path
  publish_root="$(coakka_default_publish_root "${repo_root}")"
  if [[ ! -d "${publish_root}" ]]; then
    printf '[skip] local public publish checkout not found at %s\n' "${publish_root}"
    return 0
  fi

  manifest="${publish_root}/${public_manifest_path}"
  [[ -f "${manifest}" ]] ||
    fail "local public publish checkout is missing artifact manifest: ${public_manifest_path}"
  check_manifest_required_rows "${manifest}" "local public publish"

  for row in "${required_rows[@]}"; do
    IFS='|' read -r label relative_path <<<"${row}"
    [[ -f "${publish_root}/${relative_path}" ]] ||
      fail "local public publish checkout is missing ${label}: ${relative_path}"
  done
}

check_local_publish_gate() {
  local publish_root verify_script
  if [[ "${COAKKA_PIN_CHECK_PUBLISH_GATE:-1}" != "1" ]]; then
    printf '[skip] local public publish verification gate disabled\n'
    return 0
  fi

  publish_root="$(coakka_default_publish_root "${repo_root}")"
  verify_script="${publish_root}/scripts/check-native-artifact-linkage.sh"
  if [[ ! -x "${verify_script}" ]]; then
    printf '[skip] local public publish verification gate not found at %s\n' "${verify_script}"
    return 0
  fi

  "${verify_script}" >/dev/null
}

check_public_artifacts() {
  local raw_base manifest_tmp row label relative_path expected_sha artifact_tmp actual_sha
  if [[ "${COAKKA_PIN_CHECK_NETWORK:-0}" != "1" ]]; then
    printf '[skip] public artifact URL checks disabled; set COAKKA_PIN_CHECK_NETWORK=1 to enable\n'
    return 0
  fi
  command -v curl >/dev/null 2>&1 || fail "curl is required for COAKKA_PIN_CHECK_NETWORK=1"

  raw_base="${COAKKA_PUBLISH_RAW_BASE:-${COAKKA_PUBLISH_RAW_BASE_DEFAULT}}"
  manifest_tmp="$(mktemp "${TMPDIR:-/tmp}/coakka-public-artifacts.XXXXXX")"
  tmp_files+=("${manifest_tmp}")
  curl -fsSL --retry 5 --retry-all-errors --retry-delay 2 --max-time 20 \
    "${raw_base%/}/${public_manifest_path}" -o "${manifest_tmp}" ||
    fail "public artifact manifest is missing: ${public_manifest_path}"
  check_manifest_required_rows "${manifest_tmp}" "public raw"

  for row in "${required_rows[@]}"; do
    IFS='|' read -r label relative_path <<<"${row}"
    curl -fsI --retry 5 --retry-all-errors --retry-delay 2 --max-time 20 \
      "${raw_base%/}/${relative_path}" >/dev/null ||
      fail "public artifact URL missing ${label}: ${relative_path}"
  done

  for row in "${compatibility_rows[@]}"; do
    [[ -n "${row}" ]] || continue
    IFS='|' read -r label relative_path expected_sha <<<"${row}"
    artifact_tmp="$(mktemp "${TMPDIR:-/tmp}/coakka-compat-artifact.XXXXXX")"
    tmp_files+=("${artifact_tmp}")
    curl -fsSL --retry 5 --retry-all-errors --retry-delay 2 --max-time 60 \
      "${raw_base%/}/${relative_path}" -o "${artifact_tmp}" ||
      fail "public compatibility artifact URL missing ${label}: ${relative_path}"
    if command -v shasum >/dev/null 2>&1; then
      actual_sha="$(shasum -a 256 "${artifact_tmp}" | awk '{print $1}')"
    else
      actual_sha="$(sha256sum "${artifact_tmp}" | awk '{print $1}')"
    fi
    [[ "${actual_sha}" == "${expected_sha}" ]] ||
      fail "public compatibility artifact checksum mismatch for ${relative_path}"
  done
}

check_metadata_rows
check_stale_patterns
check_local_artifacts
check_local_publish_gate
check_public_artifacts

printf '[ok] artifact pins match the published artifact surface\n'
