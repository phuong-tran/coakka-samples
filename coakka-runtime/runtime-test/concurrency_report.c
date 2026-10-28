#include "concurrency_evidence.h"
#include "evidence_json.h"
#include "evidence_platform.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>

#ifndef COAKKA_EVIDENCE_TSAN_ENABLED
#define COAKKA_EVIDENCE_TSAN_ENABLED 0
#endif

static const char* json_bool(int value) { return value ? "true" : "false"; }

static void core_count(const concurrency_evidence_result_t* result, uint64_t value) {
  if (!result->native.enabled || result->native.final_stats_known) printf("%" PRIu64, value);
  else printf("null");
}

static const char* operating_system_name(void) {
#if defined(__APPLE__)
  return "macos";
#elif defined(__linux__)
  return "linux";
#elif defined(_WIN32)
  return "windows";
#else
  return "unknown";
#endif
}

static const char* architecture_name(void) {
#if defined(__aarch64__) || defined(_M_ARM64)
  return "aarch64";
#elif defined(__x86_64__) || defined(_M_X64)
  return "x86_64";
#elif defined(__arm__) || defined(_M_ARM)
  return "arm";
#else
  return "unknown";
#endif
}

static const char* build_profile_name(void) {
#ifdef COAKKA_EVIDENCE_BUILD_PROFILE
  return COAKKA_EVIDENCE_BUILD_PROFILE;
#else
  return "unknown";
#endif
}

static const char* compiler_name(void) {
#ifdef COAKKA_EVIDENCE_COMPILER
  return COAKKA_EVIDENCE_COMPILER;
#else
  return "unknown";
#endif
}

static const char* execution_path_name(void) {
  const char* value = getenv("COAKKA_EVIDENCE_EXECUTION_PATH");
  return value != NULL && value[0] != '\0' ? value : "direct";
}

void concurrency_evidence_print_help_json(void) {
  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.concurrency-evidence.help.v1\",\n");
  printf("  \"usage\": \"bash run.sh runtime-test [race|hot-reload] [--threads 4] [--requests 128] [--generations 16] [--lifecycle-iterations 8] [--queue-capacity 1024] [--timeout 30s]\",\n");
  printf("  \"modes\": [\"race\", \"hot-reload\"],\n");
  printf("  \"limits\": {\"threads\":64,\"requestsPerThread\":100000,\"generations\":100000,\"timeoutMs\":600000}\n");
  printf("}\n");
}

void concurrency_evidence_print_json(
    const concurrency_evidence_config_t* config,
    const coakka_v2_runtime_info_t* runtime_info,
    const concurrency_evidence_result_t* result,
    const char* status,
    const char* error) {
  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.concurrency-evidence.%s\",\n", result->native.enabled ? "v2" : "v1");
  printf("  \"status\": ");
  evidence_json_write_string(stdout, status);
  printf(",\n");
  printf("  \"mode\": ");
  evidence_json_write_string(stdout, concurrency_evidence_mode_name(config->mode));
  printf(",\n");
  if (error != NULL) {
    printf("  \"error\": ");
    evidence_json_write_string(stdout, error);
    printf(",\n");
  }
  if (result->failure_stage != NULL) {
    printf("  \"failureStage\": ");
    evidence_json_write_string(stdout, result->failure_stage);
    printf(",\n");
  }
  if (result->native.enabled && !result->native.info_known) {
    printf("  \"runtime\": null,\n");
  } else {
  printf("  \"runtime\": {\"abi\":%u,\"version\":",
         runtime_info->abi_version);
  evidence_json_write_string(stdout, runtime_info->runtime_version);
  printf(",\"git\":");
  evidence_json_write_string(stdout, runtime_info->git_commit);
  printf("},\n");
  }
  if (result->native.enabled) {
    printf("  \"nativeProfile\": {\"factsKnown\":%s,\"factsScope\":\"Core accepted configuration before start, not live applied phase\",",
        json_bool(result->native.facts_known));
    if (result->native.facts_known) {
      printf("\"compiled\":%u,\"requestMode\":%u,\"responseMode\":%u,\"cpuPolicy\":%u,\"connectionMode\":%u,",
          result->native.compiled, result->native.request_mode, result->native.response_mode,
          result->native.cpu_policy, result->native.connection_mode);
    }
    printf("\"producerTicketCapacity\":%zu,\"producerTicketsPublished\":%" PRIu64 ",\"producerTicketsAdopted\":%" PRIu64 ",",
        result->native.producer_ticket_capacity, result->native.producer_tickets_published, result->native.producer_tickets_consumed);
    printf("\"producerObservationsDiscarded\":%" PRIu64 ",", result->native.producer_tickets_discarded);
    printf("\"responseValidation\":{\"payloadMismatches\":%" PRIu64 ",\"projectionFailures\":%" PRIu64 ",\"lastProjectionStatus\":",
        result->native.response_validation.payload_mismatches, result->native.response_validation.projection_failures);
    if (result->native.response_validation.status_known) printf("%d", (int)result->native.response_validation.last_status); else printf("null");
    printf("},");
    printf("\"finalCoreCountersKnown\":%s},\n", json_bool(result->native.final_stats_known));
  }
  printf("  \"environment\": {\"os\":");
  evidence_json_write_string(stdout, operating_system_name());
  printf(",\"arch\":");
  evidence_json_write_string(stdout, architecture_name());
  printf(",\"logicalCpus\":%ld,\"compiler\":",
         evidence_platform_logical_cpu_count());
  evidence_json_write_string(stdout, compiler_name());
  printf(",\"buildProfile\":");
  evidence_json_write_string(stdout, build_profile_name());
  printf(",\"executionPath\":");
  evidence_json_write_string(stdout, execution_path_name());
  printf(",\"readinessWaitBackend\":");
  evidence_json_write_string(stdout, result->native.enabled ? "Core-owned opaque wait" : evidence_platform_wait_backend());
  printf("},\n");
  printf("  \"sanitizer\": {\"consumerHarnessThreadSanitizer\":%s,",
         json_bool(COAKKA_EVIDENCE_TSAN_ENABLED));
  printf("\"coreClaimRequiresInstrumentedRuntime\":true,"
         "\"productionBinaryImplicitlyInstrumented\":false},\n");
  printf("  \"config\": {\"threads\":%zu,\"requestsPerThread\":%" PRIu64
         ",\"generations\":%" PRIu64
         ",\"lifecycleIterationsPerThread\":%" PRIu64
         ",\"queueCapacity\":%zu,\"timeoutMs\":%" PRIu64 "},\n",
         config->thread_count,
         config->requests_per_thread,
         config->generation_count,
         config->lifecycle_iterations_per_thread,
         config->queue_capacity,
         config->timeout_ms);
  printf("  \"scope\": {\n");
  printf("    \"surface\": \"%s\",\n", result->native.enabled ? "public Native C ABI source candidate" : "published native C ABI only");
  printf("    \"scheduler\": \"start-gate plus per-generation producer quotas\",\n");
  printf("    \"snapshotHotReload\": %s,\n",
         json_bool(config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD));
  printf("    \"tlsCredentialReload\": false,\n");
  printf("    \"connectionStrategyReload\": false,\n");
  printf("    \"invariants\": [\"bounded admission is explicit\",\"every admitted workload request reaches one terminal lane\",\"snapshot apply is atomic and monotonic\",\"every accepted generation becomes observable\",\"stale and invalid snapshots preserve active state\",\"destroy occurs only after caller threads join\"]\n");
  printf("  },\n");
  printf("  \"workload\": {\"attempted\":%" PRIu64
         ",\"admitted\":%" PRIu64
         ",\"admissionBackpressure\":%" PRIu64
         ",\"unexpectedSubmitStatuses\":%" PRIu64
         ",\"handlerReceived\":%" PRIu64
         ",\"repliesSubmitted\":%" PRIu64
         ",\"replySubmitBackpressure\":%" PRIu64
         ",\"responses\":%" PRIu64
         ",\"deadletters\":%" PRIu64,
         result->attempted,
         result->admitted,
         result->admission_backpressure,
         result->unexpected_submit_statuses,
         result->handler_received,
         result->replies_submitted,
         result->reply_submit_backpressure,
         result->response_count,
         result->deadletter_count);
  printf(",\"runtimeDeadletters\":"); core_count(result, result->runtime_deadletter_count);
  printf(",\"queueRejected\":"); core_count(result, result->queue_rejected_count);
  printf(",\"routeMisses\":"); core_count(result, result->route_miss_count);
  printf(",\"deliveryFailed\":"); core_count(result, result->delivery_failed_count);
  printf("},\n");
  printf("  \"snapshot\": {\"successfulApplies\":");
  if (!result->native.enabled || result->native.facts_known) printf("%" PRIu64, result->snapshot_apply_successes); else printf("null");
  printf(",\"successfulObservations\":%" PRIu64
         ",\"activeTargetProbePassed\":%s"
         ",\"inactiveTargetProbePassed\":%s",
         result->snapshot_observation_successes,
         json_bool(result->active_target_probe_passed),
         json_bool(result->inactive_target_probe_passed));
  printf(",\"finalGeneration\":"); core_count(result, result->final_generation);
  printf(",\"finalRouteCount\":"); core_count(result, (uint64_t)result->final_route_count);
  printf(",\"staleApplyStatus\":");
  if (!result->native.enabled || result->native.stale_status_known) printf("%d", result->stale_apply_status); else printf("null");
  printf(",\"invalidApplyStatus\":");
  if (!result->native.enabled || result->native.invalid_status_known) printf("%d", result->invalid_apply_status); else printf("null");
  printf(",\"finalRuntimeState\":");
  if (!result->native.enabled || result->native.final_stats_known) printf("%u", result->final_runtime_state); else printf("null");
  printf("},\n");
  printf("  \"stopRace\": {\"attempted\":%" PRIu64
         ",\"admitted\":%" PRIu64
         ",\"backpressure\":%" PRIu64
         ",\"closedObservers\":%" PRIu64
         ",\"unexpectedStatuses\":%" PRIu64 "},\n",
         result->stop_race_attempted,
         result->stop_race_admitted,
         result->stop_race_backpressure,
         result->stop_race_closed,
         result->stop_race_unexpected);
  printf("  \"independentLifecycleRace\": {\"attempted\":%" PRIu64
         ",\"completed\":%" PRIu64
         ",\"failures\":%" PRIu64 "},\n",
         result->lifecycle_attempted,
         result->lifecycle_completed,
         result->lifecycle_failures);
  printf("  \"timing\": {\"clock\":\"monotonic\",\"workloadElapsedMs\":%.3f,\"totalElapsedMs\":%.3f}\n",
         (double)result->workload_elapsed_ns / 1000000.0,
         (double)result->total_elapsed_ns / 1000000.0);
  printf("}\n");
}
