#!/usr/bin/env bash

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
