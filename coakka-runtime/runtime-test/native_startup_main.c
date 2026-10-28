#include "native_startup_evidence.h"

#include <string.h>

int main(int argc, char** argv) {
  native_startup_evidence_t evidence;
  int passed;
  (void)argv;
  memset(&evidence, 0, sizeof(evidence));
  if (argc != 1) {
    evidence.failure = "native-startup accepts no arguments";
    passed = 0;
  } else if (!native_startup_image_observe(&evidence.image)) {
    evidence.failure = "loaded runtime image unavailable";
    passed = 0;
  } else {
    passed = native_startup_run(&evidence);
  }
  native_startup_print(&evidence);
  return passed ? 0 : 1;
}
