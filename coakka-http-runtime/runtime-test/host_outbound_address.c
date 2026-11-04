#include "test_support.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

static int parse_u16(const char *text, uint16_t *value) {
  char *end;
  errno = 0;
  const unsigned long parsed = strtoul(text, &end, 10);
  if (text[0] < '0' || text[0] > '9' || *end != '\0' || errno != 0 ||
      parsed == 0UL || parsed > UINT16_MAX) {
    return 0;
  }
  *value = (uint16_t)parsed;
  return 1;
}

int main(int argc, char **argv) {
  uint16_t port = 0;
  uint16_t expected_status = 0;
  const int expect_rejected = argc == 5 && strcmp(argv[4], "rejected") == 0;
  const int expect_failed = argc == 5 && strcmp(argv[4], "failed") == 0;
  const int expect_cancelled = argc == 5 && strcmp(argv[4], "cancel") == 0;
  if (argc != 5 || !parse_u16(argv[2], &port) ||
      (!expect_rejected && !expect_failed && !expect_cancelled &&
       !parse_u16(argv[4], &expected_status))) {
    (void)fprintf(stderr,
                  "usage: %s host port /path status|rejected|failed|cancel\n",
                  argv[0]);
    return 2;
  }
  if (!test_platform_open()) {
    return 1;
  }
  test_service_t client;
  test_service_init(&client);
  coakka_http_host_configuration_t config;
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");

  coakka_http_host_outbound_endpoint_t endpoint;
  coakka_http_host_outbound_endpoint_init(&endpoint);
  endpoint.node_id = test_text("address-fixture-node");
  endpoint.connect_host = test_text(argv[1]);
  endpoint.connect_port = port;
  endpoint.http_authority = test_text("address-fixture.test");
  endpoint.available = 1U;
  coakka_http_host_outbound_target_t target;
  coakka_http_host_outbound_target_init(&target);
  target.name = test_text("fixture.address");
  target.generation = 1U;
  target.endpoints = &endpoint;
  target.endpoint_count = 1U;
  config.outbound_targets = &target;
  config.outbound_target_count = 1U;

  coakka_http_host_outbound_request_t request;
  coakka_http_host_outbound_request_init(&request);
  request.logical_target = target.name;
  request.method = test_text("GET");
  request.target = test_text(argv[3]);
  request.timeout_ms = 5000U;
  coakka_http_host_outbound_call_t call;
  coakka_http_host_outbound_event_t event;
  int terminal_live = 0;
  int ok = 1;
  int cleanup_ok = 1;
  TEST_CHECK(coakka_http_host_abi_version() == 10U);
  TEST_CHECK(test_service_open(&client, &config, NULL, 0U));
  TEST_CHECK(coakka_http_host_submit_outbound(client.service, &request, &call) ==
             COAKKA_HTTP_HOST_OK);
  if (expect_cancelled) {
    // The environment driver owns a process deadline and sends this signal
    // only after observing the exact socket in the TCP SYN-SENT state.
    TEST_CHECK(fgetc(stdin) == '\n');
    TEST_CHECK(coakka_http_host_cancel_outbound(client.service, call) ==
               COAKKA_HTTP_HOST_OK);
  }
  coakka_http_host_outbound_event_init(&event);
  TEST_CHECK(coakka_http_host_take_outbound(client.service, TEST_IO_TIMEOUT_MS,
                                           &event) == COAKKA_HTTP_HOST_OK);
  terminal_live = 1;
  (void)fprintf(stdout, "terminal reason=%u certainty=%u status=%u\n",
                (unsigned)event.reason, (unsigned)event.certainty,
                (unsigned)event.response_status);
  TEST_CHECK(event.call.slot == call.slot &&
             event.call.generation == call.generation);
  TEST_CHECK(event.target_generation == target.generation);
  TEST_CHECK(test_bytes_equal(event.logical_target, "fixture.address"));
  TEST_CHECK(test_bytes_equal(event.selected_node_id, "address-fixture-node"));
  if (expect_rejected) {
    TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_REJECTED);
    TEST_CHECK(event.certainty == COAKKA_HTTP_HOST_OUTBOUND_NO_REQUEST_BYTES_SENT);
  } else if (expect_failed) {
    TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_PROVIDER_FAILURE);
    TEST_CHECK(event.phase == COAKKA_HTTP_HOST_OUTBOUND_PHASE_TRANSPORT);
    TEST_CHECK(event.certainty == COAKKA_HTTP_HOST_OUTBOUND_NO_REQUEST_BYTES_SENT);
  } else if (expect_cancelled) {
    TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_CANCELLED);
    TEST_CHECK(event.certainty == COAKKA_HTTP_HOST_OUTBOUND_NO_REQUEST_BYTES_SENT);
  } else {
    TEST_CHECK(event.reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE);
    TEST_CHECK(event.certainty == COAKKA_HTTP_HOST_OUTBOUND_COMPLETE_RESPONSE);
    TEST_CHECK(event.response_status == expected_status);
    TEST_CHECK(test_bytes_equal(event.response_body, "coakka-address-fixture\n"));
  }
  TEST_CHECK(coakka_http_host_release_outbound(client.service, &event) ==
             COAKKA_HTTP_HOST_OK);
  coakka_http_host_outbound_event_init(&event);
  const coakka_http_host_status_t second =
      coakka_http_host_take_outbound(client.service, 0U, &event);
  terminal_live = second == COAKKA_HTTP_HOST_OK;
  TEST_CHECK(second == COAKKA_HTTP_HOST_TIMEOUT);

cleanup:
  if (terminal_live && coakka_http_host_release_outbound(client.service, &event) !=
                           COAKKA_HTTP_HOST_OK) {
    cleanup_ok = 0;
  }
  if (!test_service_close(&client)) {
    cleanup_ok = 0;
  }
  return test_finish("outbound_address", ok, cleanup_ok);
}
