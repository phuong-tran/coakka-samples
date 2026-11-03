cmake_minimum_required(VERSION 3.24)
project(CoAkkaHttpHostOutboundSamples LANGUAGES C CXX)
include(CTest)

set(COAKKA_HTTP_HOST_SAMPLE_SDK_VERSION "1.1.2" CACHE STRING
  "Exact installed CoAkka HTTP host SDK version")
if(NOT IS_DIRECTORY "${COAKKA_HTTP_HOST_PREFIX}")
  message(FATAL_ERROR "Set COAKKA_HTTP_HOST_PREFIX to the verified host SDK")
endif()
# Bind cache reuse to the explicitly selected SDK prefix as well as its version.
set(CoAkkaHttpHost_DIR "${COAKKA_HTTP_HOST_PREFIX}/lib/cmake/CoAkkaHttpHost")
find_package(CoAkkaHttpHost "${COAKKA_HTTP_HOST_SAMPLE_SDK_VERSION}" EXACT CONFIG REQUIRED
  PATHS "${COAKKA_HTTP_HOST_PREFIX}" NO_DEFAULT_PATH)

add_executable(coakka-http-c-host-outbound ../../c/host_outbound.c)
add_executable(coakka-http-cpp-host-outbound ../../cpp/host_outbound.cpp)
target_compile_features(coakka-http-c-host-outbound PRIVATE c_std_11)
target_compile_features(coakka-http-cpp-host-outbound PRIVATE cxx_std_20)
foreach(target IN ITEMS coakka-http-c-host-outbound coakka-http-cpp-host-outbound)
  target_link_libraries(${target} PRIVATE CoAkkaHttp::host)
  set_target_properties(${target} PROPERTIES C_EXTENSIONS NO CXX_EXTENSIONS NO)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion -Werror)
  endif()
  if(WIN32)
    add_custom_command(TARGET ${target} POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
        $<TARGET_FILE:CoAkkaHttp::host> $<TARGET_FILE_DIR:${target}> VERBATIM)
  endif()
  add_test(NAME ${target} COMMAND ${target})
  set_tests_properties(${target} PROPERTIES TIMEOUT 30)
endforeach()
