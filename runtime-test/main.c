#include "evidence.h"

#include <string.h>

/*
 * Keep the process entrypoint limited to orchestration. Configuration,
 * runtime ownership, and report serialization each live in their own module.
 */
int main(int argc, char** argv) {
  evidence_config_t config;
  evidence_result_t result;
  coakka_v2_runtime_info_t runtime_info;
  const char* error = NULL;
  const evidence_parse_status_t parse_status =
      evidence_parse_args(argc, argv, &config, &error);
  int run_status;

  if (parse_status == EVIDENCE_PARSE_HELP) {
    evidence_print_help_json();
    return 0;
  }
  if (parse_status != EVIDENCE_PARSE_OK) {
    memset(&runtime_info, 0, sizeof(runtime_info));
    memset(&result, 0, sizeof(result));
    evidence_print_result_json(&config,
                               &runtime_info,
                               &result,
                               "fail",
                               error);
    return 2;
  }

  run_status = evidence_run(&config, &runtime_info, &result, &error);
  evidence_print_result_json(&config,
                             &runtime_info,
                             &result,
                             run_status == 0 ? "pass" : "fail",
                             error);
  return run_status == 0 ? 0 : 1;
}
