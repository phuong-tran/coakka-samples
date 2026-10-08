#include <coakka/http/http.h>

#include "test_threads.h"

#include <limits.h>
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
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
typedef int test_socket_t;
#define TEST_INVALID_SOCKET (-1)
#endif

typedef struct client_context {
  uint16_t port;
  test_socket_t socket_value;
} client_context_t;

#define REQUIRE(expression)                                                    \
  do {                                                                         \
    if (!(expression)) {                                                       \
      (void)fprintf(stderr, "check failed at line %d: %s\n", __LINE__,         \
                    #expression);                                              \
      exit_code = EXIT_FAILURE;                                                \
      goto cleanup;                                                            \
    }                                                                          \
  } while (0)

#define REQUIRE_OK(expression)                                                 \
  do {                                                                         \
    operation = (expression);                                                  \
    if (operation.code != COAKKA_HTTP_RESULT_OK) {                             \
      (void)fprintf(stderr,                                                    \
                    "%s failed: code=%s actual=%llu limit=%llu detail=%s\n",   \
                    #expression, coakka_http_result_code_name(operation.code), \
                    (unsigned long long)operation.actual,                      \
                    (unsigned long long)operation.limit, operation.detail);    \
      exit_code = EXIT_FAILURE;                                                \
      goto cleanup;                                                            \
    }                                                                          \
  } while (0)

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? UINT64_C(0) : (uint64_t)strlen(value);
  return result;
}

static int wait_for_exchange_reap(coakka_http_core_t *core,
                                  coakka_http_health_t *health) {
  const uint64_t budget_ns = UINT64_C(2000000000);
  uint64_t deadline_ns;
  coakka_http_result_t result;
  coakka_http_health_init(health);
  result = coakka_http_core_health(core, health);
  if (result.code != COAKKA_HTTP_RESULT_OK) {
    (void)fprintf(stderr, "health read failed: %s\n",
                  coakka_http_result_code_name(result.code));
    return 0;
  }
  if (health->observed_monotonic_ns == UINT64_C(0) ||
      health->observed_monotonic_ns > UINT64_MAX - budget_ns) {
    (void)fprintf(stderr, "health observation clock is unavailable\n");
    return 0;
  }
  deadline_ns = health->observed_monotonic_ns + budget_ns;
  /* Terminal observation and request release precede event-loop slot reaping.
   * Probe ACK can itself precede reap in a turn, so inspect each fresh snapshot
   * until the gauge converges, within one deadline and a finite turn budget. */
  for (uint32_t attempt = 0U; attempt <= 128U; ++attempt) {
    uint64_t remaining_ms;
    uint64_t previous_ns;
    if (health->failed_components != 0U ||
        health->observed_monotonic_ns >= deadline_ns) {
      break;
    }
    if (health->server_active_exchanges == 0U) {
      return 1;
    }
    if (attempt == 128U) {
      break;
    }
    remaining_ms =
        (deadline_ns - health->observed_monotonic_ns + UINT64_C(999999)) /
        UINT64_C(1000000);
    previous_ns = health->observed_monotonic_ns;
    result = coakka_http_core_probe_liveness(core, remaining_ms, health);
    if (result.code != COAKKA_HTTP_RESULT_OK ||
        health->observed_monotonic_ns < previous_ns) {
      (void)fprintf(stderr, "health convergence probe failed: %s\n",
                    coakka_http_result_code_name(result.code));
      return 0;
    }
  }
  (void)fprintf(stderr, "exchange reap did not converge: active=%u failed=%u\n",
                (unsigned int)health->server_active_exchanges,
                (unsigned int)health->failed_components);
  return 0;
}

static int bytes_equal(coakka_http_bytes_t value, const char *expected) {
  const size_t expected_size = strlen(expected);
  return value.size == (uint64_t)expected_size &&
         (expected_size == 0U ||
          memcmp(value.data, expected, expected_size) == 0);
}

static uint8_t ascii_lower(uint8_t value) {
  if (value >= (uint8_t)'A' && value <= (uint8_t)'Z') {
    return (uint8_t)(value + ((uint8_t)'a' - (uint8_t)'A'));
  }
  return value;
}

static int bytes_equal_case_insensitive(coakka_http_bytes_t value,
                                        const char *expected) {
  const size_t expected_size = strlen(expected);
  size_t index;
  if (value.size != (uint64_t)expected_size) {
    return 0;
  }
  for (index = 0U; index < expected_size; ++index) {
    if (ascii_lower(value.data[index]) !=
        ascii_lower((uint8_t)expected[index])) {
      return 0;
    }
  }
  return 1;
}

static int request_has_header(const coakka_http_event_t *event,
                              const char *name, const char *value) {
  const uint32_t count = coakka_http_event_request_header_count(event);
  uint32_t index;
  for (index = 0U; index < count; ++index) {
    coakka_http_header_t header;
    memset(&header, 0, sizeof(header));
    if (coakka_http_event_request_header(event, index, &header) != 0U &&
        bytes_equal_case_insensitive(header.name, name) &&
        bytes_equal(header.value, value)) {
      return 1;
    }
  }
  return 0;
}

static int request_has_query_parameter(const coakka_http_event_t *event,
                                       const char *key, const char *value,
                                       uint8_t has_value) {
  const uint32_t count = coakka_http_event_request_query_parameter_count(event);
  uint32_t index;
  for (index = 0U; index < count; ++index) {
    coakka_http_query_parameter_t parameter;
    memset(&parameter, 0, sizeof(parameter));
    if (coakka_http_event_request_query_parameter(event, index, &parameter) !=
            0U &&
        bytes_equal(parameter.encoded_key, key) &&
        bytes_equal(parameter.encoded_value, value) &&
        parameter.has_value == has_value) {
      return 1;
    }
  }
  return 0;
}

static void close_socket(test_socket_t socket_value) {
  if (socket_value == TEST_INVALID_SOCKET) {
    return;
  }
#if defined(_WIN32)
  (void)closesocket(socket_value);
#else
  (void)close(socket_value);
#endif
}

static int socket_runtime_start(void) {
#if defined(_WIN32)
  WSADATA data;
  return WSAStartup(MAKEWORD(2, 2), &data) == 0;
#else
  return 1;
#endif
}

static void socket_runtime_stop(void) {
#if defined(_WIN32)
  (void)WSACleanup();
#endif
}

static test_socket_t connect_loopback(uint16_t port) {
  test_socket_t socket_value;
  struct sockaddr_in address;
#if defined(_WIN32)
  DWORD timeout_ms = 5000U;
#else
  struct timeval timeout;
  timeout.tv_sec = 5;
  timeout.tv_usec = 0;
#endif

  socket_value = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (socket_value == TEST_INVALID_SOCKET) {
    return TEST_INVALID_SOCKET;
  }
#if defined(_WIN32)
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO,
                   (const char *)&timeout_ms, (int)sizeof(timeout_ms));
  (void)setsockopt(socket_value, SOL_SOCKET, SO_SNDTIMEO,
                   (const char *)&timeout_ms, (int)sizeof(timeout_ms));
#else
  (void)setsockopt(socket_value, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   (socklen_t)sizeof(timeout));
  (void)setsockopt(socket_value, SOL_SOCKET, SO_SNDTIMEO, &timeout,
                   (socklen_t)sizeof(timeout));
#endif
  memset(&address, 0, sizeof(address));
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
#if defined(_WIN32)
  if (connect(socket_value, (const struct sockaddr *)&address,
              (int)sizeof(address)) != 0) {
#else
  if (connect(socket_value, (const struct sockaddr *)&address,
              (socklen_t)sizeof(address)) != 0) {
#endif
    close_socket(socket_value);
    return TEST_INVALID_SOCKET;
  }
  return socket_value;
}

static int send_all(test_socket_t socket_value, const char *data, size_t size) {
  size_t offset = 0U;
  while (offset < size) {
#if defined(_WIN32)
    const size_t remaining = size - offset;
    const int chunk = remaining > (size_t)INT_MAX ? INT_MAX : (int)remaining;
    const int sent = send(socket_value, data + offset, chunk, 0);
#else
    const ssize_t sent = send(socket_value, data + offset, size - offset, 0);
#endif
    if (sent <= 0) {
      return 0;
    }
    offset += (size_t)sent;
  }
  return 1;
}

static int read_response(test_socket_t socket_value, char *output,
                         size_t capacity, const char *expected_body) {
  size_t used = 0U;
  while (used + 1U < capacity) {
#if defined(_WIN32)
    const size_t remaining = capacity - used - 1U;
    const int chunk = remaining > (size_t)INT_MAX ? INT_MAX : (int)remaining;
    const int received = recv(socket_value, output + used, chunk, 0);
#else
    const ssize_t received =
        recv(socket_value, output + used, capacity - used - 1U, 0);
#endif
    if (received <= 0) {
      break;
    }
    used += (size_t)received;
    output[used] = '\0';
    if (strstr(output, expected_body) != NULL) {
      return 1;
    }
  }
  output[used] = '\0';
  return 0;
}

static int client_send_request(void *opaque) {
  static const char request[] = "POST /items/a%2Fb?mode=fast&flag HTTP/1.1\r\n"
                                "Host: 127.0.0.1\r\n"
                                "X-Core-Test: installed-package\r\n"
                                "Content-Type: text/plain\r\n"
                                "Content-Length: 12\r\n"
                                "Connection: close\r\n\r\n"
                                "request-body";
  client_context_t *context = (client_context_t *)opaque;
  test_socket_t socket_value = connect_loopback(context->port);
  if (socket_value == TEST_INVALID_SOCKET ||
      !send_all(socket_value, request, sizeof(request) - 1U)) {
    close_socket(socket_value);
    return -1;
  }

  /*
   * test_threads joins this worker before the main thread consumes the field.
   * Ownership of the still-open socket therefore transfers without a race;
   * the main thread can service Core's sole-reader event lane before recv.
   */
  context->socket_value = socket_value;
  return 0;
}

int main(void) {
  coakka_http_configuration_t *configuration = NULL;
  coakka_http_core_t *core = NULL;
  coakka_http_listener_t listener;
  coakka_http_core_route_t route;
  coakka_http_monitor_options_t monitor_options;
  coakka_http_monitor_config_view_t monitor_config;
  coakka_http_monitor_snapshot_view_t monitor_snapshot;
  coakka_http_monitor_policy_view_t monitor_policy;
  coakka_http_monitor_apply_outcome_t monitor_outcome;
  coakka_http_monitor_event_view_t monitor_events[8];
  coakka_http_monitor_event_page_view_t monitor_page;
  coakka_http_health_t health;
  coakka_http_health_t probe;
  coakka_http_runtime_info_t runtime_info;
  coakka_http_event_t request_event;
  coakka_http_event_t terminal_event;
  coakka_http_terminal_summary_t terminal_summary;
  coakka_http_response_t response;
  coakka_http_header_t response_header;
  coakka_http_path_parameter_t path_parameter;
  coakka_http_header_t invalid_header;
  coakka_http_exchange_id_t exchange = {UINT64_C(0), UINT64_C(0)};
  coakka_http_result_t operation;
  client_context_t client_context;
  void *thread_contexts[1];
  test_socket_t client = TEST_INVALID_SOCKET;
  char response_data[4096];
  uint16_t port = 0U;
  uint8_t cancelled = 1U;
  int sockets_started = 0;
  int core_started = 0;
  int request_leased = 0;
  int terminal_leased = 0;
  int thread_result;
  int exit_code = EXIT_FAILURE;

  coakka_http_event_init(&request_event);
  coakka_http_event_init(&terminal_event);
  coakka_http_terminal_summary_init(&terminal_summary);
  memset(&client_context, 0, sizeof(client_context));
  client_context.socket_value = TEST_INVALID_SOCKET;

  REQUIRE(coakka_http_abi_version() == COAKKA_HTTP_ABI_VERSION);
  REQUIRE((coakka_http_features() & COAKKA_HTTP_CAPABILITY_INBOUND) != 0U);
  REQUIRE((coakka_http_features() & COAKKA_HTTP_CAPABILITY_HEALTH) != 0U);
  coakka_http_runtime_info_init(&runtime_info);
  REQUIRE_OK(coakka_http_runtime_get_info(&runtime_info));
  REQUIRE(runtime_info.struct_size == sizeof(runtime_info));
  REQUIRE(runtime_info.info_version == COAKKA_HTTP_RUNTIME_INFO_VERSION);
  REQUIRE(runtime_info.abi_version == COAKKA_HTTP_ABI_VERSION);
  REQUIRE(runtime_info.features == coakka_http_features());
  REQUIRE(runtime_info.requested_io_backend == 0U);
  REQUIRE(runtime_info.effective_io_backend == 0U);
  REQUIRE(runtime_info.fallback_reason == COAKKA_HTTP_IO_BACKEND_FALLBACK_NONE);
  REQUIRE(runtime_info.core_started == 0U);
  REQUIRE(runtime_info.io_uring_supported == 0U ||
          runtime_info.io_uring_compiled != 0U);
#if defined(COAKKA_HTTP_EXPECT_IO_URING)
  REQUIRE(runtime_info.io_uring_compiled == 1U);
#endif
  REQUIRE((runtime_info.io_uring_supported != 0U) ==
          (runtime_info.io_uring_probe_error == 0));
#if !defined(__linux__)
  REQUIRE(runtime_info.io_uring_compiled == 0U);
  REQUIRE(runtime_info.io_uring_supported == 0U);
  REQUIRE(runtime_info.io_uring_probe_error != 0);
#endif
  REQUIRE((coakka_http_features() & COAKKA_HTTP_CAPABILITY_STATIC_FILES) != 0U);
  REQUIRE((coakka_http_features() & COAKKA_HTTP_CAPABILITY_STATIC_FRONTEND) !=
          0U);
  REQUIRE((coakka_http_features() & COAKKA_HTTP_CAPABILITY_APPLICATION_FILES) !=
          0U);
  REQUIRE(socket_runtime_start());
  sockets_started = 1;

  REQUIRE_OK(coakka_http_configuration_create(&configuration));
  REQUIRE(configuration != NULL);

  coakka_http_listener_init(&listener);
  listener.listener_id = UINT64_C(1);
  listener.bind_address = bytes("127.0.0.1");
  listener.port = 0U;
  REQUIRE_OK(coakka_http_configuration_add_listener(configuration, &listener));

  coakka_http_core_route_init(&route);
  route.route_id = UINT64_C(41);
  route.handler_binding_id = UINT64_C(101);
  route.method = bytes("POST");
  route.path = bytes("/items/{item_id}");
  route.body_policy.enabled = 1U;
  route.body_policy.accept_other = 1U;
  route.body_policy.max_body_bytes = UINT64_C(1024);
  REQUIRE_OK(coakka_http_configuration_add_route(configuration, &route));

  coakka_http_monitor_options_init(&monitor_options);
  monitor_options.collection = COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS;
  monitor_options.event_capacity = 16U;
  monitor_options.max_events_per_read = 8U;
  monitor_options.aggregate_categories =
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_LIFECYCLE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_EXCHANGE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_RESPONSE;
  monitor_options.event_categories = monitor_options.aggregate_categories;
  monitor_options.signal_reserved = 1U;
  REQUIRE_OK(
      coakka_http_configuration_set_monitor(configuration, &monitor_options));
  REQUIRE_OK(coakka_http_configuration_set_io_backend(configuration,
                                                      COAKKA_HTTP_IO_URING));

  REQUIRE_OK(coakka_http_core_create(configuration, &core));
  REQUIRE(core != NULL);
  coakka_http_runtime_info_init(&runtime_info);
  REQUIRE_OK(coakka_http_core_get_runtime_info(core, &runtime_info));
  REQUIRE(runtime_info.requested_io_backend == COAKKA_HTTP_IO_URING);
  REQUIRE(runtime_info.effective_io_backend == COAKKA_HTTP_IO_PLATFORM_DEFAULT);
  REQUIRE(runtime_info.fallback_reason ==
          COAKKA_HTTP_IO_BACKEND_FALLBACK_CONFIGURATION);
  REQUIRE(runtime_info.core_started == 0U);
  /* Core copied the complete configuration, so its builder can now die. */
  REQUIRE_OK(coakka_http_configuration_destroy(&configuration));
  REQUIRE(configuration == NULL);
  REQUIRE_OK(coakka_http_core_start(core));
  core_started = 1;
  coakka_http_runtime_info_init(&runtime_info);
  REQUIRE_OK(coakka_http_core_get_runtime_info(core, &runtime_info));
  REQUIRE(runtime_info.core_started != 0U);
  REQUIRE_OK(coakka_http_core_bound_port(core, &port));
  REQUIRE(port != 0U);

  coakka_http_health_init(&health);
  REQUIRE_OK(coakka_http_core_health(core, &health));
  REQUIRE(health.struct_size == sizeof(health));
  REQUIRE(health.configured_components != 0U);
  REQUIRE(health.running_components != 0U);
  REQUIRE(health.failed_components == 0U);
  REQUIRE(health.admission_open != 0U);

  coakka_http_health_init(&probe);
  REQUIRE_OK(coakka_http_core_probe_liveness(core, UINT64_C(2000), &probe));
  REQUIRE(probe.struct_size == sizeof(probe));
  REQUIRE(probe.acknowledged_probe_sequence != UINT64_C(0));
  REQUIRE(probe.acknowledged_probe_monotonic_ns != UINT64_C(0));

  client_context.port = port;
  thread_contexts[0] = &client_context;
  thread_result =
      coakka_http_test_run_threads(client_send_request, thread_contexts, 1U);
  client = client_context.socket_value;
  client_context.socket_value = TEST_INVALID_SOCKET;
  REQUIRE(thread_result == 0);
  REQUIRE(client != TEST_INVALID_SOCKET);

  REQUIRE_OK(coakka_http_core_take_event(core, UINT64_C(5000), &request_event));
  request_leased = 1;
  REQUIRE(request_event.kind == COAKKA_HTTP_EVENT_REQUEST);
  REQUIRE(request_event.private_owner == core);
  REQUIRE(request_event.private_lease != UINT64_C(0));
  REQUIRE(request_event.data != NULL);
  REQUIRE(request_event.data_size != UINT64_C(0));
  REQUIRE(request_event.exchange.slot != UINT64_C(0));
  REQUIRE(request_event.exchange.generation != UINT64_C(0));
  REQUIRE(request_event.handler_binding_id == UINT64_C(101));
  REQUIRE(
      bytes_equal(coakka_http_event_request_method(&request_event), "POST"));
  REQUIRE(
      bytes_equal(coakka_http_event_request_scheme(&request_event), "http"));
  REQUIRE(bytes_equal(coakka_http_event_request_authority(&request_event),
                      "127.0.0.1"));
  REQUIRE(bytes_equal(coakka_http_event_request_target(&request_event),
                      "/items/a%2Fb?mode=fast&flag"));
  REQUIRE(bytes_equal(coakka_http_event_request_body(&request_event),
                      "request-body"));
  REQUIRE(coakka_http_event_request_body_delivery(&request_event) ==
          COAKKA_HTTP_BODY_INLINE);
  REQUIRE(coakka_http_event_request_route_id(&request_event) == UINT64_C(41));
  REQUIRE(coakka_http_event_request_route_generation(&request_event) !=
          UINT64_C(0));
  REQUIRE(coakka_http_event_request_binding_revision(&request_event) !=
          UINT64_C(0));
  REQUIRE(
      request_has_header(&request_event, "x-core-test", "installed-package"));
  REQUIRE(request_has_header(&request_event, "content-type", "text/plain"));
  memset(&invalid_header, 0, sizeof(invalid_header));
  REQUIRE(coakka_http_event_request_header(
              &request_event,
              coakka_http_event_request_header_count(&request_event),
              &invalid_header) == 0U);

  REQUIRE(coakka_http_event_request_path_parameter_count(&request_event) == 1U);
  memset(&path_parameter, 0, sizeof(path_parameter));
  REQUIRE(coakka_http_event_request_path_parameter(&request_event, 0U,
                                                   &path_parameter) != 0U);
  REQUIRE(bytes_equal(path_parameter.name, "item_id"));
  REQUIRE(bytes_equal(path_parameter.encoded_value, "a%2Fb"));
  REQUIRE(coakka_http_event_request_query_parameter_count(&request_event) ==
          2U);
  REQUIRE(request_has_query_parameter(&request_event, "mode", "fast", 1U));
  REQUIRE(request_has_query_parameter(&request_event, "flag", "", 0U));
  REQUIRE(coakka_http_event_request_trailer_count(&request_event) == 0U);
  REQUIRE_OK(coakka_http_core_exchange_cancelled(core, request_event.exchange,
                                                 &cancelled));
  REQUIRE(cancelled == 0U);

  exchange = request_event.exchange;
  response_header.name = bytes("content-type");
  response_header.value = bytes("text/plain");
  coakka_http_response_init(&response);
  response.status_code = 201U;
  response.headers = &response_header;
  response.header_count = 1U;
  response.body = bytes("core-runtime-ready");
  REQUIRE_OK(coakka_http_core_respond(core, exchange, &response));

  /* Every successful take owns exactly one lease, independent of completion. */
  REQUIRE_OK(coakka_http_core_release_event(core, &request_event));
  request_leased = 0;
  REQUIRE(request_event.kind == 0U);
  REQUIRE(request_event.private_owner == NULL);
  REQUIRE(request_event.private_lease == UINT64_C(0));

  REQUIRE(read_response(client, response_data, sizeof(response_data),
                        "core-runtime-ready"));
  close_socket(client);
  client = TEST_INVALID_SOCKET;
  REQUIRE(strstr(response_data, "HTTP/1.1 201") != NULL);
  REQUIRE(strstr(response_data, "core-runtime-ready") != NULL);

  REQUIRE_OK(
      coakka_http_core_take_event(core, UINT64_C(5000), &terminal_event));
  terminal_leased = 1;
  REQUIRE(terminal_event.kind == COAKKA_HTTP_EVENT_TERMINAL);
  REQUIRE(terminal_event.exchange.slot == exchange.slot);
  REQUIRE(terminal_event.exchange.generation == exchange.generation);
  REQUIRE(terminal_event.handler_binding_id == UINT64_C(101));
  REQUIRE(terminal_event.private_owner == core);
  REQUIRE(terminal_event.private_lease != UINT64_C(0));
  REQUIRE(terminal_event.data == NULL);
  REQUIRE(terminal_event.data_size == UINT64_C(0));
  REQUIRE_OK(
      coakka_http_event_terminal_summary(&terminal_event, &terminal_summary));
  REQUIRE(terminal_summary.outcome == COAKKA_HTTP_EXCHANGE_COMPLETED);
  REQUIRE(terminal_summary.has_response != 0U);
  REQUIRE(terminal_summary.response_status == 201U);
  REQUIRE(terminal_summary.has_failure == 0U);
  REQUIRE(terminal_summary.has_admitted_time != 0U);
  REQUIRE(terminal_summary.has_completed_time != 0U);
  REQUIRE(terminal_summary.completed_monotonic_ns >=
          terminal_summary.admitted_monotonic_ns);
  REQUIRE_OK(coakka_http_core_release_event(core, &terminal_event));
  terminal_leased = 0;
  REQUIRE(terminal_event.kind == 0U);
  REQUIRE(terminal_event.private_owner == NULL);
  REQUIRE(terminal_event.private_lease == UINT64_C(0));

  REQUIRE(wait_for_exchange_reap(core, &health));
  REQUIRE(health.server_active_exchanges == 0U);

  coakka_http_monitor_config_view_init(&monitor_config);
  REQUIRE_OK(coakka_http_core_monitor_config(core, &monitor_config));
  REQUIRE(monitor_config.generation != UINT64_C(0));
  REQUIRE(monitor_config.policy.collection ==
          COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS);
  REQUIRE(monitor_config.reserved_event_capacity == 16U);
  REQUIRE(monitor_config.max_events_per_read == 8U);

  coakka_http_monitor_snapshot_view_init(&monitor_snapshot);
  REQUIRE_OK(coakka_http_core_monitor_snapshot(core, &monitor_snapshot));
  REQUIRE(monitor_snapshot.collecting != 0U);
  REQUIRE(monitor_snapshot.inbound_admitted_requests >= UINT64_C(1));
  REQUIRE(monitor_snapshot.exchange_completed >= UINT64_C(1));

  for (uint32_t index = 0U; index < 8U; ++index) {
    coakka_http_monitor_event_view_init(&monitor_events[index]);
  }
  coakka_http_monitor_event_page_init(&monitor_page);
  REQUIRE_OK(coakka_http_core_monitor_read(core, UINT64_C(0), 8U,
                                           monitor_events, 8U, &monitor_page));
  REQUIRE(monitor_page.count != 0U);
  REQUIRE(monitor_page.count <= 8U);
  REQUIRE(monitor_page.latest_sequence != UINT64_C(0));
  REQUIRE_OK(coakka_http_core_monitor_interrupt(core));

  coakka_http_monitor_policy_view_init(&monitor_policy);
  monitor_policy.collection = COAKKA_HTTP_MONITOR_AGGREGATES;
  monitor_policy.notification = COAKKA_HTTP_MONITOR_NOTIFY_POLL;
  monitor_policy.latency = COAKKA_HTTP_MONITOR_LATENCY_NONE;
  monitor_policy.detail = COAKKA_HTTP_MONITOR_DETAIL_NONE;
  monitor_policy.aggregate_categories = monitor_options.aggregate_categories;
  coakka_http_monitor_apply_outcome_init(&monitor_outcome);
  REQUIRE_OK(coakka_http_core_monitor_apply(core, monitor_config.generation,
                                            &monitor_policy, &monitor_outcome));
  REQUIRE(monitor_outcome.reason == COAKKA_HTTP_MONITOR_APPLY_REASON_APPLIED);
  REQUIRE(monitor_outcome.changed != 0U);
  REQUIRE(monitor_outcome.effective.policy.collection ==
          COAKKA_HTTP_MONITOR_AGGREGATES);

  /* A terminal observation cannot retire a still-borrowed request. */
  client_context.port = port;
  thread_contexts[0] = &client_context;
  thread_result =
      coakka_http_test_run_threads(client_send_request, thread_contexts, 1U);
  client = client_context.socket_value;
  client_context.socket_value = TEST_INVALID_SOCKET;
  REQUIRE(thread_result == 0);
  REQUIRE(client != TEST_INVALID_SOCKET);
  REQUIRE_OK(coakka_http_core_take_event(core, UINT64_C(5000), &request_event));
  request_leased = 1;
  REQUIRE(request_event.kind == COAKKA_HTTP_EVENT_REQUEST);
  exchange = request_event.exchange;
  REQUIRE_OK(coakka_http_core_respond(core, exchange, &response));
  REQUIRE(read_response(client, response_data, sizeof(response_data),
                        "core-runtime-ready"));
  close_socket(client);
  client = TEST_INVALID_SOCKET;
  REQUIRE_OK(
      coakka_http_core_take_event(core, UINT64_C(5000), &terminal_event));
  terminal_leased = 1;
  REQUIRE(terminal_event.kind == COAKKA_HTTP_EVENT_TERMINAL);
  REQUIRE(terminal_event.exchange.slot == exchange.slot);
  REQUIRE(terminal_event.exchange.generation == exchange.generation);
  REQUIRE_OK(coakka_http_core_release_event(core, &terminal_event));
  terminal_leased = 0;
  coakka_http_health_init(&health);
  REQUIRE_OK(coakka_http_core_probe_liveness(core, UINT64_C(2000), &health));
  REQUIRE(health.failed_components == 0U);
  REQUIRE(health.server_active_exchanges == 1U);
  REQUIRE_OK(coakka_http_core_release_event(core, &request_event));
  request_leased = 0;
  REQUIRE(wait_for_exchange_reap(core, &health));
  REQUIRE(health.server_active_exchanges == 0U);

  /* Leave a third terminal queued to exercise the stopped-state handoff. */
  client_context.port = port;
  thread_contexts[0] = &client_context;
  thread_result =
      coakka_http_test_run_threads(client_send_request, thread_contexts, 1U);
  client = client_context.socket_value;
  client_context.socket_value = TEST_INVALID_SOCKET;
  REQUIRE(thread_result == 0);
  REQUIRE(client != TEST_INVALID_SOCKET);
  REQUIRE_OK(coakka_http_core_take_event(core, UINT64_C(5000), &request_event));
  request_leased = 1;
  REQUIRE(request_event.kind == COAKKA_HTTP_EVENT_REQUEST);
  exchange = request_event.exchange;
  REQUIRE_OK(coakka_http_core_respond(core, exchange, &response));
  REQUIRE_OK(coakka_http_core_release_event(core, &request_event));
  request_leased = 0;
  REQUIRE(read_response(client, response_data, sizeof(response_data),
                        "core-runtime-ready"));
  close_socket(client);
  client = TEST_INVALID_SOCKET;

  REQUIRE_OK(coakka_http_core_stop(core));
  core_started = 0;

  /* Stop closes waiters but must not strand an already queued terminal lease.
   */
  REQUIRE_OK(coakka_http_core_take_event(core, UINT64_C(0), &terminal_event));
  terminal_leased = 1;
  REQUIRE(terminal_event.kind == COAKKA_HTTP_EVENT_TERMINAL);
  REQUIRE(terminal_event.exchange.slot == exchange.slot);
  REQUIRE(terminal_event.exchange.generation == exchange.generation);
  REQUIRE(terminal_event.handler_binding_id == UINT64_C(101));
  REQUIRE(terminal_event.private_owner == core);
  REQUIRE(terminal_event.private_lease != UINT64_C(0));
  REQUIRE(terminal_event.data == NULL);
  REQUIRE(terminal_event.data_size == UINT64_C(0));
  REQUIRE_OK(
      coakka_http_event_terminal_summary(&terminal_event, &terminal_summary));
  REQUIRE(terminal_summary.outcome == COAKKA_HTTP_EXCHANGE_COMPLETED);
  REQUIRE(terminal_summary.has_response != 0U);
  REQUIRE(terminal_summary.response_status == 201U);
  REQUIRE(terminal_summary.has_failure == 0U);
  REQUIRE(terminal_summary.has_admitted_time != 0U);
  REQUIRE(terminal_summary.has_completed_time != 0U);
  REQUIRE(terminal_summary.completed_monotonic_ns >=
          terminal_summary.admitted_monotonic_ns);
  REQUIRE_OK(coakka_http_core_release_event(core, &terminal_event));
  terminal_leased = 0;
  REQUIRE(terminal_event.kind == 0U);
  REQUIRE(terminal_event.private_owner == NULL);
  REQUIRE(terminal_event.private_lease == UINT64_C(0));

  REQUIRE_OK(coakka_http_core_stop(core));
  REQUIRE_OK(coakka_http_core_destroy(&core));
  REQUIRE(core == NULL);
  socket_runtime_stop();
  sockets_started = 0;

  (void)printf("coakka_http_core_runtime_test=pass abi=%u port=%u "
               "io_uring_compiled=%u io_uring_supported=%u io_uring_error=%d\n",
               coakka_http_abi_version(), (unsigned int)port,
               (unsigned int)runtime_info.io_uring_compiled,
               (unsigned int)runtime_info.io_uring_supported,
               (int)runtime_info.io_uring_probe_error);
  exit_code = EXIT_SUCCESS;

cleanup:
  if (client != TEST_INVALID_SOCKET) {
    close_socket(client);
  }
  if (core != NULL && request_leased != 0) {
    (void)coakka_http_core_release_event(core, &request_event);
  }
  if (core != NULL && terminal_leased != 0) {
    (void)coakka_http_core_release_event(core, &terminal_event);
  }
  if (core != NULL && core_started != 0) {
    (void)coakka_http_core_stop(core);
  }
  if (core != NULL) {
    (void)coakka_http_core_destroy(&core);
  }
  if (configuration != NULL) {
    (void)coakka_http_configuration_destroy(&configuration);
  }
  if (sockets_started != 0) {
    socket_runtime_stop();
  }
  return exit_code;
}
