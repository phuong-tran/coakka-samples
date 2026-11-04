#include "test_support.h"

#include <string.h>

int main(int argc, char **argv) {
  test_service_t upstream, client;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_outbound_endpoint_t endpoint;
  coakka_http_host_outbound_target_t target;
  coakka_http_host_outbound_request_t request;
  coakka_http_host_outbound_call_t call;
  coakka_http_host_outbound_event_t event;
  char request_path[] = "/upstream";
  uint16_t port;
  int terminal_live = 0;
  int ok = 1, cleanup_ok = 1;
  if (argc > 2) {
    (void)fprintf(stderr, "usage: %s [local-bind-address]\n", argv[0]);
    return 1;
  }
  const char *upstream_address = argc == 2 ? argv[1] : "127.0.0.1";
  if (!test_platform_open()) return 1;
  test_service_init(&upstream);
  test_service_init(&client);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text(upstream_address);
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/upstream");
  TEST_CHECK(test_service_open(&upstream, &config, &route, 1U));
  TEST_CHECK(coakka_http_host_service_port(upstream.service, &port) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_outbound_endpoint_init(&endpoint);
  endpoint.node_id = test_text("fixture-upstream");
  endpoint.connect_host = test_text(upstream_address);
  endpoint.connect_port = port;
  endpoint.http_authority = test_text("localhost");
  endpoint.available = 1U;
  coakka_http_host_outbound_target_init(&target);
  target.name = test_text("fixture.upstream");
  target.generation = 1U;
  target.endpoints = &endpoint;
  target.endpoint_count = 1U;
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  config.outbound_targets = &target;
  config.outbound_target_count = 1U;
  TEST_CHECK(test_service_open(&client, &config, NULL, 0U));
  coakka_http_host_outbound_request_init(&request);
  request.logical_target = test_text("fixture.upstream");
  request.method = test_text("GET");
  request.target = test_text(request_path);
  request.timeout_ms = 30000U;
  TEST_CHECK(coakka_http_host_submit_outbound(client.service, &request, &call) ==
             COAKKA_HTTP_HOST_OK);
  /* Submission borrows caller values only for the call. Target selection and
   * completion matching remain Core-owned, not a fixture-side call map. */
  memset(request_path, '?', sizeof(request_path) - 1U);
  const int received = test_next_request(&upstream);
  if (!received) {
    coakka_http_host_outbound_event_init(&event);
    if (coakka_http_host_take_outbound(client.service, 0U, &event) ==
        COAKKA_HTTP_HOST_OK) {
      terminal_live = 1;
      (void)fprintf(stderr, "outbound terminal reason=%u delivery=%u: %.*s\n",
                    (unsigned)event.reason, (unsigned)event.certainty,
                    (int)event.diagnostic.size,
                    event.diagnostic.size == 0U
                        ? ""
                        : (const char *)event.diagnostic.data);
    }
  }
  TEST_CHECK(received);
  TEST_CHECK(test_bytes_equal(coakka_http_host_request_target(&upstream.event), "/upstream"));
  TEST_CHECK(test_respond(&upstream, 200U, "outbound reply"));
  TEST_CHECK(test_release(&upstream));
  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, TEST_IO_TIMEOUT_MS,
                                            &event) == COAKKA_HTTP_HOST_OK);
  terminal_live = 1;
  TEST_CHECK(event.call.slot == call.slot && event.call.generation == call.generation);
  TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE);
  TEST_CHECK(event.response_status == 200U);
  TEST_CHECK(test_bytes_equal(event.response_body, "outbound reply"));
  TEST_CHECK(test_bytes_equal(event.logical_target, "fixture.upstream"));
  TEST_CHECK(test_bytes_equal(event.selected_node_id, "fixture-upstream"));
  TEST_CHECK(coakka_http_host_release_outbound(client.service, &event) == COAKKA_HTTP_HOST_OK);
  terminal_live = 0;
  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, 0U, &event) == COAKKA_HTTP_HOST_TIMEOUT);

  request.target = test_text("/upstream");
  TEST_CHECK(coakka_http_host_submit_outbound(client.service, &request, &call) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(test_next_request(&upstream));
  /* Cancel while the fixture deliberately withholds a reply. No sleep is
   * used to guess which transport or lifecycle phase has been reached. */
  TEST_CHECK(coakka_http_host_cancel_outbound(client.service, call) == COAKKA_HTTP_HOST_OK);
  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, TEST_IO_TIMEOUT_MS,
                                            &event) == COAKKA_HTTP_HOST_OK);
  terminal_live = 1;
  TEST_CHECK(event.call.slot == call.slot && event.call.generation == call.generation);
  TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_CANCELLED);
  TEST_CHECK(coakka_http_host_release_outbound(client.service, &event) == COAKKA_HTTP_HOST_OK);
  terminal_live = 0;
  TEST_CHECK(test_release(&upstream));
  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, 0U, &event) == COAKKA_HTTP_HOST_TIMEOUT);
cleanup:
  /* A successful take, not a guessed terminal phase, owns this release duty. */
  if (terminal_live && coakka_http_host_release_outbound(client.service, &event) !=
                         COAKKA_HTTP_HOST_OK) cleanup_ok = 0;
  if (!test_service_close(&client)) cleanup_ok = 0;
  if (!test_service_close(&upstream)) cleanup_ok = 0;
  return test_finish("outbound_correlation_cancel", ok, cleanup_ok);
}
