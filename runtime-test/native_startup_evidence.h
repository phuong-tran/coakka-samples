#ifndef COAKKA_NATIVE_STARTUP_EVIDENCE_H
#define COAKKA_NATIVE_STARTUP_EVIDENCE_H

#include "coakka/v2/runtime_startup.h"

enum { NATIVE_STARTUP_CASE_COUNT = 4, NATIVE_STARTUP_PHASE_COUNT = 3 };

/* Consumer artifact attribution, not a Core capability or platform probe. */
typedef struct native_startup_image_t {
  int checked;
  char path[4096];
} native_startup_image_t;

/* Fixed consumer evidence only; no Core state/layout is reproduced here. */
typedef struct native_startup_case_t {
  const char* name;
  const char* failure;
  int create_checked;
  coakka_v2_status_t create_status;
  int phase_checked[NATIVE_STARTUP_PHASE_COUNT];
  coakka_v2_status_t phase_status[NATIVE_STARTUP_PHASE_COUNT];
  coakka_v2_runtime_startup_config_t phases[NATIVE_STARTUP_PHASE_COUNT];
  int lifecycle_passed;
  int passed;
} native_startup_case_t;

typedef struct native_startup_evidence_t {
  native_startup_image_t image;
  coakka_v2_runtime_core_info_t core;
  int core_checked;
  coakka_v2_status_t core_status;
  native_startup_case_t cases[NATIVE_STARTUP_CASE_COUNT];
  unsigned int rejection_checks;
  int legacy_checked;
  coakka_v2_status_t legacy_status;
  int legacy_output_preserved;
  const char* failure;
} native_startup_evidence_t;

int native_startup_run(native_startup_evidence_t* evidence);
int native_startup_image_observe(native_startup_image_t* image);
void native_startup_print(const native_startup_evidence_t* evidence);

#endif
