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
    coakka_note "${label} passed through HTTP fallback after a runtime deadletter; smoke is runnable, but remote runtime delivery did not handle this request."
  fi

  if grep -q '"status"[[:space:]]*:[[:space:]]*"RUNTIME_DELIVERY_FAILED"' "${body_file}"; then
    rm -f "${body_file}"
    coakka_die "${label} reached the sample app, but the published runtime artifact is still using the stub backend. Publish a remote-capable runtime artifact before treating this scenario as a passing smoke."
  fi

  if [[ ! "${http_code}" =~ ^2[0-9][0-9]$ ]]; then
    rm -f "${body_file}"
    coakka_die "${label} returned HTTP ${http_code}."
  fi

  rm -f "${body_file}"
}
