#ifndef COAKKA_RUNTIME_NATIVE_CONCURRENCY_EVIDENCE_H
#define COAKKA_RUNTIME_NATIVE_CONCURRENCY_EVIDENCE_H

#include "coakka/v2/runtime.h"

#include <stddef.h>
#include <stdint.h>

typedef enum concurrency_evidence_mode_t {
  CONCURRENCY_EVIDENCE_MODE_RACE,
  CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD,
} concurrency_evidence_mode_t;

typedef enum concurrency_evidence_parse_status_t {
  CONCURRENCY_EVIDENCE_PARSE_OK = 0,
  CONCURRENCY_EVIDENCE_PARSE_ERROR = 1,
  CONCURRENCY_EVIDENCE_PARSE_HELP = 2,
} concurrency_evidence_parse_status_t;

typedef struct concurrency_evidence_config_t {
  concurrency_evidence_mode_t mode;
  size_t thread_count;
  uint64_t requests_per_thread;
  uint64_t generation_count;
  uint64_t lifecycle_iterations_per_thread;
  size_t queue_capacity;
  uint64_t timeout_ms;
} concurrency_evidence_config_t;

typedef struct concurrency_evidence_result_t {
  uint64_t attempted;
  uint64_t admitted;
  uint64_t admission_backpressure;
  uint64_t unexpected_submit_statuses;
  uint64_t handler_received;
  uint64_t replies_submitted;
  uint64_t reply_submit_backpressure;
  uint64_t response_count;
  uint64_t deadletter_count;
  uint64_t runtime_deadletter_count;
  uint64_t queue_rejected_count;
  uint64_t route_miss_count;
  uint64_t delivery_failed_count;
  uint64_t snapshot_apply_successes;
  uint64_t snapshot_observation_successes;
  int32_t stale_apply_status;
  int32_t invalid_apply_status;
  uint64_t final_generation;
  size_t final_route_count;
  uint32_t final_runtime_state;
  int active_target_probe_passed;
  int inactive_target_probe_passed;
  uint64_t stop_race_attempted;
  uint64_t stop_race_admitted;
  uint64_t stop_race_backpressure;
  uint64_t stop_race_closed;
  uint64_t stop_race_unexpected;
  uint64_t lifecycle_attempted;
  uint64_t lifecycle_completed;
  uint64_t lifecycle_failures;
  uint64_t workload_elapsed_ns;
  uint64_t total_elapsed_ns;
  const char* failure_stage;
} concurrency_evidence_result_t;

const char* concurrency_evidence_mode_name(concurrency_evidence_mode_t mode);

concurrency_evidence_parse_status_t concurrency_evidence_parse_args(
    int argc,
    char** argv,
    concurrency_evidence_config_t* config,
    const char** out_error);

int concurrency_evidence_run(const concurrency_evidence_config_t* config,
                             coakka_v2_runtime_info_t* runtime_info,
                             concurrency_evidence_result_t* result,
                             const char** out_error);

void concurrency_evidence_print_help_json(void);
void concurrency_evidence_print_json(
    const concurrency_evidence_config_t* config,
    const coakka_v2_runtime_info_t* runtime_info,
    const concurrency_evidence_result_t* result,
    const char* status,
    const char* error);

#endif
