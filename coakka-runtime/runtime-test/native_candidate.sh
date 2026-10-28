#!/usr/bin/env bash

# Cold installed-SDK owner shared by startup and traffic entrypoints. Core
# decides runtime policy; this helper selects only source targets/artifact paths.
run_installed_native_candidate() {
  [[ "$#" -ge 2 ]] || coakka_die "Candidate source and repository roots are required"
  local candidate_source_dir="$1" candidate_repo_root="$2"
  shift 2
  local mode="${1:-smoke}" target prefix build source_root
  if [[ "$#" -gt 0 ]]; then shift; fi
  case "${mode}" in
    native-startup)
      [[ "$#" == 0 ]] || coakka_die "native-startup accepts no arguments"
      target=coakka_runtime_v2_native_startup_evidence ;;
    connection-strategies)
      [[ "$#" == 0 ]] || coakka_die "connection-strategies accepts no arguments"
      target=coakka_runtime_v2_connection_strategy_evidence ;;
    file-lane|file-lane-simple|file-lane-owner-aware)
      [[ "$#" == 0 ]] || coakka_die "File Lane profile accepts no extra arguments"
      target=coakka_runtime_v2_file_lane_evidence
      if [[ "${mode}" == *-owner-aware ]]; then set -- --owner-aware; fi ;;
    stream-lane|stream-lane-simple|stream-lane-owner-aware)
      [[ "$#" == 0 ]] || coakka_die "Stream Lane profile accepts no extra arguments"
      target=coakka_runtime_v2_stream_lane_evidence
      if [[ "${mode}" == *-owner-aware ]]; then set -- --owner-aware; fi ;;
    smoke|pressure|stress|soak)
      target=coakka_runtime_v2_native_evidence
      set -- "${mode}" "$@" ;;
    race|hot-reload)
      target=coakka_runtime_v2_concurrency_evidence
      set -- "${mode}" "$@" ;;
    *) coakka_die "Unknown installed-candidate evidence mode" ;;
  esac

  [[ "${COAKKA_NATIVE_EVIDENCE_USE_PREBUILT:-0}" == 0 ]] ||
    coakka_die "Installed candidate has no published/prebuilt fallback"
  [[ -z "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR:-}" ]] ||
    coakka_die "Installed candidate cannot select a Core source build"
  prefix="${COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX:-}"
  build="${COAKKA_NATIVE_EVIDENCE_BUILD_DIR:-}"
  [[ -n "${prefix}" && -n "${build}" ]] ||
    coakka_die "Installed candidate requires explicit runtime prefix and build directory"
  [[ "${prefix}" == /* && "${build}" == /* &&
      "${prefix}" != *$'\n'* && "${prefix}" != *$'\r'* &&
      "${build}" != *$'\n'* && "${build}" != *$'\r'* ]] ||
    coakka_die "Candidate paths must be absolute without line breaks"
  [[ -d "${prefix}" && -d "${build}" ]] ||
    coakka_die "Candidate prefix/build directories must already exist"
  prefix="$(cd -P "${prefix}" && pwd -P)"
  build="$(cd -P "${build}" && pwd -P)"
  source_root="$(cd -P "${candidate_repo_root}" && pwd -P)"
  candidate_source_dir="$(cd -P "${candidate_source_dir}" && pwd -P)"
  [[ "${prefix}" != / && "${build}" != / &&
      "${build}/" != "${prefix}/"* && "${prefix}/" != "${build}/"* &&
      "${build}/" != "${source_root}/"* && "${source_root}/" != "${build}/"* &&
      ! -e "${build}/.git" && ! -f "${build}/CMakeLists.txt" ]] ||
    coakka_die "Candidate prefix/build overlap or source build root is forbidden"
  # Generated CMake drivers use bracket-quoted canonical paths. Reject its
  # terminator rather than interpret a caller path as generated CMake source.
  [[ "${prefix}" != *']==]'* && "${build}" != *']==]'* &&
      "${source_root}" != *']==]'* ]] ||
    coakka_die "Candidate path cannot contain a CMake driver delimiter"

  local sanitizer="${COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS:-0}"
  local thread_sanitizer="${COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN:-0}"
  [[ ( "${sanitizer}" == 0 || "${sanitizer}" == 1 ) &&
      ( "${thread_sanitizer}" == 0 || "${thread_sanitizer}" == 1 ) &&
      "${sanitizer}:${thread_sanitizer}" != 1:1 ]] ||
    coakka_die "Candidate sanitizers must be 0/1 with ASan/UBSan separate from TSan"
  coakka_require_command cmake "Install CMake to build installed-candidate evidence."

  # A metadata rejection must not rewrite a caller's previous top-level cache.
  # This admission directory is small, explicit and removed before exec handoff.
  coakka_evidence_tmp_dir="$(mktemp -d "${build}/native-startup-admission.XXXXXX")"
  cmake -S "${candidate_source_dir}/native-startup-preflight" -B "${coakka_evidence_tmp_dir}" \
    -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX="${prefix}" >&2
  local args=( -S "${candidate_source_dir}" -B "${build}"
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_PREFIX_PATH="${prefix}"
    -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PACKAGE=CoAkkaV2
    -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX="${prefix}"
    -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR=
    -DCOAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP=ON
    -DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=OFF
    -DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=OFF
    -DCMAKE_SKIP_RPATH=OFF -DCMAKE_SKIP_BUILD_RPATH=OFF
    -DCMAKE_BUILD_WITH_INSTALL_RPATH=OFF )
  if [[ "${sanitizer}" == 1 ]]; then args+=( -DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON ); fi
  if [[ "${thread_sanitizer}" == 1 ]]; then args+=( -DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=ON ); fi
  cmake "${args[@]}" >&2
  local targets=(coakka_runtime_v2_native_startup_evidence)
  if [[ "${target}" != coakka_runtime_v2_native_startup_evidence ]]; then targets+=("${target}"); fi
  cmake --build "${build}" --config Release --target "${targets[@]}" >&2
  coakka_cleanup_evidence_tmp_dir
  coakka_evidence_tmp_dir=""

  if [[ "${mode}" == native-startup ]]; then
    COAKKA_EVIDENCE_EXECUTION_PATH=installed-source-candidate \
      exec cmake -P "${build}/native-startup-Release.cmake"
  fi
  # Loader-observed startup identity is validated first with the existing 30s
  # driver. Traffic keeps its own original deadlines and exact exit status;
  # exec adds no competing timeout, supervisor, replay or native-state guess.
  COAKKA_EVIDENCE_EXECUTION_PATH=installed-source-candidate \
    cmake -P "${build}/native-startup-Release.cmake" \
      >"${build}/native-startup-admission.stdout"
  # CMake owns target layout/suffix, including multi-config and native Windows.
  # Never choose a stale executable merely because one exists in a guessed path.
  local executable path_file="${build}/native-candidate-${target}-Release.path"
  [[ -f "${path_file}" && ! -L "${path_file}" ]] ||
    coakka_die "Candidate target path metadata is missing or linked"
  local path_bytes
  path_bytes="$(wc -c <"${path_file}")"
  [[ "${path_bytes}" -gt 0 && "${path_bytes}" -le 32768 ]] ||
    coakka_die "Candidate target path metadata exceeds its bound"
  local extra_line=""
  {
    IFS= read -r executable
    if IFS= read -r extra_line || [[ -n "${extra_line}" ]]; then
      coakka_die "Candidate target path metadata must contain exactly one line"
    fi
  } <"${path_file}"
  [[ -x "${executable}" ]] || coakka_die "Selected candidate executable is missing"
  # This is the launcher process only, not the caller's shell or a global
  # machine setting. Direct exec preserves signal/exit ownership without a
  # supervising child process after build/admission have finished.
  unset DYLD_LIBRARY_PATH DYLD_INSERT_LIBRARIES LD_LIBRARY_PATH LD_PRELOAD
  export COAKKA_EVIDENCE_EXECUTION_PATH=installed-source-candidate
  exec "${executable}" "$@"
}
