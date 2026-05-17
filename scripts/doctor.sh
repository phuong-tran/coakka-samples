#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
source "${script_dir}/sample-metadata.sh"

missing_count=0

print_version() {
  case "$1" in
    java)
      java -version 2>&1 | head -n 1
      ;;
    python3)
      python3 --version 2>&1 | head -n 1
      ;;
    node)
      node --version 2>&1 | head -n 1
      ;;
    npm)
      npm --version 2>&1 | head -n 1 | sed 's/^/npm /'
      ;;
    go)
      go version 2>&1 | head -n 1
      ;;
    dotnet)
      dotnet --version 2>&1 | head -n 1 | sed 's/^/.NET SDK /'
      ;;
    zig)
      zig version 2>&1 | head -n 1 | sed 's/^/Zig /'
      ;;
    mojo)
      mojo --version 2>&1 | head -n 1
      ;;
    curl)
      curl --version 2>&1 | head -n 1
      ;;
    tar)
      printf 'tar at %s\n' "$(command -v tar)"
      ;;
    cmake)
      cmake --version 2>&1 | head -n 1
      ;;
    *)
      printf '%s at %s\n' "$1" "$(command -v "$1")"
      ;;
  esac
}

check_command() {
  local command_name="$1"
  local affects="$2"
  local hint="$3"

  if command -v "${command_name}" >/dev/null 2>&1; then
    printf '[ok]      %-8s %s\n' "${command_name}" "$(print_version "${command_name}")"
  else
    printf '[missing] %-8s affects %s. %s\n' "${command_name}" "${affects}" "${hint}"
    missing_count=$((missing_count + 1))
  fi
}

command_major_minor() {
  case "$1" in
    java)
      java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1 0/p'
      ;;
    python3)
      python3 -c 'import sys; print(sys.version_info.major, sys.version_info.minor)'
      ;;
    node)
      node -p 'process.versions.node.split(".").slice(0, 2).join(" ")'
      ;;
    go)
      go version | sed -n 's/.* go\([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p'
      ;;
    dotnet)
      dotnet --version | sed -n 's/^\([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p'
      ;;
    zig)
      zig version | sed -n 's/^\([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p'
      ;;
    mojo)
      mojo --version | sed -n 's/^Mojo \([0-9][0-9]*\)\.\([0-9][0-9]*\).*/\1 \2/p'
      ;;
  esac
}

check_minimum_version() {
  local command_name="$1"
  local label="$2"
  local min_major="$3"
  local min_minor="$4"
  local version major minor

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    return 0
  fi

  version="$(command_major_minor "${command_name}")"
  if [[ -z "${version}" ]]; then
    printf '[warn]    %-8s could not parse version for %s\n' "${command_name}" "${label}"
    return 0
  fi

  read -r major minor <<<"${version}"
  if (( major < min_major || (major == min_major && minor < min_minor) )); then
    printf '[warn]    %-8s %s expect %s.%s or newer\n' "${command_name}" "${label}" "${min_major}" "${min_minor}"
  fi
}

print_artifact_source() {
  local publish_root raw_base row label relative_path missing_artifacts first_artifact verify_script
  publish_root="$(coakka_default_publish_root "${repo_root}")"
  raw_base="${COAKKA_PUBLISH_RAW_BASE:-${COAKKA_PUBLISH_RAW_BASE_DEFAULT}}"
  missing_artifacts=0
  first_artifact=""
  verify_script="${publish_root}/scripts/verify-public-surface.sh"

  printf '\nArtifact source:\n'
  printf '  local publish root: %s\n' "${publish_root}"

  if [[ -d "${publish_root}" ]]; then
    printf '  [ok] local public publish checkout exists\n'
    for row in "${COAKKA_ARTIFACT_ROWS[@]}"; do
      IFS='|' read -r label relative_path <<<"${row}"
      if [[ ! -f "${publish_root}/${relative_path}" ]]; then
        printf '  [missing local] %-21s %s\n' "${label}" "${relative_path}"
        missing_artifacts=$((missing_artifacts + 1))
      fi
    done

    if [[ "${missing_artifacts}" -eq 0 ]]; then
      printf '  [ok] all pinned local artifacts are present\n'
    else
      printf '  local checkout is incomplete for the current public artifact set; samples can still download missing artifacts from public raw base\n'
    fi

    if [[ -x "${verify_script}" ]]; then
      if "${verify_script}" >/dev/null 2>&1; then
        printf '  [ok] public publish verification gate passes\n'
      else
        printf '  [warn] public publish verification gate failed\n'
      fi
    else
      printf '  verification gate not found in local public publish checkout\n'
    fi
  else
    printf '  local checkout not found; logger samples will download artifacts when needed\n'
  fi

  printf '  public raw base: %s\n' "${raw_base}"
  if command -v curl >/dev/null 2>&1; then
    if [[ "${COAKKA_DOCTOR_NETWORK:-0}" == "1" ]]; then
      IFS='|' read -r _ first_artifact <<<"${COAKKA_ARTIFACT_ROWS[0]}"
      if curl -fsI --max-time 10 "${raw_base%/}/${first_artifact}" >/dev/null; then
        printf '  [ok] public artifact URL is reachable\n'
      else
        printf '  [warn] public artifact URL was not reachable from this machine\n'
      fi
    else
      printf '  network check skipped; set COAKKA_DOCTOR_NETWORK=1 to test public artifact reachability\n'
    fi
  else
    printf '  [missing] curl is required when local artifacts are not present\n'
  fi
}

cat <<'EOF'
coakka-samples doctor

Install only the toolchain for the language you want to try. Missing commands
below only affect the matching samples.

Toolchains:
EOF

check_command java "JVM samples" "Install JDK 17 or newer."
check_command python3 "Python samples" "Install Python 3.11 or newer."
check_command node "Node.js samples" "Install Node.js 20 or newer."
check_command npm "Node.js samples" "Install npm."
check_command go "Go samples" "Install Go 1.22+ for logger or Go 1.23+ for runtime v2."
check_command dotnet "C# runtime samples" "Install .NET SDK 10 or newer."
check_command zig "Zig runtime source-package sample" "Install Zig 0.16 or newer."
check_command mojo "Mojo runtime source-package sample" "Install Mojo 1.0 beta or newer."
check_command cmake "native C/C++ samples" "Install CMake."
check_command cc "native C samples" "Install a C compiler."
check_command c++ "native C++ samples" "Install a C++ compiler."
check_command tar "Go and native package samples" "Install tar."
check_command curl "artifact download fallback" "Install curl or provide COAKKA_PUBLISH_ROOT."

check_minimum_version java "JVM samples" 17 0
check_minimum_version python3 "Python samples" 3 11
check_minimum_version node "Node.js samples" 20 0
check_minimum_version go "Go logger samples" 1 22
check_minimum_version go "Go runtime v2 samples" 1 23
check_minimum_version dotnet "C# runtime samples" 10 0
check_minimum_version zig "Zig runtime source-package sample" 0 16
check_minimum_version mojo "Mojo runtime source-package sample" 1 0

print_artifact_source

cat <<'EOF'

Runtime samples:
  Runtime JVM, Python, Node.js, Go, C#, Rust, native C/C++, Spring Boot, and
  Quarkus samples are backed by the current public publish surface. Zig and
  Mojo use public source connector packages and require local Zig/Mojo
  toolchains.
EOF

printf '\nTry:\n'
printf '  bash run.sh\n'
printf '  bash run.sh list\n'
printf '  bash run.sh logger basic\n'
printf '  bash run.sh runtime jvm basic\n'
printf '  bash run.sh runtime native basic\n'
printf '  bash run.sh logger node basic\n'

exit 0
