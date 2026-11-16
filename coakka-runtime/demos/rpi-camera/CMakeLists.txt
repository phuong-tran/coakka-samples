cmake_minimum_required(VERSION 3.20)
project(CoAkkaRpiCameraSample VERSION 1.1.2 LANGUAGES C CXX)

include(CTest)

option(COAKKA_CAMERA_BUILD_HOST "Build the desktop host application" ON)
option(COAKKA_CAMERA_BUILD_PI "Build the Raspberry Pi publisher" OFF)
option(COAKKA_CAMERA_BUILD_FUZZER
  "Build the Clang bounded camera JSON fuzz target" OFF)
set(COAKKA_CAMERA_SOURCE_COMMIT "development" CACHE STRING
  "Full public Samples commit embedded in release executables")
string(LENGTH "${COAKKA_CAMERA_SOURCE_COMMIT}" COAKKA_CAMERA_SOURCE_COMMIT_LENGTH)
if(NOT COAKKA_CAMERA_SOURCE_COMMIT STREQUAL "development" AND
   (NOT COAKKA_CAMERA_SOURCE_COMMIT MATCHES "^[0-9a-f]+$" OR
    NOT COAKKA_CAMERA_SOURCE_COMMIT_LENGTH EQUAL 40))
  message(FATAL_ERROR
    "COAKKA_CAMERA_SOURCE_COMMIT must be 'development' or 40 lowercase hex")
endif()

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
if(COAKKA_HTTP_HOST_LIBRARY)
  if(NOT COAKKA_HTTP_HOST_INCLUDE_DIR)
    message(FATAL_ERROR
      "Set COAKKA_HTTP_HOST_INCLUDE_DIR when COAKKA_HTTP_HOST_LIBRARY is used")
  endif()
  add_library(CoAkkaHttp::host SHARED IMPORTED)
  set_target_properties(CoAkkaHttp::host PROPERTIES
    IMPORTED_LOCATION "${COAKKA_HTTP_HOST_LIBRARY}"
    INTERFACE_INCLUDE_DIRECTORIES "${COAKKA_HTTP_HOST_INCLUDE_DIR}")

  # A direct Unix override commonly names the unversioned package symlink.
  # Stage every link in that bounded chain because the executable loads the
  # package SONAME, while retaining the exact implementation image as well.
  set(COAKKA_HTTP_HOST_STAGE_FILES)
  set(_coakka_http_stage_file "${COAKKA_HTTP_HOST_LIBRARY}")
  set(_coakka_http_stage_links_remaining 8)
  while(IS_SYMLINK "${_coakka_http_stage_file}" AND
        _coakka_http_stage_links_remaining GREATER 0)
    list(APPEND COAKKA_HTTP_HOST_STAGE_FILES "${_coakka_http_stage_file}")
    file(READ_SYMLINK "${_coakka_http_stage_file}"
      _coakka_http_stage_link)
    if(IS_ABSOLUTE "${_coakka_http_stage_link}")
      set(_coakka_http_stage_file "${_coakka_http_stage_link}")
    else()
      get_filename_component(_coakka_http_stage_directory
        "${_coakka_http_stage_file}" DIRECTORY)
      get_filename_component(_coakka_http_stage_file
        "${_coakka_http_stage_directory}/${_coakka_http_stage_link}"
        ABSOLUTE)
    endif()
    math(EXPR _coakka_http_stage_links_remaining
      "${_coakka_http_stage_links_remaining} - 1")
  endwhile()
  if(IS_SYMLINK "${_coakka_http_stage_file}")
    message(FATAL_ERROR
      "COAKKA_HTTP_HOST_LIBRARY has more than 8 symbolic-link levels")
  endif()
  if(NOT EXISTS "${_coakka_http_stage_file}")
    message(FATAL_ERROR
      "COAKKA_HTTP_HOST_LIBRARY resolves to a missing image")
  endif()
  list(APPEND COAKKA_HTTP_HOST_STAGE_FILES "${_coakka_http_stage_file}")
  if(UNIX)
    file(REAL_PATH "${_coakka_http_stage_file}"
      _coakka_http_stage_real_file)
    get_filename_component(_coakka_http_stage_directory
      "${_coakka_http_stage_real_file}" DIRECTORY)
    file(GLOB _coakka_http_stage_candidates
      "${_coakka_http_stage_directory}/*")
    foreach(_coakka_http_stage_candidate IN LISTS
            _coakka_http_stage_candidates)
      if(IS_SYMLINK "${_coakka_http_stage_candidate}")
        file(REAL_PATH "${_coakka_http_stage_candidate}"
          _coakka_http_stage_candidate_real)
        if(_coakka_http_stage_candidate_real STREQUAL
           _coakka_http_stage_real_file)
          list(APPEND COAKKA_HTTP_HOST_STAGE_FILES
            "${_coakka_http_stage_candidate}")
        endif()
      endif()
    endforeach()
  endif()
  list(REMOVE_DUPLICATES COAKKA_HTTP_HOST_STAGE_FILES)
  unset(_coakka_http_stage_candidate)
  unset(_coakka_http_stage_candidate_real)
  unset(_coakka_http_stage_candidates)
  unset(_coakka_http_stage_directory)
  unset(_coakka_http_stage_file)
  unset(_coakka_http_stage_link)
  unset(_coakka_http_stage_links_remaining)
  unset(_coakka_http_stage_real_file)
else()
  find_package(CoAkkaHttpHost 2.0.1 EXACT CONFIG REQUIRED)
endif()
find_package(Threads REQUIRED)

add_library(coakka_camera_json STATIC src/camera_json.cc)
set_target_properties(coakka_camera_json PROPERTIES
  CXX_STANDARD 20
  CXX_STANDARD_REQUIRED YES
  CXX_EXTENSIONS NO)
target_include_directories(coakka_camera_json PUBLIC
  "${CMAKE_CURRENT_SOURCE_DIR}/src")
if(MSVC)
  target_compile_options(coakka_camera_json PRIVATE /W4 /WX)
else()
  target_compile_options(coakka_camera_json PRIVATE
    -Wall -Wextra -Wpedantic -Werror)
endif()

if(COAKKA_CAMERA_BUILD_FUZZER)
  if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
    message(FATAL_ERROR "The camera JSON fuzz target requires Clang")
  endif()
  add_executable(coakka_camera_json_fuzz
    src/camera_json_fuzz.cc
    src/camera_json.cc)
  set_target_properties(coakka_camera_json_fuzz PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO)
  target_include_directories(coakka_camera_json_fuzz PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src")
  target_compile_options(coakka_camera_json_fuzz PRIVATE
    -Wall -Wextra -Wpedantic -Werror
    -fsanitize=fuzzer,address,undefined -fno-omit-frame-pointer)
  target_link_options(coakka_camera_json_fuzz PRIVATE
    -fsanitize=fuzzer,address,undefined)
endif()

if(BUILD_TESTING)
  add_executable(coakka_camera_json_tests src/camera_json_tests.cc)
  set_target_properties(coakka_camera_json_tests PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO)
  target_link_libraries(coakka_camera_json_tests PRIVATE coakka_camera_json)
  if(MSVC)
    target_compile_options(coakka_camera_json_tests PRIVATE /W4 /WX)
  else()
    target_compile_options(coakka_camera_json_tests PRIVATE
      -Wall -Wextra -Wpedantic -Werror)
  endif()
  add_test(NAME coakka_camera_json_tests COMMAND coakka_camera_json_tests)
  set_tests_properties(coakka_camera_json_tests PROPERTIES
    LABELS "camera;parser;security" TIMEOUT 10)

endif()

if(WIN32)
  if(NOT COAKKA_RUNTIME_IMPORT_LIBRARY)
    message(FATAL_ERROR
      "Set COAKKA_RUNTIME_IMPORT_LIBRARY to the import library matching "
      "libcoakka_runtime_v2.dll")
  endif()
  set_target_properties(CoAkkaRuntimeNativeV2::runtime_v2 PROPERTIES
    IMPORTED_IMPLIB "${COAKKA_RUNTIME_IMPORT_LIBRARY}")
  if(COAKKA_HTTP_HOST_LIBRARY)
    if(NOT COAKKA_HTTP_HOST_IMPORT_LIBRARY)
      message(FATAL_ERROR
        "Set COAKKA_HTTP_HOST_IMPORT_LIBRARY to the import library matching "
        "the HTTP host DLL")
    endif()
    set_target_properties(CoAkkaHttp::host PROPERTIES
      IMPORTED_IMPLIB "${COAKKA_HTTP_HOST_IMPORT_LIBRARY}")
  endif()
endif()

function(coakka_camera_target_defaults target)
  set_target_properties(${target} PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
    C_EXTENSIONS NO
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO)
  target_include_directories(${target} PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
  target_link_libraries(${target} PRIVATE
    CoAkkaRuntimeNativeV2::runtime_v2
    CoAkkaHttp::host
    Threads::Threads
    coakka_camera_json)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Werror)
  endif()
  if(APPLE)
    set(camera_runtime_rpath "@loader_path")
  elseif(UNIX)
    set(camera_runtime_rpath "\$ORIGIN")
  endif()
  if(DEFINED camera_runtime_rpath)
    set_target_properties(${target} PROPERTIES
      BUILD_RPATH "${camera_runtime_rpath}"
      INSTALL_RPATH "${camera_runtime_rpath}"
      BUILD_WITH_INSTALL_RPATH TRUE
      INSTALL_RPATH_USE_LINK_PATH FALSE)
    unset(camera_runtime_rpath)
  endif()
  add_custom_command(TARGET ${target} POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
      "$<TARGET_FILE:CoAkkaRuntimeNativeV2::runtime_v2>"
      "$<TARGET_FILE_DIR:${target}>")
  if(COAKKA_HTTP_HOST_STAGE_FILES)
    foreach(http_stage_file IN LISTS COAKKA_HTTP_HOST_STAGE_FILES)
      add_custom_command(TARGET ${target} POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
          "${http_stage_file}"
          "$<TARGET_FILE_DIR:${target}>")
    endforeach()
  else()
    add_custom_command(TARGET ${target} POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:CoAkkaHttp::host>"
        "$<TARGET_FILE_DIR:${target}>")
  endif()
  if(UNIX AND NOT COAKKA_HTTP_HOST_STAGE_FILES)
    # The executable records HTTP Runtime's major-version SONAME. Copy that
    # package-owned link/name as well as the exact implementation image.
    add_custom_command(TARGET ${target} POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_SONAME_FILE:CoAkkaHttp::host>"
        "$<TARGET_FILE_DIR:${target}>")
  endif()
endfunction()

function(coakka_camera_install_app target component)
  install(TARGETS ${target} RUNTIME DESTINATION . COMPONENT ${component})

  # The build stages the exact runtime images and their loader names next to
  # the executable. Package those files so a fresh install has the same layout.
  set(build_directory "$<TARGET_FILE_DIR:${target}>")
  set(runtime_files
    "${build_directory}/$<TARGET_FILE_NAME:CoAkkaRuntimeNativeV2::runtime_v2>")
  if(COAKKA_HTTP_HOST_STAGE_FILES)
    foreach(http_stage_file IN LISTS COAKKA_HTTP_HOST_STAGE_FILES)
      get_filename_component(http_stage_name "${http_stage_file}" NAME)
      list(APPEND runtime_files "${build_directory}/${http_stage_name}")
    endforeach()
  else()
    list(APPEND runtime_files
      "${build_directory}/$<TARGET_FILE_NAME:CoAkkaHttp::host>")
    if(UNIX)
      list(APPEND runtime_files
        "${build_directory}/$<TARGET_SONAME_FILE_NAME:CoAkkaHttp::host>")
    endif()
  endif()
  list(REMOVE_DUPLICATES runtime_files)
  install(FILES ${runtime_files} DESTINATION . COMPONENT ${component})
endfunction()

if(COAKKA_CAMERA_BUILD_HOST)
  add_executable(coakka_camera_host
    src/stream_lane_camera_web_demo.cc
    src/stream_lane_camera_recorder.cc
    src/stream_lane_camera_web_gateway.cc
    src/stream_lane_camera_web_ui.cc)
  target_compile_definitions(coakka_camera_host PRIVATE
    COAKKA_V2_CAMERA_HOST_APP=1
    COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT="${COAKKA_CAMERA_SOURCE_COMMIT}")
  coakka_camera_target_defaults(coakka_camera_host)
  coakka_camera_install_app(coakka_camera_host host)
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
    src/stream_lane_camera_web_ui.cc)
  coakka_camera_target_defaults(coakka_camera_gateway_lifecycle_tests)
  if(WIN32)
    target_link_libraries(coakka_camera_gateway_lifecycle_tests
      PRIVATE dbghelp ws2_32)
  endif()
  add_test(NAME coakka_camera_gateway_lifecycle_tests
    COMMAND coakka_camera_gateway_lifecycle_tests)
  set_tests_properties(coakka_camera_gateway_lifecycle_tests PROPERTIES
    LABELS "camera;concurrency;lifecycle" TIMEOUT 15)

  add_executable(coakka_camera_http_integration_tests
    src/stream_lane_camera_http_integration_tests.cc
    src/stream_lane_camera_control_server.cc
    src/stream_lane_camera_recorder.cc
    src/stream_lane_camera_web_gateway.cc
    src/stream_lane_camera_web_ui.cc)
  coakka_camera_target_defaults(coakka_camera_http_integration_tests)
  if(WIN32)
    target_link_libraries(coakka_camera_http_integration_tests
      PRIVATE dbghelp ws2_32)
  endif()
  add_test(NAME coakka_camera_http_integration_tests
    COMMAND coakka_camera_http_integration_tests)
  set_tests_properties(coakka_camera_http_integration_tests PROPERTIES
    LABELS "camera;http;websocket;security;package" TIMEOUT 20)

  add_executable(coakka_camera_profile_status_tests
    src/stream_lane_camera_profile_status_tests.cc
    src/stream_lane_camera_recorder.cc
    src/stream_lane_camera_web_ui.cc)
  coakka_camera_target_defaults(coakka_camera_profile_status_tests)
  if(WIN32)
    target_link_libraries(coakka_camera_profile_status_tests
      PRIVATE dbghelp ws2_32)
  endif()
  add_test(NAME coakka_camera_profile_status_tests
    COMMAND coakka_camera_profile_status_tests)
  set_tests_properties(coakka_camera_profile_status_tests PROPERTIES
    LABELS "camera;status;ownership" TIMEOUT 10)

  add_executable(coakka_camera_lane_owner_lifecycle_tests
    src/stream_lane_camera_lane_owner_tests.cc)
  set_target_properties(coakka_camera_lane_owner_lifecycle_tests PROPERTIES
    CXX_STANDARD 20
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO)
  target_include_directories(coakka_camera_lane_owner_lifecycle_tests PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src")
  target_link_libraries(coakka_camera_lane_owner_lifecycle_tests PRIVATE
    CoAkkaRuntimeNativeV2::runtime_v2)
  if(MSVC)
    target_compile_options(coakka_camera_lane_owner_lifecycle_tests PRIVATE
      /W4 /WX)
  else()
    target_compile_options(coakka_camera_lane_owner_lifecycle_tests PRIVATE
      -Wall -Wextra -Wpedantic -Werror)
  endif()
  add_test(NAME coakka_camera_lane_owner_lifecycle_tests
    COMMAND coakka_camera_lane_owner_lifecycle_tests)
  set_tests_properties(coakka_camera_lane_owner_lifecycle_tests PROPERTIES
    LABELS "camera;lifecycle;ownership" TIMEOUT 10)
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
    src/v4l2_mjpeg_camera_source.c)
  target_compile_definitions(coakka_camera_pi PRIVATE
    COAKKA_V2_CAMERA_PI_APP=1
    COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT="${COAKKA_CAMERA_SOURCE_COMMIT}")
  coakka_camera_target_defaults(coakka_camera_pi)
  coakka_camera_install_app(coakka_camera_pi pi)
  target_link_libraries(coakka_camera_pi PRIVATE ALSA::ALSA)
  add_test(NAME coakka_camera_pi_cli_help
    COMMAND coakka_camera_pi --help)
  set_tests_properties(coakka_camera_pi_cli_help PROPERTIES TIMEOUT 10)
endif()

if(NOT COAKKA_CAMERA_BUILD_HOST AND NOT COAKKA_CAMERA_BUILD_PI AND
   NOT COAKKA_CAMERA_BUILD_FUZZER)
  message(FATAL_ERROR "Enable at least one camera application")
endif()
