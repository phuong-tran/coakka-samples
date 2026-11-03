cmake_minimum_required(VERSION 3.20)
project(CoAkkaHttpRuntimeTest LANGUAGES C CXX)
include("${CMAKE_CURRENT_SOURCE_DIR}/verify-source.cmake")
include(CTest)

# Override only when explicitly qualifying a different complete SDK. Header,
# metadata and binary must come from the same admitted package prefix.
set(COAKKA_HTTP_TEST_SDK_VERSION "1.1.2" CACHE STRING "Exact installed SDK version")
find_package(CoAkkaHttpHost ${COAKKA_HTTP_TEST_SDK_VERSION} EXACT REQUIRED CONFIG)
find_package(Threads REQUIRED)
option(COAKKA_HTTP_TEST_FUZZ "Build the optional Clang libFuzzer fixture seam" OFF)

add_library(http_test_options INTERFACE)
target_compile_features(http_test_options INTERFACE c_std_11 cxx_std_20)
if(MSVC)
  target_compile_options(http_test_options INTERFACE /W4 /WX)
else()
  target_compile_options(http_test_options INTERFACE
    -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion -Wshadow -Werror
    $<$<COMPILE_LANGUAGE:C>:-Wstrict-prototypes>)
endif()

add_library(http_test_support STATIC test_support.c test_socket.c test_wire.c)
set_target_properties(http_test_support PROPERTIES C_EXTENSIONS NO)
target_link_libraries(http_test_support PUBLIC http_test_options CoAkkaHttp::host)
if(WIN32)
  target_link_libraries(http_test_support PUBLIC ws2_32)
endif()
add_library(http_test_threads STATIC
  $<$<PLATFORM_ID:Windows>:test_threads_windows.c>
  $<$<NOT:$<PLATFORM_ID:Windows>>:test_threads_posix.c>)
set_target_properties(http_test_threads PROPERTIES C_EXTENSIONS NO)
target_link_libraries(http_test_threads PUBLIC http_test_options Threads::Threads)

function(register_test name)
  set_target_properties(${name} PROPERTIES C_EXTENSIONS NO CXX_EXTENSIONS NO)
  if(WIN32)
    add_custom_command(TARGET ${name} POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
        $<TARGET_FILE:CoAkkaHttp::host> $<TARGET_FILE_DIR:${name}> VERBATIM)
  endif()
  add_test(NAME ${name} COMMAND ${name})
  # One process-level watchdog complements finite Core and socket waits. The
  # thread helper never frees a child context when completion is unproven.
  set_tests_properties(${name} PROPERTIES TIMEOUT 120)
endfunction()

foreach(case IN ITEMS runtime contract ownership pressure outbound routes stream concurrency execution)
  add_executable(http_host_${case} host_${case}.c)
  target_link_libraries(http_host_${case} PRIVATE http_test_support)
  register_test(http_host_${case})
endforeach()
target_link_libraries(http_host_concurrency PRIVATE http_test_threads)
# Version 1.1.2 introduces the focused TLS credential control surface.
if(COAKKA_HTTP_TEST_SDK_VERSION VERSION_GREATER_EQUAL "1.1.2")
  add_executable(http_host_tls_reload host_tls_reload.c)
  target_link_libraries(http_host_tls_reload PRIVATE http_test_support)
  register_test(http_host_tls_reload)
  file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/tls-fixture")
  set_tests_properties(http_host_tls_reload PROPERTIES
    WORKING_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/tls-fixture")
endif()
add_executable(http_host_cpp host_cpp_header.cc)
target_link_libraries(http_host_cpp PRIVATE http_test_options CoAkkaHttp::host)
register_test(http_host_cpp)
add_executable(http_fixture_wire fixture_wire_test.c test_wire.c)
target_link_libraries(http_fixture_wire PRIVATE http_test_options CoAkkaHttp::host)
register_test(http_fixture_wire)
if(NOT WIN32)
  add_executable(http_fixture_threads fixture_threads_test.c)
  target_link_libraries(http_fixture_threads PRIVATE http_test_options Threads::Threads)
  add_test(NAME http_fixture_threads COMMAND http_fixture_threads)
  set_tests_properties(http_fixture_threads PROPERTIES TIMEOUT 30)
endif()
if(COAKKA_HTTP_TEST_FUZZ)
  if(NOT CMAKE_C_COMPILER_ID MATCHES "Clang")
    message(FATAL_ERROR "The optional fuzz seam requires Clang libFuzzer")
  endif()
  add_executable(http_fixture_fuzz fixture_wire_fuzz.c test_wire.c)
  target_link_libraries(http_fixture_fuzz PRIVATE http_test_options CoAkkaHttp::host)
  target_compile_options(http_fixture_fuzz PRIVATE -fsanitize=fuzzer,address,undefined)
  target_link_options(http_fixture_fuzz PRIVATE -fsanitize=fuzzer,address,undefined)
endif()
