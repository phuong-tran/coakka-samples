#ifndef COAKKA_NATIVE_CONNECTION_STRATEGY_H
#define COAKKA_NATIVE_CONNECTION_STRATEGY_H

#include "coakka/v2/runtime_startup.h"

enum { NATIVE_CONNECTION_CASES = 7 };

/* Private finite consumer evidence, not an installed ABI. A checked status
 * records the Core call; only successful getters make the snapshot known. */
typedef struct native_connection_check_t {
  int checked;
  coakka_v2_status_t status;
} native_connection_check_t;

typedef struct native_connection_case_t {
  const char* name;
  const char* failure;
  native_connection_check_t validation;
  uint32_t validation_code;
  native_connection_check_t create;
  native_connection_check_t startup;
  native_connection_check_t delivery;
  native_connection_check_t connection;
  coakka_v2_runtime_startup_config_t accepted;
  coakka_v2_runtime_delivery_config_t composition;
  coakka_v2_tcp_connection_config_snapshot_t initial;
  native_connection_check_t created_apply;
  native_connection_check_t invalid_apply;
  native_connection_check_t export_handles;
  native_connection_check_t snapshot;
  native_connection_check_t start;
  native_connection_check_t started_apply;
  native_connection_check_t stop;
  int passed;
} native_connection_case_t;

typedef struct native_connection_evidence_t {
  native_connection_check_t info;
  coakka_v2_runtime_core_info_t core;
  native_connection_case_t cases[NATIVE_CONNECTION_CASES];
} native_connection_evidence_t;

int native_connection_run(native_connection_evidence_t* evidence);
void native_connection_print(const native_connection_evidence_t* evidence);

#endif
