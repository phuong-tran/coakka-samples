#ifndef COAKKA_RUNTIME_NATIVE_CONNECTION_STRATEGY_EVIDENCE_H
#define COAKKA_RUNTIME_NATIVE_CONNECTION_STRATEGY_EVIDENCE_H

#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"

#include <stddef.h>
#include <stdint.h>

enum { CONNECTION_STRATEGY_EVIDENCE_CASE_COUNT = 4 };

typedef struct connection_strategy_case_result_t {
  uint32_t mode;
  uint64_t capability;
  int expected_supported;
  const char* failure_stage;
  int validation_checked;
  int32_t validation_status;
  uint32_t validation_code;
  int apply_checked;
  int32_t apply_status;
  uint32_t apply_reason;
  uint32_t apply_changed;
  coakka_v2_tcp_connection_config_snapshot_t effective_after_apply;
  int invalid_apply_checked;
  int32_t invalid_apply_status;
  uint32_t invalid_apply_reason;
  uint32_t invalid_validation_code;
  int invalid_apply_preserved_state;
  int tuning_checked;
  int32_t tuning_apply_status;
  uint32_t tuning_apply_reason;
  int tuning_apply_preserved_or_applied_state;
  int host_handles_export_checked;
  int32_t host_handles_export_status;
  int control_snapshot_apply_checked;
  int32_t control_snapshot_apply_status;
  int start_checked;
  int32_t start_status;
  int started_apply_checked;
  int32_t started_apply_status;
  uint32_t started_apply_reason;
  int started_apply_preserved_state;
  int stop_checked;
  int32_t stop_status;
  int host_handles_closed;
  int passed;
} connection_strategy_case_result_t;

typedef struct connection_strategy_evidence_t {
  coakka_v2_runtime_core_info_t core;
  connection_strategy_case_result_t
      cases[CONNECTION_STRATEGY_EVIDENCE_CASE_COUNT];
  size_t case_count;
  size_t passed_case_count;
} connection_strategy_evidence_t;

int connection_strategy_evidence_run(connection_strategy_evidence_t* evidence,
                                     const char** out_error);
void connection_strategy_evidence_print_json(
    const connection_strategy_evidence_t* evidence,
    const char* status,
    const char* error);
const char* connection_strategy_mode_name(uint32_t mode);

#endif
