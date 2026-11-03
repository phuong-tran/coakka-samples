#include "test_support.h"

#include <string.h>

int main(void) {
  static const char request[] =
      "GET /pressure HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
  test_service_t owner;
  test_socket_t accepted = TEST_INVALID_SOCKET, refused = TEST_INVALID_SOCKET;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_limits_t effective;
  coakka_http_host_response_t bad;
  char wire[TEST_WIRE_CAPACITY];
  size_t size;
  uint16_t port;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  config.limits.max_connections = 4U;
  config.limits.max_active_requests = 1U;
  config.limits.request_queue_capacity = 1U;
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/pressure");
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  coakka_http_host_limits_init(&effective);
  TEST_CHECK(coakka_http_host_service_limits(owner.service, &effective) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(effective.max_active_requests == 1U && effective.request_queue_capacity == 1U);
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  accepted = test_connect(port);
  TEST_CHECK(accepted != TEST_INVALID_SOCKET);
  TEST_CHECK(test_send(accepted, request, sizeof(request) - 1U));
  TEST_CHECK(test_next_request(&owner));
  /* Hold the admitted exchange. This deterministically occupies the accepted
   * active-request ceiling; pressure does not depend on winning a burst race. */
  refused = test_connect(port);
  TEST_CHECK(refused != TEST_INVALID_SOCKET);
  TEST_CHECK(test_send(refused, request, sizeof(request) - 1U));
  TEST_CHECK(test_receive(refused, wire, sizeof(wire), &size));
  TEST_CHECK(size >= 13U && memcmp(wire, "HTTP/1.1 503 ", 13U) == 0);
  TEST_CHECK(test_socket_close(&refused));
  /* A caller-built invalid response is refused without consuming the token.
   * The same captured exchange must still accept a valid response afterward. */
  coakka_http_host_response_init(&bad);
  bad.status_code = 0U;
  TEST_CHECK(coakka_http_host_respond(owner.service, owner.event.exchange, &bad) ==
             COAKKA_HTTP_HOST_INVALID_ARGUMENT);
  TEST_CHECK(test_respond(&owner, 200U, "accepted once"));
  TEST_CHECK(test_release(&owner));
  TEST_CHECK(test_receive(accepted, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "accepted once"));
  TEST_CHECK(test_socket_close(&accepted));
  /* Pressure is temporary admission refusal, not a permanent service close. */
  accepted = test_connect(port);
  TEST_CHECK(accepted != TEST_INVALID_SOCKET);
  TEST_CHECK(test_send(accepted, request, sizeof(request) - 1U));
  TEST_CHECK(test_next_request(&owner));
  TEST_CHECK(test_respond(&owner, 200U, "recovered"));
  TEST_CHECK(test_release(&owner));
  TEST_CHECK(test_receive(accepted, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "recovered"));
cleanup:
  if (!test_socket_close(&accepted)) cleanup_ok = 0;
  if (!test_socket_close(&refused)) cleanup_ok = 0;
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("admission_and_recovery", ok, cleanup_ok);
}
