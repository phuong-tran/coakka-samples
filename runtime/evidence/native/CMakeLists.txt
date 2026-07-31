cmake_minimum_required(VERSION 3.20)
project(CoAkkaRuntimeNativeEvidence LANGUAGES C)

if(NOT CMAKE_CONFIGURATION_TYPES AND NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)

add_executable(coakka_runtime_v2_native_evidence main.c)
set_target_properties(
  coakka_runtime_v2_native_evidence
  PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
)
target_compile_definitions(
  coakka_runtime_v2_native_evidence
  PRIVATE
    COAKKA_EVIDENCE_BUILD_PROFILE="$<CONFIG>"
    COAKKA_EVIDENCE_COMPILER="${CMAKE_C_COMPILER_ID} ${CMAKE_C_COMPILER_VERSION}"
)
if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
  target_compile_options(
    coakka_runtime_v2_native_evidence
    PRIVATE -Wall -Wextra -Wpedantic
  )
elseif(MSVC)
  target_compile_options(coakka_runtime_v2_native_evidence PRIVATE /W4)
endif()
target_link_libraries(coakka_runtime_v2_native_evidence PRIVATE CoAkkaRuntimeNativeV2::runtime_v2)
