/* Fixed response through the installed public C callback API. The benchmark
 * never substitutes the internal event-pump ABI for an application handler.
 * This runner targets the physical Linux Pi; signal waiting adds no spin loop.
 */
#define _POSIX_C_SOURCE 200809L
#include <coakka/http/http.h>
#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static atomic_int failed = 0;
static coakka_http_response_t fixed_response;
static const coakka_http_header_t content_type = {
    {(const uint8_t *)"content-type", 12U},
    {(const uint8_t *)"application/octet-stream", 24U}};
static const char body[] = "0123456789abcdef0123456789abcdef";

/* Static text survives every callback; successful respond copies these views. */
static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result = {(const uint8_t *)value, strlen(value)};
  return result;
}

/* Diagnostics are cold failure evidence, not a per-request logging workload. */
static int check(coakka_http_result_t result, const char *operation) {
  if (result.code == COAKKA_HTTP_RESULT_OK) return 1;
  fprintf(stderr, "%s: %s (%s)\n", operation,
          coakka_http_result_code_name(result.code), result.detail);
  atomic_store(&failed, 1);
  return 0;
}

/* Immutable response data is safe for concurrent runtime-owned callbacks.
 * This API records the handler's outcome; Core submits it after callback
 * return and owns races with peer disconnect. OK is not proof of wire delivery.
 * Do not copy direct event-reader late-submission handling into this surface. */
static void respond(void *context, coakka_http_request_t *request) {
  (void)context;
  (void)check(coakka_http_request_respond(request, &fixed_response), "respond");
}

int main(int argc, char **argv) {
  coakka_http_server_options_t options;
  coakka_http_server_tuning_t tuning;
  coakka_http_route_t route;
  coakka_http_runtime_info_t info;
  coakka_http_server_t *server = NULL;
  sigset_t signals;
  char *end = NULL;
  const char *policy = getenv("COAKKA_BENCH_CPU_POLICY");
  unsigned long port;
  int received;
  if (argc != 2 || policy == NULL ||
      (strcmp(policy, "single") != 0 && strcmp(policy, "auto") != 0)) return EXIT_FAILURE;
  errno = 0;
  port = strtoul(argv[1], &end, 10);
  if (errno || *argv[1] == '\0' || *end != '\0' || port == 0 || port > 65535) return EXIT_FAILURE;
  sigemptyset(&signals); sigaddset(&signals, SIGINT); sigaddset(&signals, SIGTERM);
  if (pthread_sigmask(SIG_BLOCK, &signals, NULL) != 0) return EXIT_FAILURE;

  coakka_http_server_options_init(&options);
  coakka_http_server_tuning_init(&tuning);
  options.port = (uint16_t)port;
  options.bind_address = bytes("127.0.0.1");
  tuning.cpu_policy = strcmp(policy, "single") == 0 ? COAKKA_HTTP_CPU_SINGLE : COAKKA_HTTP_CPU_AUTO;
  options.tuning = &tuning;
  coakka_http_route_init(&route);
  route.route_id = UINT64_C(1);
  route.method = bytes("GET"); route.path = bytes("/fixed"); route.handler = respond;
  coakka_http_response_init(&fixed_response);
  fixed_response.headers = &content_type; fixed_response.header_count = 1;
  fixed_response.body = bytes(body);
  if (!check(coakka_http_server_create(&options, &route, 1, &server), "create") ||
      !check(coakka_http_server_start(server), "start")) goto cleanup;
  coakka_http_runtime_info_init(&info);
  if (!check(coakka_http_server_get_runtime_info(server, &info), "runtime info")) goto cleanup;
  if (!info.execution.observed || !info.core_started || info.cpu.selected_cpu_count < 1 ||
      info.cpu.selected_cpu_count > 2) { atomic_store(&failed, 1); goto cleanup; }
  if (info.effective_io_backend != COAKKA_HTTP_IO_PLATFORM_DEFAULT &&
      info.effective_io_backend != COAKKA_HTTP_IO_URING) {
    atomic_store(&failed, 1); goto cleanup;
  }
  /* Only observed Core fields are serialized. This ABI does not expose a full
   * effective timeout table: null explicitly records that unavailable fact. */
  printf("coakka-runtime-info={\"cpu\":{\"requestedPolicy\":%u,\"placement\":%u,"
         "\"selectedCpuCount\":%u,\"selectedCpuIds\":[%u",
         info.cpu.requested_policy, info.cpu.placement, info.cpu.selected_cpu_count,
         info.cpu.selected_cpu_ids[0]);
  if (info.cpu.selected_cpu_count == 2) printf(",%u", info.cpu.selected_cpu_ids[1]);
  printf("]},\"execution\":{\"observed\":true,\"configuredEventLoops\":%u,\"activeEventLoops\":%u},"
         "\"requestNotificationBatchSize\":%u,\"terminalNotificationBatchSize\":%u,"
         "\"effectiveIoBackend\":%u,\"ioUringEffective\":%s,\"limits\":null}\n",
         info.execution.configured_event_loops, info.execution.active_event_loops,
         info.request_notification_batch_size, info.terminal_notification_batch_size,
         info.effective_io_backend,
         info.effective_io_backend == COAKKA_HTTP_IO_URING ? "true" : "false");
  fflush(stdout);
  if (sigwait(&signals, &received) != 0) atomic_store(&failed, 1);
cleanup:
  if (server != NULL) {
    (void)check(coakka_http_server_stop(server), "stop");
    (void)check(coakka_http_server_destroy(&server), "destroy");
  }
  if (!atomic_load(&failed)) puts("benchmark-shutdown=pass");
  return atomic_load(&failed) ? EXIT_FAILURE : EXIT_SUCCESS;
}
