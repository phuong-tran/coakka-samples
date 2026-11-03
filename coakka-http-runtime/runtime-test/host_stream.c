#include "test_support.h"

#include <string.h>

static int wait_writable(test_service_t *owner,
                          coakka_http_host_exchange_t exchange, uint64_t until) {
  uint64_t now;
  if (!test_now_ms(&now) || now >= until ||
      test_take(owner, until - now) != COAKKA_HTTP_HOST_OK) return 0;
  if (owner->event.kind != COAKKA_HTTP_HOST_RESPONSE_WRITABLE ||
      owner->event.exchange.slot != exchange.slot ||
      owner->event.exchange.generation != exchange.generation) {
    (void)fprintf(stderr, "unexpected stream admission event: %u\n", owner->event.kind);
    return 0;
  }
  return test_release(owner);
}

static int write_chunk(test_service_t *owner,
                        coakka_http_host_exchange_t exchange, const char *bytes) {
  uint64_t until;
  if (!test_now_ms(&until) || until > UINT64_MAX - TEST_IO_TIMEOUT_MS) return 0;
  until += TEST_IO_TIMEOUT_MS;
  for (;;) {
    const coakka_http_host_status_t status =
        coakka_http_host_write_response_stream(owner->service, exchange, test_text(bytes));
    if (status == COAKKA_HTTP_HOST_OK) return 1;
    /* Core's coalesced writable event is a hint to retry the authoritative
     * admission call. No local queue count, sleep or elapsed-time guess opens it. */
    if (status != COAKKA_HTTP_HOST_CAPACITY_EXHAUSTED ||
        !wait_writable(owner, exchange, until)) return 0;
  }
}

int main(void) {
  static const char request[] =
      "POST /stream HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n"
      "Trailer: x-checksum\r\nConnection: close\r\n\r\n"
      "2\r\nab\r\n3\r\ncde\r\n0\r\nx-checksum: yes\r\n\r\n";
  test_service_t owner;
  test_socket_t peer = TEST_INVALID_SOCKET;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_response_t response;
  coakka_http_host_request_view_t view;
  coakka_http_host_header_t trailer;
  coakka_http_host_exchange_t exchange = {0U, 0U};
  char body[16], wire[TEST_WIRE_CAPACITY];
  size_t used = 0U, size;
  uint16_t port;
  uint64_t until, now;
  unsigned events = 0U;
  int saw_request = 0, saw_trailer = 0, saw_end = 0;
  int ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("POST");
  route.encoded_path_pattern = test_text("/stream");
  route.flags = COAKKA_HTTP_HOST_ROUTE_STREAMING_REQUEST;
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  peer = test_connect(port);
  TEST_CHECK(peer != TEST_INVALID_SOCKET && test_send(peer, request, sizeof(request) - 1U));
  TEST_CHECK(test_now_ms(&until) && until <= UINT64_MAX - TEST_IO_TIMEOUT_MS);
  until += TEST_IO_TIMEOUT_MS;
  while (!saw_end && events < 64U) {
    TEST_CHECK(test_now_ms(&now) && now < until);
    TEST_CHECK(test_take(&owner, until - now) == COAKKA_HTTP_HOST_OK);
    ++events;
    if (owner.event.kind == COAKKA_HTTP_HOST_REQUEST) {
      TEST_CHECK(!saw_request);
      coakka_http_host_request_view_init(&view);
      TEST_CHECK(coakka_http_host_request_view(&owner.event, &view) == COAKKA_HTTP_HOST_OK);
      TEST_CHECK(view.body_delivery == COAKKA_HTTP_HOST_REQUEST_BODY_STREAM);
      exchange = owner.event.exchange;
      saw_request = 1;
    } else {
      TEST_CHECK(saw_request && owner.event.exchange.slot == exchange.slot &&
                  owner.event.exchange.generation == exchange.generation);
      if (owner.event.kind == COAKKA_HTTP_HOST_REQUEST_DATA) {
        TEST_CHECK(!saw_trailer && owner.event.data.size <= sizeof(body) - used);
        if (owner.event.data.size != 0U)
          memcpy(body + used, owner.event.data.data, owner.event.data.size);
        used += owner.event.data.size;
      } else if (owner.event.kind == COAKKA_HTTP_HOST_REQUEST_TRAILERS) {
        TEST_CHECK(!saw_trailer);
        TEST_CHECK(coakka_http_host_request_stream_trailer(&owner.event, 0U, &trailer));
        TEST_CHECK(test_bytes_equal(trailer.name, "x-checksum") && test_bytes_equal(trailer.value, "yes"));
        TEST_CHECK(!coakka_http_host_request_stream_trailer(&owner.event, 1U, &trailer));
        saw_trailer = 1;
      } else if (owner.event.kind == COAKKA_HTTP_HOST_REQUEST_END) {
        saw_end = 1;
      } else {
        TEST_CHECK(0); /* Cancellation or a premature terminal is not success. */
      }
    }
    /* No borrowed chunk/header survives its event; copied bytes stay fixture-owned. */
    TEST_CHECK(test_release(&owner));
  }
  TEST_CHECK(saw_request && saw_trailer && saw_end && used == 5U && memcmp(body, "abcde", 5U) == 0);
  coakka_http_host_response_init(&response);
  TEST_CHECK(coakka_http_host_start_response_stream(owner.service, exchange, &response) == COAKKA_HTTP_HOST_OK);
  /* Start accepts intent, not producer readiness. Only Core's first writable
   * observation admits application emission; do not infer it from return/time. */
  TEST_CHECK(test_now_ms(&until) && until <= UINT64_MAX - TEST_IO_TIMEOUT_MS);
  TEST_CHECK(wait_writable(&owner, exchange, until + TEST_IO_TIMEOUT_MS));
  TEST_CHECK(write_chunk(&owner, exchange, "ab"));
  TEST_CHECK(write_chunk(&owner, exchange, "cde"));
  trailer.name = test_text("x-finished");
  trailer.value = test_text("yes");
  TEST_CHECK(coakka_http_host_finish_response_stream(owner.service, exchange, &trailer, 1U) == COAKKA_HTTP_HOST_OK);
  TEST_CHECK(test_receive(peer, wire, sizeof(wire), &size));
  TEST_CHECK(test_wire_chunked(wire, size, "abcde", "x-finished", "yes"));
cleanup:
  if (!test_socket_close(&peer)) cleanup_ok = 0;
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("stream_body_and_trailers", ok, cleanup_ok);
}
