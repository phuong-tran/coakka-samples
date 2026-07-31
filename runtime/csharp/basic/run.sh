#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command dotnet "Install .NET SDK 10 or newer, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
export NUGET_PACKAGES="${tmp_dir}/nuget-packages"

dotnet new console -o "${tmp_dir}/consumer" --framework net10.0 --force >/dev/null
dotnet add "${tmp_dir}/consumer/consumer.csproj" package CoAkka.Runtime \
  --version 1.3.5 \
  --source "https://api.nuget.org/v3/index.json" >/dev/null
cp "${script_dir}/Program.cs" "${tmp_dir}/consumer/Program.cs"

dotnet run --project "${tmp_dir}/consumer/consumer.csproj"
