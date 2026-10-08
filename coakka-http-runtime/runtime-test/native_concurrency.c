#if defined(__linux__)
#define _POSIX_C_SOURCE 200809L
#endif
#include <coakka/http/http.h>

#include "test_threads.h"

#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

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

enum { TEST_WORKERS = 4, TEST_REQUESTS_PER_WORKER = 32 };
enum { TEST_MONITOR_ADMISSION_ATTEMPTS = 1000000 };

static void yield_thread(void) {
#if defined(_WIN32)
  Sleep(0U);
#else
  (void)sched_yield();
#endif
}

/* Only RETAINED proves that this cold publication was not admitted. Never
 * infer retryability from timing, text, INTERNAL, or an ambiguous outcome.
 * Bound the fixture's retry by one monotonic deadline, allowing the owning
 * loop to finish its asynchronous retirement observation between attempts. */
static uint64_t monotonic_ms(void) {
#if defined(_WIN32)
  return (uint64_t)GetTickCount64();
#else
  struct timespec now;
  if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
    abort();
  }
  return (uint64_t)now.tv_sec * UINT64_C(1000) +
         (uint64_t)now.tv_nsec / UINT64_C(1000000);
#endif
}

static coakka_http_result_t rebind_when_retired(
    coakka_http_server_t *server, const coakka_http_route_rebind_t *request,
    coakka_http_route_rebind_outcome_t *outcome) {
  const uint64_t deadline = monotonic_ms() + UINT64_C(2000);
  coakka_http_result_t result;
  for (;;) {
    const uint64_t now = monotonic_ms();
    result = coakka_http_server_rebind_handler(
        server, request, now < deadline ? deadline - now : 0U, outcome);
    if (result.code != COAKKA_HTTP_RESULT_RETAINED || monotonic_ms() >= deadline) {
      return result;
    }
#if defined(_WIN32)
    Sleep(1U);
#else
    const struct timespec pause = {0, 1000000};
    (void)nanosleep(&pause, NULL);
#endif
  }
}

typedef struct server_state {
  atomic_uint calls;
  atomic_uint failures;
} server_state_t;

typedef struct client_state {
  uint16_t port;
  uint32_t requests;
  uint32_t failures;
} client_state_t;

typedef struct monitor_race_state {
  coakka_http_server_t *server;
  atomic_uint waiter_ready;
  int stop;
  coakka_http_result_t waiter_result;
  coakka_http_result_t control_result;
} monitor_race_state_t;

typedef struct monitor_worker_state {
  monitor_race_state_t *race;
  int waiter;
} monitor_worker_state_t;

typedef struct rebind_race_state {
  coakka_http_server_t *server;
  uint16_t port;
  atomic_uint entered;
  atomic_uint release;
  coakka_http_route_rebind_outcome_t outcome;
} rebind_race_state_t;

typedef struct rebind_worker_state {
  rebind_race_state_t *race;
  int controller;
} rebind_worker_state_t;

typedef struct response_stream_race_state {
  coakka_http_server_t *server;
  uint16_t port;
  atomic_uintptr_t stream;
  atomic_uint failures;
} response_stream_race_state_t;

typedef struct response_stream_worker_state {
  response_stream_race_state_t *race;
  int writer;
} response_stream_worker_state_t;

typedef struct websocket_race_state {
  coakka_http_server_t *server;
  uint16_t port;
  atomic_uint failures;
} websocket_race_state_t;

typedef struct websocket_worker_state {
  websocket_race_state_t *race;
  int reader;
} websocket_worker_state_t;

typedef struct websocket_stop_race_state {
  coakka_http_server_t *server;
  coakka_http_socket_event_t *event;
  atomic_uint waiter_ready;
  coakka_http_result_t waiter_result;
  coakka_http_result_t release_result;
  coakka_http_result_t stop_result;
} websocket_stop_race_state_t;

typedef struct websocket_stop_worker_state {
  websocket_stop_race_state_t *race;
  int waiter;
} websocket_stop_worker_state_t;

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : (uint64_t)strlen(value);
  return result;
}

static int bytes_equal(coakka_http_bytes_t value, const char *expected) {
  const size_t size = strlen(expected);
  return value.size == (uint64_t)size &&
         (size == 0U || memcmp(value.data, expected, size) == 0);
}

static void handle_request(void *opaque, coakka_http_request_t *request) {
  server_state_t *state = (server_state_t *)opaque;
  coakka_http_response_t response;
  coakka_http_result_t submitted;

  (void)atomic_fetch_add_explicit(&state->calls, 1U, memory_order_relaxed);
  if (coakka_http_request_route_id(request) != UINT64_C(73) ||
      !bytes_equal(coakka_http_request_method(request), "GET") ||
      !bytes_equal(coakka_http_request_target(request), "/parallel") ||
      coakka_http_request_cancelled(request) != 0U) {
    (void)atomic_fetch_add_explicit(&state->failures, 1U, memory_order_relaxed);
  }
  coakka_http_response_init(&response);
  response.body = bytes("coakka-parallel-response");
  submitted = coakka_http_request_respond(request, &response);
  if (submitted.code != COAKKA_HTTP_RESULT_OK) {
    (void)atomic_fetch_add_explicit(&state->failures, 1U, memory_order_relaxed);
  }
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

static int exchange_expected_fields(uint16_t port, const char *status,
                                    const char *body, const char *trailer) {
  static const char request[] = "GET /parallel HTTP/1.1\r\n"
                                "Host: 127.0.0.1\r\n"
                                "Connection: close\r\n\r\n";
  test_socket_t socket_value = connect_loopback(port);
  char response[2048];
  size_t sent = 0U;
  size_t used = 0U;

  if (socket_value == TEST_INVALID_SOCKET) {
    return -1;
  }
  while (sent < sizeof(request) - 1U) {
#if defined(_WIN32)
    const int count = send(socket_value, request + sent,
                           (int)(sizeof(request) - 1U - sent), 0);
#else
    const ssize_t count =
        send(socket_value, request + sent, sizeof(request) - 1U - sent, 0);
#endif
    if (count <= 0) {
      close_socket(socket_value);
      return -1;
    }
    sent += (size_t)count;
  }
  while (used + 1U < sizeof(response)) {
#if defined(_WIN32)
    const int count = recv(socket_value, response + used,
                           (int)(sizeof(response) - used - 1U), 0);
#else
    const ssize_t count =
        recv(socket_value, response + used, sizeof(response) - used - 1U, 0);
#endif
    if (count <= 0) {
      break;
    }
    used += (size_t)count;
  }
  close_socket(socket_value);
  response[used] = '\0';
  return strstr(response, status) != NULL && strstr(response, body) != NULL &&
                 (trailer == NULL || strstr(response, trailer) != NULL)
             ? 0
             : -1;
}

static int exchange_expected(uint16_t port, const char *status,
                             const char *body) {
  return exchange_expected_fields(port, status, body, NULL);
}

static int exchange(uint16_t port) {
  return exchange_expected(port, "HTTP/1.1 200", "coakka-parallel-response");
}

static int send_all(test_socket_t socket_value, const uint8_t *data,
                    size_t size) {
  size_t sent = 0U;
  while (sent < size) {
#if defined(_WIN32)
    const int count =
        send(socket_value, (const char *)data + sent, (int)(size - sent), 0);
#else
    const ssize_t count = send(socket_value, data + sent, size - sent, 0);
#endif
    if (count <= 0) {
      return -1;
    }
    sent += (size_t)count;
  }
  return 0;
}

static int contains_bytes(const uint8_t *haystack, size_t haystack_size,
                          const uint8_t *needle, size_t needle_size) {
  size_t offset;
  if (needle_size == 0U || needle_size > haystack_size) {
    return 0;
  }
  for (offset = 0U; offset <= haystack_size - needle_size; ++offset) {
    if (memcmp(haystack + offset, needle, needle_size) == 0) {
      return 1;
    }
  }
  return 0;
}

static uint8_t ascii_lower(uint8_t value) {
  return value >= (uint8_t)'A' && value <= (uint8_t)'Z'
             ? (uint8_t)(value + ((uint8_t)'a' - (uint8_t)'A'))
             : value;
}

static int contains_ascii_case_insensitive(const uint8_t *haystack,
                                           size_t haystack_size,
                                           const char *needle) {
  const size_t needle_size = strlen(needle);
  size_t offset;
  size_t index;
  if (needle_size == 0U || needle_size > haystack_size) {
    return 0;
  }
  for (offset = 0U; offset <= haystack_size - needle_size; ++offset) {
    for (index = 0U; index < needle_size; ++index) {
      if (ascii_lower(haystack[offset + index]) !=
          ascii_lower((uint8_t)needle[index])) {
        break;
      }
    }
    if (index == needle_size) {
      return 1;
    }
  }
  return 0;
}

static int client_worker(void *opaque) {
  client_state_t *state = (client_state_t *)opaque;
  uint32_t index;
  for (index = 0U; index < state->requests; ++index) {
    if (exchange(state->port) != 0) {
      state->failures += 1U;
    }
  }
  return state->failures == 0U ? 0 : -1;
}

static void handle_captured_request(void *opaque,
                                    coakka_http_request_t *request) {
  rebind_race_state_t *race = (rebind_race_state_t *)opaque;
  coakka_http_response_t response;

  atomic_store_explicit(&race->entered, 1U, memory_order_release);
  while (atomic_load_explicit(&race->release, memory_order_acquire) == 0U) {
    yield_thread();
  }
  coakka_http_response_init(&response);
  response.body = bytes("captured-old-response");
  (void)coakka_http_request_respond(request, &response);
}

static void handle_replacement_request(void *opaque,
                                       coakka_http_request_t *request) {
  coakka_http_response_t response;
  (void)opaque;
  coakka_http_response_init(&response);
  response.status_code = 201U;
  response.body = bytes("replacement-new-response");
  (void)coakka_http_request_respond(request, &response);
}

static int rebind_worker(void *opaque) {
  rebind_worker_state_t *worker = (rebind_worker_state_t *)opaque;
  rebind_race_state_t *race = worker->race;
  coakka_http_route_rebind_t request;
  coakka_http_result_t result;
  uint32_t attempt;
  int exchanged;

  if (worker->controller == 0) {
    return exchange_expected(race->port, "HTTP/1.1 200",
                             "captured-old-response");
  }
  for (attempt = 0U; attempt < TEST_MONITOR_ADMISSION_ATTEMPTS; ++attempt) {
    if (atomic_load_explicit(&race->entered, memory_order_acquire) != 0U) {
      break;
    }
    yield_thread();
  }
  if (attempt == TEST_MONITOR_ADMISSION_ATTEMPTS) {
    atomic_store_explicit(&race->release, 1U, memory_order_release);
    return -1;
  }
  coakka_http_route_rebind_init(&request);
  request.activation_id = 2U;
  request.expected_route_generation = 1U;
  request.route_id = 73U;
  request.expected_binding_revision = 2U;
  request.new_handler_binding_id = 75U;
  coakka_http_route_rebind_outcome_init(&race->outcome);
  result = coakka_http_server_rebind_handler(race->server, &request, 2000U,
                                             &race->outcome);
  exchanged = result.code == COAKKA_HTTP_RESULT_OK &&
                      race->outcome.code == COAKKA_HTTP_ROUTE_REBIND_APPLIED &&
                      race->outcome.changed != 0U &&
                      race->outcome.effective_handler_binding_id == 75U &&
                      race->outcome.request_retirement.state == COAKKA_HTTP_REQUEST_RETIREMENT_CAPTURED
                  ? exchange_expected(race->port, "HTTP/1.1 201",
                                      "replacement-new-response")
                  : -1;
  if (exchanged == 0) {
    /* Keep the old callback captured while attempting a distinct activation.
     * Its deterministic refusal must retain the typed cause and leave the
     * caller's outcome untouched. Activation 3 remains available afterwards. */
    coakka_http_route_rebind_outcome_t refused;
    coakka_http_route_rebind_outcome_t before;
    coakka_http_route_rebind_outcome_init(&refused);
    before = refused;
    request.activation_id = 3U;
    request.expected_binding_revision = race->outcome.effective_binding_revision;
    result = coakka_http_server_rebind_handler(race->server, &request, 0U, &refused);
    if (result.code != COAKKA_HTTP_RESULT_RETAINED ||
        memcmp(&refused, &before, sizeof(refused)) != 0) {
      fprintf(stderr, "captured-handler refusal: code=%u\n", result.code);
      exchanged = -1;
    }
  }
  atomic_store_explicit(&race->release, 1U, memory_order_release);
  return exchanged;
}

static int run_rebind_race(rebind_race_state_t *race,
                           coakka_http_server_t *server, uint16_t port,
                           coakka_http_route_rebind_outcome_t *out_outcome) {
  rebind_worker_state_t workers[2];
  void *contexts[2];
  coakka_http_route_rebind_t request;
  coakka_http_route_rebind_outcome_t outcome;
  coakka_http_result_t result;
  int threaded;

  memset(race, 0, sizeof(*race));
  race->server = server;
  race->port = port;
  atomic_init(&race->entered, 0U);
  atomic_init(&race->release, 0U);
  result = coakka_http_server_prepare_handler(server, 74U, race,
                                              handle_captured_request);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    return -1;
  }
  result = coakka_http_server_prepare_handler(server, 75U, race,
                                              handle_replacement_request);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    return -1;
  }
  coakka_http_route_rebind_init(&request);
  request.activation_id = 1U;
  request.expected_route_generation = 1U;
  request.route_id = 73U;
  request.expected_binding_revision = 1U;
  request.new_handler_binding_id = 74U;
  coakka_http_route_rebind_outcome_init(&outcome);
  result = rebind_when_retired(server, &request, &outcome);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED ||
      outcome.changed == 0U) {
    return -1;
  }
  workers[0].race = race;
  workers[0].controller = 0;
  workers[1].race = race;
  workers[1].controller = 1;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  threaded = coakka_http_test_run_threads(rebind_worker, contexts, 2U);
  *out_outcome = race->outcome;
  return threaded;
}

static void handle_response_stream_request(void *opaque,
                                           coakka_http_request_t *request) {
  response_stream_race_state_t *race = (response_stream_race_state_t *)opaque;
  coakka_http_response_stream_t *stream = NULL;
  coakka_http_response_t head;
  coakka_http_header_t header;
  coakka_http_result_t result;

  coakka_http_response_init(&head);
  header.name = bytes("content-type");
  header.value = bytes("text/plain");
  head.headers = &header;
  head.header_count = 1U;
  result = coakka_http_request_start_response_stream(request, &head, &stream);
  if (result.code != COAKKA_HTTP_RESULT_OK || stream == NULL) {
    coakka_http_response_t fallback;
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    coakka_http_response_init(&fallback);
    fallback.status_code = 500U;
    fallback.body = bytes("stream-start-failed");
    (void)coakka_http_request_respond(request, &fallback);
    return;
  }
  atomic_store_explicit(&race->stream, (uintptr_t)stream, memory_order_release);
}

static int response_stream_worker(void *opaque) {
  response_stream_worker_state_t *worker =
      (response_stream_worker_state_t *)opaque;
  response_stream_race_state_t *race = worker->race;
  coakka_http_response_stream_t *stream = NULL;
  coakka_http_header_t trailer;
  coakka_http_result_t result;
  uint32_t attempt;

  if (worker->writer == 0) {
    return exchange_expected_fields(race->port, "HTTP/1.1 200", "stream-body",
                                    "x-stream-end: done");
  }
  for (attempt = 0U; attempt < TEST_MONITOR_ADMISSION_ATTEMPTS; ++attempt) {
    const uintptr_t published =
        atomic_load_explicit(&race->stream, memory_order_acquire);
    if (published != (uintptr_t)0U) {
      stream = (coakka_http_response_stream_t *)atomic_exchange_explicit(
          &race->stream, (uintptr_t)0U, memory_order_acq_rel);
      if (stream != NULL) {
        break;
      }
    }
    yield_thread();
  }
  if (stream == NULL) {
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    return -1;
  }

  result = coakka_http_response_stream_wait_writable(stream, 5000U);
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_response_stream_write(stream, bytes("stream-body"));
  }
  trailer.name = bytes("x-stream-end");
  trailer.value = bytes("done");
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_response_stream_finish(&stream, &trailer, 1U);
  }
  if (result.code != COAKKA_HTTP_RESULT_OK || stream != NULL) {
    fprintf(stderr, "response stream writer: code=%u retained=%d\n",
            result.code, stream != NULL);
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    coakka_http_response_stream_release(&stream);
    return -1;
  }
  return 0;
}

static int run_response_stream_race(
    response_stream_race_state_t *race, coakka_http_server_t *server,
    uint16_t port, const coakka_http_route_rebind_outcome_t *previous_outcome,
    coakka_http_route_rebind_outcome_t *out_outcome) {
  response_stream_worker_state_t workers[2];
  void *contexts[2];
  coakka_http_route_rebind_t request;
  coakka_http_route_rebind_outcome_t outcome;
  coakka_http_result_t result;
  int threaded;

  memset(race, 0, sizeof(*race));
  race->server = server;
  race->port = port;
  atomic_init(&race->stream, (uintptr_t)0U);
  atomic_init(&race->failures, 0U);
  result = coakka_http_server_prepare_handler(server, 76U, race,
                                              handle_response_stream_request);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "response stream prepare: code=%u\n", result.code);
    return -1;
  }
  coakka_http_route_rebind_init(&request);
  request.activation_id = 3U;
  request.expected_route_generation = 1U;
  request.route_id = 73U;
  request.expected_binding_revision =
      previous_outcome->effective_binding_revision;
  request.new_handler_binding_id = 76U;
  coakka_http_route_rebind_outcome_init(&outcome);
  result = rebind_when_retired(server, &request, &outcome);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED ||
      outcome.changed == 0U || outcome.effective_handler_binding_id != 76U) {
    fprintf(stderr, "response stream rebind: result=%u detail=%s outcome=%u changed=%u "
                    "binding=%llu expected_revision=%llu revision=%llu\n",
            result.code, result.detail, outcome.code, outcome.changed,
            (unsigned long long)outcome.effective_handler_binding_id,
            (unsigned long long)request.expected_binding_revision,
            (unsigned long long)outcome.effective_binding_revision);
    return -1;
  }

  workers[0].race = race;
  workers[0].writer = 0;
  workers[1].race = race;
  workers[1].writer = 1;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  threaded = coakka_http_test_run_threads(response_stream_worker, contexts, 2U);
  if (threaded != 0 ||
      atomic_load_explicit(&race->failures, memory_order_relaxed) != 0U) {
    fprintf(stderr, "response stream threads: result=%d failures=%u\n",
            threaded, atomic_load_explicit(&race->failures, memory_order_relaxed));
    coakka_http_response_stream_t *stream =
        (coakka_http_response_stream_t *)atomic_exchange_explicit(
            &race->stream, (uintptr_t)0U, memory_order_acq_rel);
    coakka_http_response_stream_release(&stream);
    return -1;
  }
  *out_outcome = outcome;
  return 0;
}

static void handle_sse_request(void *opaque, coakka_http_request_t *request) {
  response_stream_race_state_t *race = (response_stream_race_state_t *)opaque;
  coakka_http_response_stream_t *stream = NULL;
  coakka_http_header_t header;
  coakka_http_result_t result;

  header.name = bytes("x-events");
  header.value = bytes("ordered");
  result = coakka_http_request_start_sse(request, &header, 1U, &stream);
  if (result.code != COAKKA_HTTP_RESULT_OK || stream == NULL) {
    coakka_http_response_t fallback;
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    coakka_http_response_init(&fallback);
    fallback.status_code = 500U;
    fallback.body = bytes("sse-start-failed");
    (void)coakka_http_request_respond(request, &fallback);
    return;
  }
  atomic_store_explicit(&race->stream, (uintptr_t)stream, memory_order_release);
}

static int sse_worker(void *opaque) {
  response_stream_worker_state_t *worker =
      (response_stream_worker_state_t *)opaque;
  response_stream_race_state_t *race = worker->race;
  coakka_http_response_stream_t *stream = NULL;
  coakka_http_sse_event_t event;
  coakka_http_result_t result;
  uint32_t attempt;

  if (worker->writer == 0) {
    return exchange_expected_fields(race->port, "HTTP/1.1 200",
                                    "content-type: text/event-stream",
                                    "data: native-ready");
  }
  for (attempt = 0U; attempt < TEST_MONITOR_ADMISSION_ATTEMPTS; ++attempt) {
    const uintptr_t published =
        atomic_load_explicit(&race->stream, memory_order_acquire);
    if (published != (uintptr_t)0U) {
      stream = (coakka_http_response_stream_t *)atomic_exchange_explicit(
          &race->stream, (uintptr_t)0U, memory_order_acq_rel);
      if (stream != NULL) {
        break;
      }
    }
    yield_thread();
  }
  if (stream == NULL) {
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    return -1;
  }
  result = coakka_http_response_stream_wait_writable(stream, 5000U);
  coakka_http_sse_event_init(&event);
  event.data = bytes("native-ready");
  event.event_type = bytes("state");
  event.id = bytes("1");
  event.retry_ms = 1500U;
  event.has_event_type = 1U;
  event.has_id = 1U;
  event.has_retry = 1U;
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_response_stream_write_sse(stream, &event);
  }
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_response_stream_finish(&stream, NULL, 0U);
  }
  if (result.code != COAKKA_HTTP_RESULT_OK || stream != NULL) {
    (void)atomic_fetch_add_explicit(&race->failures, 1U, memory_order_relaxed);
    coakka_http_response_stream_release(&stream);
    return -1;
  }
  return 0;
}

static int run_sse_race(
    response_stream_race_state_t *race, coakka_http_server_t *server,
    uint16_t port, const coakka_http_route_rebind_outcome_t *previous_outcome,
    coakka_http_route_rebind_outcome_t *out_outcome) {
  response_stream_worker_state_t workers[2];
  void *contexts[2];
  coakka_http_route_rebind_t request;
  coakka_http_route_rebind_outcome_t outcome;
  coakka_http_result_t result;
  int threaded;

  memset(race, 0, sizeof(*race));
  race->server = server;
  race->port = port;
  atomic_init(&race->stream, (uintptr_t)0U);
  atomic_init(&race->failures, 0U);
  result = coakka_http_server_prepare_handler(server, 77U, race,
                                              handle_sse_request);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    return -1;
  }
  coakka_http_route_rebind_init(&request);
  request.activation_id = 4U;
  request.expected_route_generation = 1U;
  request.route_id = 73U;
  request.expected_binding_revision =
      previous_outcome->effective_binding_revision;
  request.new_handler_binding_id = 77U;
  coakka_http_route_rebind_outcome_init(&outcome);
  result = rebind_when_retired(server, &request, &outcome);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED ||
      outcome.changed == 0U || outcome.effective_handler_binding_id != 77U) {
    return -1;
  }
  workers[0].race = race;
  workers[0].writer = 0;
  workers[1].race = race;
  workers[1].writer = 1;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  threaded = coakka_http_test_run_threads(sse_worker, contexts, 2U);
  if (threaded != 0 ||
      atomic_load_explicit(&race->failures, memory_order_relaxed) != 0U) {
    coakka_http_response_stream_t *stream =
        (coakka_http_response_stream_t *)atomic_exchange_explicit(
            &race->stream, (uintptr_t)0U, memory_order_acq_rel);
    coakka_http_response_stream_release(&stream);
    return -1;
  }
  *out_outcome = outcome;
  return 0;
}

static void handle_websocket_request(void *opaque,
                                     coakka_http_request_t *request) {
  websocket_race_state_t *race = (websocket_race_state_t *)opaque;
  const coakka_http_result_t accepted =
      coakka_http_request_accept_websocket(request, bytes("coakka.test"));
  if (accepted.code != COAKKA_HTTP_RESULT_OK) {
    (void)atomic_fetch_add_explicit(&race->failures, 1U,
                                    memory_order_relaxed);
  }
}

static int websocket_client_worker(websocket_race_state_t *race) {
  static const char request[] =
      "GET /parallel HTTP/1.1\r\n"
      "Host: 127.0.0.1\r\n"
      "Upgrade: websocket\r\n"
      "Connection: Upgrade\r\n"
      "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
      "Sec-WebSocket-Version: 13\r\n"
      "Sec-WebSocket-Protocol: coakka.test\r\n\r\n";
  static const uint8_t client_text[] = {0x81U, 0x85U, 0x01U, 0x02U, 0x03U,
                                        0x04U, 0x69U, 0x67U, 0x6fU, 0x68U,
                                        0x6eU};
  static const uint8_t welcome[] = {0x81U, 0x07U, 'w', 'e', 'l', 'c', 'o', 'm',
                                    'e'};
  static const uint8_t echo[] = {0x81U, 0x05U, 'w', 'o', 'r', 'l', 'd'};
  static const uint8_t closed[] = {0x88U, 0x06U, 0x03U, 0xe8U,
                                   'd',   'o',   'n',   'e'};
  test_socket_t socket_value = connect_loopback(race->port);
  uint8_t response[4096];
  size_t used = 0U;
  int upgraded = 0;

  if (socket_value == TEST_INVALID_SOCKET ||
      send_all(socket_value, (const uint8_t *)request, sizeof(request) - 1U) !=
          0) {
    if (socket_value != TEST_INVALID_SOCKET) {
      close_socket(socket_value);
    }
    return -1;
  }
  while (used < sizeof(response)) {
#if defined(_WIN32)
    const int count = recv(socket_value, (char *)response + used,
                           (int)(sizeof(response) - used), 0);
#else
    const ssize_t count =
        recv(socket_value, response + used, sizeof(response) - used, 0);
#endif
    if (count <= 0) {
      break;
    }
    used += (size_t)count;
    if (contains_bytes(response, used, (const uint8_t *)"\r\n\r\n", 4U) !=
        0) {
      upgraded = 1;
      break;
    }
  }
  if (upgraded == 0 ||
      contains_bytes(response, used,
                     (const uint8_t *)"HTTP/1.1 101 Switching Protocols",
                     sizeof("HTTP/1.1 101 Switching Protocols") - 1U) == 0 ||
      contains_ascii_case_insensitive(
          response, used, "sec-websocket-protocol: coakka.test") == 0 ||
      send_all(socket_value, client_text, sizeof(client_text)) != 0) {
    fprintf(stderr, "WebSocket client upgrade failed: bytes=%u upgraded=%d\n",
            (unsigned int)used, upgraded);
    close_socket(socket_value);
    return -1;
  }
  while (used < sizeof(response) &&
         contains_bytes(response, used, closed, sizeof(closed)) == 0) {
#if defined(_WIN32)
    const int count = recv(socket_value, (char *)response + used,
                           (int)(sizeof(response) - used), 0);
#else
    const ssize_t count =
        recv(socket_value, response + used, sizeof(response) - used, 0);
#endif
    if (count <= 0) {
      break;
    }
    used += (size_t)count;
  }
  close_socket(socket_value);
  if (contains_bytes(response, used, welcome, sizeof(welcome)) == 0 ||
      contains_bytes(response, used, echo, sizeof(echo)) == 0 ||
      contains_bytes(response, used, closed, sizeof(closed)) == 0) {
    fprintf(stderr,
            "WebSocket client frames missing: bytes=%u welcome=%d echo=%d "
            "close=%d\n",
            (unsigned int)used,
            contains_bytes(response, used, welcome, sizeof(welcome)),
            contains_bytes(response, used, echo, sizeof(echo)),
            contains_bytes(response, used, closed, sizeof(closed)));
    return -1;
  }
  return 0;
}

static test_socket_t open_websocket_for_stop(uint16_t port) {
  static const char request[] =
      "GET /parallel HTTP/1.1\r\n"
      "Host: 127.0.0.1\r\n"
      "Upgrade: websocket\r\n"
      "Connection: Upgrade\r\n"
      "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
      "Sec-WebSocket-Version: 13\r\n"
      "Sec-WebSocket-Protocol: coakka.test\r\n\r\n";
  test_socket_t socket_value = connect_loopback(port);
  uint8_t response[1024];
  size_t used = 0U;

  if (socket_value == TEST_INVALID_SOCKET ||
      send_all(socket_value, (const uint8_t *)request, sizeof(request) - 1U) !=
          0) {
    if (socket_value != TEST_INVALID_SOCKET) {
      close_socket(socket_value);
    }
    return TEST_INVALID_SOCKET;
  }
  while (used < sizeof(response) &&
         contains_bytes(response, used, (const uint8_t *)"\r\n\r\n", 4U) ==
             0) {
#if defined(_WIN32)
    const int count = recv(socket_value, (char *)response + used,
                           (int)(sizeof(response) - used), 0);
#else
    const ssize_t count =
        recv(socket_value, response + used, sizeof(response) - used, 0);
#endif
    if (count <= 0) {
      close_socket(socket_value);
      return TEST_INVALID_SOCKET;
    }
    used += (size_t)count;
  }
  if (contains_bytes(response, used,
                     (const uint8_t *)"HTTP/1.1 101 Switching Protocols",
                     sizeof("HTTP/1.1 101 Switching Protocols") - 1U) == 0 ||
      contains_ascii_case_insensitive(
          response, used, "sec-websocket-protocol: coakka.test") == 0) {
    close_socket(socket_value);
    return TEST_INVALID_SOCKET;
  }
  return socket_value;
}

static int websocket_reader_worker(websocket_race_state_t *race) {
  coakka_http_socket_event_t event;
  coakka_http_websocket_id_t session;
  coakka_http_result_t result;

  coakka_http_socket_event_init(&event);
  result = coakka_http_server_take_websocket(race->server, 5000U, &event);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      event.kind != COAKKA_HTTP_WEBSOCKET_OPEN ||
      event.data_size != sizeof("coakka.test") - 1U ||
      memcmp(event.data, "coakka.test", sizeof("coakka.test") - 1U) != 0) {
    fprintf(stderr,
            "WebSocket open event failed: result=%s kind=%u bytes=%llu\n",
            coakka_http_result_code_name(result.code), (unsigned int)event.kind,
            (unsigned long long)event.data_size);
    if (result.code == COAKKA_HTTP_RESULT_OK) {
      (void)coakka_http_server_release_websocket(race->server, &event);
    }
    return -1;
  }
  session = event.session;
  result = coakka_http_server_release_websocket(race->server, &event);
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_server_send_websocket(
        race->server, session, COAKKA_HTTP_WEBSOCKET_TEXT, bytes("welcome"));
  }
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "WebSocket welcome failed: %s\n",
            coakka_http_result_code_name(result.code));
    return -1;
  }

  coakka_http_socket_event_init(&event);
  result = coakka_http_server_take_websocket(race->server, 5000U, &event);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      event.kind != COAKKA_HTTP_WEBSOCKET_TEXT ||
      event.data_size != sizeof("hello") - 1U ||
      memcmp(event.data, "hello", sizeof("hello") - 1U) != 0) {
    fprintf(stderr,
            "WebSocket text event failed: result=%s kind=%u bytes=%llu\n",
            coakka_http_result_code_name(result.code), (unsigned int)event.kind,
            (unsigned long long)event.data_size);
    if (result.code == COAKKA_HTTP_RESULT_OK) {
      (void)coakka_http_server_release_websocket(race->server, &event);
    }
    return -1;
  }
  result = coakka_http_server_release_websocket(race->server, &event);
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_server_send_websocket(
        race->server, session, COAKKA_HTTP_WEBSOCKET_TEXT, bytes("world"));
  }
  if (result.code == COAKKA_HTTP_RESULT_OK) {
    result = coakka_http_server_close_websocket(race->server, session, 1000U,
                                                bytes("done"));
  }
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "WebSocket echo/close failed: %s\n",
            coakka_http_result_code_name(result.code));
    return -1;
  }

  coakka_http_socket_event_init(&event);
  result = coakka_http_server_take_websocket(race->server, 5000U, &event);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      event.kind != COAKKA_HTTP_WEBSOCKET_CLOSE || event.close_code != 1000U) {
    fprintf(stderr,
            "WebSocket close event failed: result=%s kind=%u code=%u\n",
            coakka_http_result_code_name(result.code), (unsigned int)event.kind,
            (unsigned int)event.close_code);
    if (result.code == COAKKA_HTTP_RESULT_OK) {
      (void)coakka_http_server_release_websocket(race->server, &event);
    }
    return -1;
  }
  result = coakka_http_server_release_websocket(race->server, &event);
  return result.code == COAKKA_HTTP_RESULT_OK ? 0 : -1;
}

static int websocket_worker(void *opaque) {
  websocket_worker_state_t *worker = (websocket_worker_state_t *)opaque;
  return worker->reader != 0 ? websocket_reader_worker(worker->race)
                             : websocket_client_worker(worker->race);
}

static int run_websocket_race(
    websocket_race_state_t *race, coakka_http_server_t *server, uint16_t port,
    const coakka_http_route_rebind_outcome_t *previous_outcome) {
  websocket_worker_state_t workers[2];
  void *contexts[2];
  coakka_http_route_rebind_t request;
  coakka_http_route_rebind_outcome_t outcome;
  coakka_http_result_t result;

  memset(race, 0, sizeof(*race));
  race->server = server;
  race->port = port;
  atomic_init(&race->failures, 0U);
  result = coakka_http_server_prepare_handler(server, 78U, race,
                                              handle_websocket_request);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    return -1;
  }
  coakka_http_route_rebind_init(&request);
  request.activation_id = 5U;
  request.expected_route_generation = 1U;
  request.route_id = 73U;
  request.expected_binding_revision =
      previous_outcome->effective_binding_revision;
  request.new_handler_binding_id = 78U;
  coakka_http_route_rebind_outcome_init(&outcome);
  result = rebind_when_retired(server, &request, &outcome);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED ||
      outcome.changed == 0U || outcome.effective_handler_binding_id != 78U) {
    return -1;
  }
  workers[0].race = race;
  workers[0].reader = 0;
  workers[1].race = race;
  workers[1].reader = 1;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  return coakka_http_test_run_threads(websocket_worker, contexts, 2U) == 0 &&
                 atomic_load_explicit(&race->failures,
                                      memory_order_relaxed) == 0U
             ? 0
             : -1;
}

static int monitor_worker(void *opaque) {
  monitor_worker_state_t *worker = (monitor_worker_state_t *)opaque;
  monitor_race_state_t *race = worker->race;

  if (worker->waiter != 0) {
    atomic_store_explicit(&race->waiter_ready, 1U, memory_order_release);
    race->waiter_result = coakka_http_server_monitor_wait(
        race->server, COAKKA_HTTP_TIMEOUT_FOREVER);
    /* Stop may close call admission before this waiter acquires its lease. */
    return (race->stop != 0 &&
            race->waiter_result.code == COAKKA_HTTP_RESULT_OK) ||
                   race->waiter_result.code == COAKKA_HTTP_RESULT_CANCELLED ||
                   race->waiter_result.code == COAKKA_HTTP_RESULT_CLOSED ||
                   (race->stop != 0 && race->waiter_result.code ==
                                           COAKKA_HTTP_RESULT_INVALID_STATE)
               ? 0
               : -1;
  }

  while (atomic_load_explicit(&race->waiter_ready, memory_order_acquire) ==
         0U) {
    yield_thread();
  }
  race->control_result =
      race->stop != 0 ? coakka_http_server_stop(race->server)
                      : coakka_http_server_monitor_interrupt(race->server);
  return race->control_result.code == COAKKA_HTTP_RESULT_OK ? 0 : -1;
}

static int run_monitor_race(coakka_http_server_t *server, int stop,
                            coakka_http_result_t *waiter_result,
                            coakka_http_result_t *control_result) {
  monitor_race_state_t race;
  monitor_worker_state_t workers[2];
  void *contexts[2];
  int threaded;

  memset(&race, 0, sizeof(race));
  race.server = server;
  race.stop = stop;
  atomic_init(&race.waiter_ready, 0U);
  workers[0].race = &race;
  workers[0].waiter = 1;
  workers[1].race = &race;
  workers[1].waiter = 0;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  threaded = coakka_http_test_run_threads(monitor_worker, contexts, 2U);
  *waiter_result = race.waiter_result;
  *control_result = race.control_result;
  return threaded;
}

static int drain_monitor_signal(coakka_http_server_t *server,
                                coakka_http_result_t *out_result) {
  uint32_t attempt;
  for (attempt = 0U; attempt < TEST_MONITOR_ADMISSION_ATTEMPTS; ++attempt) {
    *out_result = coakka_http_server_monitor_wait(server, 0U);
    if (out_result->code == COAKKA_HTTP_RESULT_TIMEOUT) {
      return 0;
    }
    if (out_result->code != COAKKA_HTTP_RESULT_OK &&
        out_result->code != COAKKA_HTTP_RESULT_CANCELLED) {
      return -1;
    }
    yield_thread();
  }
  return -1;
}

static int websocket_stop_worker(void *opaque) {
  websocket_stop_worker_state_t *worker =
      (websocket_stop_worker_state_t *)opaque;
  websocket_stop_race_state_t *race = worker->race;
  if (worker->waiter != 0) {
    int info_refused = 0;
    atomic_store_explicit(&race->waiter_ready, 1U, memory_order_release);
    race->waiter_result = coakka_http_server_monitor_wait(
        race->server, COAKKA_HTTP_TIMEOUT_FOREVER);
    /* Stop cannot finish until this worker releases its retained socket event.
     * An instance query must refuse lifecycle overlap rather than wait behind
     * stop and form a wait cycle with this owner. Observe the refusal directly;
     * do not infer that stop has entered from a sleep or local readiness flag.
     */
    {
      const uint64_t deadline = monotonic_ms() + UINT64_C(3000);
      coakka_http_runtime_info_t info;
      coakka_http_runtime_info_init(&info);
      const coakka_http_runtime_info_t before = info;
      while (monotonic_ms() < deadline) {
        const coakka_http_result_t queried = coakka_http_server_get_runtime_info(race->server, &info);
        if (queried.code == COAKKA_HTTP_RESULT_RETAINED) {
          info_refused = memcmp(&info, &before, sizeof(info)) == 0;
          break;
        }
        if (queried.code != COAKKA_HTTP_RESULT_OK || info.execution.observed == 0U) break;
        info = before;
        yield_thread();
      }
    }
    race->release_result =
        coakka_http_server_release_websocket(race->server, race->event);
    return (race->waiter_result.code == COAKKA_HTTP_RESULT_OK ||
            race->waiter_result.code == COAKKA_HTTP_RESULT_CANCELLED ||
            race->waiter_result.code == COAKKA_HTTP_RESULT_CLOSED ||
            race->waiter_result.code == COAKKA_HTTP_RESULT_INVALID_STATE) &&
                   race->release_result.code == COAKKA_HTTP_RESULT_OK && info_refused
               ? 0
               : -1;
  }
  while (atomic_load_explicit(&race->waiter_ready, memory_order_acquire) ==
         0U) {
    yield_thread();
  }
  race->stop_result = coakka_http_server_stop(race->server);
  return race->stop_result.code == COAKKA_HTTP_RESULT_OK ? 0 : -1;
}

static int run_websocket_stop_race(coakka_http_server_t *server,
                                   uint16_t port) {
  websocket_stop_race_state_t race;
  websocket_stop_worker_state_t workers[2];
  void *contexts[2];
  coakka_http_socket_event_t event;
  coakka_http_result_t result;
  test_socket_t socket_value = open_websocket_for_stop(port);
  int threaded;

  if (socket_value == TEST_INVALID_SOCKET) {
    return -1;
  }
  coakka_http_socket_event_init(&event);
  result = coakka_http_server_take_websocket(server, 5000U, &event);
  if (result.code != COAKKA_HTTP_RESULT_OK ||
      event.kind != COAKKA_HTTP_WEBSOCKET_OPEN ||
      drain_monitor_signal(server, &result) != 0) {
    if (event.private_lease != 0U) {
      (void)coakka_http_server_release_websocket(server, &event);
    }
    close_socket(socket_value);
    return -1;
  }

  memset(&race, 0, sizeof(race));
  race.server = server;
  race.event = &event;
  atomic_init(&race.waiter_ready, 0U);
  workers[0].race = &race;
  workers[0].waiter = 1;
  workers[1].race = &race;
  workers[1].waiter = 0;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  threaded = coakka_http_test_run_threads(websocket_stop_worker, contexts, 2U);
  close_socket(socket_value);
  if (threaded != 0 || race.stop_result.code != COAKKA_HTTP_RESULT_OK ||
      race.release_result.code != COAKKA_HTTP_RESULT_OK ||
      event.private_lease != 0U) {
    fprintf(stderr,
            "WebSocket stop/release race failed: threads=%d wait=%s "
            "release=%s stop=%s\n",
            threaded, coakka_http_result_code_name(race.waiter_result.code),
            coakka_http_result_code_name(race.release_result.code),
            coakka_http_result_code_name(race.stop_result.code));
    return -1;
  }
  return 0;
}

int main(void) {
  coakka_http_server_options_t options;
  coakka_http_route_t route;
  coakka_http_server_t *server = NULL;
  coakka_http_result_t result;
  coakka_http_result_t waiter_result;
  coakka_http_result_t control_result;
  coakka_http_monitor_options_t monitor_options;
  coakka_http_route_rebind_outcome_t rebind_outcome;
  coakka_http_route_rebind_outcome_t response_stream_outcome;
  coakka_http_route_rebind_outcome_t sse_outcome;
  rebind_race_state_t rebind_race;
  response_stream_race_state_t response_stream_race;
  websocket_race_state_t websocket_race;
  server_state_t state;
  client_state_t clients[TEST_WORKERS];
  void *contexts[TEST_WORKERS];
  uint16_t port = 0U;
  size_t index;
  int thread_result;
#if defined(_WIN32)
  WSADATA socket_data;
  if (WSAStartup(MAKEWORD(2, 2), &socket_data) != 0) {
    return EXIT_FAILURE;
  }
#endif

  atomic_init(&state.calls, 0U);
  atomic_init(&state.failures, 0U);
  coakka_http_server_options_init(&options);
  /* The public convenience server reserves WebSocket only when requested. */
  options.reserve_websocket = 1U;
  options.worker_count = TEST_WORKERS;
  options.max_connections = 64U;
  options.max_active_requests = 64U;
  options.request_queue_capacity = 64U;
  options.response_queue_capacity = 64U;
  options.max_request_body_bytes = 1024U;
  options.max_response_body_bytes = 1024U;
  options.request_timeout_ms = 5000U;
  options.shutdown_timeout_ms = 5000U;
  coakka_http_monitor_options_init(&monitor_options);
  monitor_options.collection = COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS;
  monitor_options.event_capacity = 64U;
  monitor_options.max_events_per_read = 16U;
  monitor_options.aggregate_categories =
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_LIFECYCLE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_EXCHANGE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_RESPONSE;
  monitor_options.event_categories = monitor_options.aggregate_categories;
  monitor_options.signal_reserved = 1U;
  options.monitor = &monitor_options;

  coakka_http_route_init(&route);
  route.route_id = UINT64_C(73);
  route.method = bytes("GET");
  route.path = bytes("/parallel");
  route.context = &state;
  route.handler = handle_request;

  result = coakka_http_server_create(&options, &route, 1U, &server);
  if (result.code != COAKKA_HTTP_RESULT_OK || server == NULL) {
    fprintf(stderr, "create failed: %s detail=%s actual=%llu limit=%llu\n",
            coakka_http_result_code_name(result.code), result.detail,
            (unsigned long long)result.actual,
            (unsigned long long)result.limit);
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }
  result = coakka_http_server_start(server);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    fprintf(stderr, "start failed: %s detail=%s actual=%llu limit=%llu\n",
            coakka_http_result_code_name(result.code), result.detail,
            (unsigned long long)result.actual,
            (unsigned long long)result.limit);
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }
  result = coakka_http_server_port(server, &port);
  if (result.code != COAKKA_HTTP_RESULT_OK || port == 0U) {
    fprintf(stderr, "port failed: %s port=%u\n",
            coakka_http_result_code_name(result.code), (unsigned int)port);
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }

  for (index = 0U; index < TEST_WORKERS; ++index) {
    clients[index].port = port;
    clients[index].requests = TEST_REQUESTS_PER_WORKER;
    clients[index].failures = 0U;
    contexts[index] = &clients[index];
  }
  thread_result =
      coakka_http_test_run_threads(client_worker, contexts, TEST_WORKERS);

  coakka_http_route_rebind_outcome_init(&rebind_outcome);
  if (thread_result != 0 ||
      run_rebind_race(&rebind_race, server, port, &rebind_outcome) != 0) {
    fprintf(stderr,
            "handler rebind race failed: threads=%d code=%u effective=%llu "
            "request-retirement=%u\n",
            thread_result, (unsigned int)rebind_outcome.code,
            (unsigned long long)rebind_outcome.effective_handler_binding_id,
            (unsigned int)rebind_outcome.request_retirement.state);
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }

  if (run_response_stream_race(&response_stream_race, server, port,
                               &rebind_outcome,
                               &response_stream_outcome) != 0) {
    fprintf(stderr, "response stream race failed\n");
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }

  if (run_sse_race(&response_stream_race, server, port,
                   &response_stream_outcome, &sse_outcome) != 0) {
    fprintf(stderr, "SSE stream race failed\n");
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }

  if (run_websocket_race(&websocket_race, server, port, &sse_outcome) != 0) {
    fprintf(stderr, "WebSocket lifecycle race failed\n");
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }

  coakka_http_result_init(&waiter_result);
  coakka_http_result_init(&control_result);
  if (drain_monitor_signal(server, &result) != 0 ||
      run_monitor_race(server, 0, &waiter_result, &control_result) != 0 ||
      waiter_result.code != COAKKA_HTTP_RESULT_CANCELLED ||
      control_result.code != COAKKA_HTTP_RESULT_OK ||
      drain_monitor_signal(server, &result) != 0) {
    fprintf(stderr, "monitor race failed: wait=%s control=%s drain=%s\n",
            coakka_http_result_code_name(waiter_result.code),
            coakka_http_result_code_name(control_result.code),
            coakka_http_result_code_name(result.code));
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }
  if (run_websocket_stop_race(server, port) != 0) {
    (void)coakka_http_server_destroy(&server);
#if defined(_WIN32)
    (void)WSACleanup();
#endif
    return EXIT_FAILURE;
  }
  result = coakka_http_server_destroy(&server);
#if defined(_WIN32)
  (void)WSACleanup();
#endif

  if (thread_result != 0 || result.code != COAKKA_HTTP_RESULT_OK ||
      server != NULL ||
      atomic_load_explicit(&state.calls, memory_order_relaxed) !=
          TEST_WORKERS * TEST_REQUESTS_PER_WORKER ||
      atomic_load_explicit(&state.failures, memory_order_relaxed) != 0U) {
    fprintf(stderr,
            "concurrency failed: threads=%d destroy=%s callbacks=%u "
            "handler_failures=%u clients=%u,%u,%u,%u\n",
            thread_result, coakka_http_result_code_name(result.code),
            atomic_load_explicit(&state.calls, memory_order_relaxed),
            atomic_load_explicit(&state.failures, memory_order_relaxed),
            clients[0].failures, clients[1].failures, clients[2].failures,
            clients[3].failures);
    return EXIT_FAILURE;
  }
  printf("{\"schema\":\"coakka.http.native-concurrency.v1\","
         "\"workers\":%u,\"requests\":%u,\"status\":\"pass\"}\n",
         (unsigned int)TEST_WORKERS,
         (unsigned int)(TEST_WORKERS * TEST_REQUESTS_PER_WORKER));
  return EXIT_SUCCESS;
}
