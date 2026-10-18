#include "native_startup_evidence.h"
#include "evidence_platform.h"
#include "coakka/v2/runtime_event_log.h"
#include "coakka/v2/runtime_monitor.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

static coakka_v2_runtime_config_t base_config(void) {
  coakka_v2_runtime_config_t base;
  memset(&base, 0, sizeof(base));
  base.system_name = "native-startup-evidence";
  base.node_id = "native-startup-evidence-node";
  base.queue_capacity = 8;
  base.strict_no_drop = 1;
  return base;
}

static void init_handles(coakka_v2_host_handles_t* handles) {
  memset(handles, 0, sizeof(*handles));
  handles->struct_size = sizeof(*handles);
  handles->request_write_fd = -1;
  handles->response_read_fd = -1;
  handles->deadletter_read_fd = -1;
  handles->control_write_fd = -1;
  handles->monitor_read_fd = -1;
  handles->delivered_request_read_fd = -1;
}

static void close_handles(coakka_v2_host_handles_t* handles) {
  int* channels[] = {
      &handles->request_write_fd, &handles->response_read_fd,
      &handles->deadletter_read_fd, &handles->control_write_fd,
      &handles->monitor_read_fd, &handles->delivered_request_read_fd};
  evidence_platform_close_channels(channels, sizeof(channels) / sizeof(channels[0]));
}

static coakka_v2_status_t read_config(coakka_v2_runtime_t* runtime,
                                     coakka_v2_runtime_startup_config_t* output) {
  memset(output, 0, sizeof(*output));
  output->struct_size = sizeof(*output);
  return coakka_v2_runtime_get_startup_config(runtime, output);
}

/* Compare only contract values: padding is not an effective-config fact. */
static int immutable_equal(const coakka_v2_runtime_startup_config_t* a,
                           const coakka_v2_runtime_startup_config_t* b) {
  return a->defaults_revision == b->defaults_revision &&
      a->defaulted_groups == b->defaulted_groups &&
      a->strict_no_drop == b->strict_no_drop &&
      a->queue_capacity == b->queue_capacity &&
      a->request_max_frame_size == b->request_max_frame_size &&
      a->execution.requested_policy == b->execution.requested_policy &&
      a->execution.supported == b->execution.supported &&
      a->execution.available_cpu_count == b->execution.available_cpu_count &&
      a->execution.selected_cpu_count == b->execution.selected_cpu_count &&
      a->execution.selected_cpu_ids[0] == b->execution.selected_cpu_ids[0] &&
      a->execution.selected_cpu_ids[1] == b->execution.selected_cpu_ids[1] &&
      a->connection.mode == b->connection.mode &&
      a->connection.defaults_revision == b->connection.defaults_revision &&
      a->connection.applicable_fields == b->connection.applicable_fields &&
      a->connection.explicitly_configured_fields == b->connection.explicitly_configured_fields &&
      a->connection.defaulted_fields == b->connection.defaulted_fields &&
      a->connection.configurable_fields == b->connection.configurable_fields &&
      a->connection.max_connections == b->connection.max_connections &&
      a->connection.max_requests_per_connection == b->connection.max_requests_per_connection &&
      a->connection.idle_timeout_ms == b->connection.idle_timeout_ms &&
      a->io.requested == b->io.requested && a->io.compiled == b->io.compiled &&
      a->io.probed == b->io.probed && a->io.supported == b->io.supported &&
      a->io.probe_error == b->io.probe_error &&
      a->monitor.enabled == b->monitor.enabled;
}

static int observe_permission(coakka_v2_runtime_t* runtime, int enabled) {
  coakka_v2_runtime_monitor_snapshot_t monitor;
  coakka_v2_runtime_event_log_snapshot_t events;
  const coakka_v2_status_t expected = enabled ? COAKKA_V2_OK : COAKKA_V2_ERR_FEATURE_UNAVAILABLE;
  coakka_v2_status_t monitor_status, event_status;
  memset(&monitor, 0, sizeof(monitor));
  memset(&events, 0, sizeof(events));
  monitor_status = coakka_v2_runtime_get_monitor_snapshot(runtime, &monitor);
  event_status = coakka_v2_runtime_get_event_log_snapshot(runtime, 0u, 4u, &events);
  coakka_v2_runtime_free_monitor_snapshot(&monitor);
  coakka_v2_runtime_free_event_log_snapshot(&events);
  return monitor_status == expected && event_status == expected;
}

static void run_case(unsigned int index, native_startup_case_t* result) {
  static const char* names[NATIVE_STARTUP_CASE_COUNT] = {
      "omitted-groups", "explicit-off", "monitor-on", "optional-io-on"};
  coakka_v2_runtime_config_t base = base_config();
  coakka_v2_startup_execution_options_t cpu = {sizeof(cpu), COAKKA_V2_STARTUP_CPU_AUTO, 0u};
  coakka_v2_startup_io_options_t io = {sizeof(io), index == 3u ? 1u : 0u, 0u};
  coakka_v2_startup_monitor_options_t monitor = {sizeof(monitor), index == 2u ? 1u : 0u, 0u};
  coakka_v2_runtime_startup_options_t options = {sizeof(options), NULL, NULL, NULL, NULL};
  coakka_v2_runtime_t* runtime = NULL;
  coakka_v2_host_handles_t handles;
  coakka_v2_runtime_startup_config_t copied, invalid, saved;
  coakka_v2_network_options_t network;
  coakka_v2_network_config_snapshot_t network_observed;
  coakka_v2_control_snapshot_t routes;
  coakka_v2_tcp_connection_options_t change;
  uint32_t expected_groups = COAKKA_V2_STARTUP_DEFAULT_EXECUTION |
      COAKKA_V2_STARTUP_DEFAULT_CONNECTION | COAKKA_V2_STARTUP_DEFAULT_IO |
      COAKKA_V2_STARTUP_DEFAULT_MONITOR;
  int started = 0;
  result->name = names[index];
  init_handles(&handles);
  if (index == 1u) {
    options.execution = &cpu; options.io = &io; options.monitor = &monitor;
    expected_groups = COAKKA_V2_STARTUP_DEFAULT_CONNECTION;
  } else if (index == 2u) {
    options.monitor = &monitor;
    expected_groups &= ~COAKKA_V2_STARTUP_DEFAULT_MONITOR;
  } else if (index == 3u) {
    options.io = &io;
    expected_groups &= ~COAKKA_V2_STARTUP_DEFAULT_IO;
  }
#define REQUIRE(condition, message) do { if (!(condition)) { result->failure = message; goto cleanup; } } while (0)
  result->create_checked = 1;
  result->create_status = coakka_v2_runtime_create_native(&base, index == 0u ? NULL : &options, &runtime);
  REQUIRE(result->create_status == COAKKA_V2_OK && runtime != NULL, "create native");
  result->phase_checked[0] = 1;
  result->phase_status[0] = read_config(runtime, &result->phases[0]);
  REQUIRE(result->phase_status[0] == COAKKA_V2_OK, "created effective config");
  REQUIRE(result->phases[0].defaulted_groups == expected_groups &&
      result->phases[0].execution.requested_policy == COAKKA_V2_STARTUP_CPU_AUTO &&
      result->phases[0].connection.mode == COAKKA_V2_TCP_CONNECTION_MULTIPLEXED &&
      result->phases[0].monitor.enabled == (index == 2u ? 1u : 0u) &&
      result->phases[0].io.requested == (index == 3u ? 1u : 0u), "public default and presence contract");
  REQUIRE(result->phases[0].execution.selected_cpu_count <= 2u &&
      result->phases[0].execution.startup_applied == 0u &&
      result->phases[0].io.effective == 0u, "created selection not running");
  if (index != 3u)
    REQUIRE(result->phases[0].io.probed == 0u && result->phases[0].io.supported == 0u &&
        result->phases[0].io.probe_error == 0, "OFF must not probe");
  /* Stack intent dies here logically; changing it cannot alter Core's copy. */
  cpu.cpu_policy = UINT32_MAX; io.use_io_uring = 2u; monitor.enabled = 2u;
  memset(&options, 0, sizeof(options));
  REQUIRE(read_config(runtime, &copied) == COAKKA_V2_OK &&
      immutable_equal(&copied, &result->phases[0]), "input mutation altered accepted config");
  memset(&invalid, 0xa5, sizeof(invalid)); invalid.struct_size = sizeof(invalid) - 1u;
  memcpy(&saved, &invalid, sizeof(saved));
  REQUIRE(coakka_v2_runtime_get_startup_config(runtime, &invalid) == COAKKA_V2_ERR_INVALID_ARG &&
      memcmp(&saved, &invalid, sizeof(saved)) == 0, "short getter output mutated");
  REQUIRE(observe_permission(runtime, index == 2u), "created observation permission");
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
  if (index == 2u) handles.flags |= COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR;
  REQUIRE(coakka_v2_runtime_get_host_handles(runtime, &handles) == COAKKA_V2_OK, "host handles");
  REQUIRE((handles.monitor_read_fd >= 0) == (index == 2u), "monitor export permission");
  memset(&network, 0, sizeof(network)); network.struct_size = sizeof(network);
  network.fields = COAKKA_V2_NETWORK_FIELD_MODE; network.mode = COAKKA_V2_NETWORK_EMBEDDED;
  REQUIRE(coakka_v2_runtime_apply_network_options(runtime, &network) == COAKKA_V2_OK, "explicit embedded network");
  memset(&network_observed, 0, sizeof(network_observed)); network_observed.struct_size = sizeof(network_observed);
  REQUIRE(coakka_v2_runtime_get_network_config(runtime, &network_observed) == COAKKA_V2_OK &&
      network_observed.mode == COAKKA_V2_NETWORK_EMBEDDED && network_observed.explicit_policy == 1u, "network readback");
  memset(&routes, 0, sizeof(routes)); routes.generation = 1u;
  REQUIRE(coakka_v2_runtime_apply_control_snapshot(runtime, &routes) == COAKKA_V2_OK, "empty route snapshot");
  REQUIRE(coakka_v2_runtime_start(runtime) == COAKKA_V2_OK, "start");
  started = 1;
  result->phase_checked[1] = 1;
  result->phase_status[1] = read_config(runtime, &result->phases[1]);
  REQUIRE(result->phase_status[1] == COAKKA_V2_OK &&
      result->phases[1].runtime_state == COAKKA_V2_STATE_STARTED &&
      immutable_equal(&result->phases[0], &result->phases[1]) &&
      result->phases[1].io.effective == 0u, "started effective config");
  memset(&change, 0, sizeof(change)); change.struct_size = sizeof(change);
  change.fields = COAKKA_V2_TCP_CONNECTION_FIELD_MODE; change.mode = COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE;
  REQUIRE(coakka_v2_runtime_apply_tcp_connection_options(runtime, &change) == COAKKA_V2_ERR_BAD_STATE,
      "started connection mutation accepted");
  REQUIRE(coakka_v2_runtime_stop(runtime) == COAKKA_V2_OK, "stop");
  started = 0;
  result->phase_checked[2] = 1;
  result->phase_status[2] = read_config(runtime, &result->phases[2]);
  REQUIRE(result->phase_status[2] == COAKKA_V2_OK &&
      result->phases[2].runtime_state == COAKKA_V2_STATE_STOPPED &&
      immutable_equal(&result->phases[0], &result->phases[2]) &&
      result->phases[2].io.effective == 0u && observe_permission(runtime, index == 2u), "stopped immutable config");
  REQUIRE(coakka_v2_runtime_apply_tcp_connection_options(runtime, &change) == COAKKA_V2_ERR_BAD_STATE,
      "stopped connection mutation accepted");
  result->lifecycle_passed = 1;
  result->passed = 1;
cleanup:
  if (started) (void)coakka_v2_runtime_stop(runtime);
  if (runtime != NULL) coakka_v2_runtime_destroy(runtime);
  close_handles(&handles);
#undef REQUIRE
}

static int rejection_checks(native_startup_evidence_t* evidence) {
  coakka_v2_runtime_config_t base = base_config();
  coakka_v2_startup_monitor_options_t monitor = {sizeof(monitor), 2u, 0u};
  coakka_v2_startup_io_options_t io = {sizeof(io), 2u, 0u};
  coakka_v2_runtime_startup_options_t options = {sizeof(options), NULL, NULL, NULL, &monitor};
  coakka_v2_runtime_t* output;
  unsigned int index;
  for (index = 0u; index < 4u; ++index) {
    if (index == 1u) { options.monitor = NULL; options.io = &io; }
    if (index == 2u) { options.struct_size = offsetof(coakka_v2_runtime_startup_options_t, execution);
      options.execution = (const coakka_v2_startup_execution_options_t*)(uintptr_t)1u; }
    if (index == 3u) { memset(&options, 0, sizeof(options)); options.struct_size = sizeof(options) + 1u; }
    output = (coakka_v2_runtime_t*)(uintptr_t)1u;
    if (coakka_v2_runtime_create_native(&base, &options, &output) != COAKKA_V2_ERR_INVALID_ARG || output != NULL)
      return 0;
    ++evidence->rejection_checks;
  }
  return 1;
}

int native_startup_run(native_startup_evidence_t* evidence) {
  coakka_v2_runtime_config_t base = base_config();
  coakka_v2_runtime_t* legacy;
  coakka_v2_runtime_startup_config_t output, saved;
  unsigned int index;
  /* Main has already recorded the actual loaded image. Preserve that cold
   * attribution while initializing the independent Core contract evidence. */
  memset(&evidence->core, 0, sizeof(evidence->core));
  evidence->core.struct_size = sizeof(evidence->core);
  evidence->core_checked = 1;
  evidence->core_status = coakka_v2_runtime_get_core_info(&evidence->core);
  if (evidence->core_status != COAKKA_V2_OK) { evidence->failure = "Core identity"; return 0; }
  for (index = 0u; index < NATIVE_STARTUP_CASE_COUNT; ++index) {
    run_case(index, &evidence->cases[index]);
    if (!evidence->cases[index].passed) evidence->failure = "native startup case";
  }
  if (!rejection_checks(evidence)) evidence->failure = "invalid constructor output contract";
  legacy = coakka_v2_runtime_create(&base);
  if (legacy == NULL) { evidence->failure = "legacy construction"; return 0; }
  memset(&output, 0xa5, sizeof(output)); output.struct_size = sizeof(output);
  memcpy(&saved, &output, sizeof(saved));
  evidence->legacy_checked = 1;
  evidence->legacy_status = coakka_v2_runtime_get_startup_config(legacy, &output);
  evidence->legacy_output_preserved = memcmp(&output, &saved, sizeof(output)) == 0;
  coakka_v2_runtime_destroy(legacy);
  if (evidence->legacy_status != COAKKA_V2_ERR_FEATURE_UNAVAILABLE || !evidence->legacy_output_preserved)
    evidence->failure = "legacy startup facts must remain unknown";
  return evidence->failure == NULL;
}
