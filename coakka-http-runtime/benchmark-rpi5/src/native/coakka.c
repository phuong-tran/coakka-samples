/* Installed public C, host-inlined fixed-response benchmark. One application
 * reader handles request/terminal leases; Core owns HTTP I/O and its loops.
 * This is the explicitly configured native surface, NOT server callback defaults.
 * Only http.h and the installed library are used; no private headers or ABI.
 */
#define _POSIX_C_SOURCE 200809L
#include <coakka/http/http.h>
#include <errno.h>
#include <inttypes.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static volatile sig_atomic_t stopping;
static uint64_t requests, terminals, late_responses;
static const char body[] = "0123456789abcdef0123456789abcdef";
static const coakka_http_header_t content_type = {
    {(const uint8_t *)"content-type", 12U},
    {(const uint8_t *)"application/octet-stream", 24U}};

static coakka_http_bytes_t bytes(const char *text) {
  const coakka_http_bytes_t result = {(const uint8_t *)text, strlen(text)};
  return result;
}
static void stop_signal(int number) { (void)number; stopping = 1; }
static int check(coakka_http_result_t result, const char *operation) {
  if (result.code == COAKKA_HTTP_RESULT_OK) return 1;
  fprintf(stderr, "%s: %s (%s)\n", operation,
          coakka_http_result_code_name(result.code), result.detail);
  return 0;
}

/* Every successful take has exactly one release. A duration-limited client can
 * disconnect before respond: accept only Core's typed STALE_EXCHANGE, not an
 * inferred error-string match or retry. Measured wire failures still fail the run.
 * Immutable payload lifetime spans the process; respond copies its public views.
 */
static int consume(coakka_http_core_t *core, int after_stop) {
  coakka_http_event_t event;
  coakka_http_event_init(&event);
  coakka_http_result_t taken = coakka_http_core_take_event(core, after_stop ? 0U : 100U, &event);
  if (taken.code == COAKKA_HTTP_RESULT_TIMEOUT || taken.code == COAKKA_HTTP_RESULT_CLOSED)
    return after_stop ? 0 : (taken.code == COAKKA_HTTP_RESULT_TIMEOUT ? 1 : -1);
  if (!check(taken, "take")) return -1;
  int ok = 1;
  if (event.kind == COAKKA_HTTP_EVENT_REQUEST && !after_stop) {
    ++requests;
    coakka_http_response_t response;
    coakka_http_response_init(&response);
    response.headers = &content_type;
    response.header_count = 1U;
    response.body = bytes(body);
    coakka_http_result_t sent = coakka_http_core_respond(core, event.exchange, &response);
    if (sent.code == COAKKA_HTTP_RESULT_STALE_EXCHANGE) ++late_responses;
    else ok = check(sent, "respond");
  } else if (event.kind == COAKKA_HTTP_EVENT_TERMINAL) {
    ++terminals;
  } else {
    fprintf(stderr, "unexpected event: %u\n", event.kind);
    ok = 0;
  }
  if (!check(coakka_http_core_release_event(core, &event), "release")) ok = 0;
  return ok ? 1 : -1;
}

int main(int argc, char **argv) {
  const char *policy = getenv("COAKKA_BENCH_CPU_POLICY");
  char *end = NULL;
  if (argc != 2 || policy == NULL ||
      (strcmp(policy, "single") != 0 && strcmp(policy, "auto") != 0)) return EXIT_FAILURE;
  errno = 0;
  unsigned long port = strtoul(argv[1], &end, 10);
  if (errno || end == argv[1] || *end || !port || port > 65535UL) return EXIT_FAILURE;
  const uint32_t loops = strcmp(policy, "single") == 0 ? 1U : 2U;
  coakka_http_configuration_t *config = NULL;
  coakka_http_core_t *core = NULL;
  int ok = 0, started = 0;
  if (!check(coakka_http_configuration_create(&config), "configuration")) goto cleanup;

  /* Fixed native profile, selected BEFORE measurement (not a universal default).
   * The runner confines the entire process to CPU 0 or 0-1. EXACT fails rather
   * than silently measuring another topology. CPU policy here is a harness
   * label, not a fabricated Core CPU-selection observation.
   * Bounded queues leave headroom for 64 persistent clients, leases and teardown.
   * Full terminal events remain enabled. No success-event omission optimization.
   */
  const coakka_http_execution_options_t execution = {
      sizeof(execution), COAKKA_HTTP_EXECUTION_EXACT, loops, 0U};
  coakka_http_core_limits_t limits;
  coakka_http_core_limits_init(&limits);
  limits.max_connections = 512U;
  limits.max_active_exchanges = 256U;
  limits.request_queue_depth = 256U;
  limits.completion_queue_depth = 256U;
  limits.terminal_observation_queue_depth = 256U;
  limits.completion_batch_size = 64U;
  limits.request_notification_profile = COAKKA_HTTP_NOTIFICATION_ULTRA;
  limits.terminal_notification_profile = COAKKA_HTTP_NOTIFICATION_XXLARGE;
  coakka_http_listener_t listener;
  coakka_http_listener_init(&listener);
  listener.listener_id = 1U; listener.port = (uint16_t)port;
  listener.bind_address = bytes("127.0.0.1");
  coakka_http_core_route_t route;
  coakka_http_core_route_init(&route);
  route.route_id = 1U; route.handler_binding_id = 1U;
  route.method = bytes("GET"); route.path = bytes("/fixed");
  if (!check(coakka_http_configuration_set_execution(config, &execution), "execution") ||
      !check(coakka_http_configuration_set_limits(config, &limits), "limits") ||
      !check(coakka_http_configuration_set_io_backend(config, COAKKA_HTTP_IO_PLATFORM_DEFAULT), "backend") ||
      !check(coakka_http_configuration_add_listener(config, &listener), "listener") ||
      !check(coakka_http_configuration_add_route(config, &route), "route") ||
      !check(coakka_http_core_create(config, &core), "create") ||
      !check(coakka_http_core_start(core), "start")) goto cleanup;
  started = 1;
  coakka_http_runtime_info_t info;
  coakka_http_runtime_info_init(&info);
  if (!check(coakka_http_core_get_runtime_info(core, &info), "runtime info") ||
      !info.core_started || !info.execution.observed ||
      info.execution.configured_event_loops != loops || info.execution.active_event_loops != loops ||
      info.request_notification_batch_size != 64U || info.terminal_notification_batch_size != 32U ||
      info.effective_io_backend != COAKKA_HTTP_IO_PLATFORM_DEFAULT) goto cleanup;
  /* Direct native construction does not own process CPU selection. Preserve its
   * actual observation (including zero/unobserved); the runner independently
   * verifies Linux process affinity instead of inventing Core-selected CPUs.
   */
  printf("coakka-runtime-info={\"nativeProfile\":\"explicit-host-inlined-v1\","
         "\"cpu\":{\"requestedPolicy\":%u,\"placement\":%u,\"selectedCpuCount\":%u,\"selectedCpuIds\":[",
         info.cpu.requested_policy, info.cpu.placement, info.cpu.selected_cpu_count);
  if (info.cpu.selected_cpu_count > 2U) goto cleanup;
  for (uint32_t i = 0; i < info.cpu.selected_cpu_count; ++i)
    printf("%s%u", i ? "," : "", info.cpu.selected_cpu_ids[i]);
  printf("]},\"execution\":{\"observed\":true,\"configuredEventLoops\":%u,\"activeEventLoops\":%u},"
         "\"requestNotificationBatchSize\":%u,\"terminalNotificationBatchSize\":%u,"
         "\"effectiveIoBackend\":%u,\"ioUringEffective\":false,\"limits\":null}\n",
         info.execution.configured_event_loops, info.execution.active_event_loops,
         info.request_notification_batch_size, info.terminal_notification_batch_size, info.effective_io_backend);
  fflush(stdout);
  if (signal(SIGINT, stop_signal) == SIG_ERR || signal(SIGTERM, stop_signal) == SIG_ERR) goto cleanup;
  ok = 1;
  while (!stopping && ok) ok = consume(core, 0) >= 0;
cleanup:
  if (config && !check(coakka_http_configuration_destroy(&config), "configuration destroy")) ok = 0;
  if (core) {
    if (started) {
      if (!check(coakka_http_core_drain(core), "drain")) ok = 0;
      if (!check(coakka_http_core_stop(core), "stop")) ok = 0;
      /* Resource reservation bounds retained terminals. Stop produces no new
       * requests; drain remaining leases before destroying the owning Core. */
      unsigned int remaining = 1024U;
      int status = 1;
      while (remaining-- && status > 0) status = consume(core, 1);
      if (status != 0 || terminals < requests) ok = 0;
    }
    if (!check(coakka_http_core_destroy(&core), "destroy")) ok = 0;
  }
  printf("native-events requests=%" PRIu64 " terminals=%" PRIu64 " late=%" PRIu64 "\n",
         requests, terminals, late_responses);
  if (ok) puts("benchmark-shutdown=pass");
  return ok ? EXIT_SUCCESS : EXIT_FAILURE;
}
