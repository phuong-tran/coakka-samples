cmake_minimum_required(VERSION 3.20)
project(CoAkkaSftpArtifactPublisherSample LANGUAGES C)

find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)
find_package(CoAkkaRuntimeAddonArtifactPublisherSftp CONFIG REQUIRED)

add_library(coakka_sftp_sample_common STATIC sample_common.c sample_common.h)
target_compile_features(coakka_sftp_sample_common PUBLIC c_std_11)
target_include_directories(coakka_sftp_sample_common PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(coakka_sftp_sample_common PUBLIC
    CoAkkaRuntimeNativeV2::runtime_v2)

add_executable(coakka_sftp_sample_service_a service_a.c)
target_link_libraries(coakka_sftp_sample_service_a PRIVATE
    coakka_sftp_sample_common
    CoAkkaRuntimeAddonArtifactPublisherSftp::artifact_publisher_sftp)

add_executable(coakka_sftp_sample_service_b service_b.c)
target_link_libraries(coakka_sftp_sample_service_b PRIVATE
    coakka_sftp_sample_common)

if(MSVC)
    target_compile_options(coakka_sftp_sample_common PRIVATE /W4 /WX)
    target_compile_options(coakka_sftp_sample_service_a PRIVATE /W4 /WX)
    target_compile_options(coakka_sftp_sample_service_b PRIVATE /W4 /WX)
else()
    target_compile_options(coakka_sftp_sample_common PRIVATE
        -Wall -Wextra -Wpedantic -Werror)
    target_compile_options(coakka_sftp_sample_service_a PRIVATE
        -Wall -Wextra -Wpedantic -Werror)
    target_compile_options(coakka_sftp_sample_service_b PRIVATE
        -Wall -Wextra -Wpedantic -Werror)
endif()
