cmake_minimum_required(VERSION 3.20)

project(CoAkkaHttpRuntimeTest LANGUAGES C CXX)

include("${CMAKE_CURRENT_SOURCE_DIR}/verify-source.cmake")

include(CTest)
find_package(CoAkkaHttp 1.0.0 EXACT REQUIRED CONFIG)
find_package(Threads REQUIRED)

add_executable(coakka_http_runtime_execution_test native_execution.c)
set_target_properties(coakka_http_runtime_execution_test PROPERTIES
    C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(coakka_http_runtime_execution_test PRIVATE CoAkkaHttp::runtime)
if(MSVC)
  target_compile_options(coakka_http_runtime_execution_test PRIVATE /W4 /WX)
else()
  target_compile_options(coakka_http_runtime_execution_test PRIVATE
      -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion -Wshadow
      -Wstrict-prototypes -Werror)
endif()
if(WIN32)
  add_custom_command(TARGET coakka_http_runtime_execution_test POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
      $<TARGET_FILE:CoAkkaHttp::runtime> $<TARGET_FILE_DIR:coakka_http_runtime_execution_test>
      VERBATIM)
endif()
add_test(NAME coakka_http_runtime_execution_test COMMAND coakka_http_runtime_execution_test)
set_tests_properties(coakka_http_runtime_execution_test PROPERTIES TIMEOUT 30)

option(COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS
       "Treat native test compiler warnings as errors" ON)
option(COAKKA_HTTP_RUNTIME_TEST_EXPECT_TLS
       "Require the installed Core package to advertise TLS and mutual TLS" OFF)
option(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS
       "Run installed-package HTTP/2 and HTTP/3 convenience-server tests" OFF)
option(COAKKA_HTTP_RUNTIME_TEST_EXPECT_OUTBOUND
       "Require and test installed Core logical outbound support" OFF)
option(COAKKA_HTTP_RUNTIME_TEST_EXPECT_IO_URING
       "Require installed Linux Core-owned optional I/O discovery" OFF)
set(COAKKA_HTTP_RUNTIME_TEST_HTTP3_CLIENT "" CACHE FILEPATH
    "Path to the Core-owned HTTP/3 wire client fixture")
set(COAKKA_HTTP_RUNTIME_TEST_HTTP2_CLIENT "" CACHE FILEPATH
    "Path to the Core-owned HTTP/2 wire client fixture")
set(COAKKA_HTTP_RUNTIME_TEST_TLS_FIXTURES "" CACHE PATH
    "Path to the test-only TLS identity directory")

add_executable(coakka_http_runtime_c_test native_runtime.c)
set_target_properties(
    coakka_http_runtime_c_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_c_test PRIVATE CoAkkaHttp::runtime)

add_executable(coakka_http_runtime_cpp_test cpp_header_smoke.cc)
set_target_properties(
    coakka_http_runtime_cpp_test
    PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED YES CXX_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_cpp_test PRIVATE CoAkkaHttp::runtime)

add_executable(coakka_http_runtime_contract_test native_contract.c)
set_target_properties(
    coakka_http_runtime_contract_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_contract_test PRIVATE CoAkkaHttp::runtime)
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_TLS)
  target_compile_definitions(
      coakka_http_runtime_contract_test PRIVATE COAKKA_HTTP_EXPECT_TLS=1)
endif()
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
  if(NOT EXISTS "${COAKKA_HTTP_RUNTIME_TEST_HTTP2_CLIENT}")
    message(FATAL_ERROR "The HTTP/2 protocol client fixture is missing")
  endif()
  if(NOT EXISTS "${COAKKA_HTTP_RUNTIME_TEST_HTTP3_CLIENT}")
    message(FATAL_ERROR "The HTTP/3 protocol client fixture is missing")
  endif()
  foreach(fixture IN ITEMS ca.pem server.pem server.key)
    if(NOT EXISTS "${COAKKA_HTTP_RUNTIME_TEST_TLS_FIXTURES}/${fixture}")
      message(FATAL_ERROR "The TLS protocol fixture is missing: ${fixture}")
    endif()
  endforeach()
  target_compile_definitions(
      coakka_http_runtime_contract_test PRIVATE COAKKA_HTTP_EXPECT_PROTOCOLS=1)

  add_executable(coakka_http_runtime_protocol_test native_protocol.c)
  set_target_properties(
      coakka_http_runtime_protocol_test
      PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
  target_link_libraries(
      coakka_http_runtime_protocol_test PRIVATE CoAkkaHttp::runtime)
endif()

add_executable(
    coakka_http_runtime_concurrency_test
    native_concurrency.c
    test_threads.h
    $<$<PLATFORM_ID:Windows>:test_threads_windows.c>
    $<$<NOT:$<PLATFORM_ID:Windows>>:test_threads_posix.c>)
set_target_properties(
    coakka_http_runtime_concurrency_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_concurrency_test
    PRIVATE CoAkkaHttp::runtime Threads::Threads)

add_executable(
    coakka_http_runtime_pressure_test
    native_pressure.c
    test_threads.h
    $<$<PLATFORM_ID:Windows>:test_threads_windows.c>
    $<$<NOT:$<PLATFORM_ID:Windows>>:test_threads_posix.c>)
set_target_properties(
    coakka_http_runtime_pressure_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_pressure_test
    PRIVATE CoAkkaHttp::runtime Threads::Threads)

add_executable(
    coakka_http_runtime_outbound_test
    native_outbound.c
    test_threads.h
    $<$<PLATFORM_ID:Windows>:test_threads_windows.c>
    $<$<NOT:$<PLATFORM_ID:Windows>>:test_threads_posix.c>)
set_target_properties(
    coakka_http_runtime_outbound_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_runtime_outbound_test
    PRIVATE CoAkkaHttp::runtime Threads::Threads)
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_OUTBOUND)
  target_compile_definitions(
      coakka_http_runtime_outbound_test PRIVATE COAKKA_HTTP_EXPECT_OUTBOUND=1)
endif()

add_executable(
    coakka_http_core_runtime_test
    core_runtime.c
    test_threads.h
    $<$<PLATFORM_ID:Windows>:test_threads_windows.c>
    $<$<NOT:$<PLATFORM_ID:Windows>>:test_threads_posix.c>)
set_target_properties(
    coakka_http_core_runtime_test
    PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES C_EXTENSIONS NO)
target_link_libraries(
    coakka_http_core_runtime_test
    PRIVATE CoAkkaHttp::runtime Threads::Threads)
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_IO_URING)
  target_compile_definitions(
      coakka_http_core_runtime_test PRIVATE COAKKA_HTTP_EXPECT_IO_URING=1)
endif()

if(MSVC)
  target_compile_options(
      coakka_http_runtime_c_test
      PRIVATE /W4 /experimental:c11atomics
              $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:/WX>)
  target_compile_options(
      coakka_http_runtime_cpp_test
      PRIVATE /W4 $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:/WX>)
  foreach(target IN ITEMS coakka_http_runtime_contract_test
                          coakka_http_runtime_concurrency_test
                          coakka_http_runtime_pressure_test
                          coakka_http_runtime_outbound_test
                          coakka_http_core_runtime_test)
    target_compile_options(
        ${target}
        PRIVATE /W4 /experimental:c11atomics
                $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:/WX>)
  endforeach()
  target_link_libraries(
      coakka_http_runtime_c_test PRIVATE ws2_32)
  target_link_libraries(
      coakka_http_runtime_concurrency_test PRIVATE ws2_32)
  target_link_libraries(
      coakka_http_runtime_pressure_test PRIVATE ws2_32)
  target_link_libraries(
      coakka_http_runtime_outbound_test PRIVATE ws2_32)
  target_link_libraries(
      coakka_http_core_runtime_test PRIVATE ws2_32)
  if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
    target_compile_options(
        coakka_http_runtime_protocol_test
        PRIVATE /W4 /experimental:c11atomics
                $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:/WX>)
  endif()
else()
  foreach(target IN ITEMS coakka_http_runtime_c_test
                          coakka_http_runtime_cpp_test
                          coakka_http_runtime_contract_test
                          coakka_http_runtime_concurrency_test
                          coakka_http_runtime_pressure_test
                          coakka_http_runtime_outbound_test
                          coakka_http_core_runtime_test)
    target_compile_options(
        ${target}
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
            -Wconversion
            -Wshadow
            -Wsign-conversion
            $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:-Werror>)
  endforeach()
  foreach(target IN ITEMS coakka_http_runtime_c_test
                          coakka_http_runtime_contract_test
                          coakka_http_runtime_concurrency_test
                          coakka_http_runtime_pressure_test
                          coakka_http_runtime_outbound_test
                          coakka_http_core_runtime_test)
    target_compile_options(${target} PRIVATE -Wstrict-prototypes)
  endforeach()
  if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
    target_compile_options(
        coakka_http_runtime_protocol_test
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
            -Wconversion
            -Wshadow
            -Wsign-conversion
            -Wstrict-prototypes
            $<$<BOOL:${COAKKA_HTTP_RUNTIME_TEST_WARNINGS_AS_ERRORS}>:-Werror>)
  endif()
endif()

if(WIN32)
  foreach(target IN ITEMS coakka_http_runtime_c_test
                          coakka_http_runtime_cpp_test
                          coakka_http_runtime_contract_test
                          coakka_http_runtime_concurrency_test
                          coakka_http_runtime_pressure_test
                          coakka_http_runtime_outbound_test
                          coakka_http_core_runtime_test)
    add_custom_command(
        TARGET ${target}
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
                $<TARGET_FILE:CoAkkaHttp::runtime>
                $<TARGET_FILE_DIR:${target}>
        VERBATIM)
  endforeach()
  if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
    target_compile_definitions(
        coakka_http_runtime_protocol_test PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
    add_custom_command(
        TARGET coakka_http_runtime_protocol_test
        POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
                $<TARGET_FILE:CoAkkaHttp::runtime>
                $<TARGET_FILE_DIR:coakka_http_runtime_protocol_test>
        VERBATIM)
  endif()
endif()

add_test(NAME coakka_http_runtime_c_test COMMAND coakka_http_runtime_c_test)
add_test(NAME coakka_http_runtime_cpp_test COMMAND coakka_http_runtime_cpp_test)
add_test(
    NAME coakka_http_runtime_contract_test
    COMMAND coakka_http_runtime_contract_test)
add_test(
    NAME coakka_http_runtime_concurrency_test
    COMMAND coakka_http_runtime_concurrency_test)
add_test(
    NAME coakka_http_runtime_pressure_test
    COMMAND coakka_http_runtime_pressure_test)
add_test(
    NAME coakka_http_runtime_outbound_test
    COMMAND coakka_http_runtime_outbound_test)
set_tests_properties(coakka_http_runtime_outbound_test PROPERTIES SKIP_RETURN_CODE 77)
add_test(
    NAME coakka_http_core_runtime_test
    COMMAND coakka_http_core_runtime_test)
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
  add_test(
      NAME coakka_http_runtime_protocol_test
      COMMAND
          ${CMAKE_COMMAND} -E env
          "COAKKA_HTTP_TEST_HTTP2_CLIENT=${COAKKA_HTTP_RUNTIME_TEST_HTTP2_CLIENT}"
          "COAKKA_HTTP_TEST_HTTP3_CLIENT=${COAKKA_HTTP_RUNTIME_TEST_HTTP3_CLIENT}"
          "COAKKA_HTTP_TEST_TLS_FIXTURES=${COAKKA_HTTP_RUNTIME_TEST_TLS_FIXTURES}"
          $<TARGET_FILE:coakka_http_runtime_protocol_test>)
endif()
set_tests_properties(
    coakka_http_runtime_c_test
    coakka_http_runtime_cpp_test
    coakka_http_runtime_contract_test
    coakka_http_runtime_concurrency_test
    coakka_http_runtime_pressure_test
    coakka_http_runtime_outbound_test
    coakka_http_core_runtime_test
    PROPERTIES TIMEOUT 30)
if(COAKKA_HTTP_RUNTIME_TEST_EXPECT_PROTOCOLS)
  set_tests_properties(coakka_http_runtime_protocol_test PROPERTIES TIMEOUT 30)
endif()
