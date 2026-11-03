#include "test_support.h"

/* Optional libFuzzer entrypoint: length-delimited untrusted wire never becomes
 * a C string. Expected values are bounded fixture literals. */
int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size);
int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  (void)test_wire_response((const char *)data, size, 200U, "abcde");
  (void)test_wire_chunked((const char *)data, size, "abcde", "x-finished", "yes");
  return 0;
}
