#include "test_support.h"

static int send_request(test_service_t *owner, uint16_t port, test_socket_t *peer) {
  static const char request[] =
      "GET /route HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
  *peer = test_connect(port);
  return *peer != TEST_INVALID_SOCKET &&
         test_send(*peer, request, sizeof(request) - 1U) && test_next_request(owner);
}

int main(void) {
  test_service_t owner;
  test_socket_t peer = TEST_INVALID_SOCKET;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_route_state_t before, after, stable;
  coakka_http_host_route_identity_t first, changed, unchanged;
  coakka_http_host_rebind_request_t request, stale;
  coakka_http_host_rebind_outcome_t outcome;
  coakka_http_host_request_view_t captured;
  char wire[TEST_WIRE_CAPACITY];
  size_t size;
  uint16_t port;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = 41U;
  route.handler_binding_id = 73U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/route");
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  coakka_http_host_route_identity_init(&first);
  TEST_CHECK(coakka_http_host_route_snapshot(owner.service, &before, 1U, &first) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(first.route_count == 1U && before.route_id == 41U && before.handler_binding_id == 73U);
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(send_request(&owner, port, &peer));
  coakka_http_host_request_view_init(&captured);
  TEST_CHECK(coakka_http_host_request_view(&owner.event, &captured) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(captured.binding_revision == before.binding_revision);
  coakka_http_host_rebind_request_init(&request);
  request.activation_id = 1U;
  request.expected_route_generation = first.route_generation;
  request.route_id = before.route_id;
  request.expected_binding_revision = before.binding_revision;
  request.new_handler_binding_id = 74U;
  coakka_http_host_rebind_outcome_init(&outcome);
  TEST_CHECK(coakka_http_host_rebind(owner.service, &request, &outcome) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.code == COAKKA_HTTP_HOST_CONTROL_APPLIED && outcome.changed);
  coakka_http_host_route_identity_init(&changed);
  TEST_CHECK(coakka_http_host_route_snapshot(owner.service, &after, 1U, &changed) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(changed.route_generation == first.route_generation);
  TEST_CHECK(after.handler_binding_id == 74U && after.binding_revision > before.binding_revision);
  /* Dispatch this already captured work through its captured binding. A live
   * snapshot is observation, never permission to select a replacement handler. */
  TEST_CHECK(owner.event.handler_binding_id == 73U);
  TEST_CHECK(test_respond(&owner, 200U, "old binding"));
  TEST_CHECK(test_release(&owner));
  TEST_CHECK(test_receive(peer, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "old binding"));
  TEST_CHECK(test_socket_close(&peer));
  TEST_CHECK(send_request(&owner, port, &peer));
  TEST_CHECK(owner.event.handler_binding_id == 74U);
  TEST_CHECK(test_respond(&owner, 200U, "new binding"));
  TEST_CHECK(test_release(&owner));
  TEST_CHECK(test_receive(peer, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_response(wire, size, 200U, "new binding"));
  TEST_CHECK(test_socket_close(&peer));
  stale = request;
  stale.activation_id = 2U;
  stale.new_handler_binding_id = 75U;
  coakka_http_host_rebind_outcome_init(&outcome);
  TEST_CHECK(coakka_http_host_rebind(owner.service, &stale, &outcome) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.code == COAKKA_HTTP_HOST_CONTROL_REVISION_MISMATCH && !outcome.changed);
  coakka_http_host_route_identity_init(&unchanged);
  TEST_CHECK(coakka_http_host_route_snapshot(owner.service, &stable, 1U, &unchanged) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(unchanged.route_generation == changed.route_generation);
  TEST_CHECK(unchanged.binding_change_sequence == changed.binding_change_sequence);
  TEST_CHECK(stable.handler_binding_id == after.handler_binding_id && stable.binding_revision == after.binding_revision);
  /* Exact recovery of an earlier activation must not apply it a second time.
   * Core compares complete retained intent and issues the replay outcome. */
  coakka_http_host_rebind_outcome_init(&outcome);
  TEST_CHECK(coakka_http_host_rebind(owner.service, &request, &outcome) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(outcome.code == COAKKA_HTTP_HOST_CONTROL_APPLIED && outcome.replayed);
  TEST_CHECK(outcome.effective_handler_binding_id == 74U);
  TEST_CHECK(outcome.effective_binding_revision == after.binding_revision);
cleanup:
  if (!test_socket_close(&peer)) cleanup_ok = 0;
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("captured_binding_and_control", ok, cleanup_ok);
}
