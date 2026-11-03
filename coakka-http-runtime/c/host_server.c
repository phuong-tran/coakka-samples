#include "../native/host-server/example.h"

#include <stdlib.h>

int main(int argc, char **argv) {
  coakka_http_host_service_t *service = NULL;
  const int streaming = parse_mode(argc, argv);
  if (streaming < 0) return EXIT_FAILURE;
  const int served = open_server(&service, streaming) && serve_one(service, streaming);
  const int closed = check(coakka_http_host_service_destroy(&service), "destroy service");
  return served && closed ? EXIT_SUCCESS : EXIT_FAILURE;
}
