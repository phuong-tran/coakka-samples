#include "sample_common.h"

#include "coakka/v2/utils.h"

#include <stdio.h>
#include <string.h>

enum { SAMPLE_WAIT_ATTEMPTS = 240, SAMPLE_WAIT_MS = 250 };

static int wait_for_receive(coakka_v2_file_lane_t *lane,
                            const char *transfer_id,
                            coakka_v2_file_transfer_snapshot_t *snapshot) {
  int attempt;
  memset(snapshot, 0, sizeof(*snapshot));
  snapshot->struct_size = sizeof(*snapshot);
  if (coakka_v2_file_lane_get_transfer(
          lane, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE,
          snapshot) != COAKKA_V2_OK) {
    fprintf(stderr, "receiver could not read prepared transfer\n");
    return 1;
  }
  for (attempt = 0; attempt < SAMPLE_WAIT_ATTEMPTS; ++attempt) {
    coakka_v2_status_t status;
    uint64_t sequence;
    if (sample_file_state_is_terminal(snapshot->state)) {
      return 0;
    }
    sequence = snapshot->update_sequence;
    memset(snapshot, 0, sizeof(*snapshot));
    snapshot->struct_size = sizeof(*snapshot);
    status = coakka_v2_file_lane_wait_transfer(
        lane, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE, sequence,
        SAMPLE_WAIT_MS, snapshot);
    if (status != COAKKA_V2_OK && status != COAKKA_V2_ERR_WOULD_BLOCK) {
      fprintf(stderr, "receiver wait failed: %s\n",
              coakka_v2_status_name(status));
      return 1;
    }
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
      memset(snapshot, 0, sizeof(*snapshot));
      snapshot->struct_size = sizeof(*snapshot);
      if (coakka_v2_file_lane_get_transfer(
              lane, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE,
              snapshot) != COAKKA_V2_OK) {
        return 1;
      }
    }
  }
  fprintf(stderr, "receiver did not reach a terminal state\n");
  return 1;
}

int main(void) {
  const char *bind_host = sample_required_env("COAKKA_SAMPLE_RECEIVER_HOST");
  const char *port_text = sample_required_env("COAKKA_SAMPLE_RECEIVER_PORT");
  const char *transfer_id = sample_required_env("COAKKA_SAMPLE_TRANSFER_ID");
  const char *token = sample_required_env("COAKKA_SAMPLE_TRANSFER_TOKEN");
  const char *destination =
      sample_required_env("COAKKA_SAMPLE_DESTINATION_PATH");
  const char *size_text = sample_required_env("COAKKA_SAMPLE_ARTIFACT_SIZE");
  const char *digest_text =
      sample_required_env("COAKKA_SAMPLE_ARTIFACT_SHA256");
  const char *ready_file = sample_required_env("COAKKA_SAMPLE_READY_FILE");
  coakka_v2_file_lane_t *lane = NULL;
  coakka_v2_file_transfer_snapshot_t snapshot;
  uint8_t digest[COAKKA_V2_FILE_LANE_SHA256_BYTES];
  uint8_t received_digest[COAKKA_V2_FILE_LANE_SHA256_BYTES];
  uint64_t expected_size = 0;
  uint64_t received_size = 0;
  uint16_t port = 0;
  uint16_t bound_port = 0;
  int started = 0;
  int exit_code = 1;

  if (bind_host == NULL || port_text == NULL || transfer_id == NULL ||
      token == NULL || destination == NULL || size_text == NULL ||
      digest_text == NULL || ready_file == NULL ||
      sample_parse_u16(port_text, &port) != 0 ||
      sample_parse_u64(size_text, &expected_size) != 0 ||
      sample_parse_sha256(digest_text, digest) != 0 || expected_size == 0 ||
      expected_size > UINT64_MAX - 4096u) {
    fprintf(stderr, "receiver configuration is invalid\n");
    return 1;
  }

  {
    coakka_v2_file_lane_config_t config;
    memset(&config, 0, sizeof(config));
    config.struct_size = sizeof(config);
    config.flags = COAKKA_V2_FILE_LANE_ENABLE_RECEIVER;
    config.bind_host = bind_host;
    config.bind_port = port;
    config.queue_capacity = 4;
    config.max_file_size = expected_size + 4096u;
    if (coakka_v2_file_lane_create_ex(&config, &lane) != COAKKA_V2_OK) {
      fprintf(stderr, "receiver lane creation failed\n");
      goto cleanup;
    }
  }
  if (coakka_v2_file_lane_start(lane) != COAKKA_V2_OK) {
    fprintf(stderr, "receiver lane start failed\n");
    goto cleanup;
  }
  started = 1;
  if (coakka_v2_file_lane_get_bound_port(lane, &bound_port) != COAKKA_V2_OK ||
      bound_port == 0 || (port != 0 && bound_port != port)) {
    fprintf(stderr, "receiver did not bind the requested port\n");
    goto cleanup;
  }

  {
    coakka_v2_file_receive_spec_t receive;
    memset(&receive, 0, sizeof(receive));
    receive.struct_size = sizeof(receive);
    receive.transfer_id = transfer_id;
    receive.authorization_token = token;
    receive.destination_path = destination;
    receive.expected_size = expected_size;
    memcpy(receive.expected_sha256, digest, sizeof(digest));
    if (coakka_v2_file_lane_prepare_receive(lane, &receive) != COAKKA_V2_OK) {
      fprintf(stderr, "receiver authorization preparation failed\n");
      goto cleanup;
    }
  }
  /* Service A is released only after the exact one-use receive grant exists. */
  if (sample_write_ready_file(ready_file, bound_port) != 0) {
    goto cleanup;
  }
  printf("coakka_artifact_service_b_ready transfer=%s host=%s port=%u\n",
         transfer_id, bind_host, (unsigned int)bound_port);
  fflush(stdout);

  if (wait_for_receive(lane, transfer_id, &snapshot) != 0) {
    goto cleanup;
  }
  if (snapshot.state != COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED ||
      snapshot.result != COAKKA_V2_FILE_TRANSFER_RESULT_OK ||
      snapshot.transferred_bytes != expected_size) {
    fprintf(stderr,
            "receiver terminal failure: state=%u result=%u bytes=%llu "
            "detail=%s\n",
            snapshot.state, snapshot.result,
            (unsigned long long)snapshot.transferred_bytes, snapshot.detail);
    goto cleanup;
  }
  if (coakka_v2_file_sha256_path(destination, received_digest,
                                 &received_size) != COAKKA_V2_OK ||
      received_size != expected_size ||
      memcmp(received_digest, digest, sizeof(digest)) != 0) {
    fprintf(stderr, "receiver destination verification failed\n");
    goto cleanup;
  }

  printf("coakka_artifact_service_b_completed transfer=%s bytes=%llu\n",
         transfer_id, (unsigned long long)received_size);
  if (coakka_v2_file_lane_forget_transfer(
          lane, transfer_id, COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE) !=
      COAKKA_V2_OK) {
    fprintf(stderr, "receiver could not forget terminal record\n");
    goto cleanup;
  }
  exit_code = 0;

cleanup:
  if (started) {
    (void)coakka_v2_file_lane_stop(lane);
  }
  coakka_v2_file_lane_destroy(lane);
  return exit_code;
}
