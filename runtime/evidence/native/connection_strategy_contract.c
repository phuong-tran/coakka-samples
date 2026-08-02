#include "connection_strategy_evidence.h"
#include "evidence_platform.h"

#include <string.h>

typedef struct connection_strategy_case_spec_t {
  uint32_t mode;
  uint64_t capability;
} connection_strategy_case_spec_t;

static const connection_strategy_case_spec_t k_case_specs[] = {
    {COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE, 0u},
    {COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL,
     COAKKA_V2_CAPABILITY_TCP_BOUNDED_POOL},
    {COAKKA_V2_TCP_CONNECTION_PERSISTENT_SINGLE_FLIGHT,
     COAKKA_V2_CAPABILITY_TCP_PERSISTENT_SINGLE_FLIGHT},
    {COAKKA_V2_TCP_CONNECTION_MULTIPLEXED,
     COAKKA_V2_CAPABILITY_TCP_MULTIPLEXING},
};

_Static_assert(sizeof(k_case_specs) / sizeof(k_case_specs[0]) ==
                   CONNECTION_STRATEGY_EVIDENCE_CASE_COUNT,
               "connection strategy evidence case count drifted");

const char* connection_strategy_mode_name(uint32_t mode) {
  switch (mode) {
    case COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE:
      return "PER_EXCHANGE";
    case COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL:
      return "BOUNDED_POOL";
    case COAKKA_V2_TCP_CONNECTION_PERSISTENT_SINGLE_FLIGHT:
      return "PERSISTENT_SINGLE_FLIGHT";
    case COAKKA_V2_TCP_CONNECTION_MULTIPLEXED:
      return "MULTIPLEXING";
    default:
      return "UNKNOWN";
  }
}

static coakka_v2_tcp_connection_options_t make_mode_options(uint32_t mode) {
  coakka_v2_tcp_connection_options_t options;
  memset(&options, 0, sizeof(options));
  options.struct_size = sizeof(options);
  options.fields = COAKKA_V2_TCP_CONNECTION_FIELD_MODE;
  options.mode = mode;
  return options;
}

static void init_apply_result(coakka_v2_tcp_connection_apply_result_t* result) {
  memset(result, 0, sizeof(*result));
  result->struct_size = sizeof(*result);
}

static void init_host_handles(coakka_v2_host_handles_t* handles) {
  memset(handles, 0, sizeof(*handles));
  handles->struct_size = sizeof(*handles);
  handles->request_write_fd = -1;
  handles->response_read_fd = -1;
  handles->deadletter_read_fd = -1;
  handles->control_write_fd = -1;
  handles->monitor_read_fd = -1;
  handles->delivered_request_read_fd = -1;
}

static void close_host_handles(coakka_v2_host_handles_t* handles) {
  int* channels[] = {
      &handles->request_write_fd,
      &handles->response_read_fd,
      &handles->deadletter_read_fd,
      &handles->control_write_fd,
      &handles->monitor_read_fd,
      &handles->delivered_request_read_fd,
  };
  evidence_platform_close_channels(
      channels, sizeof(channels) / sizeof(channels[0]));
}

static int fail_case(connection_strategy_case_result_t* result,
                     const char* stage) {
  if (result->failure_stage == NULL) {
    result->failure_stage = stage;
  }
  return 1;
}

static int connection_config_equal(
    const coakka_v2_tcp_connection_config_snapshot_t* left,
    const coakka_v2_tcp_connection_config_snapshot_t* right) {
  return left->defaults_revision == right->defaults_revision &&
         left->mode == right->mode &&
         left->applicable_fields == right->applicable_fields &&
         left->explicitly_configured_fields ==
             right->explicitly_configured_fields &&
         left->defaulted_fields == right->defaulted_fields &&
         left->configurable_fields == right->configurable_fields &&
         left->max_connections == right->max_connections &&
         left->max_requests_per_connection ==
             right->max_requests_per_connection &&
         left->idle_timeout_ms == right->idle_timeout_ms;
}

static int read_connection_config(
    coakka_v2_runtime_t* runtime,
    coakka_v2_tcp_connection_config_snapshot_t* config) {
  memset(config, 0, sizeof(*config));
  config->struct_size = sizeof(*config);
  return coakka_v2_runtime_get_tcp_connection_config(runtime, config) ==
                 COAKKA_V2_OK
             ? 0
             : 1;
}

static int expected_rejection_status(
    const coakka_v2_runtime_capability_snapshot_t* capabilities,
    uint64_t capability) {
  if ((capabilities->compiled_capabilities & capability) == 0u) {
    return COAKKA_V2_ERR_FEATURE_UNAVAILABLE;
  }
  return COAKKA_V2_ERR_FEATURE_NOT_ENTITLED;
}

static uint32_t expected_rejection_validation_code(
    const coakka_v2_runtime_capability_snapshot_t* capabilities,
    uint64_t capability) {
  if ((capabilities->compiled_capabilities & capability) == 0u) {
    return COAKKA_V2_TCP_CONNECTION_FEATURE_UNAVAILABLE;
  }
  return COAKKA_V2_TCP_CONNECTION_FEATURE_NOT_ENTITLED;
}

static int run_bounded_pool_tuning_check(
    coakka_v2_runtime_t* runtime,
    const coakka_v2_runtime_capability_snapshot_t* capabilities,
    const coakka_v2_tcp_connection_config_snapshot_t* before,
    connection_strategy_case_result_t* result) {
  coakka_v2_tcp_connection_options_t options =
      make_mode_options(COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL);
  coakka_v2_tcp_connection_apply_result_t apply_result;
  coakka_v2_tcp_connection_config_snapshot_t observed;
  const int tuning_supported =
      (capabilities->effective_capabilities &
       COAKKA_V2_CAPABILITY_TCP_POOL_TUNING) != 0u;

  result->tuning_checked = 1;
  options.fields |= COAKKA_V2_TCP_CONNECTION_FIELD_MAX_CONNECTIONS |
                    COAKKA_V2_TCP_CONNECTION_FIELD_MAX_REQUESTS_PER_CONNECTION |
                    COAKKA_V2_TCP_CONNECTION_FIELD_IDLE_TIMEOUT_MS;
  options.max_connections = 3u;
  options.max_requests_per_connection = 17u;
  options.idle_timeout_ms = 2500u;
  init_apply_result(&apply_result);
  result->tuning_apply_status =
      coakka_v2_runtime_apply_tcp_connection_options_ex(
          runtime, &options, &apply_result);
  result->tuning_apply_reason = apply_result.reason;
  if (read_connection_config(runtime, &observed)) {
    return 1;
  }

  if (tuning_supported) {
    result->tuning_apply_preserved_or_applied_state =
        result->tuning_apply_status == COAKKA_V2_OK &&
        apply_result.changed == 1u &&
        observed.mode == COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL &&
        observed.max_connections == 3u &&
        observed.max_requests_per_connection == 17u &&
        observed.idle_timeout_ms == 2500u;
  } else {
    const int tuning_rejection_status =
        expected_rejection_status(capabilities,
                                  COAKKA_V2_CAPABILITY_TCP_POOL_TUNING);
    result->tuning_apply_preserved_or_applied_state =
        result->tuning_apply_status == tuning_rejection_status &&
        apply_result.changed == 0u && connection_config_equal(before, &observed);
  }
  return result->tuning_apply_preserved_or_applied_state ? 0 : 1;
}

static int run_case(const connection_strategy_case_spec_t* spec,
                    const coakka_v2_runtime_capability_snapshot_t* capabilities,
                    connection_strategy_case_result_t* result) {
  const coakka_v2_runtime_config_t runtime_config = {
      .system_name = "runtime-v2-connection-strategy-evidence",
      .node_id = "runtime-v2-connection-strategy-evidence-node",
      .strict_no_drop = 1,
      .queue_capacity = 32,
  };
  coakka_v2_runtime_t* runtime = NULL;
  coakka_v2_tcp_connection_options_t options = make_mode_options(spec->mode);
  coakka_v2_tcp_connection_options_t invalid_options;
  coakka_v2_tcp_connection_validation_t validation;
  coakka_v2_tcp_connection_apply_result_t apply_result;
  coakka_v2_tcp_connection_config_snapshot_t before_invalid;
  coakka_v2_tcp_connection_config_snapshot_t observed;
  int failed = 0;

  memset(result, 0, sizeof(*result));
  result->mode = spec->mode;
  result->capability = spec->capability;
  result->expected_supported =
      spec->capability == 0u ||
      (capabilities->effective_capabilities & spec->capability) != 0u;

  memset(&validation, 0, sizeof(validation));
  validation.struct_size = sizeof(validation);
  result->validation_checked = 1;
  result->validation_status =
      coakka_v2_runtime_validate_tcp_connection_options(&options, &validation);
  result->validation_code = validation.code;

  runtime = coakka_v2_runtime_create(&runtime_config);
  if (runtime == NULL) {
    return fail_case(result, "runtime.create");
  }

  init_apply_result(&apply_result);
  result->apply_checked = 1;
  result->apply_status = coakka_v2_runtime_apply_tcp_connection_options_ex(
      runtime, &options, &apply_result);
  result->apply_reason = apply_result.reason;
  result->apply_changed = apply_result.changed;
  if (read_connection_config(runtime, &result->effective_after_apply)) {
    failed = fail_case(result, "configuration.read-after-apply");
    goto cleanup;
  }

  if (result->expected_supported) {
    if (result->validation_status != COAKKA_V2_OK ||
        result->validation_code != COAKKA_V2_TCP_CONNECTION_VALID ||
        result->apply_status != COAKKA_V2_OK ||
        result->effective_after_apply.mode != spec->mode) {
      failed = fail_case(result, "configuration.supported-mode-contract");
      goto cleanup;
    }
  } else {
    const int rejection_status =
        expected_rejection_status(capabilities, spec->capability);
    const uint32_t rejection_validation =
        expected_rejection_validation_code(capabilities, spec->capability);
    if (result->validation_status != rejection_status ||
        result->validation_code != rejection_validation ||
        result->apply_status != rejection_status ||
        result->apply_changed != 0u ||
        result->effective_after_apply.mode !=
            COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE) {
      failed = fail_case(result, "configuration.capability-rejection");
      goto cleanup;
    }
  }

  before_invalid = result->effective_after_apply;
  invalid_options = make_mode_options(UINT32_MAX);
  init_apply_result(&apply_result);
  result->invalid_apply_checked = 1;
  result->invalid_apply_status =
      coakka_v2_runtime_apply_tcp_connection_options_ex(
          runtime, &invalid_options, &apply_result);
  result->invalid_apply_reason = apply_result.reason;
  result->invalid_validation_code = apply_result.validation.code;
  if (read_connection_config(runtime, &observed)) {
    failed = fail_case(result, "invalid-apply.read-effective-state");
    goto cleanup;
  }
  result->invalid_apply_preserved_state =
      result->invalid_apply_status == COAKKA_V2_ERR_INVALID_ARG &&
      apply_result.changed == 0u &&
      result->invalid_validation_code ==
          COAKKA_V2_TCP_CONNECTION_UNKNOWN_MODE &&
      connection_config_equal(&before_invalid, &observed);
  if (!result->invalid_apply_preserved_state) {
    failed = fail_case(result, "invalid-apply.atomicity");
    goto cleanup;
  }

  if (spec->mode == COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL &&
      result->expected_supported &&
      run_bounded_pool_tuning_check(runtime, capabilities, &before_invalid,
                                    result)) {
    failed = fail_case(result, "bounded-pool.tuning-contract");
    goto cleanup;
  }

  if (read_connection_config(runtime, &before_invalid)) {
    failed = fail_case(result, "lifecycle.read-before-start");
    goto cleanup;
  }
  {
    coakka_v2_host_handles_t handles;
    coakka_v2_control_snapshot_t snapshot;
    coakka_v2_tcp_connection_options_t started_options;

    init_host_handles(&handles);
    result->host_handles_export_checked = 1;
    result->host_handles_export_status =
        coakka_v2_runtime_get_host_handles(runtime, &handles);
    if (result->host_handles_export_status != COAKKA_V2_OK) {
      close_host_handles(&handles);
      result->host_handles_closed = 1;
      failed = fail_case(result, "lifecycle.export-host-handles");
      goto cleanup;
    }

    memset(&snapshot, 0, sizeof(snapshot));
    snapshot.generation = 1u;
    result->control_snapshot_apply_checked = 1;
    result->control_snapshot_apply_status =
        coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot);
    if (result->control_snapshot_apply_status != COAKKA_V2_OK) {
      close_host_handles(&handles);
      result->host_handles_closed = 1;
      failed = fail_case(result, "lifecycle.apply-control-snapshot");
      goto cleanup;
    }

    result->start_checked = 1;
    result->start_status = coakka_v2_runtime_start(runtime);
    if (result->start_status != COAKKA_V2_OK) {
      close_host_handles(&handles);
      result->host_handles_closed = 1;
      failed = fail_case(result, "lifecycle.start");
      goto cleanup;
    }
    started_options = make_mode_options(before_invalid.mode);
    init_apply_result(&apply_result);
    result->started_apply_checked = 1;
    result->started_apply_status =
        coakka_v2_runtime_apply_tcp_connection_options_ex(
            runtime, &started_options, &apply_result);
    result->started_apply_reason = apply_result.reason;
    if (read_connection_config(runtime, &observed)) {
      failed = fail_case(result, "started-apply.read-effective-state");
    } else {
      result->started_apply_preserved_state =
          result->started_apply_status == COAKKA_V2_ERR_BAD_STATE &&
          result->started_apply_reason ==
              COAKKA_V2_TRANSPORT_APPLY_REASON_RUNTIME_NOT_CONFIGURABLE &&
          apply_result.changed == 0u &&
          connection_config_equal(&before_invalid, &observed);
      if (!result->started_apply_preserved_state) {
        failed = fail_case(result, "started-apply.atomicity");
      }
    }

    result->stop_checked = 1;
    result->stop_status = coakka_v2_runtime_stop(runtime);
    if (result->stop_status != COAKKA_V2_OK) {
      failed = fail_case(result, "lifecycle.stop");
    }
    close_host_handles(&handles);
    result->host_handles_closed = 1;
  }

cleanup:
  coakka_v2_runtime_destroy(runtime);
  result->passed = failed == 0;
  return failed;
}

int connection_strategy_evidence_run(connection_strategy_evidence_t* evidence,
                                     const char** out_error) {
  size_t index;

  if (evidence == NULL || out_error == NULL) {
    return 1;
  }
  memset(evidence, 0, sizeof(*evidence));
  *out_error = NULL;
  evidence->core.struct_size = sizeof(evidence->core);
  if (coakka_v2_runtime_get_core_info(&evidence->core) != COAKKA_V2_OK) {
    *out_error = "runtime_get_core_info failed";
    return 1;
  }

  evidence->case_count = CONNECTION_STRATEGY_EVIDENCE_CASE_COUNT;
  for (index = 0u; index < evidence->case_count; ++index) {
    if (run_case(&k_case_specs[index], &evidence->core.capabilities,
                 &evidence->cases[index]) == 0) {
      ++evidence->passed_case_count;
    }
  }
  if (evidence->passed_case_count != evidence->case_count) {
    *out_error = "one or more connection-strategy invariants failed";
    return 1;
  }
  return 0;
}
