cmake_minimum_required(VERSION 3.20)
project(CoAkkaRpiCameraSample VERSION 1.1.0 LANGUAGES C CXX)

include(CTest)

option(COAKKA_CAMERA_BUILD_HOST "Build the desktop host application" ON)
option(COAKKA_CAMERA_BUILD_PI "Build the Raspberry Pi publisher" OFF)

if(COAKKA_RUNTIME_LIBRARY)
  if(NOT COAKKA_RUNTIME_INCLUDE_DIR)
    message(FATAL_ERROR
      "Set COAKKA_RUNTIME_INCLUDE_DIR when COAKKA_RUNTIME_LIBRARY is used")
  endif()
  add_library(CoAkkaRuntimeNativeV2::runtime_v2 SHARED IMPORTED)
  set_target_properties(CoAkkaRuntimeNativeV2::runtime_v2 PROPERTIES
    IMPORTED_LOCATION "${COAKKA_RUNTIME_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${COAKKA_RUNTIME_INCLUDE_DIR}")
  set(CoAkkaRuntimeNativeV2_NATIVE_LIBRARY "${COAKKA_RUNTIME_LIBRARY}")
else()
  find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)
endif()
find_package(Threads REQUIRED)
find_package(Protobuf CONFIG QUIET)
if(NOT Protobuf_FOUND)
  find_package(Protobuf REQUIRED)
endif()

# CMake 4.2+ expects an import-link location for Windows imported targets.
# Older static Protobuf/Abseil package exports provide only IMPORTED_LOCATION.
if(WIN32 AND CMAKE_VERSION VERSION_GREATER_EQUAL 4.2)
  get_property(coakka_camera_imported_targets DIRECTORY PROPERTY IMPORTED_TARGETS)
  foreach(imported_target IN LISTS coakka_camera_imported_targets)
    get_target_property(imported_type "${imported_target}" TYPE)
    if(imported_type STREQUAL "STATIC_LIBRARY")
      get_target_property(imported_release_location "${imported_target}"
        IMPORTED_LOCATION_RELEASE)
      if(imported_release_location)
        set_property(TARGET "${imported_target}" PROPERTY
          IMPORTED_IMPLIB_RELEASE "${imported_release_location}")
      endif()
    endif()
  endforeach()
endif()

if(NOT CMAKE_CROSSCOMPILING)
  find_package(PkgConfig QUIET)
endif()
if(PkgConfig_FOUND AND NOT CMAKE_CROSSCOMPILING)
  pkg_check_modules(COAKKA_CAMERA_LIBUV QUIET IMPORTED_TARGET libuv)
endif()
if(TARGET PkgConfig::COAKKA_CAMERA_LIBUV)
  set(COAKKA_CAMERA_LIBUV_TARGET PkgConfig::COAKKA_CAMERA_LIBUV)
else()
  find_path(COAKKA_CAMERA_LIBUV_INCLUDE_DIR uv.h REQUIRED)
  find_library(COAKKA_CAMERA_LIBUV_LIBRARY NAMES uv libuv REQUIRED)
  add_library(coakka_camera_libuv UNKNOWN IMPORTED)
  set_target_properties(coakka_camera_libuv PROPERTIES
    IMPORTED_LOCATION "${COAKKA_CAMERA_LIBUV_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${COAKKA_CAMERA_LIBUV_INCLUDE_DIR}")
  if(WIN32)
    set_property(TARGET coakka_camera_libuv PROPERTY INTERFACE_LINK_LIBRARIES
      "psapi;user32;advapi32;iphlpapi;userenv;ws2_32;dbghelp;ole32;shell32")
  endif()
  set(COAKKA_CAMERA_LIBUV_TARGET coakka_camera_libuv)
endif()

if(WIN32)
  if(NOT COAKKA_RUNTIME_IMPORT_LIBRARY)
    message(FATAL_ERROR
      "Set COAKKA_RUNTIME_IMPORT_LIBRARY to the import library matching "
      "libcoakka_runtime_v2.dll")
  endif()
  set_target_properties(CoAkkaRuntimeNativeV2::runtime_v2 PROPERTIES
    IMPORTED_IMPLIB "${COAKKA_RUNTIME_IMPORT_LIBRARY}")
endif()

function(coakka_camera_target_defaults target)
  target_compile_features(${target} PRIVATE c_std_11 cxx_std_20)
  target_include_directories(${target} PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
  target_link_libraries(${target} PRIVATE
    CoAkkaRuntimeNativeV2::runtime_v2
    Threads::Threads
    protobuf::libprotobuf
    ${COAKKA_CAMERA_LIBUV_TARGET})
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Werror)
  endif()
  if(APPLE)
    set_target_properties(${target} PROPERTIES BUILD_RPATH "@loader_path")
  elseif(UNIX)
    set_target_properties(${target} PROPERTIES BUILD_RPATH "\$ORIGIN")
  endif()
  add_custom_command(TARGET ${target} POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
      "${CoAkkaRuntimeNativeV2_NATIVE_LIBRARY}"
      "$<TARGET_FILE_DIR:${target}>")
endfunction()

if(COAKKA_CAMERA_BUILD_HOST)
  add_executable(coakka_camera_host
    src/stream_lane_camera_web_demo.cc
    src/stream_lane_camera_recorder.cc
    src/stream_lane_camera_web_gateway.cc
    src/stream_lane_camera_web_ui.cc
    src/runtime_addon_web_wire.cc)
  target_compile_definitions(coakka_camera_host PRIVATE
    COAKKA_V2_CAMERA_HOST_APP=1)
  coakka_camera_target_defaults(coakka_camera_host)
  if(WIN32)
    target_link_libraries(coakka_camera_host PRIVATE dbghelp ws2_32)
  endif()
  add_test(NAME coakka_camera_host_cli_help
    COMMAND coakka_camera_host --help)
  set_tests_properties(coakka_camera_host_cli_help PROPERTIES TIMEOUT 10)

  add_executable(coakka_camera_gateway_lifecycle_tests
    src/stream_lane_camera_gateway_lifecycle_tests.cc
    src/stream_lane_camera_recorder.cc
    src/stream_lane_camera_web_gateway.cc
    src/stream_lane_camera_web_ui.cc
    src/runtime_addon_web_wire.cc)
  coakka_camera_target_defaults(coakka_camera_gateway_lifecycle_tests)
  if(WIN32)
    target_link_libraries(coakka_camera_gateway_lifecycle_tests
      PRIVATE dbghelp ws2_32)
  endif()
  add_test(NAME coakka_camera_gateway_lifecycle_tests
    COMMAND coakka_camera_gateway_lifecycle_tests)
  set_tests_properties(coakka_camera_gateway_lifecycle_tests PROPERTIES
    LABELS "camera;concurrency;lifecycle" TIMEOUT 15)
endif()

if(COAKKA_CAMERA_BUILD_PI)
  if(NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
    message(FATAL_ERROR "coakka_camera_pi requires Linux V4L2 and ALSA")
  endif()
  find_package(ALSA REQUIRED)
  add_executable(coakka_camera_pi
    src/stream_lane_camera_web_demo.cc
    src/stream_lane_camera_control_server.cc
    src/alsa_pcm_audio_source.c
    src/v4l2_mjpeg_camera_source.c
    src/runtime_addon_web_wire.cc)
  target_compile_definitions(coakka_camera_pi PRIVATE
    COAKKA_V2_CAMERA_PI_APP=1)
  coakka_camera_target_defaults(coakka_camera_pi)
  target_link_libraries(coakka_camera_pi PRIVATE ALSA::ALSA)
  add_test(NAME coakka_camera_pi_cli_help
    COMMAND coakka_camera_pi --help)
  set_tests_properties(coakka_camera_pi_cli_help PROPERTIES TIMEOUT 10)
endif()

if(NOT COAKKA_CAMERA_BUILD_HOST AND NOT COAKKA_CAMERA_BUILD_PI)
  message(FATAL_ERROR "Enable at least one camera application")
endif()
