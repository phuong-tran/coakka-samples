#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command dotnet "Install .NET SDK 8 or newer, then retry."

tmp_dir="$(mktemp -d)"
cleanup() {
  local exit_code="$?"
  trap - EXIT
  rm -rf "${tmp_dir}"
  exit "${exit_code}"
}
trap cleanup EXIT
export NUGET_PACKAGES="${tmp_dir}/nuget-packages"
export NUGET_HTTP_CACHE_PATH="${tmp_dir}/http-cache"

dotnet new console -o "${tmp_dir}/consumer" --framework net8.0 --force >/dev/null
dotnet add "${tmp_dir}/consumer/consumer.csproj" package CoAkka.Runtime \
  --version 2.5.0 \
  --source "https://api.nuget.org/v3/index.json" >/dev/null
cp "${script_dir}/Program.cs" "${tmp_dir}/consumer/Program.cs"

dotnet run --project "${tmp_dir}/consumer/consumer.csproj"
