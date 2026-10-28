#include "native_connection_strategy.h"
#include "evidence_json.h"

#include <inttypes.h>
#include <stdio.h>

static void check(const char* name, const native_connection_check_t* value) {
  printf("\"%s\":", name);
  if (value->checked) printf("%d", (int)value->status);
  else fputs("null", stdout);
}

static void config(const coakka_v2_tcp_connection_config_snapshot_t* value) {
  printf("{\"mode\":%u,\"defaultsRevision\":%u,\"applicableFields\":%" PRIu64
      ",\"explicitlyConfiguredFields\":%" PRIu64 ",\"defaultedFields\":%" PRIu64
      ",\"configurableFields\":%" PRIu64 ",\"maxConnections\":%u"
      ",\"maxRequestsPerConnection\":%" PRIu64 ",\"idleTimeoutMs\":%" PRIu64 "}",
      value->mode, value->defaults_revision, value->applicable_fields,
      value->explicitly_configured_fields, value->defaulted_fields,
      value->configurable_fields, value->max_connections,
      value->max_requests_per_connection, value->idle_timeout_ms);
}

void native_connection_print(const native_connection_evidence_t* evidence) {
  size_t index;
  int passed = evidence->info.checked && evidence->info.status == COAKKA_V2_OK;
  for (index = 0u; index < NATIVE_CONNECTION_CASES; ++index)
    passed = passed && evidence->cases[index].passed;
  printf("{\"schema\":\"coakka.runtime.native.connection-strategy-evidence.v2\",\"status\":\"%s\",",
      passed ? "pass" : "fail");
  check("coreInfoStatus", &evidence->info);
  fputs(",\"runtime\":", stdout);
  if (evidence->info.checked && evidence->info.status == COAKKA_V2_OK) {
    printf("{\"abi\":%u,\"version\":", evidence->core.runtime.abi_version);
    evidence_json_write_string(stdout, evidence->core.runtime.runtime_version);
    fputs(",\"git\":", stdout);
    evidence_json_write_string(stdout, evidence->core.runtime.git_commit);
    fputc('}', stdout);
  } else fputs("null", stdout);
  fputs(",\"factsScope\":\"Core accepted pre-start configuration, not live applied placement\",\"cases\":[", stdout);
  for (index = 0u; index < NATIVE_CONNECTION_CASES; ++index) {
    const native_connection_case_t* item = &evidence->cases[index];
    if (index != 0u) fputc(',', stdout);
    fputs("{\"name\":", stdout);
    if (item->name) evidence_json_write_string(stdout, item->name);
    else fputs("null", stdout);
    printf(",\"passed\":%s,\"failureStage\":", item->passed ? "true" : "false");
    if (item->failure) evidence_json_write_string(stdout, item->failure);
    else fputs("null", stdout);
    fputc(',', stdout); check("validationStatus", &item->validation);
    fputs(",\"validationCode\":", stdout);
    if (item->validation.checked) printf("%u", item->validation_code);
    else fputs("null", stdout);
    fputc(',', stdout); check("createStatus", &item->create);
    fputc(',', stdout); check("startupStatus", &item->startup);
    fputc(',', stdout); check("deliveryStatus", &item->delivery);
    fputc(',', stdout); check("connectionStatus", &item->connection);
    fputs(",\"accepted\":", stdout);
    if (item->startup.checked && item->startup.status == COAKKA_V2_OK) {
      printf("{\"cpuPolicy\":%u,\"startupApplied\":%u,\"defaultedGroups\":%u,\"connection\":",
          item->accepted.execution.requested_policy, item->accepted.execution.startup_applied,
          item->accepted.defaulted_groups);
      config(&item->accepted.connection);
      fputc('}', stdout);
    } else fputs("null", stdout);
    fputs(",\"delivery\":", stdout);
    if (item->delivery.checked && item->delivery.status == COAKKA_V2_OK)
      printf("{\"compiled\":%u,\"requestMode\":%u,\"responseMode\":%u,\"remoteBatchMaxCalls\":%u,\"slotCapacity\":%u,\"byteCapacity\":%" PRIu64 "}",
          item->composition.compiled, item->composition.request_mode,
          item->composition.response_mode, item->composition.remote_batch_max_calls,
          item->composition.slot_capacity, item->composition.byte_capacity);
    else fputs("null", stdout);
    fputs(",\"initialConnection\":", stdout);
    if (item->connection.checked && item->connection.status == COAKKA_V2_OK) config(&item->initial);
    else fputs("null", stdout);
    fputc(',', stdout); check("createdApplyStatus", &item->created_apply);
    fputc(',', stdout); check("invalidApplyStatus", &item->invalid_apply);
    fputc(',', stdout); check("handlesStatus", &item->export_handles);
    fputc(',', stdout); check("snapshotStatus", &item->snapshot);
    fputc(',', stdout); check("startStatus", &item->start);
    fputc(',', stdout); check("startedApplyStatus", &item->started_apply);
    fputc(',', stdout); check("stopStatus", &item->stop);
    fputc('}', stdout);
  }
  fputs("]}\n", stdout);
}

int main(void) {
  native_connection_evidence_t evidence;
  int result = native_connection_run(&evidence);
  native_connection_print(&evidence);
  return result;
}
