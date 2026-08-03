#include "concurrency_evidence.h"

#include <string.h>

int main(int argc, char** argv) {
  concurrency_evidence_config_t config;
  concurrency_evidence_result_t result;
  coakka_v2_runtime_info_t runtime_info;
  const char* error = NULL;
  const concurrency_evidence_parse_status_t parse_status =
      concurrency_evidence_parse_args(argc, argv, &config, &error);
  int run_status;

  memset(&runtime_info, 0, sizeof(runtime_info));
  memset(&result, 0, sizeof(result));
  if (parse_status == CONCURRENCY_EVIDENCE_PARSE_HELP) {
    concurrency_evidence_print_help_json();
    return 0;
  }
  if (parse_status != CONCURRENCY_EVIDENCE_PARSE_OK) {
    concurrency_evidence_print_json(&config,
                                    &runtime_info,
                                    &result,
                                    "fail",
                                    error);
    return 2;
  }
  run_status =
      concurrency_evidence_run(&config, &runtime_info, &result, &error);
  concurrency_evidence_print_json(&config,
                                  &runtime_info,
                                  &result,
                                  run_status == 0 ? "pass" : "fail",
                                  error);
  return run_status == 0 ? 0 : 1;
}
