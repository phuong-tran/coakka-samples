#include "test_support.h"

#include <string.h>

/* Malformed/truncated wire must fail the fixture assertion. Otherwise a broken
 * runtime response could masquerade as a passing public contract test. */
int main(void) {
  static const char valid[] =
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
      "2\r\nab\r\n3\r\ncde\r\n0\r\nX-Finished: yes\r\n\r\n";
  static const char *const invalid[] = {
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nFFFFFFFFFFFFFFFFF\r\n",
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nabcdeXX0\r\nX-Finished: yes\r\n\r\n",
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 5\r\n\r\n5\r\nabcde\r\n0\r\nX-Finished: yes\r\n\r\n",
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nabcde\r\n0\r\nX-Finished: no\r\n\r\n",
      "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nabcde\r\n0\r\nX-Finished: yes\r\n\r\nextra"
  };
  size_t i;
  if (!test_wire_chunked(valid, sizeof(valid) - 1U, "abcde", "x-finished", "yes")) return 1;
  for (i = 0U; i < sizeof(valid) - 1U; ++i)
    if (test_wire_chunked(valid, i, "abcde", "x-finished", "yes")) return 1;
  for (i = 0U; i < sizeof(invalid) / sizeof(invalid[0]); ++i)
    if (test_wire_chunked(invalid[i], strlen(invalid[i]), "abcde", "x-finished", "yes")) return 1;
  if (test_wire_chunked(valid, SIZE_MAX, "abcde", "x-finished", "yes") ||
      test_wire_response(valid, SIZE_MAX, 200U, "abcde") ||
      test_wire_response(NULL, 0U, 200U, "") ||
      test_wire_chunked(NULL, 0U, "", "x", "y")) return 1;
  (void)puts("{\"case\":\"fixture_wire_rejection\",\"ok\":true}");
  return 0;
}
