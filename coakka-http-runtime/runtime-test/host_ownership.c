#include "test_support.h"

static int open_owner(test_service_t *owner, test_socket_t *peer) {
  static const char request[] =
      "GET /owner HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  uint16_t port;
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/owner");
  if (!test_service_open(owner, &config, &route, 1U) ||
      coakka_http_host_service_port(owner->service, &port) != COAKKA_HTTP_HOST_OK)
    return 0;
  *peer = test_connect(port);
  return *peer != TEST_INVALID_SOCKET &&
         test_send(*peer, request, sizeof(request) - 1U) && test_next_request(owner);
}

int main(void) {
  test_service_t a, b;
  test_socket_t pa = TEST_INVALID_SOCKET, pb = TEST_INVALID_SOCKET;
  coakka_http_host_response_t response;
  coakka_http_host_drain_state_t state = COAKKA_HTTP_HOST_DRAIN_NOT_STARTED;
  coakka_http_host_exchange_t retired;
  coakka_http_host_service_t *saved;
  char wire[TEST_WIRE_CAPACITY];
  size_t size;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&a);
  test_service_init(&b);
  TEST_CHECK(open_owner(&a, &pa) && open_owner(&b, &pb));
  coakka_http_host_response_init(&response);
  response.body = test_text("wrong owner");
  /* Tokens are opaque Core-issued values. Never reconstruct one from a slot,
   * object address or a locally guessed generation. */
  TEST_CHECK(coakka_http_host_respond(a.service, b.event.exchange, &response) ==
             COAKKA_HTTP_HOST_STALE_EXCHANGE);
  TEST_CHECK(coakka_http_host_respond(b.service, a.event.exchange, &response) ==
             COAKKA_HTTP_HOST_STALE_EXCHANGE);
  retired = a.event.exchange;
  TEST_CHECK(test_respond(&a, 200U, "owner a"));
  TEST_CHECK(test_release(&a));
  TEST_CHECK(test_receive(pa, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "owner a"));
  TEST_CHECK(test_socket_close(&pa));
  /* Retire the REQUEST lease so the reader can make native progress, then
   * borrow its immutable terminal observation. Drain is not lease disposal. */
  TEST_CHECK(test_take(&a, TEST_IO_TIMEOUT_MS) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(a.event.kind == COAKKA_HTTP_HOST_REQUEST_TERMINAL);
  TEST_CHECK(coakka_http_host_service_begin_drain(a.service) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(coakka_http_host_service_wait_drained(a.service, TEST_IO_TIMEOUT_MS,
                                                  &state) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(state == COAKKA_HTTP_HOST_DRAIN_COMPLETE);
  TEST_CHECK(coakka_http_host_service_stop(a.service) == COAKKA_HTTP_HOST_OK);
  saved = a.service;
  /* Native drain completion and lease retirement are different facts. A
   * successful HTTP reply does not permit freeing memory still borrowed here. */
  TEST_CHECK(coakka_http_host_service_destroy(&a.service) != COAKKA_HTTP_HOST_OK);
  TEST_CHECK(a.service == saved && a.lease_live);
  TEST_CHECK(test_release(&a));
  TEST_CHECK(test_service_close(&a));
  TEST_CHECK(open_owner(&a, &pa));
  TEST_CHECK(coakka_http_host_respond(a.service, retired, &response) ==
             COAKKA_HTTP_HOST_STALE_EXCHANGE);
  TEST_CHECK(test_respond(&a, 200U, "new owner a"));
  TEST_CHECK(test_release(&a));
  TEST_CHECK(test_receive(pa, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "new owner a"));
  TEST_CHECK(test_respond(&b, 200U, "owner b"));
  TEST_CHECK(test_release(&b));
  TEST_CHECK(test_receive(pb, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "owner b"));
cleanup:
  if (!test_socket_close(&pa)) cleanup_ok = 0;
  if (!test_socket_close(&pb)) cleanup_ok = 0;
  if (!test_service_close(&a)) cleanup_ok = 0;
  if (!test_service_close(&b)) cleanup_ok = 0;
  return test_finish("lease_and_token_ownership", ok, cleanup_ok);
}
