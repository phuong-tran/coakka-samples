/* Finite logical-target example with one application request/terminal reader.
 * See the shared recipe for explicit lease release and Core-owned routing. */
#include "../native/host-outbound/example.h"

#include <stdlib.h>

int main(void) {
  coakka_http_host_service_t *upstream = NULL;
  coakka_http_host_service_t *client = NULL;
  int ok = sample_open_upstream(&upstream) &&
           sample_open_client(upstream, &client) &&
           sample_run(upstream, client);

  /* Destroy stops and joins Core. Destroy the client first, including on
   * startup failure. A refused destroy must fail the sample. */
  if (!sample_check(coakka_http_host_service_destroy(&client), "destroy client"))
    ok = 0;
  if (!sample_check(coakka_http_host_service_destroy(&upstream), "destroy upstream"))
    ok = 0;
  if (!ok) return EXIT_FAILURE;
  puts("host-outbound-smoke=pass");
  return EXIT_SUCCESS;
}
