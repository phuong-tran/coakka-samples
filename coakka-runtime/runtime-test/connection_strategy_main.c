#include "connection_strategy_evidence.h"

#include <string.h>

int main(void) {
  connection_strategy_evidence_t evidence;
  const char* error = NULL;
  int status;

  memset(&evidence, 0, sizeof(evidence));
  status = connection_strategy_evidence_run(&evidence, &error);
  connection_strategy_evidence_print_json(
      &evidence, status == 0 ? "pass" : "fail", error);
  return status == 0 ? 0 : 1;
}
