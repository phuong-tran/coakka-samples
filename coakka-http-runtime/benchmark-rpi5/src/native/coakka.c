/* Fixed response through the public host-inlined C11 event surface. */
#include <coakka/http/host.h>

#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static volatile sig_atomic_t stopping = 0;
static const char body[] = "0123456789abcdef0123456789abcdef";

static coakka_http_host_bytes_t bytes(const char *value) {
  coakka_http_host_bytes_t result = {(const uint8_t *)value, strlen(value)};
  return result;
}

static void stop_signal(int signal_number) {
  (void)signal_number;
  stopping = 1;
}

static int serve(coakka_http_host_service_t *service) {
  static const coakka_http_host_header_t content_type = {
      {(const uint8_t *)"content-type", 12U},
      {(const uint8_t *)"application/octet-stream", 24U}};
  while (stopping == 0) {
    coakka_http_host_request_event_t event;
    coakka_http_host_status_t status;
    coakka_http_host_request_event_init(&event);
    status = coakka_http_host_take_request(service, 100U, &event);
    if (status == COAKKA_HTTP_HOST_TIMEOUT) {
      continue;
    }
    if (status == COAKKA_HTTP_HOST_CLOSED) {
      break;
    }
    if (status != COAKKA_HTTP_HOST_OK) {
      return 0;
    }
    if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
      coakka_http_host_response_t response;
      coakka_http_host_response_init(&response);
      response.headers = &content_type;
      response.header_count = 1U;
      response.body = bytes(body);
      if (coakka_http_host_respond(service, event.exchange, &response) !=
          COAKKA_HTTP_HOST_OK) {
        (void)coakka_http_host_release_request(service, &event);
        return 0;
      }
    }
    if (coakka_http_host_release_request(service, &event) !=
        COAKKA_HTTP_HOST_OK) {
      return 0;
    }
  }
  return 1;
}

int main(int argc, char **argv) {
  coakka_http_host_configuration_t configuration;
  coakka_http_host_route_t route;
  coakka_http_host_service_t *service = NULL;
  coakka_http_host_issue_t issue;
  uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  unsigned long port;
  int passed;
  if (argc != 2) {
    return EXIT_FAILURE;
  }
  port = strtoul(argv[1], NULL, 10);
  if (port == 0UL || port > 65535UL) {
    return EXIT_FAILURE;
  }

  coakka_http_host_configuration_init(&configuration);
  coakka_http_host_issue_init(&issue);
  configuration.port = (uint16_t)port;
  configuration.bind_address = bytes("127.0.0.1");
  configuration.limits.event_loop_threads = 1U;
  configuration.limits.max_connections = 512U;
  configuration.limits.max_active_requests = 256U;
  configuration.limits.request_queue_capacity = 256U;
  configuration.limits.completion_queue_capacity = 256U;
  coakka_http_host_route_init(&route);
  route.route_id = UINT64_C(1);
  route.handler_binding_id = UINT64_C(1);
  route.method = bytes("GET");
  route.encoded_path_pattern = bytes("/fixed");
  if (coakka_http_host_service_create(&configuration, &route, 1U, &service,
                                      &failed_route) != COAKKA_HTTP_HOST_OK ||
      coakka_http_host_service_start(service, &issue) != COAKKA_HTTP_HOST_OK) {
    coakka_http_host_service_destroy(&service);
    return EXIT_FAILURE;
  }

  (void)signal(SIGINT, stop_signal);
  (void)signal(SIGTERM, stop_signal);
  passed = serve(service);
  (void)coakka_http_host_service_begin_drain(service);
  if (coakka_http_host_service_stop(service) != COAKKA_HTTP_HOST_OK) {
    passed = 0;
  }
  coakka_http_host_service_destroy(&service);
  return passed != 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
