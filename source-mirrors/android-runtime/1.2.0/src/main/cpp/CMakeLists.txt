cmake_minimum_required(VERSION 3.22.1)
project(CoAkkaAndroidJni LANGUAGES CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "coakka_android_jni must be built with the Android NDK")
endif()

set(COAKKA_RUNTIME_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../.." CACHE PATH
    "CoAkka Core v2 source root containing include/")
set(COAKKA_RUNTIME_SO
    "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libcoakka_runtime_v2.so")

if(NOT EXISTS "${COAKKA_RUNTIME_SO}")
    message(FATAL_ERROR
        "Missing ${COAKKA_RUNTIME_SO}. Run scripts/build-native-runtime.sh first.")
endif()

add_library(coakka_runtime_v2 SHARED IMPORTED)
set_target_properties(coakka_runtime_v2 PROPERTIES
    IMPORTED_LOCATION "${COAKKA_RUNTIME_SO}")

add_library(coakka_android_jni SHARED
    coakka_android_jni.cpp
    coakka_android_file_lane_jni.cpp
    coakka_android_stream_lane_jni.cpp)
target_compile_features(coakka_android_jni PRIVATE cxx_std_20)
target_compile_options(coakka_android_jni PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Werror
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    "-ffile-prefix-map=${COAKKA_RUNTIME_ROOT}=coakka/runtime"
    "-fmacro-prefix-map=${COAKKA_RUNTIME_ROOT}=coakka/runtime"
    "-fdebug-prefix-map=${COAKKA_RUNTIME_ROOT}=coakka/runtime"
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=coakka/android"
    "-fmacro-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=coakka/android"
    "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=coakka/android")
target_include_directories(coakka_android_jni PRIVATE
    "${COAKKA_RUNTIME_ROOT}/include")
target_link_libraries(coakka_android_jni PRIVATE coakka_runtime_v2 log)
set(COAKKA_ANDROID_JNI_EXPORTS
    "${CMAKE_CURRENT_SOURCE_DIR}/coakka_android_jni.exports.map")
set_property(TARGET coakka_android_jni APPEND PROPERTY LINK_DEPENDS
    "${COAKKA_ANDROID_JNI_EXPORTS}")
target_link_options(coakka_android_jni PRIVATE
    "LINKER:--version-script=${COAKKA_ANDROID_JNI_EXPORTS}"
    "LINKER:--exclude-libs,ALL"
    "LINKER:-z,start-stop-visibility=hidden")
add_custom_command(TARGET coakka_android_jni POST_BUILD
    COMMAND "${CMAKE_STRIP}" --strip-unneeded
            "$<TARGET_FILE:coakka_android_jni>"
    COMMAND "${CMAKE_OBJCOPY}" --remove-section=.comment
            "$<TARGET_FILE:coakka_android_jni>"
    VERBATIM)
