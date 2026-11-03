#ifndef COAKKA_HTTP_SAMPLE_HOST_SERVER_H
#define COAKKA_HTTP_SAMPLE_HOST_SERVER_H

#include <coakka/http/host.h>

#include <stdio.h>
#include <string.h>

static coakka_http_host_bytes_t text_bytes(const char *text) {
  const coakka_http_host_bytes_t bytes = {(const uint8_t *)text, strlen(text)};
  return bytes;
}

static int check(coakka_http_host_status_t status, const char *operation) {
  if (status == COAKKA_HTTP_HOST_OK) return 1;
  fprintf(stderr, "%s: %s\n", operation, coakka_http_host_status_name(status));
  return 0;
}

static int open_server(coakka_http_host_service_t **service, int streaming) {
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_issue_t issue;
  uint32_t failed_index;
  uint16_t port;
  coakka_http_host_configuration_init(&config);
  config.bind_address = text_bytes("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = 1U;
  route.handler_binding_id = 1U;
  route.method = text_bytes("GET");
  route.encoded_path_pattern = text_bytes(streaming ? "/stream" : "/hello");
  coakka_http_host_issue_init(&issue);
  if (!check(coakka_http_host_service_create(&config, &route, 1U, service, &failed_index),
             "create service") ||
      !check(coakka_http_host_service_start(*service, &issue), "start service") ||
      !check(coakka_http_host_service_port(*service, &port), "read listener port")) return 0;
  printf("host-server-port=%u\n", (unsigned)port);
  return fflush(stdout) == 0;
}

/* Copy the value identity before releasing the lease. No borrowed data survives
 * this function. There is one event reader and one exchange in this example. */
static int take_event(coakka_http_host_service_t *service,
                      coakka_http_host_request_event_kind_t expected,
                      coakka_http_host_exchange_t *exchange) {
  coakka_http_host_request_event_t event;
  coakka_http_host_request_event_init(&event);
  if (!check(coakka_http_host_take_request(service, 5000U, &event), "take event")) return 0;
  const int matches = event.kind == expected &&
      (expected == COAKKA_HTTP_HOST_REQUEST ? event.handler_binding_id == 1U :
       event.exchange.slot == exchange->slot &&
       event.exchange.generation == exchange->generation);
  if (matches) *exchange = event.exchange;
  if (!matches) fprintf(stderr, "unexpected event %u (expected %u)\n",
                        (unsigned)event.kind, (unsigned)expected);
  return check(coakka_http_host_release_request(service, &event), "release event") && matches;
}

static int serve_one(coakka_http_host_service_t *service, int streaming) {
  coakka_http_host_exchange_t exchange = {0U, 0U};
  coakka_http_host_response_t response;
  coakka_http_host_response_init(&response);
  response.status_code = 200U;
  if (!take_event(service, COAKKA_HTTP_HOST_REQUEST, &exchange)) return 0;
  if (streaming) {
    const coakka_http_host_header_t trailer = {text_bytes("x-stream-end"), text_bytes("done")};
    /* A successful start submits the head. Core announces when the producer
     * can write. This finite producer reports pressure as failure. */
    if (!check(coakka_http_host_start_response_stream(service, exchange, &response), "start response stream") ||
        !take_event(service, COAKKA_HTTP_HOST_RESPONSE_WRITABLE, &exchange) ||
        !check(coakka_http_host_write_response_stream(service, exchange, text_bytes("stream-")), "write first chunk") ||
        !check(coakka_http_host_write_response_stream(service, exchange, text_bytes("ready")), "write second chunk") ||
        !check(coakka_http_host_finish_response_stream(service, exchange, &trailer, 1U), "finish stream")) return 0;
  } else {
    response.body = text_bytes("hello\n");
    if (!check(coakka_http_host_respond(service, exchange, &response), "submit response")) return 0;
  }
  return take_event(service, COAKKA_HTTP_HOST_REQUEST_TERMINAL, &exchange);
}

static int parse_mode(int argc, char **argv) {
  if (argc == 2 && strcmp(argv[1], "server") == 0) return 0;
  if (argc == 2 && strcmp(argv[1], "streaming") == 0) return 1;
  fputs("usage: host-server server|streaming\n", stderr);
  return -1;
}
#endif
