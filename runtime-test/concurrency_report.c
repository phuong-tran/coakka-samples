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
  printf("  \"schema\": \"coakka.runtime.native.concurrency-evidence.v1\",\n");
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
  printf("  \"runtime\": {\"abi\":%u,\"version\":",
         runtime_info->abi_version);
  evidence_json_write_string(stdout, runtime_info->runtime_version);
  printf(",\"git\":");
  evidence_json_write_string(stdout, runtime_info->git_commit);
  printf("},\n");
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
  evidence_json_write_string(stdout, evidence_platform_wait_backend());
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
  printf("    \"surface\": \"published native C ABI only\",\n");
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
         ",\"deadletters\":%" PRIu64
         ",\"runtimeDeadletters\":%" PRIu64
         ",\"queueRejected\":%" PRIu64
         ",\"routeMisses\":%" PRIu64
         ",\"deliveryFailed\":%" PRIu64 "},\n",
         result->attempted,
         result->admitted,
         result->admission_backpressure,
         result->unexpected_submit_statuses,
         result->handler_received,
         result->replies_submitted,
         result->reply_submit_backpressure,
         result->response_count,
         result->deadletter_count,
         result->runtime_deadletter_count,
         result->queue_rejected_count,
         result->route_miss_count,
         result->delivery_failed_count);
  printf("  \"snapshot\": {\"successfulApplies\":%" PRIu64
         ",\"successfulObservations\":%" PRIu64
         ",\"staleApplyStatus\":%d,\"invalidApplyStatus\":%d"
         ",\"finalGeneration\":%" PRIu64
         ",\"finalRouteCount\":%zu,\"finalRuntimeState\":%u"
         ",\"activeTargetProbePassed\":%s"
         ",\"inactiveTargetProbePassed\":%s},\n",
         result->snapshot_apply_successes,
         result->snapshot_observation_successes,
         result->stale_apply_status,
         result->invalid_apply_status,
         result->final_generation,
         result->final_route_count,
         result->final_runtime_state,
         json_bool(result->active_target_probe_passed),
         json_bool(result->inactive_target_probe_passed));
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
