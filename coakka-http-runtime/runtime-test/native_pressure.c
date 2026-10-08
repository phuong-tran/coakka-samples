#include <coakka/http/http.h>

#include "test_threads.h"

#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
typedef SOCKET test_socket_t;
#define TEST_INVALID_SOCKET INVALID_SOCKET
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sched.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
typedef int test_socket_t;
#define TEST_INVALID_SOCKET (-1)
#endif

enum { TEST_PRESSURE_CLIENTS = 8, TEST_WAIT_ATTEMPTS = 1000000 };

typedef struct test_state {
  coakka_http_server_t *server;
  uint16_t port;
  atomic_uint mode;
  atomic_uint entered;
  atomic_uint release;
  atomic_uint sent;
  atomic_uint completed;
  atomic_uint busy;
  atomic_uint failures;
  coakka_http_result_t first_stop;
  coakka_http_result_t retry_stop;
} test_state_t;

typedef struct worker_state {
  test_state_t *test;
  int controller;
} worker_state_t;

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : (uint64_t)strlen(value);
  return result;
}

static void yield_thread(void) {
#if defined(_WIN32)
  Sleep(0U);
#else
  (void)sched_yield();
#endif
}

static int wait_at_least(const atomic_uint *value, unsigned int expected) {
  unsigned int attempt;
  for (attempt = 0U; attempt < TEST_WAIT_ATTEMPTS; ++attempt) {
    if (atomic_load_explicit(value, memory_order_acquire) >= expected) {
      return 1;
    }
    yield_thread();
  }
  return 0;
}

static void close_socket(test_socket_t socket_value) {
#if defined(_WIN32)
  (void)closesocket(socket_value);
#else
  (void)close(socket_value);
#endif
}

static test_socket_t connect_loopback(uint16_t port) {
  struct sockaddr_in address;
  test_socket_t socket_value = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
#if defined(_WIN32)
  DWORD timeout_ms = 5000U;
#else
  struct timeval timeout = {5, 0};
#endif

  if (socket_value == TEST_INVALID_SOCKET) {
    return TEST_INVALID_SOCKET;
  }
#if defined(_WIN32)
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO,
                   (const char *)&timeout_ms, (int)sizeof(timeout_ms));
#else
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   (socklen_t)sizeof(timeout));
#endif
  memset(&address, 0, sizeof(address));
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (connect(socket_value, (const struct sockaddr *)&address,
              (socklen_t)sizeof(address)) != 0) {
    close_socket(socket_value);
    return TEST_INVALID_SOCKET;
  }
  return socket_value;
}

static int send_request(test_socket_t socket_value) {
  static const char request[] = "GET /pressure HTTP/1.1\r\n"
                                "Host: 127.0.0.1\r\n"
                                "Connection: close\r\n\r\n";
  size_t sent = 0U;
  while (sent < sizeof(request) - 1U) {
#if defined(_WIN32)
    const int count = send(socket_value, request + sent,
                           (int)(sizeof(request) - 1U - sent), 0);
#else
    const ssize_t count =
        send(socket_value, request + sent, sizeof(request) - 1U - sent, 0);
#endif
    if (count <= 0) {
      return 0;
    }
    sent += (size_t)count;
  }
  return 1;
}

static int read_response(test_socket_t socket_value, char *response,
                         size_t capacity) {
  size_t used = 0U;
  while (used + 1U < capacity) {
#if defined(_WIN32)
    const int count =
        recv(socket_value, response + used, (int)(capacity - used - 1U), 0);
#else
    const ssize_t count =
        recv(socket_value, response + used, capacity - used - 1U, 0);
#endif
    if (count <= 0) {
      break;
    }
    used += (size_t)count;
  }
  response[used] = '\0';
  return used != 0U;
}

static void handle_request(void *opaque, coakka_http_request_t *request) {
  test_state_t *test = (test_state_t *)opaque;
  const unsigned int mode =
      atomic_load_explicit(&test->mode, memory_order_acquire);
  coakka_http_response_t response;
  coakka_http_result_t submitted;

  (void)atomic_fetch_add_explicit(&test->entered, 1U, memory_order_release);
  if (mode != 0U) {
    while (atomic_load_explicit(&test->release, memory_order_acquire) == 0U) {
      yield_thread();
    }
  }
  coakka_http_response_init(&response);
  response.body = bytes("pressure-ready");
  submitted = coakka_http_request_respond(request, &response);
  if (mode == 1U && submitted.code != COAKKA_HTTP_RESULT_OK) {
    (void)atomic_fetch_add_explicit(&test->failures, 1U, memory_order_relaxed);
  } else if (mode == 2U && submitted.code != COAKKA_HTTP_RESULT_OK &&
             submitted.code != COAKKA_HTTP_RESULT_CLOSED &&
             submitted.code != COAKKA_HTTP_RESULT_CANCELLED &&
             submitted.code != COAKKA_HTTP_RESULT_STALE_EXCHANGE &&
             submitted.code != COAKKA_HTTP_RESULT_INVALID_STATE) {
    (void)atomic_fetch_add_explicit(&test->failures, 1U, memory_order_relaxed);
  }
}

static int client_worker(void *opaque) {
  worker_state_t *worker = (worker_state_t *)opaque;
  test_state_t *test = worker->test;
  const unsigned int mode =
      atomic_load_explicit(&test->mode, memory_order_acquire);
  test_socket_t socket_value = connect_loopback(test->port);
  char response[2048];
  int read;

  if (socket_value == TEST_INVALID_SOCKET || !send_request(socket_value)) {
    if (socket_value != TEST_INVALID_SOCKET) {
      close_socket(socket_value);
    }
    return -1;
  }
  (void)atomic_fetch_add_explicit(&test->sent, 1U, memory_order_release);
  read = read_response(socket_value, response, sizeof(response));
  close_socket(socket_value);
  if (read != 0 && strstr(response, "HTTP/1.1 503") != NULL &&
      strstr(response, "runtime busy") != NULL) {
    (void)atomic_fetch_add_explicit(&test->busy, 1U, memory_order_relaxed);
  } else if ((mode != 2U &&
              (read == 0 || strstr(response, "HTTP/1.1 200") == NULL)) ||
             (mode == 2U && read != 0 &&
              strstr(response, "HTTP/1.1 200") == NULL &&
              strstr(response, "HTTP/1.1 503") == NULL)) {
    (void)atomic_fetch_add_explicit(&test->failures, 1U, memory_order_relaxed);
  }
  (void)atomic_fetch_add_explicit(&test->completed, 1U, memory_order_release);
  return 0;
}

static int pressure_controller(void *opaque) {
  worker_state_t *worker = (worker_state_t *)opaque;
  test_state_t *test = worker->test;
  const int ready = wait_at_least(&test->entered, 1U) &&
                    wait_at_least(&test->sent, TEST_PRESSURE_CLIENTS) &&
                    wait_at_least(&test->completed, TEST_PRESSURE_CLIENTS - 2U);
  atomic_store_explicit(&test->release, 1U, memory_order_release);
  return ready != 0 ? 0 : -1;
}

static int stop_controller(void *opaque) {
  worker_state_t *worker = (worker_state_t *)opaque;
  test_state_t *test = worker->test;
  if (!wait_at_least(&test->entered, 1U)) {
    atomic_store_explicit(&test->release, 1U, memory_order_release);
    return -1;
  }
  test->first_stop = coakka_http_server_stop(test->server);
  atomic_store_explicit(&test->release, 1U, memory_order_release);
  test->retry_stop = coakka_http_server_stop(test->server);
  return test->first_stop.code == COAKKA_HTTP_RESULT_TIMEOUT &&
                 test->retry_stop.code == COAKKA_HTTP_RESULT_OK
             ? 0
             : -1;
}

static int controller_worker(void *opaque) {
  worker_state_t *worker = (worker_state_t *)opaque;
  return atomic_load_explicit(&worker->test->mode, memory_order_acquire) == 1U
             ? pressure_controller(opaque)
             : stop_controller(opaque);
}

static int dispatch_worker(void *opaque);

static int run_pressure(test_state_t *test) {
  worker_state_t workers[TEST_PRESSURE_CLIENTS + 1U];
  void *contexts[TEST_PRESSURE_CLIENTS + 1U];
  size_t index;

  atomic_store_explicit(&test->mode, 1U, memory_order_release);
  atomic_store_explicit(&test->entered, 0U, memory_order_release);
  atomic_store_explicit(&test->release, 0U, memory_order_release);
  atomic_store_explicit(&test->sent, 0U, memory_order_release);
  atomic_store_explicit(&test->completed, 0U, memory_order_release);
  atomic_store_explicit(&test->busy, 0U, memory_order_release);
  for (index = 0U; index < TEST_PRESSURE_CLIENTS; ++index) {
    workers[index].test = test;
    workers[index].controller = 0;
    contexts[index] = &workers[index];
  }
  workers[TEST_PRESSURE_CLIENTS].test = test;
  workers[TEST_PRESSURE_CLIENTS].controller = 1;
  contexts[TEST_PRESSURE_CLIENTS] = &workers[TEST_PRESSURE_CLIENTS];
  if (coakka_http_test_run_threads(dispatch_worker, contexts,
                                   TEST_PRESSURE_CLIENTS + 1U) != 0) {
    return -1;
  }
  return atomic_load_explicit(&test->busy, memory_order_relaxed) >=
                     TEST_PRESSURE_CLIENTS - 2U &&
                 atomic_load_explicit(&test->completed, memory_order_relaxed) ==
                     TEST_PRESSURE_CLIENTS &&
                 atomic_load_explicit(&test->failures, memory_order_relaxed) ==
                     0U
             ? 0
             : -1;
}

static int dispatch_worker(void *opaque) {
  worker_state_t *worker = (worker_state_t *)opaque;
  return worker->controller != 0 ? controller_worker(opaque)
                                 : client_worker(opaque);
}

static int run_stop_retry(test_state_t *test) {
  worker_state_t workers[2];
  void *contexts[2];

  atomic_store_explicit(&test->mode, 2U, memory_order_release);
  atomic_store_explicit(&test->entered, 0U, memory_order_release);
  atomic_store_explicit(&test->release, 0U, memory_order_release);
  atomic_store_explicit(&test->sent, 0U, memory_order_release);
  atomic_store_explicit(&test->completed, 0U, memory_order_release);
  workers[0].test = test;
  workers[0].controller = 0;
  workers[1].test = test;
  workers[1].controller = 1;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  return coakka_http_test_run_threads(dispatch_worker, contexts, 2U);
}

int main(void) {
  coakka_http_server_options_t options;
  coakka_http_route_t route;
  test_state_t test;
  coakka_http_result_t result;
  worker_state_t recovery;
#if defined(_WIN32)
  WSADATA socket_data;
  if (WSAStartup(MAKEWORD(2, 2), &socket_data) != 0) {
    return EXIT_FAILURE;
  }
#endif

  memset(&test, 0, sizeof(test));
  atomic_init(&test.mode, 0U);
  atomic_init(&test.entered, 0U);
  atomic_init(&test.release, 0U);
  atomic_init(&test.sent, 0U);
  atomic_init(&test.completed, 0U);
  atomic_init(&test.busy, 0U);
  atomic_init(&test.failures, 0U);
  coakka_http_server_options_init(&options);
  options.worker_count = 1U;
  options.max_connections = TEST_PRESSURE_CLIENTS;
  options.max_active_requests = 2U;
  options.request_queue_capacity = 2U;
  options.response_queue_capacity = 2U;
  options.max_request_body_bytes = 1024U;
  options.max_response_body_bytes = 1024U;
  options.request_timeout_ms = 5000U;
  options.shutdown_timeout_ms = 200U;
  coakka_http_route_init(&route);
  route.route_id = UINT64_C(91);
  route.method = bytes("GET");
  route.path = bytes("/pressure");
  route.context = &test;
  route.handler = handle_request;
  result = coakka_http_server_create(&options, &route, 1U, &test.server);
  if (result.code != COAKKA_HTTP_RESULT_OK || test.server == NULL ||
      coakka_http_server_start(test.server).code != COAKKA_HTTP_RESULT_OK ||
      coakka_http_server_port(test.server, &test.port).code !=
          COAKKA_HTTP_RESULT_OK ||
      run_pressure(&test) != 0) {
    fprintf(stderr,
            "native pressure setup failed: create=%s busy=%u failures=%u\n",
            coakka_http_result_code_name(result.code),
            atomic_load_explicit(&test.busy, memory_order_relaxed),
            atomic_load_explicit(&test.failures, memory_order_relaxed));
    (void)coakka_http_server_destroy(&test.server);
    return EXIT_FAILURE;
  }

  atomic_store_explicit(&test.mode, 0U, memory_order_release);
  recovery.test = &test;
  recovery.controller = 0;
  if (client_worker(&recovery) != 0 || run_stop_retry(&test) != 0 ||
      coakka_http_server_destroy(&test.server).code != COAKKA_HTTP_RESULT_OK ||
      test.server != NULL ||
      atomic_load_explicit(&test.failures, memory_order_relaxed) != 0U) {
    fprintf(stderr,
            "native shutdown retry failed: first=%s retry=%s failures=%u\n",
            coakka_http_result_code_name(test.first_stop.code),
            coakka_http_result_code_name(test.retry_stop.code),
            atomic_load_explicit(&test.failures, memory_order_relaxed));
    (void)coakka_http_server_destroy(&test.server);
    return EXIT_FAILURE;
  }
#if defined(_WIN32)
  (void)WSACleanup();
#endif
  printf("native-pressure=pass busy=%u first_stop=%s retry_stop=%s\n",
         atomic_load_explicit(&test.busy, memory_order_relaxed),
         coakka_http_result_code_name(test.first_stop.code),
         coakka_http_result_code_name(test.retry_stop.code));
  return EXIT_SUCCESS;
}
