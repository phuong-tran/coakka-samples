/* C11 callbacks using only the installed native application API. Request
 * scheduling belongs to the runtime, not to an application event-pump thread.
 */
#include "../native/sample_options.h"
#include "../native/monitor_example.h"
#include <signal.h>
#include <stdatomic.h>
#include <inttypes.h>
static volatile sig_atomic_t stopping = 0;
static atomic_int failed = 0;

static int check(coakka_http_result_t result, const char *operation) {
  if (result.code == COAKKA_HTTP_RESULT_OK) return 1;
  fprintf(stderr, "%s: %s (%s)\n", operation, coakka_http_result_code_name(result.code), result.detail);
  atomic_store(&failed, 1);
  return 0;
}

/* Incremental input needs no application body collector. Core enforces the
 * body ceiling and deadline. Each borrowed chunk/trailer expires at the next
 * read; only scalar counters survive. This runs on the runtime's handler
 * worker, never its transport loop, and adds no application thread.
 */
static void handle_upload(coakka_http_request_t *request) {
  uint64_t bytes = 0;
  uint32_t trailers = 0;
  int announced = 0;
  coakka_http_request_body_event_t event;
  coakka_http_response_t response;
  char text[96];
  for (;;) {
    coakka_http_result_t read;
    coakka_http_request_body_event_init(&event);
    read = coakka_http_request_body_read(request, 5000, &event);
    if (read.code == COAKKA_HTTP_RESULT_CANCELLED ||
        read.code == COAKKA_HTTP_RESULT_CLOSED ||
        (read.code == COAKKA_HTTP_RESULT_OK &&
         event.kind == COAKKA_HTTP_REQUEST_BODY_CANCELLED)) {
      /* Peer cancellation is not a server failure or a response opportunity. */
      puts("native-upload-cancelled"); fflush(stdout);
      return;
    }
    if (read.code == COAKKA_HTTP_RESULT_TIMEOUT) {
      coakka_http_response_init(&response);
      response.status_code = 408;
      /* A concurrent Core timeout can already have retired this exchange. */
      read = coakka_http_request_respond(request, &response);
      if (read.code != COAKKA_HTTP_RESULT_CANCELLED &&
          read.code != COAKKA_HTTP_RESULT_CLOSED) (void)check(read, "upload timeout response");
      return;
    }
    if (!check(read, "upload read")) return;
    if (event.kind == COAKKA_HTTP_REQUEST_BODY_DATA) {
      if (event.data.size > UINT64_MAX - bytes) {
        atomic_store(&failed, 1); return;
      }
      bytes += event.data.size;
      if (!announced) {
        /* Lifecycle evidence only: never log request bytes or credentials. */
        puts("native-upload-data"); fflush(stdout); announced = 1;
      }
    } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_TRAILERS) {
      trailers = coakka_http_request_body_event_trailer_count(&event);
    } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_END) {
      int size = snprintf(text, sizeof(text), "bytes=%" PRIu64 " trailers=%" PRIu32, bytes, trailers);
      if (size < 0 || (size_t)size >= sizeof(text)) { atomic_store(&failed, 1); return; }
      coakka_http_response_init(&response);
      response.body = sample_bytes(text);
      read = coakka_http_request_respond(request, &response);
      if (read.code != COAKKA_HTTP_RESULT_CANCELLED &&
          read.code != COAKKA_HTTP_RESULT_CLOSED) (void)check(read, "upload response");
      return;
    }
  }
}

/* Views are callback-scoped. Successful completion copies the body before
 * returning; neither request pointers nor their borrowed bytes are retained.
 */
static void handle(void *context, coakka_http_request_t *request) {
  coakka_http_response_t response;
  uint64_t route = coakka_http_request_route_id(request);
  if (route == 5) { handle_upload(request); return; }
  if (route == 6) {
    (void)check(coakka_http_request_accept_websocket(request, sample_bytes("")), "websocket upgrade");
    return;
  }
  coakka_http_response_init(&response);
  if (route == 7) {
    coakka_http_path_parameter_t path;
    coakka_http_query_parameter_t query;
    coakka_http_header_t headers[3];
    char count_text[11], values_text[11];
    const uint32_t count = coakka_http_request_query_parameter_count(request);
    uint32_t index, values = 0;
    if (!coakka_http_request_path_parameter(request, 0, &path)) {
      atomic_store(&failed, 1); return;
    }
    /* Core preserves duplicate keys/order and distinguishes ?flag from
     * ?flag=. Walk the already parsed entries once; never split raw_target. */
    for (index = 0; index < count; ++index) {
      if (!coakka_http_request_query_parameter(request, index, &query)) {
        atomic_store(&failed, 1); return;
      }
      values += query.has_value != 0;
    }
    (void)snprintf(count_text, sizeof(count_text), "%" PRIu32, count);
    (void)snprintf(values_text, sizeof(values_text), "%" PRIu32, values);
    headers[0].name = sample_bytes("content-type"); headers[0].value = sample_bytes("text/plain");
    headers[1].name = sample_bytes("x-query-count"); headers[1].value = sample_bytes(count_text);
    headers[2].name = sample_bytes("x-query-with-value"); headers[2].value = sample_bytes(values_text);
    response.headers = headers; response.header_count = 3;
    /* The encoded capture stays borrowed until respond copies it. */
    response.body = path.encoded_value;
    (void)check(coakka_http_request_respond(request, &response), "parameter response");
    return;
  }
  if (route == 2) {
    coakka_http_file_response_t file;
    coakka_http_file_response_init(&file);
    file.authority_id = 82; file.encoded_path = sample_bytes("/sample.txt");
    (void)check(coakka_http_request_respond_file(request, &file), "file response");
    return;
  }
  response.body = route == 1 ? coakka_http_request_body(request) : sample_bytes((const char *)context);
  response.status_code = route == 1 ? 201U : 200U;
  (void)check(coakka_http_request_respond(request, &response), "response");
}
static void stop_signal(int number) { (void)number; stopping = 1; }

/* The main thread is the sole WebSocket event reader. Echo copies bytes before
 * exact release; no borrowed frame escapes or application queue is introduced.
 * On pressure, close explicitly instead of silently dropping an echo.
 */
static void websocket_turn(coakka_http_server_t *server) {
  coakka_http_socket_event_t event;
  coakka_http_result_t result;
  coakka_http_socket_event_init(&event);
  result = coakka_http_server_take_websocket(server, 50, &event);
  if (result.code == COAKKA_HTTP_RESULT_TIMEOUT) return;
  if (!check(result, "websocket take")) { stopping = 1; return; }
  if (event.kind == COAKKA_HTTP_WEBSOCKET_TEXT || event.kind == COAKKA_HTTP_WEBSOCKET_BINARY) {
    const coakka_http_bytes_t data = {event.data, event.data_size};
    result = coakka_http_server_send_websocket(server, event.session, event.kind, data);
    if (result.code == COAKKA_HTTP_RESULT_QUEUE_FULL)
      result = coakka_http_server_close_websocket(server, event.session, 1013, sample_bytes("echo capacity"));
    if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED)
      (void)check(result, "websocket echo/close");
  }
  (void)check(coakka_http_server_release_websocket(server, &event), "websocket release");
}

int main(int argc, char **argv) {
  sample_options_t config;
  coakka_http_server_t *server = NULL;
  coakka_http_route_t routes[7];
  coakka_http_route_rebind_t change;
  coakka_http_route_rebind_outcome_t outcome;
  coakka_http_runtime_info_t info;
  coakka_http_health_t health;
  coakka_http_monitor_event_view_t events[8];
  coakka_http_monitor_event_page_view_t page;
  uint16_t port = 0;
  unsigned index;
  const char *paths[] = {"/echo", "/download", "/version", "/secure", "/upload", "/socket", "/items/{id}"};
  if (!sample_options_parse(&config, argc, argv)) {
    fputs("invalid arguments; see README for --assets, --port, --security, --fixtures, --protocol\n", stderr);
    return EXIT_FAILURE;
  }
  config.server.reserve_websocket = 1;
  for (index = 0; index < 7; ++index) {
    coakka_http_route_init(&routes[index]); routes[index].route_id = index + 1;
    routes[index].method = sample_bytes(index == 0 || index == 4 ? "POST" : "GET");
    if (index == 4) routes[index].body_delivery = COAKKA_HTTP_BODY_STREAM;
    routes[index].path = sample_bytes(paths[index]); routes[index].handler = handle;
    routes[index].context = (void *)(index == 2 ? "v1" : config.response);
  }
  if (!check(coakka_http_server_create(&config.server, routes, 7, &server), "create") ||
      !check(coakka_http_server_start(server), "start")) goto cleanup;
  if (!sample_monitor_reload(server)) { atomic_store(&failed, 1); goto cleanup; }
  /* Stage before publishing; retain both contexts until destroy succeeds. */
  if (!check(coakka_http_server_prepare_handler(server, 10, (void *)"v2", handle), "prepare")) goto cleanup;
  coakka_http_route_rebind_init(&change);
  change.activation_id = 1; change.expected_route_generation = 1;
  change.route_id = 3; change.expected_binding_revision = 1; change.new_handler_binding_id = 10;
  coakka_http_route_rebind_outcome_init(&outcome);
  if (!check(coakka_http_server_rebind_handler(server, &change, 2000, &outcome), "rebind")) goto cleanup;
  if (outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED) { atomic_store(&failed, 1); goto cleanup; }
  /* Prepare first, then intentionally use the old revision. Core must refuse
   * the switch; a later /version request still has to execute the v2 binding.
   */
  if (!check(coakka_http_server_prepare_handler(server, 11, (void *)"unreachable", handle), "prepare rejected candidate")) goto cleanup;
  change.activation_id = 2; change.new_handler_binding_id = 11;
  coakka_http_route_rebind_outcome_init(&outcome);
  if (!check(coakka_http_server_rebind_handler(server, &change, 2000, &outcome), "stale rebind")) goto cleanup;
  if (outcome.code != COAKKA_HTTP_ROUTE_REBIND_BINDING_REVISION_MISMATCH || outcome.changed ||
      outcome.effective_handler_binding_id != 10) { atomic_store(&failed, 1); goto cleanup; }
  coakka_http_runtime_info_init(&info); coakka_http_health_init(&health);
  if (!check(coakka_http_server_get_runtime_info(server, &info), "runtime info") ||
      !check(coakka_http_server_probe_liveness(server, 2000, &health), "liveness") ||
      !check(coakka_http_server_port(server, &port), "port")) goto cleanup;
  /* Read actual Core execution, never substitute worker_count or a default
   * when an instance observation is absent. This sample has no concurrent
   * lifecycle controller at startup. */
  if (!info.execution.observed || !info.core_started ||
      info.execution.active_event_loops == 0 ||
      info.execution.active_event_loops != info.execution.configured_event_loops) {
    fprintf(stderr, "Core execution observation is not ready\n");
    atomic_store(&failed, 1); goto cleanup;
  }
  printf("execution-observed=%u configured-loops=%u active-loops=%u\n",
         info.execution.observed, info.execution.configured_event_loops,
         info.execution.active_event_loops);
  printf("cpu-policy=%u placement=%u selected=%u verified=%u request-batch=%u terminal-batch=%u\n",
         info.cpu.requested_policy, info.cpu.placement, info.cpu.selected_cpu_count,
         info.cpu.startup_verified, info.request_notification_batch_size,
         info.terminal_notification_batch_size);
  coakka_http_monitor_event_page_init(&page);
  for (index = 0; index < 8; ++index) coakka_http_monitor_event_view_init(&events[index]);
  if (!check(coakka_http_server_monitor_read(server, 0, 8, events, 8, &page), "monitor")) goto cleanup;
  printf("io-backend-requested=%u effective=%u fallback=%u\n", info.requested_io_backend, info.effective_io_backend, info.fallback_reason);
  signal(SIGINT, stop_signal); signal(SIGTERM, stop_signal);
  printf("native-sample-port=%u\n", (unsigned)port); fflush(stdout);
  while (!stopping) {
    websocket_turn(server);
  }
cleanup:
  /* Explicit close reports refusal; never restart after incomplete shutdown. */
  if (server != NULL) {
    (void)check(coakka_http_server_stop(server), "stop");
    (void)check(coakka_http_server_destroy(&server), "destroy");
  }
  return atomic_load(&failed) ? EXIT_FAILURE : EXIT_SUCCESS;
}
