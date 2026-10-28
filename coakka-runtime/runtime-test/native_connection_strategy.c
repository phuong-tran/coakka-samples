#include "native_connection_strategy.h"
#include "coakka/v2/control.h"
#include "coakka/v2/transport.h"

#include <string.h>

static int config_equal(const coakka_v2_tcp_connection_config_snapshot_t* a,
                        const coakka_v2_tcp_connection_config_snapshot_t* b) {
  return a->defaults_revision == b->defaults_revision && a->mode == b->mode &&
         a->applicable_fields == b->applicable_fields &&
         a->explicitly_configured_fields == b->explicitly_configured_fields &&
         a->defaulted_fields == b->defaulted_fields &&
         a->configurable_fields == b->configurable_fields &&
         a->max_connections == b->max_connections &&
         a->max_requests_per_connection == b->max_requests_per_connection &&
         a->idle_timeout_ms == b->idle_timeout_ms;
}

static coakka_v2_status_t observe(native_connection_check_t* check,
                                 coakka_v2_status_t status) {
  check->checked = 1;
  check->status = status;
  return status;
}

static int failed(native_connection_case_t* result, const char* stage) {
  if (result->failure == NULL) result->failure = stage;
  return 1;
}

/* Native constructor fixes connection intent. A valid old setter must refuse
 * both before and after start; malformed intent is validated first. Verify
 * BOTH atomic apply-result and independent getter against actual Core truth. */
static int refused_apply(coakka_v2_runtime_t* runtime,
                         const coakka_v2_tcp_connection_config_snapshot_t* before,
                         int invalid, native_connection_check_t* check) {
  coakka_v2_tcp_connection_options_t options = {0};
  coakka_v2_tcp_connection_apply_result_t result = {0};
  coakka_v2_tcp_connection_config_snapshot_t after = {0};
  const coakka_v2_status_t expected =
      invalid ? COAKKA_V2_ERR_INVALID_ARG : COAKKA_V2_ERR_BAD_STATE;
  options.struct_size = sizeof(options);
  options.fields = COAKKA_V2_TCP_CONNECTION_FIELD_MODE;
  options.mode = invalid ? UINT32_MAX : before->mode;
  result.struct_size = sizeof(result);
  after.struct_size = sizeof(after);
  observe(check, coakka_v2_runtime_apply_tcp_connection_options_ex(
                     runtime, &options, &result));
  if (check->status != expected || result.apply_status != expected ||
      result.changed != 0u ||
      (invalid ? result.validation.code != COAKKA_V2_TCP_CONNECTION_UNKNOWN_MODE
               : result.reason !=
                     COAKKA_V2_TRANSPORT_APPLY_REASON_RUNTIME_NOT_CONFIGURABLE) ||
      !config_equal(before, &result.effective_config)) return 1;
  return coakka_v2_runtime_get_tcp_connection_config(runtime, &after) !=
             COAKKA_V2_OK || !config_equal(before, &after);
}

static void run_case(size_t index,
                     const coakka_v2_runtime_capability_snapshot_t* capabilities,
                     native_connection_case_t* result) {
  static const uint32_t modes[] = {
      0u, COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE,
      COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL,
      COAKKA_V2_TCP_CONNECTION_PERSISTENT_SINGLE_FLIGHT,
      COAKKA_V2_TCP_CONNECTION_MULTIPLEXED,
      COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL, UINT32_MAX};
  static const char* const names[] = {
      "omitted", "per-exchange", "bounded-pool", "persistent-single-flight",
      "multiplexed", "explicit-pool-tuning", "invalid-mode"};
  const coakka_v2_runtime_config_t base = {
      .system_name = "native-connection-evidence",
      .node_id = "native-connection-evidence-node",
      .strict_no_drop = 1, .queue_capacity = 32};
  coakka_v2_runtime_t* runtime = NULL;
  coakka_v2_runtime_startup_options_t startup = {0};
  coakka_v2_tcp_connection_options_t connection = {0};
  coakka_v2_tcp_connection_validation_t validation = {0};
  coakka_v2_control_snapshot_t routes = {0};
  coakka_v2_host_handles_t handles = {0};
  coakka_v2_status_t expected = COAKKA_V2_OK;
  uint64_t required = 0u;
  uint32_t expected_validation = COAKKA_V2_TCP_CONNECTION_VALID;
  int started = 0;

  result->name = names[index];
  handles.struct_size = sizeof(handles);
  handles.request_write_fd = handles.response_read_fd = -1;
  handles.deadletter_read_fd = handles.control_write_fd = -1;
  handles.monitor_read_fd = handles.delivered_request_read_fd = -1;
  startup.struct_size = sizeof(startup);
  connection.struct_size = sizeof(connection);
  connection.fields = COAKKA_V2_TCP_CONNECTION_FIELD_MODE;
  connection.mode = modes[index];
  switch (index) {
    case 0u: case 4u: required = COAKKA_V2_CAPABILITY_TCP_MULTIPLEXING; break;
    case 2u: required = COAKKA_V2_CAPABILITY_TCP_BOUNDED_POOL; break;
    case 3u: required = COAKKA_V2_CAPABILITY_TCP_PERSISTENT_SINGLE_FLIGHT; break;
    case 5u: required = COAKKA_V2_CAPABILITY_TCP_BOUNDED_POOL |
                           COAKKA_V2_CAPABILITY_TCP_POOL_TUNING; break;
    default: break;
  }
  if ((capabilities->compiled_capabilities & required) != required) {
    expected = COAKKA_V2_ERR_FEATURE_UNAVAILABLE;
    expected_validation = COAKKA_V2_TCP_CONNECTION_FEATURE_UNAVAILABLE;
  } else if ((capabilities->effective_capabilities & required) != required) {
    expected = COAKKA_V2_ERR_FEATURE_NOT_ENTITLED;
    expected_validation = COAKKA_V2_TCP_CONNECTION_FEATURE_NOT_ENTITLED;
  }
  if (index == 6u) {
    expected = COAKKA_V2_ERR_INVALID_ARG;
    expected_validation = COAKKA_V2_TCP_CONNECTION_UNKNOWN_MODE;
  }
  if (index == 5u) {
    connection.fields |= COAKKA_V2_TCP_CONNECTION_FIELD_MAX_CONNECTIONS |
        COAKKA_V2_TCP_CONNECTION_FIELD_MAX_REQUESTS_PER_CONNECTION |
        COAKKA_V2_TCP_CONNECTION_FIELD_IDLE_TIMEOUT_MS;
    /* Explicit test intent, never a connector-side default table. */
    connection.max_connections = 3u;
    connection.max_requests_per_connection = 17u;
    connection.idle_timeout_ms = 2500u;
  }
  if (index != 0u) {
    startup.connection = &connection;
    validation.struct_size = sizeof(validation);
    observe(&result->validation,
        coakka_v2_runtime_validate_tcp_connection_options(
            &connection, &validation));
    result->validation_code = validation.code;
    if (result->validation.status != expected ||
        validation.code != expected_validation) {
      failed(result, "constructor.capability-validation");
      goto cleanup;
    }
  }
  observe(&result->create, coakka_v2_runtime_create_native(
      &base, index == 0u ? NULL : &startup, &runtime));
  if (result->create.status != expected ||
      ((runtime != NULL) != (expected == COAKKA_V2_OK))) {
    failed(result, "constructor.result");
    goto cleanup;
  }
  if (expected != COAKKA_V2_OK) goto cleanup;

  result->accepted.struct_size = sizeof(result->accepted);
  result->composition.struct_size = sizeof(result->composition);
  result->initial.struct_size = sizeof(result->initial);
  if (observe(&result->startup, coakka_v2_runtime_get_startup_config(
          runtime, &result->accepted)) != COAKKA_V2_OK ||
      observe(&result->delivery, coakka_v2_runtime_get_delivery_config(
          runtime, &result->composition)) != COAKKA_V2_OK ||
      observe(&result->connection, coakka_v2_runtime_get_tcp_connection_config(
          runtime, &result->initial)) != COAKKA_V2_OK) {
    failed(result, "constructor.read-facts");
    goto cleanup;
  }
  if (!config_equal(&result->accepted.connection, &result->initial) ||
      result->accepted.execution.requested_policy != COAKKA_V2_STARTUP_CPU_AUTO ||
      result->accepted.execution.startup_applied != 0u ||
      result->accepted.io.requested != 0u || result->accepted.io.probed != 0u ||
      result->accepted.io.effective != 0u || result->accepted.monitor.enabled != 0u ||
      result->initial.mode != (index == 0u ?
          (uint32_t)COAKKA_V2_TCP_CONNECTION_MULTIPLEXED : modes[index]) ||
      (index == 0u ?
          (result->initial.defaulted_fields & COAKKA_V2_TCP_CONNECTION_FIELD_MODE) == 0u :
          (result->initial.explicitly_configured_fields & connection.fields) != connection.fields)) {
    failed(result, "constructor.accepted-intent");
    goto cleanup;
  }
  if (index == 5u && (result->initial.max_connections != 3u ||
      result->initial.max_requests_per_connection != 17u ||
      result->initial.idle_timeout_ms != 2500u)) {
    failed(result, "constructor.explicit-tuning");
    goto cleanup;
  }
  if (refused_apply(runtime, &result->initial, 0, &result->created_apply) ||
      refused_apply(runtime, &result->initial, 1, &result->invalid_apply)) {
    failed(result, "created.immutable-apply");
    goto cleanup;
  }
  if (observe(&result->export_handles,
          coakka_v2_runtime_get_host_handles(runtime, &handles)) != COAKKA_V2_OK) {
    failed(result, "lifecycle.export-handles");
    goto cleanup;
  }
  routes.generation = 1u;
  if (observe(&result->snapshot,
          coakka_v2_runtime_apply_control_snapshot(runtime, &routes)) != COAKKA_V2_OK ||
      observe(&result->start, coakka_v2_runtime_start(runtime)) != COAKKA_V2_OK) {
    failed(result, "lifecycle.start");
    goto cleanup;
  }
  started = 1;
  if (refused_apply(runtime, &result->initial, 0, &result->started_apply))
    failed(result, "started.immutable-apply");

cleanup:
  /* No client, frame, callback, concurrent host call or borrowed Core value.
   * runtime_destroy explicitly stops if necessary, including a refused stop;
   * still record any stop refusal as failure, never turn cleanup into a pass. */
  if (started && observe(&result->stop,
          coakka_v2_runtime_stop(runtime)) != COAKKA_V2_OK)
    failed(result, "lifecycle.stop");
  coakka_v2_runtime_destroy(runtime);
  coakka_v2_host_handles_close(&handles);
  result->passed = result->failure == NULL;
}

int native_connection_run(native_connection_evidence_t* evidence) {
  size_t index;
  int failures = 0;
  memset(evidence, 0, sizeof(*evidence));
  evidence->core.struct_size = sizeof(evidence->core);
  if (observe(&evidence->info,
          coakka_v2_runtime_get_core_info(&evidence->core)) != COAKKA_V2_OK) return 1;
  for (index = 0u; index < NATIVE_CONNECTION_CASES; ++index) {
    run_case(index, &evidence->core.capabilities, &evidence->cases[index]);
    failures += !evidence->cases[index].passed;
  }
  return failures != 0;
}
