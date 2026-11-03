#include "test_support.h"

#include <string.h>

int main(void) {
  const char wire_request[] =
      "POST /hello/encoded%20name?name=first&name=second&flag HTTP/1.1\r\n"
      "Host: localhost\r\nContent-Length: 5\r\nX-Request: kept\r\n"
      "Connection: close\r\n\r\nhello";
  test_service_t owner;
  test_socket_t peer = TEST_INVALID_SOCKET;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_request_view_t view;
  coakka_http_host_path_parameter_t path;
  coakka_http_host_query_parameter_t query;
  coakka_http_host_response_t response;
  coakka_http_host_header_t header;
  char method[] = "POST";
  char pattern[] = "/hello/{name}";
  char response_body[] = "owned reply";
  char response_wire[TEST_WIRE_CAPACITY];
  size_t received = 0U;
  uint16_t port = 0U;
  int ok = 1;
  int cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = UINT64_C(41);
  route.handler_binding_id = UINT64_C(73);
  route.method = test_text(method);
  route.encoded_path_pattern = test_text(pattern);
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  /* Create borrows the declaration only for that call. Overwriting the caller
   * buffers before traffic proves the installed library copied its route. */
  memset(method, '?', sizeof(method) - 1U);
  memset(pattern, '?', sizeof(pattern) - 1U);
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(port != 0U);
  peer = test_connect(port);
  TEST_CHECK(peer != TEST_INVALID_SOCKET);
  TEST_CHECK(test_send(peer, wire_request, sizeof(wire_request) - 1U));
  TEST_CHECK(test_next_request(&owner));
  coakka_http_host_request_view_init(&view);
  TEST_CHECK(coakka_http_host_request_view(&owner.event, &view) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(view.route_id == UINT64_C(41));
  TEST_CHECK(owner.event.handler_binding_id == UINT64_C(73));
  TEST_CHECK(test_bytes_equal(view.method, "POST"));
  TEST_CHECK(test_bytes_equal(view.body, "hello"));
  TEST_CHECK(view.path_parameter_count == 1U && view.query_parameter_count == 3U);
  TEST_CHECK(coakka_http_host_request_path_parameter(&owner.event, 0U, &path));
  TEST_CHECK(test_bytes_equal(path.name, "name"));
  TEST_CHECK(test_bytes_equal(path.encoded_value, "encoded%20name"));
  TEST_CHECK(coakka_http_host_request_query_parameter(&owner.event, 0U, &query));
  TEST_CHECK(query.has_value && test_bytes_equal(query.encoded_value, "first"));
  TEST_CHECK(coakka_http_host_request_query_parameter(&owner.event, 1U, &query));
  TEST_CHECK(query.has_value && test_bytes_equal(query.encoded_value, "second"));
  TEST_CHECK(coakka_http_host_request_query_parameter(&owner.event, 2U, &query));
  TEST_CHECK(!query.has_value && test_bytes_equal(query.encoded_key, "flag"));
  coakka_http_host_response_init(&response);
  response.status_code = 201U;
  header.name = test_text("X-Response");
  header.value = test_text("kept");
  response.headers = &header;
  response.header_count = 1U;
  response.body = test_text(response_body);
  TEST_CHECK(coakka_http_host_respond(owner.service, owner.event.exchange,
                                      &response) == COAKKA_HTTP_HOST_OK);
  /* Submission copies response data independently of the request-event lease.
   * Neither this stack buffer nor any borrowed request view escapes release. */
  memset(response_body, '?', sizeof(response_body) - 1U);
  TEST_CHECK(test_release(&owner));
  TEST_CHECK(test_receive(peer, response_wire, sizeof(response_wire), &received));
  TEST_CHECK(test_wire_response(response_wire, received, 201U, "owned reply"));
cleanup:
  if (!test_socket_close(&peer)) cleanup_ok = 0;
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("request_response", ok, cleanup_ok);
}
