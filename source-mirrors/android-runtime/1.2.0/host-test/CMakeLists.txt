cmake_minimum_required(VERSION 3.22.1)
project(CoAkkaAndroidHostJni LANGUAGES C CXX)

if(NOT DEFINED COAKKA_RUNTIME_ROOT OR
   NOT EXISTS "${COAKKA_RUNTIME_ROOT}/include/coakka/v2/runtime.h")
  message(FATAL_ERROR "COAKKA_RUNTIME_ROOT must name the exact pinned v2 source")
endif()

set(COAKKA_ANDROID_HOST_SANITIZER "none" CACHE STRING
    "Host JNI profile: none, address, undefined, or thread")
set_property(CACHE COAKKA_ANDROID_HOST_SANITIZER PROPERTY STRINGS
             none address undefined thread)

if(COAKKA_ANDROID_HOST_SANITIZER STREQUAL "none")
  set(COAKKA_V2_ENABLE_SANITIZERS OFF CACHE BOOL "" FORCE)
elseif(COAKKA_ANDROID_HOST_SANITIZER MATCHES "^(address|undefined|thread)$")
  set(COAKKA_V2_ENABLE_SANITIZERS ON CACHE BOOL "" FORCE)
  set(COAKKA_V2_SANITIZERS "${COAKKA_ANDROID_HOST_SANITIZER}"
      CACHE STRING "" FORCE)
else()
  message(FATAL_ERROR
      "Unsupported COAKKA_ANDROID_HOST_SANITIZER=${COAKKA_ANDROID_HOST_SANITIZER}")
endif()

set(COAKKA_V2_BUILD_OUTBOX_LITE_PROTOTYPE OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_QT_DESKTOP_SHELL OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_RUNTIME_INSPECT OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_RUNTIME_ADDON_PROBE_WEB OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_STREAM_LANE_VIDEO_DEMO OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_STREAM_LANE_CAMERA_DEMO OFF CACHE BOOL "" FORCE)
set(COAKKA_V2_BUILD_FUZZERS OFF CACHE BOOL "" FORCE)

add_subdirectory("${COAKKA_RUNTIME_ROOT}" runtime EXCLUDE_FROM_ALL)
find_package(JNI REQUIRED)

set(_coakka_android_host_output "${CMAKE_BINARY_DIR}/lib")
set_target_properties(coakka_runtime_v2 PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${_coakka_android_host_output}"
    RUNTIME_OUTPUT_DIRECTORY "${_coakka_android_host_output}")

add_library(coakka_android_jni SHARED
    "${CMAKE_CURRENT_LIST_DIR}/../src/main/cpp/coakka_android_jni.cpp"
    "${CMAKE_CURRENT_LIST_DIR}/../src/main/cpp/coakka_android_file_lane_jni.cpp"
    "${CMAKE_CURRENT_LIST_DIR}/../src/main/cpp/coakka_android_stream_lane_jni.cpp"
    "${CMAKE_CURRENT_LIST_DIR}/../src/hostTest/cpp/coakka_android_host_test_jni.cpp")
target_compile_features(coakka_android_jni PRIVATE cxx_std_20)
target_compile_definitions(coakka_android_jni PRIVATE
    COAKKA_ANDROID_JNI_HOST_TESTING=1)
target_compile_options(coakka_android_jni PRIVATE
    -Wall -Wextra -Wpedantic -Werror
    -fvisibility=hidden -fvisibility-inlines-hidden)
target_include_directories(coakka_android_jni PRIVATE
    "${COAKKA_RUNTIME_ROOT}/include"
    "${CMAKE_CURRENT_LIST_DIR}/../src/main/cpp"
    ${JNI_INCLUDE_DIRS})
target_link_libraries(coakka_android_jni PRIVATE coakka_runtime_v2 ${JNI_LIBRARIES})
set_target_properties(coakka_android_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${_coakka_android_host_output}"
    RUNTIME_OUTPUT_DIRECTORY "${_coakka_android_host_output}"
    BUILD_RPATH "${_coakka_android_host_output}")

if(NOT COAKKA_ANDROID_HOST_SANITIZER STREQUAL "none")
  target_compile_options(coakka_android_jni PRIVATE
      -fno-omit-frame-pointer -fno-sanitize-recover=all
      "-fsanitize=${COAKKA_ANDROID_HOST_SANITIZER}")
  target_link_options(coakka_android_jni PRIVATE
      "-fsanitize=${COAKKA_ANDROID_HOST_SANITIZER}")
endif()
