#include "test_support.h"
#include "test_threads.h"

#include <string.h>

/* Fixed correctness workload, not a Core resource default or a benchmark. */
enum { CLIENTS = 4, ROUNDS = 8 };

typedef struct worker_context {
  test_service_t *reader_owner;
  uint16_t port;
  unsigned id;
  unsigned completed;
  int cleanup_ok;
} worker_context_t;

static int read_requests(worker_context_t *context) {
  unsigned i;
  for (i = 0U; i < (unsigned)(CLIENTS * ROUNDS); ++i) {
    char body[128];
    coakka_http_host_bytes_t target;
    if (!test_next_request(context->reader_owner)) return -1;
    target = coakka_http_host_request_target(&context->reader_owner->event);
    if (target.size >= sizeof(body)) return -1;
    memcpy(body, target.data, target.size);
    body[target.size] = '\0';
    if (!test_respond(context->reader_owner, 200U, body) ||
        !test_release(context->reader_owner)) return -1;
    ++context->completed;
  }
  return 0;
}

static int client_requests(worker_context_t *context) {
  unsigned round;
  for (round = 0U; round < ROUNDS; ++round) {
    char target[80], request[256], response[TEST_WIRE_CAPACITY];
    test_socket_t peer = TEST_INVALID_SOCKET;
    size_t size;
    int written, ok = 0;
    written = snprintf(target, sizeof(target), "/concurrent/%u/%u", context->id, round);
    if (written < 0 || (size_t)written >= sizeof(target)) return -1;
    written = snprintf(request, sizeof(request),
                        "GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", target);
    if (written < 0 || (size_t)written >= sizeof(request)) return -1;
    peer = test_connect(context->port);
    if (peer != TEST_INVALID_SOCKET && test_send(peer, request, (size_t)written) &&
        test_receive(peer, response, sizeof(response), &size) &&
        test_wire_response(response, size, 200U, target)) ok = 1;
    if (!test_socket_close(&peer)) context->cleanup_ok = 0;
    if (!ok || !context->cleanup_ok) {
      (void)fprintf(stderr, "concurrent client=%u round=%u failed\n", context->id, round);
      return -1;
    }
    ++context->completed;
  }
  return 0;
}

static int worker(void *opaque) {
  worker_context_t *context = (worker_context_t *)opaque;
  return context->reader_owner != NULL ? read_requests(context) : client_requests(context);
}

int main(void) {
  test_service_t owner;
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  worker_context_t workers[CLIENTS + 1];
  void *contexts[CLIENTS + 1];
  uint16_t port;
  unsigned i, replies = 0U;
  int result, ok = 1, cleanup_ok = 1;
  if (!test_platform_open()) return 1;
  test_service_init(&owner);
  coakka_http_host_configuration_init(&config);
  config.bind_address = test_text("127.0.0.1");
  coakka_http_host_route_init(&route);
  route.route_id = route.handler_binding_id = 1U;
  route.method = test_text("GET");
  route.encoded_path_pattern = test_text("/concurrent/{client}/{round}");
  TEST_CHECK(test_service_open(&owner, &config, &route, 1U));
  TEST_CHECK(coakka_http_host_service_port(owner.service, &port) == COAKKA_HTTP_HOST_OK);
  memset(workers, 0, sizeof(workers));
  for (i = 0U; i < CLIENTS + 1U; ++i) {
    workers[i].port = port;
    workers[i].id = i;
    workers[i].cleanup_ok = 1;
    contexts[i] = &workers[i];
  }
  workers[0].reader_owner = &owner;
  /* Exactly one fixture reader consumes Core events. Clients own only their
   * sockets. Creation publishes immutable inputs; proven joins publish results
   * back to this controller. No shared mutable counter needs a guessed fence. */
  result = coakka_http_test_run_threads(worker, contexts, CLIENTS + 1U);
  for (i = 0U; i < CLIENTS + 1U; ++i)
    if (!workers[i].cleanup_ok) cleanup_ok = 0;
  TEST_CHECK(result == 0);
  for (i = 1U; i < CLIENTS + 1U; ++i) replies += workers[i].completed;
  TEST_CHECK(workers[0].completed == (unsigned)(CLIENTS * ROUNDS));
  TEST_CHECK(replies == workers[0].completed);
cleanup:
  /* The group returned only after every created worker was joined. Releasing
   * its last possible reader lease and destroying Core is now serialized. */
  if (!test_service_close(&owner)) cleanup_ok = 0;
  return test_finish("concurrent_roundtrips", ok, cleanup_ok);
}
