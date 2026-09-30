#!/usr/bin/env bash
# The sourced path is resolved at runtime from this script's repository location.
# Values exported to the language runners are intentionally unused here.
# shellcheck disable=SC1091,SC2034
set -euo pipefail

sample_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd "${sample_root}/.." && pwd)"
runtime_root="${COAKKA_HTTP_RUNTIME_ROOT:-${repo_root}/../coakka-http-runtime}"
connector_root="${COAKKA_HTTP_CONNECTOR_ROOT:-${repo_root}/../coakka-http-runtime-connector}"
work_root="${COAKKA_HTTP_SAMPLE_WORK_ROOT:?set COAKKA_HTTP_SAMPLE_WORK_ROOT to an external build directory}"

require_file() {
  local path="$1"
  [[ -f "${path}" ]] || {
    printf 'required file is missing: %s\n' "${path}" >&2
    exit 1
  }
}

prepare_work_dir() {
  local lane="$1"
  local directory="${work_root}/${lane}"
  mkdir -p "${directory}"
  printf '%s\n' "${directory}"
}

# Build and install only the focused host component. The shared prefix is reused
# by every language lane and remains outside the source checkout.
prepare_host_prefix() {
  local build prefix
  local -a platform_options=()
  if [[ -n "${COAKKA_HTTP_PREPARED_HOST_PREFIX:-}" ]]; then
    require_file "${COAKKA_HTTP_PREPARED_HOST_PREFIX}/include/coakka/http/host.h"
    require_file "${COAKKA_HTTP_PREPARED_HOST_PREFIX}/lib/cmake/CoAkkaHttpHost/CoAkkaHttpHostConfig.cmake"
    printf '%s\n' "${COAKKA_HTTP_PREPARED_HOST_PREFIX}"
    return
  fi
  build="$(prepare_work_dir native)/build"
  prefix="$(prepare_work_dir native)/prefix"
  if [[ "$(uname -s)" == Darwin ]]; then
    platform_options+=("-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0")
  fi
  cmake -S "${runtime_root}" -B "${build}" -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTING=OFF -DCOAKKA_HTTP_BUILD_DEPENDENCY_PROBE=OFF \
    -DCOAKKA_HTTP_ENABLE_OPENSSL_PROVIDER=ON \
    -DCOAKKA_HTTP_ENABLE_ZLIB_PROVIDER=ON \
    -DCOAKKA_HTTP_ENABLE_HTTP2_FOUNDATION=ON \
    -DCOAKKA_HTTP_ENABLE_HTTP3_DEPENDENCY_FOUNDATION=ON \
    -DCOAKKA_HTTP_ENABLE_PINNED_CURL_CLIENT_FOUNDATION=ON \
    -DCOAKKA_HTTP_BUILD_CURL_HTTP1_PROVIDER=ON \
    "${platform_options[@]}" \
    -DCMAKE_INSTALL_PREFIX="${prefix}" >/dev/null
  cmake --build "${build}" --target coakka_http_host --parallel >/dev/null
  cmake --install "${build}" --component coakka_http_host >/dev/null
  printf '%s\n' "${prefix}"
}

prepare_test_certificates() {
  local directory
  directory="$(prepare_work_dir tls)/identities"
  bash "${sample_root}/scripts/generate-test-certificates.sh" "${directory}"
  printf '%s\n' "${directory}"
}

free_loopback_port() {
  python3 -c 'import socket; value=socket.socket(); value.bind(("127.0.0.1", 0)); print(value.getsockname()[1]); value.close()'
}

# Exercise one native TLS server with certificate verification and finite polls.
smoke_native_security_server() {
  local executable="$1"
  local fixtures="$2"
  local label="$3"
  local mode port log pid body
  for mode in tls mtls; do
    port="$(free_loopback_port)"
    log="${work_root}/${label}/security-${mode}.log"
    mkdir -p "$(dirname "${log}")"
    "${executable}" --security "${mode}" --protocol http1 \
      --fixtures "${fixtures}" --port "${port}" >"${log}" 2>&1 &
    pid=$!
    body=""
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
      if [[ "${mode}" == tls ]]; then
        body="$(curl --fail --silent --show-error --max-time 2 \
          --cacert "${fixtures}/ca.pem" "https://localhost:${port}/secure" 2>/dev/null || true)"
      else
        body="$(curl --fail --silent --show-error --max-time 2 \
          --cacert "${fixtures}/ca.pem" --cert "${fixtures}/client.pem" \
          --key "${fixtures}/client.key" "https://localhost:${port}/secure" 2>/dev/null || true)"
      fi
      [[ "${body}" == "${mode}-ready" ]] && break
      sleep 0.1
    done
    if [[ "${body}" != "${mode}-ready" ]]; then
      kill -TERM "${pid}" 2>/dev/null || true
      wait "${pid}" 2>/dev/null || true
      cat "${log}" >&2
      printf '%s %s handshake failed\n' "${label}" "${mode}" >&2
      return 1
    fi
    if [[ "${mode}" == mtls ]] && curl --fail --silent --show-error --max-time 2 \
      --cacert "${fixtures}/ca.pem" "https://localhost:${port}/secure" \
      >/dev/null 2>&1; then
      kill -TERM "${pid}" 2>/dev/null || true
      wait "${pid}" 2>/dev/null || true
      printf '%s mutual TLS accepted a client without an identity\n' "${label}" >&2
      return 1
    fi
    kill -TERM "${pid}"
    wait "${pid}"
  done
  printf '%s-security-smoke=pass\n' "${label}"
}

host_native_target() {
  local os architecture
  os="$(uname -s)"
  architecture="$(uname -m)"
  case "${os}/${architecture}" in
    Darwin/arm64) printf 'macos-aarch64\n' ;;
    Linux/aarch64|Linux/arm64) printf 'linux-aarch64\n' ;;
    Linux/x86_64|Linux/amd64) printf 'linux-x86_64\n' ;;
    *) printf 'unsupported sample host: %s/%s\n' "${os}" "${architecture}" >&2; return 1 ;;
  esac
}
