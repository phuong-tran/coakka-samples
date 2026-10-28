#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
: "${COAKKA_SAMPLE_WORK_ROOT:?Set an existing absolute work directory}"
: "${COAKKA_GO_CANDIDATE_MODULE:?Set the exact private module directory}"
[[ "${COAKKA_SAMPLE_WORK_ROOT}" == /* && -d "${COAKKA_SAMPLE_WORK_ROOT}" && ! -L "${COAKKA_SAMPLE_WORK_ROOT}" ]] || exit 2
export GOCACHE="${GOCACHE:-${COAKKA_SAMPLE_WORK_ROOT}/go-build-cache}"
export GOTMPDIR="${GOTMPDIR:-${COAKKA_SAMPLE_WORK_ROOT}/go-tool-temp}"
[[ "${GOCACHE}" == /* && "${GOTMPDIR}" == /* ]] || exit 2
mkdir -p "${GOCACHE}" "${GOTMPDIR}"
export GOTOOLCHAIN=local GOWORK=off GOPROXY=off GOSUMDB=off GOENV=off
exec go run "${repo_root}/coakka-runtime/scripts/runtime-go-candidate.go" --repo-root "${repo_root}" deadletter
