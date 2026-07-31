#ifndef COAKKA_RUNTIME_NATIVE_EVIDENCE_H
#define COAKKA_RUNTIME_NATIVE_EVIDENCE_H

#include "coakka/v2/runtime.h"

#include <stddef.h>
#include <stdint.h>

#define EVIDENCE_TARGET_NAME "samples.runtime.native.evidence.local"

enum {
  EVIDENCE_ROUTE_GENERATION = 1,
};

/* Internal contract for this harness executable; not part of the CoAkka ABI. */
typedef enum evidence_mode_t {
  EVIDENCE_MODE_SMOKE,
  EVIDENCE_MODE_PRESSURE,
  EVIDENCE_MODE_STRESS,
  EVIDENCE_MODE_SOAK,
} evidence_mode_t;

typedef enum evidence_parse_status_t {
  EVIDENCE_PARSE_OK = 0,
  EVIDENCE_PARSE_ERROR = 1,
  EVIDENCE_PARSE_HELP = 2,
} evidence_parse_status_t;

typedef struct evidence_config_t {
  evidence_mode_t mode;
  size_t payload_bytes;
  uint64_t request_limit;
  uint64_t duration_ms;
  size_t queue_capacity;
  uint64_t max_in_flight;
} evidence_config_t;

typedef struct evidence_result_t {
  uint64_t attempted;
  uint64_t submitted;
  uint64_t handler_received;
  uint64_t replies_submitted;
  uint64_t reply_submit_backpressure;
  uint64_t completed;
  uint64_t rejected;
  uint64_t submission_window_ns;
  uint64_t final_drain_ns;
  uint64_t total_elapsed_ns;
  uint64_t runtime_generation;
  uint64_t route_miss_count;
  uint64_t deadletter_count;
  uint64_t queue_rejected_count;
  size_t route_count;
  size_t ingress_queue_capacity;
  size_t ingress_queue_high_watermark;
} evidence_result_t;

const char* evidence_mode_name(evidence_mode_t mode);
const char* evidence_submission_path_name(evidence_mode_t mode);

evidence_parse_status_t evidence_parse_args(int argc,
                                            char** argv,
                                            evidence_config_t* config,
                                            const char** out_error);

int evidence_run(const evidence_config_t* config,
                 coakka_v2_runtime_info_t* runtime_info,
                 evidence_result_t* result,
                 const char** out_error);

void evidence_print_help_json(void);
void evidence_print_result_json(const evidence_config_t* config,
                                const coakka_v2_runtime_info_t* info,
                                const evidence_result_t* result,
                                const char* status,
                                const char* error);

#endif
