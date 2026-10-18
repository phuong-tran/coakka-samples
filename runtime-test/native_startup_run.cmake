# Invoked through the generated Release driver. The child process is bounded;
# raw output remains private build evidence even on timeout or malformed JSON.
get_filename_component(_startup_build_dir "${CMAKE_SCRIPT_MODE_FILE}" DIRECTORY)
set(_startup_stdout "${_startup_build_dir}/native-startup.stdout")
set(_startup_stderr "${_startup_build_dir}/native-startup.stderr")
# A failed new execution must not leave a reusable build's earlier pass file.
file(REMOVE "${_startup_build_dir}/native-startup-validated.json")
set(_startup_expected_image "${NATIVE_STARTUP_IMAGE}")
if(WIN32)
  # The application directory wins Windows DLL search. Place the exact selected
  # image there rather than trusting an unrelated DLL left in a reused build.
  get_filename_component(_startup_executable_dir "${NATIVE_STARTUP_EXECUTABLE}" DIRECTORY)
  get_filename_component(_startup_image_name "${NATIVE_STARTUP_IMAGE}" NAME)
  configure_file("${NATIVE_STARTUP_IMAGE}" "${_startup_executable_dir}/${_startup_image_name}" COPYONLY)
  set(_startup_expected_image "${_startup_executable_dir}/${_startup_image_name}")
endif()
execute_process(COMMAND "${CMAKE_COMMAND}" -E env
  --unset=DYLD_LIBRARY_PATH --unset=DYLD_INSERT_LIBRARIES
  --unset=LD_LIBRARY_PATH --unset=LD_PRELOAD
  "${NATIVE_STARTUP_EXECUTABLE}"
  WORKING_DIRECTORY "${_startup_build_dir}"
  TIMEOUT 30 RESULT_VARIABLE _startup_result
  OUTPUT_FILE "${_startup_stdout}" ERROR_FILE "${_startup_stderr}")
file(SIZE "${_startup_stdout}" _startup_output_size)
set(_startup_valid FALSE)
if(_startup_output_size GREATER 0 AND _startup_output_size LESS 131072)
  file(READ "${_startup_stdout}" _startup_output)
  string(JSON _startup_schema ERROR_VARIABLE _startup_json_error GET "${_startup_output}" schema)
  if(NOT _startup_json_error AND _startup_schema STREQUAL "coakka.runtime.native.startup-evidence.v1")
    string(JSON _startup_image_checked ERROR_VARIABLE _startup_checked_error GET "${_startup_output}" artifact checked)
    string(JSON _startup_loaded_image ERROR_VARIABLE _startup_image_error GET "${_startup_output}" artifact loadedImage)
    if(NOT _startup_checked_error AND NOT _startup_image_error AND
       _startup_image_checked AND EXISTS "${_startup_loaded_image}")
      # The path is loader-observed by the child, not echoed from input config.
      # Canonical identity plus content hash rejects a stale compatible library
      # even if a reused toolchain/cache changes the loader's search order.
      file(REAL_PATH "${_startup_loaded_image}" _startup_loaded_real
        BASE_DIRECTORY "${_startup_build_dir}")
      file(REAL_PATH "${_startup_expected_image}" _startup_expected_real)
      file(SHA256 "${_startup_loaded_real}" _startup_loaded_sha)
      if(_startup_loaded_real STREQUAL _startup_expected_real AND
         _startup_loaded_sha STREQUAL NATIVE_STARTUP_IMAGE_SHA)
        string(JSON _startup_output SET "${_startup_output}" artifact sha256 "\"${_startup_loaded_sha}\"")
        string(JSON _startup_output SET "${_startup_output}" artifact identityVerified true)
        file(WRITE "${_startup_build_dir}/native-startup-validated.json" "${_startup_output}\n")
        set(_startup_valid TRUE)
        execute_process(COMMAND "${CMAKE_COMMAND}" -E cat
          "${_startup_build_dir}/native-startup-validated.json")
      endif()
    endif()
  endif()
endif()
if(NOT _startup_valid)
  execute_process(COMMAND "${CMAKE_COMMAND}" -E echo
    "{\"schema\":\"coakka.runtime.native.startup-evidence.v1\",\"status\":\"fail\",\"error\":\"bounded execution, JSON or artifact validation failed\",\"runtime\":{\"checked\":false}}")
endif()
if(NOT _startup_result STREQUAL "0" OR NOT _startup_valid)
  message(FATAL_ERROR "Native startup execution failed (${_startup_result}); raw logs retained in the build directory")
endif()
