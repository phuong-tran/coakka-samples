#!/usr/bin/env bash

coakka_die() {
  echo "[coakka-samples] $*" >&2
  exit 1
}

coakka_note() {
  echo "[coakka-samples] $*" >&2
}

coakka_require_command() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_require_command <command> <install-hint>"
  fi

  local command_name="$1"
  local install_hint="$2"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    coakka_die "Missing required command '${command_name}'. ${install_hint}"
  fi
}

coakka_require_file() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_require_file <path> <hint>"
  fi

  local file_path="$1"
  local hint="$2"
  if [[ ! -f "${file_path}" ]]; then
    coakka_die "Missing required file '${file_path}'. ${hint}"
  fi
}

coakka_require_executable_file() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_require_executable_file <path> <hint>"
  fi

  local file_path="$1"
  local hint="$2"
  if [[ ! -x "${file_path}" ]]; then
    coakka_die "Missing executable file '${file_path}'. ${hint}"
  fi
}

coakka_python_bin() {
  printf '%s\n' "${COAKKA_PYTHON:-python3}"
}

coakka_require_python_venv() {
  local python_bin
  python_bin="$(coakka_python_bin)"
  coakka_require_command "${python_bin}" "Install Python 3.11 or newer, then retry."
  "${python_bin}" -m venv --help >/dev/null 2>&1 ||
    coakka_die "Python venv support is required. Install the venv module for ${python_bin}, then retry."
}

coakka_with_python_wheel_env() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_with_python_wheel_env <wheel-path> <script> [args...]"
  fi

  local wheel_path="$1"
  shift

  coakka_require_python_venv
  local python_bin tmp_dir venv_python status
  python_bin="$(coakka_python_bin)"
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT INT TERM
  "${python_bin}" -m venv "${tmp_dir}/venv"
  venv_python="${tmp_dir}/venv/bin/python"
  PIP_DISABLE_PIP_VERSION_CHECK=1 "${venv_python}" -m pip install "${wheel_path}" >/dev/null

  set +e
  "${venv_python}" "$@"
  status="$?"
  set -e

  rm -rf "${tmp_dir}"
  trap - EXIT INT TERM
  return "${status}"
}

coakka_with_python_package_env() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_with_python_package_env <package-spec> <script> [args...]"
  fi

  local package_spec="$1"
  shift

  coakka_require_python_venv
  local python_bin tmp_dir venv_python status
  python_bin="$(coakka_python_bin)"
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT INT TERM
  "${python_bin}" -m venv "${tmp_dir}/venv"
  venv_python="${tmp_dir}/venv/bin/python"
  PIP_DISABLE_PIP_VERSION_CHECK=1 "${venv_python}" -m pip install --no-cache-dir "${package_spec}" >/dev/null

  set +e
  "${venv_python}" "$@"
  status="$?"
  set -e

  rm -rf "${tmp_dir}"
  trap - EXIT INT TERM
  return "${status}"
}

coakka_port_pids() {
  if [[ "$#" -lt 1 ]]; then
    coakka_die "usage: coakka_port_pids <port>"
  fi

  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti "tcp:${port}" 2>/dev/null || true
    return 0
  fi

  if command -v fuser >/dev/null 2>&1; then
    fuser "${port}/tcp" 2>/dev/null | tr ' ' '\n' | sed '/^$/d' || true
    return 0
  fi
}

coakka_stop_ports() {
  if [[ "$#" -lt 1 ]]; then
    coakka_die "usage: coakka_stop_ports <port>..."
  fi

  local port pids
  for port in "$@"; do
    pids="$(coakka_port_pids "${port}")"
    if [[ -n "${pids}" ]]; then
      coakka_note "stopping processes on port ${port}: ${pids}"
      kill ${pids} 2>/dev/null || true
    fi
  done
}

coakka_customer_smoke_request() {
  if [[ "$#" -lt 2 ]]; then
    coakka_die "usage: coakka_customer_smoke_request <label> <curl-args...>"
  fi

  local label="$1"
  shift

  local body_file http_code
  body_file="$(mktemp)"
  if ! http_code="$(curl -sS -o "${body_file}" -w '%{http_code}' "$@")"; then
    cat "${body_file}" >&2 || true
    rm -f "${body_file}"
    coakka_die "${label} failed before an HTTP response was received."
  fi

  cat "${body_file}"
  printf '\n'

  if grep -q '"deliveryMode"[[:space:]]*:[[:space:]]*"runtime-deadletter-http-fallback"' "${body_file}"; then
    rm -f "${body_file}"
    coakka_die "${label} returned the retired HTTP fallback delivery mode. Customer scenarios must keep inter-service business traffic on the runtime path."
  fi

  if grep -q '"status"[[:space:]]*:[[:space:]]*"RUNTIME_DELIVERY_FAILED"' "${body_file}"; then
    rm -f "${body_file}"
    coakka_die "${label} reached the sample app, but runtime-only cross-process delivery failed. Publish a runtime artifact with cross-process delivery enabled before treating CRUD smoke as passing."
  fi

  if [[ ! "${http_code}" =~ ^2[0-9][0-9]$ ]]; then
    rm -f "${body_file}"
    coakka_die "${label} returned HTTP ${http_code}."
  fi

  rm -f "${body_file}"
}

coakka_customer_expect_http_status() {
  if [[ "$#" -lt 3 ]]; then
    coakka_die "usage: coakka_customer_expect_http_status <label> <expected-status> <curl-args...>"
  fi

  local label="$1"
  local expected_status="$2"
  shift 2

  local body_file http_code
  body_file="$(mktemp)"
  if ! http_code="$(curl -sS -o "${body_file}" -w '%{http_code}' "$@")"; then
    cat "${body_file}" >&2 || true
    rm -f "${body_file}"
    coakka_die "${label} failed before an HTTP response was received."
  fi

  cat "${body_file}"
  printf '\n'

  if [[ "${http_code}" != "${expected_status}" ]]; then
    rm -f "${body_file}"
    coakka_die "${label} returned HTTP ${http_code}; expected ${expected_status}."
  fi

  rm -f "${body_file}"
}
