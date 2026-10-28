#include "native_startup_evidence.h"
#include "evidence_json.h"

#include <inttypes.h>
#include <stdio.h>

static const char* boolean(int value) { return value ? "true" : "false"; }

static void print_config(const coakka_v2_runtime_startup_config_t* config) {
  const coakka_v2_startup_cpu_info_t* cpu = &config->execution;
  const coakka_v2_startup_io_info_t* io = &config->io;
  unsigned int index;
  printf("{\"defaultsRevision\":%u,\"defaultedGroups\":%u,\"state\":%u,"
      "\"strictNoDrop\":%d,\"queueCapacity\":%u,\"requestMaxFrameSize\":%" PRIu64
      ",\"execution\":{\"requestedPolicy\":%u,\"supported\":%u,"
      "\"availableCpuCount\":%u,\"selectedCpuCount\":%u,\"selectedCpuIds\":[",
      config->defaults_revision, config->defaulted_groups, config->runtime_state,
      config->strict_no_drop, config->queue_capacity, config->request_max_frame_size,
      cpu->requested_policy, cpu->supported, cpu->available_cpu_count, cpu->selected_cpu_count);
  for (index = 0u; index < 2u; ++index) {
    if (index != 0u) fputc(',', stdout);
    if (cpu->selected_cpu_ids[index] == UINT32_MAX) fputs("null", stdout);
    else printf("%u", cpu->selected_cpu_ids[index]);
  }
  printf("],\"startupApplied\":%u},\"connection\":{\"mode\":%u,"
      "\"defaultsRevision\":%u,\"applicableFields\":%" PRIu64
      ",\"explicitFields\":%" PRIu64 ",\"defaultedFields\":%" PRIu64
      ",\"configurableFields\":%" PRIu64 ",\"maxConnections\":%u,"
      "\"maxRequestsPerConnection\":%" PRIu64 ",\"idleTimeoutMs\":%" PRIu64
      "},\"io\":{\"requested\":%u,\"compiled\":%u,\"probed\":%u,"
      "\"supported\":%u,\"effective\":%u,\"probeError\":%" PRId32
      "},\"monitor\":{\"enabled\":%u}}",
      cpu->startup_applied, config->connection.mode, config->connection.defaults_revision,
      config->connection.applicable_fields, config->connection.explicitly_configured_fields,
      config->connection.defaulted_fields, config->connection.configurable_fields,
      config->connection.max_connections, config->connection.max_requests_per_connection,
      config->connection.idle_timeout_ms, io->requested, io->compiled, io->probed,
      io->supported, io->effective, io->probe_error, config->monitor.enabled);
}

void native_startup_print(const native_startup_evidence_t* evidence) {
  unsigned int index, phase;
  printf("{\n  \"schema\":\"coakka.runtime.native.startup-evidence.v1\",\n  \"status\":");
  evidence_json_write_string(stdout, evidence->failure == NULL ? "pass" : "fail");
  fputs(",\n  \"error\":", stdout);
  if (evidence->failure != NULL) evidence_json_write_string(stdout, evidence->failure);
  else fputs("null", stdout);
  printf(",\n  \"artifact\":{\"checked\":%s", boolean(evidence->image.checked));
  if (evidence->image.checked) {
    fputs(",\"loadedImage\":", stdout);
    evidence_json_write_string(stdout, evidence->image.path);
  }
  fputc('}', stdout);
  printf(",\n  \"runtime\":{\"checked\":%s", boolean(evidence->core_checked));
  if (evidence->core_checked) printf(",\"status\":%d", evidence->core_status);
  if (evidence->core_checked && evidence->core_status == COAKKA_V2_OK) {
    const coakka_v2_runtime_core_info_t* core = &evidence->core;
    printf(",\"abi\":%u,\"version\":", core->runtime.abi_version);
    evidence_json_write_string(stdout, core->runtime.runtime_version);
    fputs(",\"git\":", stdout); evidence_json_write_string(stdout, core->runtime.git_commit);
    fputs(",\"buildId\":", stdout); evidence_json_write_string(stdout, core->runtime.build_id);
    fputs(",\"buildProfile\":", stdout); evidence_json_write_string(stdout, core->distribution.build_profile);
    fputs(",\"releaseChannel\":", stdout); evidence_json_write_string(stdout, core->distribution.release_channel);
    printf(",\"compiledCapabilities\":%" PRIu64 ",\"effectiveCapabilities\":%" PRIu64,
        core->capabilities.compiled_capabilities, core->capabilities.effective_capabilities);
  }
  fputs("},\n  \"scope\":{\"sourceCandidate\":true,\"publicCAbiOnly\":true,"
      "\"networkTraffic\":false,\"activeReceiverExercise\":false,"
      "\"benchmark\":false,\"coreSanitizerInstrumentationAsserted\":false},\n", stdout);
  printf("  \"invalidConstructorChecks\":%u,\n  \"legacyStartup\":{\"checked\":%s",
      evidence->rejection_checks, boolean(evidence->legacy_checked));
  if (evidence->legacy_checked)
    printf(",\"status\":%d,\"outputPreserved\":%s",
        evidence->legacy_status, boolean(evidence->legacy_output_preserved));
  fputs("},\n  \"cases\":[\n", stdout);
  for (index = 0u; index < NATIVE_STARTUP_CASE_COUNT; ++index) {
    const native_startup_case_t* item = &evidence->cases[index];
    fputs("    {\"name\":", stdout); evidence_json_write_string(stdout, item->name);
    printf(",\"passed\":%s,\"lifecyclePassed\":%s,\"create\":{\"checked\":%s",
        boolean(item->passed), boolean(item->lifecycle_passed), boolean(item->create_checked));
    if (item->create_checked) printf(",\"status\":%d", item->create_status);
    fputs("},\"failure\":", stdout);
    if (item->failure != NULL) evidence_json_write_string(stdout, item->failure);
    else fputs("null", stdout);
    fputs(",\"phases\":[", stdout);
    for (phase = 0u; phase < NATIVE_STARTUP_PHASE_COUNT; ++phase) {
      printf("%s{\"checked\":%s", phase == 0u ? "" : ",", boolean(item->phase_checked[phase]));
      if (item->phase_checked[phase]) {
        printf(",\"status\":%d", item->phase_status[phase]);
        if (item->phase_status[phase] == COAKKA_V2_OK) {
          fputs(",\"effective\":", stdout); print_config(&item->phases[phase]);
        }
      }
      fputc('}', stdout);
    }
    printf("]}%s\n", index + 1u == NATIVE_STARTUP_CASE_COUNT ? "" : ",");
  }
  fputs("  ]\n}\n", stdout);
}
