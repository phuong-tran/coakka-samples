cmake_minimum_required(VERSION 3.20)
project(CoAkkaRuntimeNativeEvidence LANGUAGES C)

option(COAKKA_NATIVE_EVIDENCE_WARNINGS_AS_ERRORS
       "Treat public native evidence compiler warnings as errors" ON)
option(COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS
       "Instrument the public consumer harness with ASan and UBSan" OFF)
option(COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN
       "Instrument the public consumer concurrency harness with ThreadSanitizer" OFF)
option(COAKKA_NATIVE_EVIDENCE_REQUIRE_CONNECTION_STRATEGY
       "Fail configure when the runtime artifact lacks the connection-strategy ABI" OFF)
option(COAKKA_NATIVE_EVIDENCE_REQUIRE_FILE_LANE
       "Fail configure when the runtime artifact lacks the file-lane ABI" OFF)
option(COAKKA_NATIVE_EVIDENCE_REQUIRE_STREAM_LANE
       "Fail configure when the runtime artifact lacks the stream-lane ABI" OFF)
set(COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR "" CACHE PATH
    "Optional CoAkka v2 source directory for an instrumented core evidence build")

if(COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS AND
   COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN)
  message(FATAL_ERROR "ASan/UBSan and ThreadSanitizer require separate builds")
endif()

if(NOT CMAKE_CONFIGURATION_TYPES AND NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

if(COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR)
  add_subdirectory(
    "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR}"
    "${CMAKE_CURRENT_BINARY_DIR}/coakka-runtime-core"
    EXCLUDE_FROM_ALL
  )
  if(NOT TARGET coakka_runtime_v2)
    message(FATAL_ERROR "CoAkka v2 source did not define coakka_runtime_v2")
  endif()
  add_library(CoAkkaRuntimeNativeV2::runtime_v2 ALIAS coakka_runtime_v2)
  set(CoAkkaRuntimeNativeV2_PLATFORM "source-instrumented")
else()
  find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)
endif()

if(WIN32 AND MSVC AND NOT COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR)
  # The published DLL is sufficient for dynamic loaders. Native MSVC consumers
  # additionally need an import library, which this harness derives without
  # modifying or relinking the runtime under test.
  get_target_property(
    _coakka_native_evidence_import_library
    CoAkkaRuntimeNativeV2::runtime_v2
    IMPORTED_IMPLIB
  )
  if(NOT _coakka_native_evidence_import_library)
    if(CMAKE_GENERATOR_PLATFORM MATCHES "^(ARM64|arm64)$")
      set(_coakka_native_evidence_machine ARM64)
    elseif(CMAKE_GENERATOR_PLATFORM MATCHES "^(x64|X64)$")
      set(_coakka_native_evidence_machine X64)
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64|ARM64)$")
      set(_coakka_native_evidence_machine ARM64)
    else()
      set(_coakka_native_evidence_machine X64)
    endif()
    set(
      _coakka_native_evidence_import_library
      "${CMAKE_CURRENT_BINARY_DIR}/coakka_runtime_v2.lib"
    )
    execute_process(
      COMMAND
        "${CMAKE_AR}"
        /nologo
        "/machine:${_coakka_native_evidence_machine}"
        "/def:${CMAKE_CURRENT_SOURCE_DIR}/coakka_runtime_v2.def"
        "/out:${_coakka_native_evidence_import_library}"
      RESULT_VARIABLE _coakka_native_evidence_import_library_status
      OUTPUT_VARIABLE _coakka_native_evidence_import_library_stdout
      ERROR_VARIABLE _coakka_native_evidence_import_library_stderr
    )
    if(NOT _coakka_native_evidence_import_library_status EQUAL 0)
      message(FATAL_ERROR
        "Failed to create the Windows consumer import library from the public export definition:\n"
        "${_coakka_native_evidence_import_library_stdout}"
        "${_coakka_native_evidence_import_library_stderr}"
      )
    endif()
    set_target_properties(
      CoAkkaRuntimeNativeV2::runtime_v2
      PROPERTIES IMPORTED_IMPLIB "${_coakka_native_evidence_import_library}"
    )
  endif()
endif()

find_package(Threads REQUIRED)
file(
  WRITE
  "${CMAKE_CURRENT_BINARY_DIR}/coakka-evidence-platform.txt"
  "${CoAkkaRuntimeNativeV2_PLATFORM}\n"
)

add_executable(
  coakka_runtime_v2_native_evidence
  main.c
  evidence.h
  evidence_config.c
  evidence_json.c
  evidence_json.h
  evidence_platform.c
  evidence_platform.h
  evidence_runtime.c
  evidence_report.c
)

function(coakka_configure_native_evidence_target target)
  set_target_properties(
    ${target}
    PROPERTIES
      C_STANDARD 11
      C_STANDARD_REQUIRED YES
      C_EXTENSIONS NO
  )
  if(UNIX)
    target_compile_definitions(${target} PRIVATE _POSIX_C_SOURCE=200809L)
  endif()
  if(APPLE)
    target_compile_definitions(${target} PRIVATE _DARWIN_C_SOURCE)
  endif()
  if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic)
    if(COAKKA_NATIVE_EVIDENCE_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE -Werror)
    endif()
  elseif(MSVC)
    target_compile_options(${target} PRIVATE /W4)
    if(COAKKA_NATIVE_EVIDENCE_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE /WX)
    endif()
  endif()
  if(COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS)
    if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
      target_compile_options(
        ${target}
        PRIVATE
          -fno-omit-frame-pointer
          -fno-sanitize-recover=all
          -fsanitize=address,undefined
      )
      target_link_options(${target} PRIVATE -fsanitize=address,undefined)
    else()
      message(FATAL_ERROR
              "Public native evidence ASan/UBSan requires Clang or GCC")
    endif()
  elseif(COAKKA_NATIVE_EVIDENCE_ENABLE_TSAN)
    if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU" AND NOT WIN32)
      target_compile_options(
        ${target}
        PRIVATE
          -fno-omit-frame-pointer
          -fno-sanitize-recover=all
          -fsanitize=thread
      )
      target_link_options(${target} PRIVATE -fsanitize=thread)
      target_compile_definitions(${target} PRIVATE COAKKA_EVIDENCE_TSAN_ENABLED=1)
    else()
      message(FATAL_ERROR
              "Public native evidence ThreadSanitizer requires Clang or GCC on a non-Windows host")
    endif()
  endif()
endfunction()

coakka_configure_native_evidence_target(coakka_runtime_v2_native_evidence)
target_compile_definitions(
  coakka_runtime_v2_native_evidence
  PRIVATE
    COAKKA_EVIDENCE_BUILD_PROFILE="$<CONFIG>"
    COAKKA_EVIDENCE_COMPILER="${CMAKE_C_COMPILER_ID} ${CMAKE_C_COMPILER_VERSION}"
)
target_link_libraries(coakka_runtime_v2_native_evidence PRIVATE CoAkkaRuntimeNativeV2::runtime_v2)

add_executable(
  coakka_runtime_v2_concurrency_evidence
  concurrency_main.c
  concurrency_config.c
  concurrency_evidence.h
  concurrency_runtime.c
  concurrency_report.c
  evidence_json.c
  evidence_json.h
  evidence_platform.c
  evidence_platform.h
)
coakka_configure_native_evidence_target(
  coakka_runtime_v2_concurrency_evidence
)
if(MSVC)
  target_compile_options(
    coakka_runtime_v2_concurrency_evidence
    PRIVATE /experimental:c11atomics
  )
endif()
target_compile_definitions(
  coakka_runtime_v2_concurrency_evidence
  PRIVATE
    COAKKA_EVIDENCE_BUILD_PROFILE="$<CONFIG>"
    COAKKA_EVIDENCE_COMPILER="${CMAKE_C_COMPILER_ID} ${CMAKE_C_COMPILER_VERSION}"
)
target_link_libraries(
  coakka_runtime_v2_concurrency_evidence
  PRIVATE
    CoAkkaRuntimeNativeV2::runtime_v2
    Threads::Threads
)

get_target_property(
  _coakka_native_include_dirs
  CoAkkaRuntimeNativeV2::runtime_v2
  INTERFACE_INCLUDE_DIRECTORIES
)
unset(COAKKA_NATIVE_EVIDENCE_FILE_LANE_INCLUDE_DIR)
unset(COAKKA_NATIVE_EVIDENCE_FILE_LANE_INCLUDE_DIR CACHE)
if(COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR AND
   EXISTS "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR}/include/coakka/v2/file_lane.h")
  set(COAKKA_NATIVE_EVIDENCE_FILE_LANE_INCLUDE_DIR
      "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR}/include")
else()
  find_path(
    COAKKA_NATIVE_EVIDENCE_FILE_LANE_INCLUDE_DIR
    NAMES coakka/v2/file_lane.h
    PATHS ${_coakka_native_include_dirs}
    NO_DEFAULT_PATH
  )
endif()
if(COAKKA_NATIVE_EVIDENCE_FILE_LANE_INCLUDE_DIR)
  add_executable(
    coakka_runtime_v2_file_lane_evidence
    file_lane_main.c
  )
  coakka_configure_native_evidence_target(
    coakka_runtime_v2_file_lane_evidence
  )
  target_link_libraries(
    coakka_runtime_v2_file_lane_evidence
    PRIVATE CoAkkaRuntimeNativeV2::runtime_v2
  )
elseif(COAKKA_NATIVE_EVIDENCE_REQUIRE_FILE_LANE)
  message(FATAL_ERROR
          "The selected runtime artifact does not expose file_lane.h")
endif()

unset(COAKKA_NATIVE_EVIDENCE_STREAM_LANE_INCLUDE_DIR)
unset(COAKKA_NATIVE_EVIDENCE_STREAM_LANE_INCLUDE_DIR CACHE)
if(COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR AND
   EXISTS "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR}/include/coakka/v2/stream_lane.h")
  set(COAKKA_NATIVE_EVIDENCE_STREAM_LANE_INCLUDE_DIR
      "${COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR}/include")
else()
  find_path(
    COAKKA_NATIVE_EVIDENCE_STREAM_LANE_INCLUDE_DIR
    NAMES coakka/v2/stream_lane.h
    PATHS ${_coakka_native_include_dirs}
    NO_DEFAULT_PATH
  )
endif()
if(COAKKA_NATIVE_EVIDENCE_STREAM_LANE_INCLUDE_DIR)
  add_executable(
    coakka_runtime_v2_stream_lane_evidence
    stream_lane_main.c
  )
  coakka_configure_native_evidence_target(
    coakka_runtime_v2_stream_lane_evidence
  )
  if(MSVC)
    target_compile_options(coakka_runtime_v2_stream_lane_evidence PRIVATE /UNDEBUG)
  else()
    target_compile_options(coakka_runtime_v2_stream_lane_evidence PRIVATE -UNDEBUG)
  endif()
  target_link_libraries(
    coakka_runtime_v2_stream_lane_evidence
    PRIVATE CoAkkaRuntimeNativeV2::runtime_v2
  )
elseif(COAKKA_NATIVE_EVIDENCE_REQUIRE_STREAM_LANE)
  message(FATAL_ERROR
          "The selected runtime artifact does not expose stream_lane.h")
endif()

find_path(
  COAKKA_NATIVE_EVIDENCE_TRANSPORT_INCLUDE_DIR
  NAMES coakka/v2/runtime_transport_config.h
  PATHS ${_coakka_native_include_dirs}
  NO_DEFAULT_PATH
)
if(COAKKA_NATIVE_EVIDENCE_TRANSPORT_INCLUDE_DIR)
  add_executable(
    coakka_runtime_v2_connection_strategy_evidence
    connection_strategy_main.c
    connection_strategy_contract.c
    connection_strategy_report.c
    connection_strategy_evidence.h
    evidence_json.c
    evidence_json.h
    evidence_platform.c
    evidence_platform.h
  )
  coakka_configure_native_evidence_target(
    coakka_runtime_v2_connection_strategy_evidence
  )
  target_link_libraries(
    coakka_runtime_v2_connection_strategy_evidence
    PRIVATE CoAkkaRuntimeNativeV2::runtime_v2
  )
elseif(COAKKA_NATIVE_EVIDENCE_REQUIRE_CONNECTION_STRATEGY)
  message(FATAL_ERROR
          "The selected runtime artifact does not expose runtime_transport_config.h")
endif()
