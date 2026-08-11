#include "sample_common.h"

#include "coakka/addons/artifact_publisher_sftp.h"

#include <stdio.h>
#include <string.h>

enum { SAMPLE_WAIT_ATTEMPTS = 240, SAMPLE_WAIT_MS = 250 };

static int sftp_state_is_terminal(uint32_t state) {
  return state == COAKKA_SFTP_PUBLISHER_COMPLETED ||
         state == COAKKA_SFTP_PUBLISHER_PARTIAL ||
         state == COAKKA_SFTP_PUBLISHER_FAILED ||
         state == COAKKA_SFTP_PUBLISHER_CANCELED;
}

static const char *sftp_result_name(uint32_t result) {
  switch (result) {
  case COAKKA_SFTP_RESULT_NONE:
    return "NONE";
  case COAKKA_SFTP_RESULT_OK:
    return "OK";
  case COAKKA_SFTP_RESULT_INVALID_REQUEST:
    return "INVALID_REQUEST";
  case COAKKA_SFTP_RESULT_CONNECT_FAILED:
    return "CONNECT_FAILED";
  case COAKKA_SFTP_RESULT_TIMEOUT:
    return "TIMEOUT";
  case COAKKA_SFTP_RESULT_HOST_KEY_MISMATCH:
    return "HOST_KEY_MISMATCH";
  case COAKKA_SFTP_RESULT_AUTH_REJECTED:
    return "AUTH_REJECTED";
  case COAKKA_SFTP_RESULT_REMOTE_NOT_FOUND:
    return "REMOTE_NOT_FOUND";
  case COAKKA_SFTP_RESULT_REMOTE_IO:
    return "REMOTE_IO";
  case COAKKA_SFTP_RESULT_SOURCE_CHANGED:
    return "SOURCE_CHANGED";
  case COAKKA_SFTP_RESULT_STORAGE_IO:
    return "STORAGE_IO";
  case COAKKA_SFTP_RESULT_SIZE_MISMATCH:
    return "SIZE_MISMATCH";
  case COAKKA_SFTP_RESULT_DIGEST_MISMATCH:
    return "DIGEST_MISMATCH";
  case COAKKA_SFTP_RESULT_DESTINATION_EXISTS:
    return "DESTINATION_EXISTS";
  case COAKKA_SFTP_RESULT_DISTRIBUTION_FAILED:
    return "DISTRIBUTION_FAILED";
  case COAKKA_SFTP_RESULT_CANCELED_BY_HOST:
    return "CANCELED_BY_HOST";
  case COAKKA_SFTP_RESULT_STOPPED:
    return "STOPPED";
  case COAKKA_SFTP_RESULT_INTERNAL_ERROR:
    return "INTERNAL_ERROR";
  default:
    return "UNKNOWN";
  }
}

static int wait_for_publish(coakka_sftp_publisher_t *publisher,
                            const char *job_id,
                            coakka_sftp_publisher_snapshot_t *snapshot) {
  int attempt;
  memset(snapshot, 0, sizeof(*snapshot));
  snapshot->struct_size = sizeof(*snapshot);
  if (coakka_sftp_publisher_get(publisher, job_id, snapshot) !=
      COAKKA_SFTP_OK) {
    fprintf(stderr, "publisher could not read admitted job\n");
    return 1;
  }
  for (attempt = 0; attempt < SAMPLE_WAIT_ATTEMPTS; ++attempt) {
    coakka_sftp_status_t status;
    uint64_t sequence;
    if (sftp_state_is_terminal(snapshot->state)) {
      return 0;
    }
    /* The sequence wait sleeps until state changes; the bounded retry exists
       only to enforce an application-level deadline. */
    sequence = snapshot->update_sequence;
    memset(snapshot, 0, sizeof(*snapshot));
    snapshot->struct_size = sizeof(*snapshot);
    status = coakka_sftp_publisher_wait(publisher, job_id, sequence,
                                        SAMPLE_WAIT_MS, snapshot);
    if (status != COAKKA_SFTP_OK && status != COAKKA_SFTP_ERR_WOULD_BLOCK) {
      fprintf(stderr, "publisher wait failed: %d\n", (int)status);
      return 1;
    }
    if (status == COAKKA_SFTP_ERR_WOULD_BLOCK) {
      memset(snapshot, 0, sizeof(*snapshot));
      snapshot->struct_size = sizeof(*snapshot);
      if (coakka_sftp_publisher_get(publisher, job_id, snapshot) !=
          COAKKA_SFTP_OK) {
        return 1;
      }
    }
  }
  fprintf(stderr, "publisher did not reach a terminal state\n");
  return 1;
}

int main(void) {
  const char *job_id = sample_required_env("COAKKA_SAMPLE_JOB_ID");
  const char *logical_host =
      sample_required_env("COAKKA_SAMPLE_SFTP_LOGICAL_HOST");
  const char *connect_address =
      sample_required_env("COAKKA_SAMPLE_SFTP_CONNECT_ADDRESS");
  const char *sftp_port_text = sample_required_env("COAKKA_SAMPLE_SFTP_PORT");
  const char *username = sample_required_env("COAKKA_SAMPLE_SFTP_USERNAME");
  const char *private_key =
      sample_required_env("COAKKA_SAMPLE_SFTP_PRIVATE_KEY");
  const char *passphrase =
      sample_optional_env("COAKKA_SAMPLE_SFTP_PRIVATE_KEY_PASSPHRASE", "");
  const char *host_digest_text =
      sample_required_env("COAKKA_SAMPLE_SFTP_HOST_SHA256");
  const char *remote_path =
      sample_required_env("COAKKA_SAMPLE_SFTP_REMOTE_PATH");
  const char *staging_root = sample_required_env("COAKKA_SAMPLE_STAGING_ROOT");
  const char *destination_name =
      sample_required_env("COAKKA_SAMPLE_STAGING_NAME");
  const char *size_text = sample_required_env("COAKKA_SAMPLE_ARTIFACT_SIZE");
  const char *artifact_digest_text =
      sample_required_env("COAKKA_SAMPLE_ARTIFACT_SHA256");
  const char *transfer_id = sample_required_env("COAKKA_SAMPLE_TRANSFER_ID");
  const char *token = sample_required_env("COAKKA_SAMPLE_TRANSFER_TOKEN");
  const char *receiver_host =
      sample_required_env("COAKKA_SAMPLE_RECEIVER_HOST");
  const char *receiver_port_text =
      sample_required_env("COAKKA_SAMPLE_RECEIVER_PORT");
  coakka_v2_file_lane_t *sender_lane = NULL;
  coakka_sftp_publisher_t *publisher = NULL;
  coakka_sftp_publisher_snapshot_t snapshot;
  coakka_sftp_target_snapshot_t target_snapshot;
  uint8_t host_digest[COAKKA_SFTP_SHA256_BYTES];
  uint8_t artifact_digest[COAKKA_SFTP_SHA256_BYTES];
  uint64_t expected_size = 0;
  uint16_t sftp_port = 0;
  uint16_t receiver_port = 0;
  int lane_started = 0;
  int publisher_started = 0;
  int submitted = 0;
  int exit_code = 1;

  if (job_id == NULL || logical_host == NULL || connect_address == NULL ||
      sftp_port_text == NULL || username == NULL || private_key == NULL ||
      host_digest_text == NULL || remote_path == NULL || staging_root == NULL ||
      destination_name == NULL || size_text == NULL ||
      artifact_digest_text == NULL || transfer_id == NULL || token == NULL ||
      receiver_host == NULL || receiver_port_text == NULL ||
      sample_parse_u16(sftp_port_text, &sftp_port) != 0 ||
      sample_parse_u16(receiver_port_text, &receiver_port) != 0 ||
      sample_parse_u64(size_text, &expected_size) != 0 ||
      sample_parse_sha256(host_digest_text, host_digest) != 0 ||
      sample_parse_sha256(artifact_digest_text, artifact_digest) != 0 ||
      sftp_port == 0 || receiver_port == 0 ||
      expected_size > UINT64_MAX - 4096) {
    fprintf(stderr, "publisher configuration is invalid\n");
    return 1;
  }

  /* The sender lane must outlive the publisher because accepted jobs retain
     the borrowed lane handle through their terminal File Lane outcome. */
  {
    coakka_v2_file_lane_config_t lane_config;
    memset(&lane_config, 0, sizeof(lane_config));
    lane_config.struct_size = sizeof(lane_config);
    lane_config.flags = COAKKA_V2_FILE_LANE_ENABLE_SENDER;
    lane_config.queue_capacity = 4;
    lane_config.max_file_size = expected_size + 4096;
    if (coakka_v2_file_lane_create_ex(&lane_config, &sender_lane) !=
        COAKKA_V2_OK) {
      fprintf(stderr, "sender lane creation failed\n");
      goto cleanup;
    }
  }
  if (coakka_v2_file_lane_start(sender_lane) != COAKKA_V2_OK) {
    fprintf(stderr, "sender lane start failed\n");
    goto cleanup;
  }
  lane_started = 1;

  {
    coakka_sftp_publisher_config_t config;
    memset(&config, 0, sizeof(config));
    config.struct_size = sizeof(config);
    config.staging_root = staging_root;
    config.sender_lane = sender_lane;
    config.queue_capacity = 2;
    config.retained_capacity = 8;
    config.default_timeout_ms = 15000;
    if (coakka_sftp_publisher_create(&config, &publisher) != COAKKA_SFTP_OK) {
      fprintf(stderr, "SFTP publisher creation failed\n");
      goto cleanup;
    }
  }
  if (coakka_sftp_publisher_start(publisher) != COAKKA_SFTP_OK) {
    fprintf(stderr, "SFTP publisher start failed\n");
    goto cleanup;
  }
  publisher_started = 1;

  {
    coakka_sftp_publish_target_t target;
    coakka_sftp_publish_spec_t spec;
    memset(&target, 0, sizeof(target));
    target.struct_size = sizeof(target);
    target.transfer_id = transfer_id;
    target.authorization_token = token;
    target.remote_host = receiver_host;
    target.remote_port = receiver_port;
    target.timeout_ms = 15000;

    memset(&spec, 0, sizeof(spec));
    spec.struct_size = sizeof(spec);
    spec.job_id = job_id;
    spec.logical_host = logical_host;
    spec.connect_address = connect_address;
    spec.port = sftp_port;
    spec.username = username;
    spec.private_key_file = private_key;
    spec.private_key_passphrase = passphrase;
    memcpy(spec.host_key_sha256, host_digest, sizeof(host_digest));
    spec.remote_path = remote_path;
    spec.destination_name = destination_name;
    spec.expected_size = expected_size;
    memcpy(spec.expected_sha256, artifact_digest, sizeof(artifact_digest));
    spec.timeout_ms = 15000;
    spec.target_count = 1;
    spec.targets = &target;
    /* Admission copies this stack-backed spec and its strings into bounded
       publisher-owned state before returning. */
    if (coakka_sftp_publisher_submit(publisher, &spec) != COAKKA_SFTP_OK) {
      fprintf(stderr, "SFTP publish admission failed\n");
      goto cleanup;
    }
    submitted = 1;
  }

  if (wait_for_publish(publisher, job_id, &snapshot) != 0) {
    goto cleanup;
  }
  memset(&target_snapshot, 0, sizeof(target_snapshot));
  target_snapshot.struct_size = sizeof(target_snapshot);
  if (coakka_sftp_publisher_get_target(publisher, job_id, 0,
                                       &target_snapshot) != COAKKA_SFTP_OK) {
    fprintf(stderr, "publisher target projection failed\n");
    goto cleanup;
  }

  printf("coakka_sftp_service_a_terminal job=%s state=%u result=%s "
         "fetched=%llu completedTargets=%u failedTargets=%u libssh2=%s\n",
         job_id, snapshot.state, sftp_result_name(snapshot.result),
         (unsigned long long)snapshot.fetched_bytes, snapshot.completed_targets,
         snapshot.failed_targets, coakka_sftp_publisher_dependency_version());
  if (snapshot.state != COAKKA_SFTP_PUBLISHER_COMPLETED ||
      snapshot.result != COAKKA_SFTP_RESULT_OK ||
      snapshot.fetched_bytes != expected_size ||
      snapshot.completed_targets != 1 || snapshot.failed_targets != 0 ||
      target_snapshot.admitted != 1 ||
      target_snapshot.lane_state != COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED ||
      target_snapshot.lane_result != COAKKA_V2_FILE_TRANSFER_RESULT_OK ||
      target_snapshot.transferred_bytes != expected_size) {
    fprintf(stderr,
            "publisher terminal failure: state=%u result=%s detail=%s "
            "targetState=%u targetResult=%u\n",
            snapshot.state, sftp_result_name(snapshot.result), snapshot.detail,
            target_snapshot.lane_state, target_snapshot.lane_result);
    goto cleanup;
  }
  if (coakka_sftp_publisher_forget(publisher, job_id) != COAKKA_SFTP_OK) {
    fprintf(stderr, "publisher could not forget terminal job\n");
    goto cleanup;
  }
  submitted = 0;
  exit_code = 0;

cleanup:
  if (submitted && publisher != NULL) {
    (void)coakka_sftp_publisher_cancel(publisher, job_id);
  }
  /* Reverse startup order so the publisher cannot submit after lane stop. */
  if (publisher_started) {
    (void)coakka_sftp_publisher_stop(publisher);
  }
  coakka_sftp_publisher_destroy(publisher);
  if (lane_started) {
    (void)coakka_v2_file_lane_stop(sender_lane);
  }
  coakka_v2_file_lane_destroy(sender_lane);
  return exit_code;
}
