#include "connection_strategy_evidence.h"
#include "evidence_json.h"

#include <inttypes.h>
#include <stdio.h>

static const char* json_bool(int value) { return value ? "true" : "false"; }

static void print_status_check(const char* name,
                               int checked,
                               int32_t status) {
  printf("\"%s\":{\"checked\":%s", name, json_bool(checked));
  if (checked) {
    printf(",\"status\":%d", status);
  }
  fputc('}', stdout);
}

static void print_connection_config(
    const coakka_v2_tcp_connection_config_snapshot_t* config) {
  printf("{\"defaultsRevision\":%u,\"mode\":%u,\"modeName\":",
         config->defaults_revision, config->mode);
  evidence_json_write_string(stdout, connection_strategy_mode_name(config->mode));
  printf(",\"applicableFields\":%" PRIu64
         ",\"explicitlyConfiguredFields\":%" PRIu64
         ",\"defaultedFields\":%" PRIu64
         ",\"configurableFields\":%" PRIu64
         ",\"maxConnections\":%u"
         ",\"maxRequestsPerConnection\":%" PRIu64
         ",\"idleTimeoutMs\":%" PRIu64 "}",
         config->applicable_fields, config->explicitly_configured_fields,
         config->defaulted_fields, config->configurable_fields,
         config->max_connections, config->max_requests_per_connection,
         config->idle_timeout_ms);
}

static void print_case(const connection_strategy_case_result_t* result) {
  fputs("    {\n", stdout);
  printf("      \"mode\": %u,\n", result->mode);
  fputs("      \"modeName\": ", stdout);
  evidence_json_write_string(stdout, connection_strategy_mode_name(result->mode));
  fputs(",\n", stdout);
  printf("      \"capabilityBit\": %" PRIu64 ",\n", result->capability);
  printf("      \"expectedSupported\": %s,\n",
         json_bool(result->expected_supported));
  fputs("      \"failureStage\": ", stdout);
  evidence_json_write_string(
      stdout, result->failure_stage == NULL ? "none" : result->failure_stage);
  fputs(",\n", stdout);
  if (result->validation_checked) {
    printf("      \"validation\": {\"checked\":true,\"status\":%d,\"code\":%u},\n",
           result->validation_status, result->validation_code);
  } else {
    fputs("      \"validation\": {\"checked\":false},\n", stdout);
  }
  if (result->apply_checked) {
    printf("      \"apply\": {\"checked\":true,\"status\":%d,\"reason\":%u,\"changed\":%u,\"effective\":",
           result->apply_status, result->apply_reason, result->apply_changed);
    print_connection_config(&result->effective_after_apply);
    fputs("},\n", stdout);
  } else {
    fputs("      \"apply\": {\"checked\":false},\n", stdout);
  }
  if (result->invalid_apply_checked) {
    printf("      \"invalidApply\": {\"checked\":true,\"status\":%d,\"reason\":%u,\"validationCode\":%u,\"preservedState\":%s},\n",
           result->invalid_apply_status, result->invalid_apply_reason,
           result->invalid_validation_code,
           json_bool(result->invalid_apply_preserved_state));
  } else {
    fputs("      \"invalidApply\": {\"checked\":false},\n", stdout);
  }
  if (result->tuning_checked) {
    printf("      \"tuningApply\": {\"checked\":true,\"status\":%d,\"reason\":%u,\"preservedOrAppliedState\":%s},\n",
           result->tuning_apply_status, result->tuning_apply_reason,
           json_bool(result->tuning_apply_preserved_or_applied_state));
  } else {
    fputs("      \"tuningApply\": {\"checked\":false},\n", stdout);
  }
  fputs("      \"lifecycle\": {", stdout);
  print_status_check("hostHandlesExport",
                     result->host_handles_export_checked,
                     result->host_handles_export_status);
  fputc(',', stdout);
  print_status_check("controlSnapshotApply",
                     result->control_snapshot_apply_checked,
                     result->control_snapshot_apply_status);
  fputc(',', stdout);
  print_status_check("start", result->start_checked, result->start_status);
  fputc(',', stdout);
  print_status_check("stop", result->stop_checked, result->stop_status);
  printf(",\"hostHandlesClosed\":%s},\n",
         json_bool(result->host_handles_closed));
  if (result->started_apply_checked) {
    printf("      \"startedApply\": {\"checked\":true,\"status\":%d,\"reason\":%u,\"preservedState\":%s},\n",
           result->started_apply_status, result->started_apply_reason,
           json_bool(result->started_apply_preserved_state));
  } else {
    fputs("      \"startedApply\": {\"checked\":false},\n", stdout);
  }
  printf("      \"passed\": %s\n", json_bool(result->passed));
  fputs("    }", stdout);
}

void connection_strategy_evidence_print_json(
    const connection_strategy_evidence_t* evidence,
    const char* status,
    const char* error) {
  size_t index;
  const coakka_v2_runtime_info_t* runtime = &evidence->core.runtime;
  const coakka_v2_runtime_capability_snapshot_t* capabilities =
      &evidence->core.capabilities;

  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.connection-strategy-evidence.v1\",\n");
  printf("  \"status\": ");
  evidence_json_write_string(stdout, status);
  printf(",\n");
  if (error != NULL) {
    printf("  \"error\": ");
    evidence_json_write_string(stdout, error);
    printf(",\n");
  }
  printf("  \"runtime\": {\"abi\":%u,\"version\":",
         runtime->abi_version);
  evidence_json_write_string(stdout, runtime->runtime_version);
  printf(",\"git\":");
  evidence_json_write_string(stdout, runtime->git_commit);
  printf("},\n");
  printf("  \"capabilities\": {\"edition\":%u,\"licenseStatus\":%u,\"compiled\":%" PRIu64
         ",\"entitled\":%" PRIu64 ",\"effective\":%" PRIu64
         ",\"connectionDefaultsRevision\":%u},\n",
         capabilities->edition, capabilities->license_status,
         capabilities->compiled_capabilities,
         capabilities->entitled_capabilities,
         capabilities->effective_capabilities,
         capabilities->tcp_connection_defaults_revision);
  printf("  \"scope\": {\n");
  printf("    \"surface\": \"published native C ABI only\",\n");
  printf("    \"tlsCertificateExercise\": false,\n");
  printf("    \"tlsNote\": \"This lane does not load certificates or claim an active TLS/mTLS handshake.\",\n");
  printf("    \"invariants\": [\"capability-consistent validation\",\"structured atomic apply\",\"invalid apply preserves effective state\",\"started runtime rejects reconfiguration without mutation\"]\n");
  printf("  },\n");
  printf("  \"summary\": {\"caseCount\":%zu,\"passedCaseCount\":%zu},\n",
         evidence->case_count, evidence->passed_case_count);
  printf("  \"cases\": [\n");
  for (index = 0u; index < evidence->case_count; ++index) {
    print_case(&evidence->cases[index]);
    fputs(index + 1u == evidence->case_count ? "\n" : ",\n", stdout);
  }
  printf("  ]\n");
  printf("}\n");
}
