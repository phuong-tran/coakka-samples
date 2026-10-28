#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}/coakka-runtime/csharp"
: "${COAKKA_SAMPLE_WORK_ROOT:?Choose an existing absolute work root}"
: "${COAKKA_SAMPLE_RID:?Choose osx-arm64, linux-arm64 or win-x64}"
case "${COAKKA_SAMPLE_WORK_ROOT}" in /*) ;; *) echo 'Absolute work root required' >&2; exit 2;; esac
[[ -d "${COAKKA_SAMPLE_WORK_ROOT}" && ! -L "${COAKKA_SAMPLE_WORK_ROOT}" ]]
case "${COAKKA_SAMPLE_RID}" in osx-arm64|linux-arm64) app=CoAkka.Runtime.Sample;; win-x64) app=CoAkka.Runtime.Sample.exe;; *) exit 2;; esac
mkdir -p "${COAKKA_SAMPLE_WORK_ROOT}/csharp/temp"
export TMPDIR="${COAKKA_SAMPLE_WORK_ROOT}/csharp/temp" TMP="${COAKKA_SAMPLE_WORK_ROOT}/csharp/temp" TEMP="${COAKKA_SAMPLE_WORK_ROOT}/csharp/temp"
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 MSBUILDDISABLENODEREUSE=1 DOTNET_GENERATE_ASPNET_CERTIFICATE=false
output="${COAKKA_SAMPLE_WORK_ROOT}/csharp/published/${COAKKA_SAMPLE_RID}"
dotnet publish basic/CoAkka.Runtime.Sample.csproj -c Release -r "${COAKKA_SAMPLE_RID}" -o "${output}" -warnaserror
exec "${output}/${app}"
