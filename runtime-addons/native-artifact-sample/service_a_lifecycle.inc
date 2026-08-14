/*
 * Compile-time publisher adapter contract.
 *
 * service_a.c selects one concrete public addon ABI before including this
 * file. Keeping the types concrete avoids erased ownership and incompatible
 * function-pointer casts while sharing the identical File Lane lifecycle.
 */

#include <stdio.h>
#include <string.h>

enum { SAMPLE_WAIT_ATTEMPTS = 240, SAMPLE_WAIT_MS = 250 };

static int sample_publisher_state_is_terminal(uint32_t state) {
  return state == SAMPLE_STATE_COMPLETED || state == SAMPLE_STATE_PARTIAL ||
         state == SAMPLE_STATE_FAILED || state == SAMPLE_STATE_CANCELED;
}

static int sample_wait_for_publish(SAMPLE_PUBLISHER_T *publisher,
                                   const char *job_id,
                                   SAMPLE_SNAPSHOT_T *snapshot) {
  int attempt;
  memset(snapshot, 0, sizeof(*snapshot));
  snapshot->struct_size = sizeof(*snapshot);
  if (SAMPLE_GET(publisher, job_id, snapshot) != SAMPLE_OK) {
    fprintf(stderr, "%s publisher could not read admitted job\n",
            SAMPLE_ADDON_LABEL);
    return 1;
  }
  for (attempt = 0; attempt < SAMPLE_WAIT_ATTEMPTS; ++attempt) {
    SAMPLE_STATUS_T status;
    uint64_t sequence;
    if (sample_publisher_state_is_terminal(snapshot->state)) {
      return 0;
    }
    /* Sequence waits avoid sleep polling; the loop adds a 60-second host
       deadline even if the protocol-specific timeout is misconfigured. */
    sequence = snapshot->update_sequence;
    memset(snapshot, 0, sizeof(*snapshot));
    snapshot->struct_size = sizeof(*snapshot);
    status = SAMPLE_WAIT(publisher, job_id, sequence, SAMPLE_WAIT_MS, snapshot);
    if (status != SAMPLE_OK && status != SAMPLE_WOULD_BLOCK) {
      fprintf(stderr, "%s publisher wait failed: %d\n", SAMPLE_ADDON_LABEL,
              (int)status);
      return 1;
    }
    if (status == SAMPLE_WOULD_BLOCK) {
      memset(snapshot, 0, sizeof(*snapshot));
      snapshot->struct_size = sizeof(*snapshot);
      if (SAMPLE_GET(publisher, job_id, snapshot) != SAMPLE_OK) {
        return 1;
      }
    }
  }
  fprintf(stderr, "%s publisher did not reach a terminal state\n",
          SAMPLE_ADDON_LABEL);
  return 1;
}

int main(void) {
  sample_publish_inputs_t inputs;
  coakka_v2_file_lane_t *sender_lane = NULL;
  SAMPLE_PUBLISHER_T *publisher = NULL;
  SAMPLE_SNAPSHOT_T snapshot;
  SAMPLE_TARGET_SNAPSHOT_T target_snapshot;
  int lane_started = 0;
  int publisher_started = 0;
  int submitted = 0;
  int exit_code = 1;

  memset(&inputs, 0, sizeof(inputs));
  if (sample_load_publish_inputs(&inputs) != 0) {
    return 1;
  }

  /* Publisher jobs retain a borrowed lane handle. The host therefore creates
     the lane first and destroys it only after publisher destruction. */
  {
    coakka_v2_file_lane_config_t lane_config;
    memset(&lane_config, 0, sizeof(lane_config));
    lane_config.struct_size = sizeof(lane_config);
    lane_config.flags = COAKKA_V2_FILE_LANE_ENABLE_SENDER;
    lane_config.queue_capacity = 4;
    lane_config.max_file_size = inputs.expected_size + 4096u;
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
    SAMPLE_CONFIG_T config;
    memset(&config, 0, sizeof(config));
    if (sample_configure_publisher(&config, &inputs, sender_lane) != 0 ||
        SAMPLE_CREATE(&config, &publisher) != SAMPLE_OK) {
      fprintf(stderr, "%s publisher creation failed\n", SAMPLE_ADDON_LABEL);
      goto cleanup;
    }
  }
  if (SAMPLE_START(publisher) != SAMPLE_OK) {
    fprintf(stderr, "%s publisher start failed\n", SAMPLE_ADDON_LABEL);
    goto cleanup;
  }
  publisher_started = 1;

  {
    SAMPLE_TARGET_T target;
    SAMPLE_SPEC_T spec;
    memset(&target, 0, sizeof(target));
    target.struct_size = sizeof(target);
    target.transfer_id = inputs.transfer_id;
    target.authorization_token = inputs.authorization_token;
    target.remote_host = inputs.receiver_host;
    target.remote_port = inputs.receiver_port;
    target.timeout_ms = 15000;

    memset(&spec, 0, sizeof(spec));
    if (sample_configure_spec(&spec, &target, &inputs) != 0) {
      fprintf(stderr, "%s source configuration is invalid\n",
              SAMPLE_ADDON_LABEL);
      goto cleanup;
    }
    /* submit deep-copies this stack-backed spec, target, strings, and digest
       into bounded publisher-owned storage before it returns. */
    {
      SAMPLE_STATUS_T submit_status = SAMPLE_SUBMIT(publisher, &spec);
      if (submit_status != SAMPLE_OK) {
        fprintf(stderr, "%s publish admission failed: %d\n", SAMPLE_ADDON_LABEL,
                (int)submit_status);
        goto cleanup;
      }
    }
    submitted = 1;
  }

  if (sample_wait_for_publish(publisher, inputs.job_id, &snapshot) != 0) {
    goto cleanup;
  }
  memset(&target_snapshot, 0, sizeof(target_snapshot));
  target_snapshot.struct_size = sizeof(target_snapshot);
  if (SAMPLE_GET_TARGET(publisher, inputs.job_id, 0, &target_snapshot) !=
      SAMPLE_OK) {
    fprintf(stderr, "%s target projection failed\n", SAMPLE_ADDON_LABEL);
    goto cleanup;
  }

  printf("coakka_artifact_service_a_terminal addon=%s job=%s state=%u "
         "result=%u fetched=%llu completedTargets=%u failedTargets=%u "
         "dependency=%s\n",
         SAMPLE_ADDON_LABEL, inputs.job_id, snapshot.state, snapshot.result,
         (unsigned long long)SAMPLE_ACQUIRED_BYTES(snapshot),
         snapshot.completed_targets, snapshot.failed_targets,
         SAMPLE_DEPENDENCY_VERSION());
  if (snapshot.state != SAMPLE_STATE_COMPLETED ||
      snapshot.result != SAMPLE_RESULT_OK ||
      SAMPLE_ACQUIRED_BYTES(snapshot) != inputs.expected_size ||
      snapshot.completed_targets != 1 || snapshot.failed_targets != 0 ||
      target_snapshot.admitted != 1 ||
      target_snapshot.lane_state != COAKKA_V2_FILE_TRANSFER_STATE_COMPLETED ||
      target_snapshot.lane_result != COAKKA_V2_FILE_TRANSFER_RESULT_OK ||
      target_snapshot.transferred_bytes != inputs.expected_size) {
    fprintf(stderr,
            "%s terminal failure: state=%u result=%u detail=%s "
            "targetState=%u targetResult=%u\n",
            SAMPLE_ADDON_LABEL, snapshot.state, snapshot.result,
            snapshot.detail, target_snapshot.lane_state,
            target_snapshot.lane_result);
    goto cleanup;
  }
  if (SAMPLE_FORGET(publisher, inputs.job_id) != SAMPLE_OK) {
    fprintf(stderr, "%s could not forget terminal job\n", SAMPLE_ADDON_LABEL);
    goto cleanup;
  }
  submitted = 0;
  exit_code = 0;

cleanup:
  if (submitted && publisher != NULL) {
    (void)SAMPLE_CANCEL(publisher, inputs.job_id);
  }
  /* Reverse startup order prevents a late publisher submission after lane
     stop, including every early-error path above. */
  if (publisher_started) {
    (void)SAMPLE_STOP(publisher);
  }
  SAMPLE_DESTROY(publisher);
  if (lane_started) {
    (void)coakka_v2_file_lane_stop(sender_lane);
  }
  coakka_v2_file_lane_destroy(sender_lane);
  return exit_code;
}
