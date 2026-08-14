#include "sample_common.h"

#include <string.h>

#if defined(COAKKA_SAMPLE_HTTPS)
#include "coakka/addons/artifact_publisher_https.h"
#define SAMPLE_ADDON_LABEL "https"
#define SAMPLE_STATUS_T coakka_https_status_t
#define SAMPLE_PUBLISHER_T coakka_https_publisher_t
#define SAMPLE_CONFIG_T coakka_https_publisher_config_t
#define SAMPLE_TARGET_T coakka_https_publish_target_t
#define SAMPLE_SPEC_T coakka_https_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_https_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_https_target_snapshot_t
#define SAMPLE_OK COAKKA_HTTPS_OK
#define SAMPLE_WOULD_BLOCK COAKKA_HTTPS_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_HTTPS_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_HTTPS_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_HTTPS_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_HTTPS_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_HTTPS_RESULT_OK
#define SAMPLE_CREATE coakka_https_publisher_create
#define SAMPLE_DESTROY coakka_https_publisher_destroy
#define SAMPLE_START coakka_https_publisher_start
#define SAMPLE_STOP coakka_https_publisher_stop
#define SAMPLE_SUBMIT coakka_https_publisher_submit
#define SAMPLE_GET coakka_https_publisher_get
#define SAMPLE_WAIT coakka_https_publisher_wait
#define SAMPLE_GET_TARGET coakka_https_publisher_get_target
#define SAMPLE_CANCEL coakka_https_publisher_cancel
#define SAMPLE_FORGET coakka_https_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_https_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_S3)
#include "coakka/addons/artifact_publisher_s3.h"
#define SAMPLE_ADDON_LABEL "s3"
#define SAMPLE_STATUS_T coakka_s3_status_t
#define SAMPLE_PUBLISHER_T coakka_s3_publisher_t
#define SAMPLE_CONFIG_T coakka_s3_publisher_config_t
#define SAMPLE_TARGET_T coakka_s3_publish_target_t
#define SAMPLE_SPEC_T coakka_s3_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_s3_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_s3_target_snapshot_t
#define SAMPLE_OK COAKKA_S3_OK
#define SAMPLE_WOULD_BLOCK COAKKA_S3_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_S3_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_S3_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_S3_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_S3_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_S3_RESULT_OK
#define SAMPLE_CREATE coakka_s3_publisher_create
#define SAMPLE_DESTROY coakka_s3_publisher_destroy
#define SAMPLE_START coakka_s3_publisher_start
#define SAMPLE_STOP coakka_s3_publisher_stop
#define SAMPLE_SUBMIT coakka_s3_publisher_submit
#define SAMPLE_GET coakka_s3_publisher_get
#define SAMPLE_WAIT coakka_s3_publisher_wait
#define SAMPLE_GET_TARGET coakka_s3_publisher_get_target
#define SAMPLE_CANCEL coakka_s3_publisher_cancel
#define SAMPLE_FORGET coakka_s3_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_s3_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_LOCAL_DROP)
#include "coakka/addons/artifact_publisher_local_drop.h"
#define SAMPLE_ADDON_LABEL "local-drop"
#define SAMPLE_STATUS_T coakka_local_drop_status_t
#define SAMPLE_PUBLISHER_T coakka_local_drop_publisher_t
#define SAMPLE_CONFIG_T coakka_local_drop_publisher_config_t
#define SAMPLE_TARGET_T coakka_local_drop_target_t
#define SAMPLE_SPEC_T coakka_local_drop_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_local_drop_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_local_drop_target_snapshot_t
#define SAMPLE_OK COAKKA_LOCAL_DROP_OK
#define SAMPLE_WOULD_BLOCK COAKKA_LOCAL_DROP_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_LOCAL_DROP_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_LOCAL_DROP_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_LOCAL_DROP_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_LOCAL_DROP_CANCELED
#define SAMPLE_RESULT_OK COAKKA_LOCAL_DROP_RESULT_OK
#define SAMPLE_CREATE coakka_local_drop_publisher_create
#define SAMPLE_DESTROY coakka_local_drop_publisher_destroy
#define SAMPLE_START coakka_local_drop_publisher_start
#define SAMPLE_STOP coakka_local_drop_publisher_stop
#define SAMPLE_SUBMIT coakka_local_drop_publisher_submit
#define SAMPLE_GET coakka_local_drop_publisher_get
#define SAMPLE_WAIT coakka_local_drop_publisher_wait
#define SAMPLE_GET_TARGET coakka_local_drop_publisher_get_target
#define SAMPLE_CANCEL coakka_local_drop_publisher_cancel
#define SAMPLE_FORGET coakka_local_drop_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_local_drop_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_AZURE_BLOB)
#include "coakka/addons/artifact_publisher_azure_blob.h"
#define SAMPLE_ADDON_LABEL "azure-blob"
#define SAMPLE_STATUS_T coakka_azure_blob_status_t
#define SAMPLE_PUBLISHER_T coakka_azure_blob_publisher_t
#define SAMPLE_CONFIG_T coakka_azure_blob_publisher_config_t
#define SAMPLE_TARGET_T coakka_azure_blob_publish_target_t
#define SAMPLE_SPEC_T coakka_azure_blob_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_azure_blob_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_azure_blob_target_snapshot_t
#define SAMPLE_OK COAKKA_AZURE_BLOB_OK
#define SAMPLE_WOULD_BLOCK COAKKA_AZURE_BLOB_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_AZURE_BLOB_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_AZURE_BLOB_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_AZURE_BLOB_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_AZURE_BLOB_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_AZURE_BLOB_RESULT_OK
#define SAMPLE_CREATE coakka_azure_blob_publisher_create
#define SAMPLE_DESTROY coakka_azure_blob_publisher_destroy
#define SAMPLE_START coakka_azure_blob_publisher_start
#define SAMPLE_STOP coakka_azure_blob_publisher_stop
#define SAMPLE_SUBMIT coakka_azure_blob_publisher_submit
#define SAMPLE_GET coakka_azure_blob_publisher_get
#define SAMPLE_WAIT coakka_azure_blob_publisher_wait
#define SAMPLE_GET_TARGET coakka_azure_blob_publisher_get_target
#define SAMPLE_CANCEL coakka_azure_blob_publisher_cancel
#define SAMPLE_FORGET coakka_azure_blob_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_azure_blob_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_GCS)
#include "coakka/addons/artifact_publisher_gcs.h"
#define SAMPLE_ADDON_LABEL "gcs"
#define SAMPLE_STATUS_T coakka_gcs_status_t
#define SAMPLE_PUBLISHER_T coakka_gcs_publisher_t
#define SAMPLE_CONFIG_T coakka_gcs_publisher_config_t
#define SAMPLE_TARGET_T coakka_gcs_publish_target_t
#define SAMPLE_SPEC_T coakka_gcs_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_gcs_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_gcs_target_snapshot_t
#define SAMPLE_OK COAKKA_GCS_OK
#define SAMPLE_WOULD_BLOCK COAKKA_GCS_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_GCS_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_GCS_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_GCS_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_GCS_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_GCS_RESULT_OK
#define SAMPLE_CREATE coakka_gcs_publisher_create
#define SAMPLE_DESTROY coakka_gcs_publisher_destroy
#define SAMPLE_START coakka_gcs_publisher_start
#define SAMPLE_STOP coakka_gcs_publisher_stop
#define SAMPLE_SUBMIT coakka_gcs_publisher_submit
#define SAMPLE_GET coakka_gcs_publisher_get
#define SAMPLE_WAIT coakka_gcs_publisher_wait
#define SAMPLE_GET_TARGET coakka_gcs_publisher_get_target
#define SAMPLE_CANCEL coakka_gcs_publisher_cancel
#define SAMPLE_FORGET coakka_gcs_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_gcs_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_WEBDAV)
#include "coakka/addons/artifact_publisher_webdav.h"
#define SAMPLE_ADDON_LABEL "webdav"
#define SAMPLE_STATUS_T coakka_webdav_status_t
#define SAMPLE_PUBLISHER_T coakka_webdav_publisher_t
#define SAMPLE_CONFIG_T coakka_webdav_publisher_config_t
#define SAMPLE_TARGET_T coakka_webdav_publish_target_t
#define SAMPLE_SPEC_T coakka_webdav_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_webdav_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_webdav_target_snapshot_t
#define SAMPLE_OK COAKKA_WEBDAV_OK
#define SAMPLE_WOULD_BLOCK COAKKA_WEBDAV_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_WEBDAV_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_WEBDAV_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_WEBDAV_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_WEBDAV_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_WEBDAV_RESULT_OK
#define SAMPLE_CREATE coakka_webdav_publisher_create
#define SAMPLE_DESTROY coakka_webdav_publisher_destroy
#define SAMPLE_START coakka_webdav_publisher_start
#define SAMPLE_STOP coakka_webdav_publisher_stop
#define SAMPLE_SUBMIT coakka_webdav_publisher_submit
#define SAMPLE_GET coakka_webdav_publisher_get
#define SAMPLE_WAIT coakka_webdav_publisher_wait
#define SAMPLE_GET_TARGET coakka_webdav_publisher_get_target
#define SAMPLE_CANCEL coakka_webdav_publisher_cancel
#define SAMPLE_FORGET coakka_webdav_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_webdav_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_OCI_REGISTRY)
#include "coakka/addons/artifact_publisher_oci_registry.h"
#define SAMPLE_ADDON_LABEL "oci-registry"
#define SAMPLE_STATUS_T coakka_oci_registry_status_t
#define SAMPLE_PUBLISHER_T coakka_oci_registry_publisher_t
#define SAMPLE_CONFIG_T coakka_oci_registry_publisher_config_t
#define SAMPLE_TARGET_T coakka_oci_registry_publish_target_t
#define SAMPLE_SPEC_T coakka_oci_registry_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_oci_registry_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_oci_registry_target_snapshot_t
#define SAMPLE_OK COAKKA_OCI_REGISTRY_OK
#define SAMPLE_WOULD_BLOCK COAKKA_OCI_REGISTRY_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_OCI_REGISTRY_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_OCI_REGISTRY_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_OCI_REGISTRY_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_OCI_REGISTRY_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_OCI_REGISTRY_RESULT_OK
#define SAMPLE_CREATE coakka_oci_registry_publisher_create
#define SAMPLE_DESTROY coakka_oci_registry_publisher_destroy
#define SAMPLE_START coakka_oci_registry_publisher_start
#define SAMPLE_STOP coakka_oci_registry_publisher_stop
#define SAMPLE_SUBMIT coakka_oci_registry_publisher_submit
#define SAMPLE_GET coakka_oci_registry_publisher_get
#define SAMPLE_WAIT coakka_oci_registry_publisher_wait
#define SAMPLE_GET_TARGET coakka_oci_registry_publisher_get_target
#define SAMPLE_CANCEL coakka_oci_registry_publisher_cancel
#define SAMPLE_FORGET coakka_oci_registry_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION                                              \
  coakka_oci_registry_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_HUGGINGFACE_HUB)
#include "coakka/addons/artifact_publisher_huggingface_hub.h"
#define SAMPLE_ADDON_LABEL "huggingface-hub"
#define SAMPLE_STATUS_T coakka_huggingface_hub_status_t
#define SAMPLE_PUBLISHER_T coakka_huggingface_hub_publisher_t
#define SAMPLE_CONFIG_T coakka_huggingface_hub_publisher_config_t
#define SAMPLE_TARGET_T coakka_huggingface_hub_publish_target_t
#define SAMPLE_SPEC_T coakka_huggingface_hub_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_huggingface_hub_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_huggingface_hub_target_snapshot_t
#define SAMPLE_OK COAKKA_HUGGINGFACE_HUB_OK
#define SAMPLE_WOULD_BLOCK COAKKA_HUGGINGFACE_HUB_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_HUGGINGFACE_HUB_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_HUGGINGFACE_HUB_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_HUGGINGFACE_HUB_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_HUGGINGFACE_HUB_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_HUGGINGFACE_HUB_RESULT_OK
#define SAMPLE_CREATE coakka_huggingface_hub_publisher_create
#define SAMPLE_DESTROY coakka_huggingface_hub_publisher_destroy
#define SAMPLE_START coakka_huggingface_hub_publisher_start
#define SAMPLE_STOP coakka_huggingface_hub_publisher_stop
#define SAMPLE_SUBMIT coakka_huggingface_hub_publisher_submit
#define SAMPLE_GET coakka_huggingface_hub_publisher_get
#define SAMPLE_WAIT coakka_huggingface_hub_publisher_wait
#define SAMPLE_GET_TARGET coakka_huggingface_hub_publisher_get_target
#define SAMPLE_CANCEL coakka_huggingface_hub_publisher_cancel
#define SAMPLE_FORGET coakka_huggingface_hub_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION                                              \
  coakka_huggingface_hub_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_GITHUB_RELEASE)
#include "coakka/addons/artifact_publisher_github_release.h"
#define SAMPLE_ADDON_LABEL "github-release"
#define SAMPLE_STATUS_T coakka_github_release_status_t
#define SAMPLE_PUBLISHER_T coakka_github_release_publisher_t
#define SAMPLE_CONFIG_T coakka_github_release_publisher_config_t
#define SAMPLE_TARGET_T coakka_github_release_publish_target_t
#define SAMPLE_SPEC_T coakka_github_release_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_github_release_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_github_release_target_snapshot_t
#define SAMPLE_OK COAKKA_GITHUB_RELEASE_OK
#define SAMPLE_WOULD_BLOCK COAKKA_GITHUB_RELEASE_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_GITHUB_RELEASE_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_GITHUB_RELEASE_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_GITHUB_RELEASE_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_GITHUB_RELEASE_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_GITHUB_RELEASE_RESULT_OK
#define SAMPLE_CREATE coakka_github_release_publisher_create
#define SAMPLE_DESTROY coakka_github_release_publisher_destroy
#define SAMPLE_START coakka_github_release_publisher_start
#define SAMPLE_STOP coakka_github_release_publisher_stop
#define SAMPLE_SUBMIT coakka_github_release_publisher_submit
#define SAMPLE_GET coakka_github_release_publisher_get
#define SAMPLE_WAIT coakka_github_release_publisher_wait
#define SAMPLE_GET_TARGET coakka_github_release_publisher_get_target
#define SAMPLE_CANCEL coakka_github_release_publisher_cancel
#define SAMPLE_FORGET coakka_github_release_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION                                              \
  coakka_github_release_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_GOOGLE_DRIVE)
#include "coakka/addons/artifact_publisher_google_drive.h"
#define SAMPLE_ADDON_LABEL "google-drive"
#define SAMPLE_STATUS_T coakka_google_drive_status_t
#define SAMPLE_PUBLISHER_T coakka_google_drive_publisher_t
#define SAMPLE_CONFIG_T coakka_google_drive_publisher_config_t
#define SAMPLE_TARGET_T coakka_google_drive_publish_target_t
#define SAMPLE_SPEC_T coakka_google_drive_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_google_drive_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_google_drive_target_snapshot_t
#define SAMPLE_OK COAKKA_GOOGLE_DRIVE_OK
#define SAMPLE_WOULD_BLOCK COAKKA_GOOGLE_DRIVE_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_GOOGLE_DRIVE_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_GOOGLE_DRIVE_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_GOOGLE_DRIVE_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_GOOGLE_DRIVE_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_GOOGLE_DRIVE_RESULT_OK
#define SAMPLE_CREATE coakka_google_drive_publisher_create
#define SAMPLE_DESTROY coakka_google_drive_publisher_destroy
#define SAMPLE_START coakka_google_drive_publisher_start
#define SAMPLE_STOP coakka_google_drive_publisher_stop
#define SAMPLE_SUBMIT coakka_google_drive_publisher_submit
#define SAMPLE_GET coakka_google_drive_publisher_get
#define SAMPLE_WAIT coakka_google_drive_publisher_wait
#define SAMPLE_GET_TARGET coakka_google_drive_publisher_get_target
#define SAMPLE_CANCEL coakka_google_drive_publisher_cancel
#define SAMPLE_FORGET coakka_google_drive_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION                                              \
  coakka_google_drive_publisher_dependency_version
#elif defined(COAKKA_SAMPLE_DROPBOX)
#include "coakka/addons/artifact_publisher_dropbox.h"
#define SAMPLE_ADDON_LABEL "dropbox"
#define SAMPLE_STATUS_T coakka_dropbox_status_t
#define SAMPLE_PUBLISHER_T coakka_dropbox_publisher_t
#define SAMPLE_CONFIG_T coakka_dropbox_publisher_config_t
#define SAMPLE_TARGET_T coakka_dropbox_publish_target_t
#define SAMPLE_SPEC_T coakka_dropbox_publish_spec_t
#define SAMPLE_SNAPSHOT_T coakka_dropbox_publisher_snapshot_t
#define SAMPLE_TARGET_SNAPSHOT_T coakka_dropbox_target_snapshot_t
#define SAMPLE_OK COAKKA_DROPBOX_OK
#define SAMPLE_WOULD_BLOCK COAKKA_DROPBOX_ERR_WOULD_BLOCK
#define SAMPLE_STATE_COMPLETED COAKKA_DROPBOX_PUBLISHER_COMPLETED
#define SAMPLE_STATE_PARTIAL COAKKA_DROPBOX_PUBLISHER_PARTIAL
#define SAMPLE_STATE_FAILED COAKKA_DROPBOX_PUBLISHER_FAILED
#define SAMPLE_STATE_CANCELED COAKKA_DROPBOX_PUBLISHER_CANCELED
#define SAMPLE_RESULT_OK COAKKA_DROPBOX_RESULT_OK
#define SAMPLE_CREATE coakka_dropbox_publisher_create
#define SAMPLE_DESTROY coakka_dropbox_publisher_destroy
#define SAMPLE_START coakka_dropbox_publisher_start
#define SAMPLE_STOP coakka_dropbox_publisher_stop
#define SAMPLE_SUBMIT coakka_dropbox_publisher_submit
#define SAMPLE_GET coakka_dropbox_publisher_get
#define SAMPLE_WAIT coakka_dropbox_publisher_wait
#define SAMPLE_GET_TARGET coakka_dropbox_publisher_get_target
#define SAMPLE_CANCEL coakka_dropbox_publisher_cancel
#define SAMPLE_FORGET coakka_dropbox_publisher_forget
#define SAMPLE_DEPENDENCY_VERSION coakka_dropbox_publisher_dependency_version
#else
#error "Select exactly one COAKKA_SAMPLE_* addon"
#endif

static int sample_configure_publisher(SAMPLE_CONFIG_T *config,
                                      const sample_publish_inputs_t *inputs,
                                      coakka_v2_file_lane_t *sender_lane) {
  config->struct_size = sizeof(*config);
#if defined(COAKKA_SAMPLE_LOCAL_DROP)
  config->drop_root = sample_required_env("COAKKA_SAMPLE_DROP_ROOT");
  if (config->drop_root == NULL) {
    return 1;
  }
  config->default_source_timeout_ms = 15000;
  config->poll_interval_ms = 25;
  config->quiet_period_ms = 100;
#else
  config->default_timeout_ms = 15000;
#endif
  config->staging_root = inputs->staging_root;
  config->sender_lane = sender_lane;
  config->queue_capacity = 2;
  config->retained_capacity = 8;
  return 0;
}

static int sample_configure_spec(SAMPLE_SPEC_T *spec, SAMPLE_TARGET_T *target,
                                 const sample_publish_inputs_t *inputs) {
  spec->struct_size = sizeof(*spec);
  spec->job_id = inputs->job_id;
  spec->destination_name = inputs->destination_name;
  spec->expected_size = inputs->expected_size;
  memcpy(spec->expected_sha256, inputs->expected_sha256,
         sizeof(inputs->expected_sha256));
  spec->timeout_ms = 15000;
  spec->target_count = 1;
  spec->targets = target;

#if defined(COAKKA_SAMPLE_LOCAL_DROP)
  spec->source_name = sample_required_env("COAKKA_SAMPLE_SOURCE_NAME");
  return spec->source_name == NULL ? 1 : 0;
#else
  const char *source_url = sample_required_env("COAKKA_SAMPLE_SOURCE_URL");
  const char *ca_file = sample_required_env("COAKKA_SAMPLE_CA_FILE");
  if (source_url == NULL || ca_file == NULL) {
    return 1;
  }
#endif

#if defined(COAKKA_SAMPLE_HTTPS)
  spec->url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->bearer_token = "";
#elif defined(COAKKA_SAMPLE_S3)
  spec->endpoint_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->region = "us-east-1";
  spec->bucket = "sample-bucket";
  spec->object_key = "models/model.bin";
  spec->version_id = "sample-version-1";
  spec->access_key = "sample-access-key";
  spec->secret_key = "sample-secret-key";
  spec->addressing_style = COAKKA_S3_ADDRESSING_PATH;
#elif defined(COAKKA_SAMPLE_AZURE_BLOB)
  spec->sas_blob_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->expected_version_id = "2026-08-12T04:05:06.0000000Z";
#elif defined(COAKKA_SAMPLE_GCS)
  spec->signed_object_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->expected_generation = UINT64_C(1700000000000001);
#elif defined(COAKKA_SAMPLE_WEBDAV)
  spec->resource_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->expected_etag = "\"coakka-sample-etag\"";
  spec->auth_mode = COAKKA_WEBDAV_AUTH_ANONYMOUS;
#elif defined(COAKKA_SAMPLE_OCI_REGISTRY)
  spec->blob_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->bearer_token = "sample-registry-token";
#elif defined(COAKKA_SAMPLE_HUGGINGFACE_HUB)
  spec->resolve_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->access_token = "hf_sample_read_token";
  spec->storage_host_suffix = "localhost";
#elif defined(COAKKA_SAMPLE_GITHUB_RELEASE)
  spec->asset_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->access_token = "github_sample_read_token";
  spec->storage_host_suffix = "localhost";
#elif defined(COAKKA_SAMPLE_GOOGLE_DRIVE)
  spec->revision_url = source_url;
  spec->ca_certificate_file = ca_file;
  spec->access_token = "google_drive_sample_read_token";
#elif defined(COAKKA_SAMPLE_DROPBOX)
  spec->endpoint_url = source_url;
  spec->revision = "015a01044acb99900000001aa8954d0";
  spec->ca_certificate_file = ca_file;
  spec->access_token = "dropbox_sample_read_token";
#endif
  return 0;
}

#if defined(COAKKA_SAMPLE_LOCAL_DROP)
#define SAMPLE_ACQUIRED_BYTES(snapshot_value) ((snapshot_value).acquired_bytes)
#else
#define SAMPLE_ACQUIRED_BYTES(snapshot_value) ((snapshot_value).fetched_bytes)
#endif

#include "service_a_lifecycle.inc"
