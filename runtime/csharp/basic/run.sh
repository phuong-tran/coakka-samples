#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../../.." && pwd)"
publish_root="${COAKKA_PUBLISH_ROOT:-${repo_root}/../coakka-publish}"
source "${repo_root}/scripts/resolve-artifact.sh"
source "${repo_root}/scripts/sample-utils.sh"

coakka_require_command dotnet "Install .NET SDK 10 or newer, then retry."

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT
artifact_rel="runtime/csharp/releases/1.3.1+0da8c2d9-8ff6f32/CoAkka.Runtime.1.3.1.nupkg"
package_path="$(coakka_resolve_artifact "${publish_root}" "${artifact_rel}" "${tmp_dir}/artifacts/CoAkka.Runtime.1.3.1.nupkg")"
package_source="$(dirname "${package_path}")"
export NUGET_PACKAGES="${tmp_dir}/nuget-packages"

dotnet new console -o "${tmp_dir}/consumer" --framework net10.0 --force >/dev/null
dotnet add "${tmp_dir}/consumer/consumer.csproj" package CoAkka.Runtime \
  --version 1.3.1 \
  --source "${package_source}" >/dev/null
cp "${script_dir}/Program.cs" "${tmp_dir}/consumer/Program.cs"

dotnet run --project "${tmp_dir}/consumer/consumer.csproj"
