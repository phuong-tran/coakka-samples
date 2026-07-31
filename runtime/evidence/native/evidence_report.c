#include "evidence.h"
#include "evidence_platform.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>

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
  return value != NULL && *value != '\0' ? value : "direct";
}

static long logical_cpu_count(void) {
  return evidence_platform_logical_cpu_count();
}

static void print_json_string(const char* text) {
  const unsigned char* cursor = (const unsigned char*)text;
  putchar('"');
  while (*cursor != '\0') {
    if (*cursor == '"' || *cursor == '\\') {
      putchar('\\');
      putchar((int)*cursor);
    } else if (*cursor == '\n') {
      fputs("\\n", stdout);
    } else if (*cursor == '\r') {
      fputs("\\r", stdout);
    } else if (*cursor == '\t') {
      fputs("\\t", stdout);
    } else if (*cursor < 0x20u) {
      printf("\\u%04x", (unsigned int)*cursor);
    } else {
      putchar((int)*cursor);
    }
    ++cursor;
  }
  putchar('"');
}

void evidence_print_help_json(void) {
  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.evidence.help.v1\",\n");
  printf("  \"usage\": \"bash run.sh runtime/evidence/native [smoke|pressure|stress|soak] [--payload 64K] [--requests 128] [--duration 10s] [--queue-capacity 1024] [--max-in-flight 64]\",\n");
  printf("  \"payloadPresets\": [\"64K\", \"128K\", \"256K\", \"512K\", \"1M\", \"2M\", \"3M\"],\n");
  printf("  \"pressurePayloadLimit\": \"16K\",\n");
  printf("  \"requestLimitMax\": 500000\n");
  printf("}\n");
}

void evidence_print_result_json(const evidence_config_t* config,
                                const coakka_v2_runtime_info_t* info,
                                const evidence_result_t* result,
                                const char* status,
                                const char* error) {
  const double submission_window_ms =
      (double)result->submission_window_ns / 1000000.0;
  const double final_drain_ms =
      (double)result->final_drain_ns / 1000000.0;
  const double total_elapsed_ms =
      (double)result->total_elapsed_ns / 1000000.0;
  double completed_round_trips_per_second = 0.0;
  double terminal_outcomes_per_second = 0.0;
  double completed_payload_mib_per_second = 0.0;
  double completed_round_trip_payload_mib_per_second = 0.0;

  /*
   * Throughput is scoped to the complete batch window. It includes envelope
   * build and final drain and deliberately does not claim latency percentiles.
   */
  if (result->total_elapsed_ns > 0u) {
    const double total_seconds =
        (double)result->total_elapsed_ns / 1000000000.0;
    completed_round_trips_per_second =
        (double)result->completed / total_seconds;
    terminal_outcomes_per_second =
        (double)(result->completed + result->rejected) / total_seconds;
    completed_payload_mib_per_second =
        ((double)result->completed * (double)config->payload_bytes) /
        (1024.0 * 1024.0 * total_seconds);
    completed_round_trip_payload_mib_per_second =
        completed_payload_mib_per_second * 2.0;
  }

  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.evidence.v1\",\n");
  printf("  \"status\": ");
  print_json_string(status);
  printf(",\n");
  printf("  \"mode\": ");
  print_json_string(evidence_mode_name(config->mode));
  printf(",\n");
  if (error != NULL) {
    printf("  \"error\": ");
    print_json_string(error);
    printf(",\n");
  }
  printf("  \"runtime\": {\n");
  printf("    \"abi\": %u,\n", info->abi_version);
  printf("    \"version\": ");
  print_json_string(info->runtime_version != NULL ? info->runtime_version : "");
  printf(",\n");
  printf("    \"git\": ");
  print_json_string(info->git_commit != NULL ? info->git_commit : "");
  printf("\n");
  printf("  },\n");
  printf("  \"environment\": {\n");
  printf("    \"os\": ");
  print_json_string(operating_system_name());
  printf(",\n");
  printf("    \"arch\": ");
  print_json_string(architecture_name());
  printf(",\n");
  printf("    \"logicalCpus\": %ld,\n", logical_cpu_count());
  printf("    \"buildProfile\": ");
  print_json_string(build_profile_name());
  printf(",\n");
  printf("    \"compiler\": ");
  print_json_string(compiler_name());
  printf(",\n");
  printf("    \"executionPath\": ");
  print_json_string(execution_path_name());
  printf("\n");
  printf("  },\n");
  printf("  \"config\": {\n");
  printf("    \"submissionPath\": ");
  print_json_string(evidence_submission_path_name(config->mode));
  printf(",\n");
  printf("    \"payloadBytes\": %zu,\n", config->payload_bytes);
  printf("    \"requestLimit\": %" PRIu64 ",\n", config->request_limit);
  printf("    \"durationMs\": %" PRIu64 ",\n", config->duration_ms);
  printf("    \"queueCapacity\": %zu,\n", config->queue_capacity);
  printf("    \"maxInFlight\": %" PRIu64 ",\n", config->max_in_flight);
  printf("    \"strictNoDrop\": true\n");
  printf("  },\n");
  printf("  \"target\": {\n");
  printf("    \"name\": \"%s\",\n", EVIDENCE_TARGET_NAME);
  printf("    \"routeStrategy\": \"single-owner\",\n");
  printf("    \"endpointKind\": \"local\",\n");
  printf("    \"requestPath\": \"caller-build-request -> %s -> bounded-ingress -> route-snapshot(generation=%d) -> target(%s) -> local-handler-handoff -> echo-handler\",\n",
         evidence_submission_path_name(config->mode),
         EVIDENCE_ROUTE_GENERATION,
         EVIDENCE_TARGET_NAME);
  printf("    \"replyPath\": \"echo-handler -> build-reply -> native-submit -> terminal-response-routing -> caller-response-drain\"\n");
  printf("  },\n");
  printf("  \"result\": {\n");
  printf("    \"attempted\": %" PRIu64 ",\n", result->attempted);
  printf("    \"submitted\": %" PRIu64 ",\n", result->submitted);
  printf("    \"handlerReceived\": %" PRIu64 ",\n", result->handler_received);
  printf("    \"repliesSubmitted\": %" PRIu64 ",\n", result->replies_submitted);
  printf("    \"replySubmitBackpressure\": %" PRIu64 ",\n",
         result->reply_submit_backpressure);
  printf("    \"completed\": %" PRIu64 ",\n", result->completed);
  printf("    \"rejected\": %" PRIu64 ",\n", result->rejected);
  printf("    \"terminalOutcomes\": %" PRIu64 ",\n",
         result->completed + result->rejected);
  printf("    \"deadletters\": %" PRIu64 ",\n", result->deadletter_count);
  printf("    \"queueRejected\": %" PRIu64 ",\n",
         result->queue_rejected_count);
  printf("    \"routeMisses\": %" PRIu64 ",\n", result->route_miss_count);
  printf("    \"routeGeneration\": %" PRIu64 ",\n",
         result->runtime_generation);
  printf("    \"routeCount\": %zu,\n", result->route_count);
  printf("    \"ingressQueueCapacity\": %zu,\n",
         result->ingress_queue_capacity);
  printf("    \"ingressQueueHighWatermark\": %zu\n",
         result->ingress_queue_high_watermark);
  printf("  },\n");
  printf("  \"timing\": {\n");
  printf("    \"clock\": \"monotonic\",\n");
  printf("    \"startsBefore\": \"first request envelope build\",\n");
  printf("    \"endsAfter\": \"final response/deadletter drain\",\n");
  printf("    \"submissionWindowMs\": %.3f,\n", submission_window_ms);
  printf("    \"finalDrainMs\": %.3f,\n", final_drain_ms);
  printf("    \"totalElapsedMs\": %.3f\n", total_elapsed_ms);
  printf("  },\n");
  printf("  \"throughput\": {\n");
  printf("    \"completedRoundTripsPerSecond\": %.3f,\n",
         completed_round_trips_per_second);
  printf("    \"terminalOutcomesPerSecond\": %.3f,\n",
         terminal_outcomes_per_second);
  printf("    \"completedRequestPayloadMiBPerSecond\": %.3f,\n",
         completed_payload_mib_per_second);
  printf("    \"completedRoundTripPayloadMiBPerSecond\": %.3f,\n",
         completed_round_trip_payload_mib_per_second);
  printf("    \"scope\": \"local native public-ABI request/reply path on this machine\"\n");
  printf("  },\n");
  printf("  \"notes\": [\n");
  printf("    \"This is a repeatable local scenario, not a cross-machine benchmark or production SLO.\",\n");
  printf("    \"Completed throughput includes request build, runtime delivery, echo reply build, terminal response routing, and final drain.\",\n");
  printf("    \"The harness uses only the published native C ABI and emits no per-request output.\",\n");
  printf("    \"Prefer a controlled Linux host that resembles the deployment worker for deployment-oriented measurements.\",\n");
  printf("    \"Treat Docker, CI, UTM, and other VM throughput as portability evidence only.\"\n");
  printf("  ]\n");
  printf("}\n");
}
