/*
 * Host-inlined C11 sample.
 *
 * One connector thread owns the inbound event lane. It dispatches by the
 * captured binding ID, completes the exchange, and releases every lease on
 * the same thread. Native code owns listener, routing, protocol, file, monitor,
 * backend-selection, and shutdown mechanics.
 */
#include <coakka/http/host.h>

#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
typedef SOCKET sample_socket_t;
typedef HANDLE sample_thread_t;
#define SAMPLE_INVALID_SOCKET INVALID_SOCKET
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
typedef int sample_socket_t;
typedef pthread_t sample_thread_t;
#define SAMPLE_INVALID_SOCKET (-1)
#endif

typedef struct sample_state {
  coakka_http_host_service_t *service;
  atomic_int stopping;
  atomic_uint failures;
} sample_state_t;

static volatile sig_atomic_t signal_stopping = 0;

static coakka_http_host_bytes_t bytes(const char *value) {
  coakka_http_host_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : strlen(value);
  return result;
}

static int require_ok(coakka_http_host_status_t status, const char *operation) {
  if (status == COAKKA_HTTP_HOST_OK) {
    return 1;
  }
  fprintf(stderr, "%s failed: %s (%d)\n", operation,
          coakka_http_host_status_name(status), (int)status);
  return 0;
}

static int require_start(coakka_http_host_service_t *service) {
  coakka_http_host_issue_t issue;
  coakka_http_host_issue_init(&issue);
  if (coakka_http_host_service_start(service, &issue) == COAKKA_HTTP_HOST_OK) {
    return 1;
  }
  fprintf(stderr, "start service failed: %s (reason=%u system=%d)\n",
          issue.detail, (unsigned)issue.reason, (int)issue.system_code);
  return 0;
}

static void record_status(sample_state_t *state,
                          coakka_http_host_status_t status,
                          const char *operation) {
  if (!require_ok(status, operation)) {
    (void)atomic_fetch_add_explicit(&state->failures, 1U, memory_order_relaxed);
  }
}

/* Complete one admitted request using its captured binding identity. */
static void dispatch_request(sample_state_t *state,
                             const coakka_http_host_request_event_t *event) {
  coakka_http_host_response_t response;
  coakka_http_host_response_init(&response);
  switch (event->handler_binding_id) {
  case UINT64_C(1):
    response.status_code = 201U;
    response.body = coakka_http_host_request_body(event);
    record_status(
        state,
        coakka_http_host_respond(state->service, event->exchange, &response),
        "respond to echo");
    break;
  case UINT64_C(2): {
    coakka_http_host_file_response_t file;
    coakka_http_host_file_response_init(&file);
    file.authority_id = UINT64_C(82);
    file.encoded_path = bytes("/sample.txt");
    record_status(
        state,
        coakka_http_host_respond_file(state->service, event->exchange, &file),
        "respond with confined file");
    break;
  }
  case UINT64_C(4):
    response.body = bytes("v2");
    record_status(
        state,
        coakka_http_host_respond(state->service, event->exchange, &response),
        "respond through rebound handler");
    break;
  default:
    response.status_code = 404U;
    response.body = bytes("not found");
    record_status(
        state,
        coakka_http_host_respond(state->service, event->exchange, &response),
        "respond not found");
    break;
  }
}

/* Sole owner of the ordered inbound lease lane. */
static void run_event_loop(sample_state_t *state) {
  while (atomic_load_explicit(&state->stopping, memory_order_acquire) == 0) {
    coakka_http_host_request_event_t event;
    coakka_http_host_status_t status;
    coakka_http_host_request_event_init(&event);
    status = coakka_http_host_take_request(state->service, 100U, &event);
    if (status == COAKKA_HTTP_HOST_TIMEOUT) {
      continue;
    }
    if (status == COAKKA_HTTP_HOST_CLOSED) {
      break;
    }
    if (status != COAKKA_HTTP_HOST_OK) {
      record_status(state, status, "take request");
      break;
    }
    if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
      dispatch_request(state, &event);
    }
    record_status(state,
                  coakka_http_host_release_request(state->service, &event),
                  "release request event");
  }
}

#if defined(_WIN32)
static DWORD WINAPI event_loop_entry(LPVOID opaque) {
  run_event_loop((sample_state_t *)opaque);
  return 0U;
}
static int start_event_loop(sample_thread_t *thread, sample_state_t *state) {
  *thread = CreateThread(NULL, 0U, event_loop_entry, state, 0U, NULL);
  return *thread != NULL;
}
static void join_event_loop(sample_thread_t thread) {
  (void)WaitForSingleObject(thread, INFINITE);
  (void)CloseHandle(thread);
}
#else
static void *event_loop_entry(void *opaque) {
  run_event_loop((sample_state_t *)opaque);
  return NULL;
}
static int start_event_loop(sample_thread_t *thread, sample_state_t *state) {
  return pthread_create(thread, NULL, event_loop_entry, state) == 0;
}
static void join_event_loop(sample_thread_t thread) {
  (void)pthread_join(thread, NULL);
}
#endif

static void close_socket(sample_socket_t value) {
#if defined(_WIN32)
  (void)closesocket(value);
#else
  (void)close(value);
#endif
}

static int socket_system_start(void) {
#if defined(_WIN32)
  WSADATA data;
  return WSAStartup(MAKEWORD(2, 2), &data) == 0;
#else
  return 1;
#endif
}

static void socket_system_stop(void) {
#if defined(_WIN32)
  (void)WSACleanup();
#endif
}

/* Send one finite HTTP/1.1 request and search its bounded response. */
static int exchange(uint16_t port, const char *wire, const char *expected) {
  sample_socket_t connection;
  struct sockaddr_in endpoint;
  char response[16384];
  size_t sent = 0U;
  size_t used = 0U;
#if defined(_WIN32)
  DWORD timeout_ms = 5000U;
#else
  struct timeval timeout = {5, 0};
#endif
  connection = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (connection == SAMPLE_INVALID_SOCKET) {
    return 0;
  }
#if defined(_WIN32)
  (void)setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO,
                   (const char *)&timeout_ms, (int)sizeof(timeout_ms));
#else
  (void)setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   (socklen_t)sizeof(timeout));
#endif
  memset(&endpoint, 0, sizeof(endpoint));
  endpoint.sin_family = AF_INET;
  endpoint.sin_port = htons(port);
  endpoint.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (connect(connection, (const struct sockaddr *)&endpoint,
              (socklen_t)sizeof(endpoint)) != 0) {
    close_socket(connection);
    return 0;
  }
  while (sent < strlen(wire)) {
#if defined(_WIN32)
    int count = send(connection, wire + sent, (int)(strlen(wire) - sent), 0);
#else
    ssize_t count = send(connection, wire + sent, strlen(wire) - sent, 0);
#endif
    if (count <= 0) {
      close_socket(connection);
      return 0;
    }
    sent += (size_t)count;
  }
  while (used + 1U < sizeof(response)) {
#if defined(_WIN32)
    int count = recv(connection, response + used,
                     (int)(sizeof(response) - used - 1U), 0);
#else
    ssize_t count =
        recv(connection, response + used, sizeof(response) - used - 1U, 0);
#endif
    if (count <= 0) {
      break;
    }
    used += (size_t)count;
    response[used] = '\0';
  }
  close_socket(connection);
  return used != 0U && strstr(response, expected) != NULL;
}

static void stop_signal(int signal_number) {
  (void)signal_number;
  signal_stopping = 1;
}

static void wait_for_signal(void) {
  while (signal_stopping == 0) {
#if defined(_WIN32)
    Sleep(50U);
#else
    const struct timespec delay = {0, 50000000L};
    (void)nanosleep(&delay, NULL);
#endif
  }
}

static int has_argument(int argc, char **argv, const char *expected) {
  int index;
  for (index = 1; index < argc; ++index) {
    if (strcmp(argv[index], expected) == 0) {
      return 1;
    }
  }
  return 0;
}

int main(int argc, char **argv) {
  static const char echo_request[] =
      "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: text/plain\r\n"
      "Content-Length: 7\r\nConnection: close\r\n\r\npayload";
  static const char version_request[] =
      "GET /version HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
  static const char download_request[] =
      "GET /download HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
  static const char frontend_request[] =
      "GET /app/client/route HTTP/1.1\r\nHost: 127.0.0.1\r\nAccept: "
      "text/html\r\n"
      "Connection: close\r\n\r\n";
  const int serve = has_argument(argc, argv, "--serve");
  const int request_io_uring = has_argument(argc, argv, "--io-uring");
  const char *asset_root = argc > 2 ? argv[2] : "../assets";
  coakka_http_host_configuration_t configuration;
  coakka_http_host_static_mount_t mount;
  coakka_http_host_file_authority_t authority;
  coakka_http_host_route_t routes[3];
  coakka_http_host_rebind_request_t rebind;
  coakka_http_host_rebind_outcome_t rebound;
  coakka_http_host_service_info_t info;
  coakka_http_host_health_t health;
  coakka_http_host_monitor_snapshot_t monitor;
  coakka_http_host_service_t *service = NULL;
  sample_state_t state;
  sample_thread_t thread;
  uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  uint16_t port = 0U;
  int failed = 0;

  if (!socket_system_start()) {
    fputs("socket startup failed\n", stderr);
    return EXIT_FAILURE;
  }
  coakka_http_host_configuration_init(&configuration);
  configuration.use_io_uring = request_io_uring != 0 ? 1U : 0U;
  configuration.monitor.initial_collection =
      COAKKA_HTTP_HOST_MONITOR_AGGREGATES_AND_EVENTS;
  configuration.monitor.event_capacity = 32U;
  configuration.monitor.max_events_per_read = 8U;
  configuration.monitor.categories =
      COAKKA_HTTP_HOST_MONITOR_LIFECYCLE | COAKKA_HTTP_HOST_MONITOR_EXCHANGE;

  coakka_http_host_static_mount_init(&mount);
  mount.flags = COAKKA_HTTP_HOST_STATIC_SPA_FALLBACK;
  mount.url_prefix = bytes("/app");
  mount.root_path = bytes(asset_root);
  mount.index_file = bytes("index.html");
  mount.spa_fallback_file = bytes("index.html");
  configuration.static_mounts = &mount;
  configuration.static_mount_count = 1U;
  configuration.max_total_static_assets = 64U;

  coakka_http_host_file_authority_init(&authority);
  authority.authority_id = UINT64_C(82);
  authority.root_path = bytes(asset_root);
  authority.max_active_files = 2U;
  authority.max_file_bytes = UINT64_C(1048576);
  configuration.file_authorities = &authority;
  configuration.file_authority_count = 1U;

  coakka_http_host_route_init(&routes[0]);
  routes[0].route_id = UINT64_C(1);
  routes[0].handler_binding_id = UINT64_C(1);
  routes[0].method = bytes("POST");
  routes[0].encoded_path_pattern = bytes("/echo");
  routes[0].body_policy.enabled = 1U;
  routes[0].body_policy.accept_other = 1U;
  routes[0].body_policy.max_body_bytes = UINT64_C(1048576);
  coakka_http_host_route_init(&routes[1]);
  routes[1].route_id = UINT64_C(2);
  routes[1].handler_binding_id = UINT64_C(2);
  routes[1].method = bytes("GET");
  routes[1].encoded_path_pattern = bytes("/download");
  coakka_http_host_route_init(&routes[2]);
  routes[2].route_id = UINT64_C(3);
  routes[2].handler_binding_id = UINT64_C(3);
  routes[2].method = bytes("GET");
  routes[2].encoded_path_pattern = bytes("/version");

  if (!require_ok(coakka_http_host_service_create(&configuration, routes, 3U,
                                                  &service, &failed_route),
                  "create service") ||
      !require_start(service) ||
      !require_ok(coakka_http_host_service_port(service, &port),
                  "read bound port")) {
    coakka_http_host_service_destroy(&service);
    socket_system_stop();
    return EXIT_FAILURE;
  }

  state.service = service;
  atomic_init(&state.stopping, 0);
  atomic_init(&state.failures, 0U);
  if (!start_event_loop(&thread, &state)) {
    fputs("event-loop thread creation failed\n", stderr);
    failed = 1;
    goto cleanup;
  }

  coakka_http_host_rebind_request_init(&rebind);
  rebind.activation_id = UINT64_C(1);
  rebind.expected_route_generation = UINT64_C(1);
  rebind.route_id = UINT64_C(3);
  rebind.expected_binding_revision = UINT64_C(1);
  rebind.new_handler_binding_id = UINT64_C(4);
  coakka_http_host_rebind_outcome_init(&rebound);
  if (!require_ok(coakka_http_host_rebind(service, &rebind, &rebound),
                  "rebind handler") ||
      rebound.code != COAKKA_HTTP_HOST_CONTROL_APPLIED) {
    failed = 1;
  }

  coakka_http_host_service_info_init(&info);
  if (!require_ok(coakka_http_host_service_info(service, &info),
                  "read runtime info") ||
      info.io_uring_requested != configuration.use_io_uring ||
      (info.io_uring_requested != 0U && info.io_uring_effective == 0U &&
       info.io_fallback_reason == COAKKA_HTTP_HOST_IO_FALLBACK_NONE)) {
    failed = 1;
  }
  printf("io_uring requested=%u effective=%u fallback=%u\n",
         (unsigned)info.io_uring_requested, (unsigned)info.io_uring_effective,
         (unsigned)info.io_fallback_reason);

  if (serve) {
    (void)signal(SIGINT, stop_signal);
#if defined(SIGTERM)
    (void)signal(SIGTERM, stop_signal);
#endif
    printf("C sample listening on http://127.0.0.1:%u\n", (unsigned)port);
    wait_for_signal();
  } else if (!exchange(port, echo_request, "payload") ||
             !exchange(port, version_request, "v2") ||
             !exchange(port, download_request, "confined application file") ||
             !exchange(port, frontend_request, "CoAkka HTTP Runtime")) {
    fputs("C sample request smoke failed\n", stderr);
    failed = 1;
  }

  coakka_http_host_health_init(&health);
  coakka_http_host_monitor_snapshot_init(&monitor);
  if (!require_ok(coakka_http_host_probe_liveness(service, 1000U, &health),
                  "probe liveness") ||
      health.acknowledged_probe_sequence == 0U ||
      !require_ok(coakka_http_host_monitor_snapshot(service, &monitor),
                  "read monitor snapshot")) {
    failed = 1;
  }

  atomic_store_explicit(&state.stopping, 1, memory_order_release);
  (void)coakka_http_host_interrupt_requests(service);
  join_event_loop(thread);
  if (atomic_load_explicit(&state.failures, memory_order_relaxed) != 0U) {
    failed = 1;
  }

cleanup:
  if (service != NULL) {
    (void)coakka_http_host_service_begin_drain(service);
    if (!require_ok(coakka_http_host_service_stop(service), "stop service")) {
      failed = 1;
    }
    coakka_http_host_service_destroy(&service);
  }
  socket_system_stop();
  if (!failed) {
    puts("coakka-http-c-sample=pass");
  }
  return failed ? EXIT_FAILURE : EXIT_SUCCESS;
}
