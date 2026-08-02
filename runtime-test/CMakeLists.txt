cmake_minimum_required(VERSION 3.20)
project(CoAkkaRuntimeNativeEvidence LANGUAGES C)

option(COAKKA_NATIVE_EVIDENCE_WARNINGS_AS_ERRORS
       "Treat public native evidence compiler warnings as errors" ON)
option(COAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS
       "Instrument the public consumer harness with ASan and UBSan" OFF)
option(COAKKA_NATIVE_EVIDENCE_REQUIRE_CONNECTION_STRATEGY
       "Fail configure when the runtime artifact lacks the connection-strategy ABI" OFF)

if(NOT CMAKE_CONFIGURATION_TYPES AND NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)
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

get_target_property(
  _coakka_native_include_dirs
  CoAkkaRuntimeNativeV2::runtime_v2
  INTERFACE_INCLUDE_DIRECTORIES
)
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
