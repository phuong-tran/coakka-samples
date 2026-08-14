cmake_minimum_required(VERSION 3.20)
project(CoAkkaNativeArtifactAddonSample LANGUAGES C)

set(COAKKA_SAMPLE_ADDON "" CACHE STRING "Artifact publisher addon slug")
find_package(CoAkkaRuntimeNativeV2 CONFIG REQUIRED)

if(COAKKA_SAMPLE_ADDON STREQUAL "https")
  find_package(CoAkkaRuntimeAddonArtifactPublisherHttps CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_HTTPS)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherHttps::artifact_publisher_https)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "s3")
  find_package(CoAkkaRuntimeAddonArtifactPublisherS3 CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_S3)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherS3::artifact_publisher_s3)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "local-drop")
  find_package(CoAkkaRuntimeAddonArtifactPublisherLocalDrop CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_LOCAL_DROP)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherLocalDrop::artifact_publisher_local_drop)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "azure-blob")
  find_package(CoAkkaRuntimeAddonArtifactPublisherAzureBlob CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_AZURE_BLOB)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherAzureBlob::artifact_publisher_azure_blob)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "gcs")
  find_package(CoAkkaRuntimeAddonArtifactPublisherGcs CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_GCS)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherGcs::artifact_publisher_gcs)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "webdav")
  find_package(CoAkkaRuntimeAddonArtifactPublisherWebdav CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_WEBDAV)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherWebdav::artifact_publisher_webdav)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "oci-registry")
  find_package(CoAkkaRuntimeAddonArtifactPublisherOciRegistry CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_OCI_REGISTRY)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherOciRegistry::artifact_publisher_oci_registry)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "huggingface-hub")
  find_package(CoAkkaRuntimeAddonArtifactPublisherHuggingfaceHub CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_HUGGINGFACE_HUB)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherHuggingfaceHub::artifact_publisher_huggingface_hub)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "github-release")
  find_package(CoAkkaRuntimeAddonArtifactPublisherGithubRelease CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_GITHUB_RELEASE)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherGithubRelease::artifact_publisher_github_release)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "google-drive")
  find_package(CoAkkaRuntimeAddonArtifactPublisherGoogleDrive CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_GOOGLE_DRIVE)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherGoogleDrive::artifact_publisher_google_drive)
elseif(COAKKA_SAMPLE_ADDON STREQUAL "dropbox")
  find_package(CoAkkaRuntimeAddonArtifactPublisherDropbox CONFIG REQUIRED)
  set(sample_definition COAKKA_SAMPLE_DROPBOX)
  set(sample_addon_target
      CoAkkaRuntimeAddonArtifactPublisherDropbox::artifact_publisher_dropbox)
else()
  message(FATAL_ERROR "Unsupported COAKKA_SAMPLE_ADDON: ${COAKKA_SAMPLE_ADDON}")
endif()

add_library(coakka_artifact_sample_common OBJECT sample_common.c sample_common.h)
target_compile_features(coakka_artifact_sample_common PUBLIC c_std_11)
target_include_directories(coakka_artifact_sample_common PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(coakka_artifact_sample_common PUBLIC
    CoAkkaRuntimeNativeV2::runtime_v2)

add_executable(coakka_artifact_sample_service_a service_a.c)
target_compile_definitions(coakka_artifact_sample_service_a PRIVATE
    ${sample_definition})
target_link_libraries(coakka_artifact_sample_service_a PRIVATE
    coakka_artifact_sample_common
    ${sample_addon_target})

add_executable(coakka_artifact_sample_service_b service_b.c)
target_link_libraries(coakka_artifact_sample_service_b PRIVATE
    coakka_artifact_sample_common)

foreach(target IN ITEMS coakka_artifact_sample_common
                        coakka_artifact_sample_service_a
                        coakka_artifact_sample_service_b)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /WX)
  else()
    target_compile_options(${target} PRIVATE
        -Wall -Wextra -Wpedantic -Werror)
  endif()
endforeach()
