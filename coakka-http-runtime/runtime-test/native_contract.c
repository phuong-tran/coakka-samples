#include <coakka/http/http.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint64_t checks = 0U;
static uint64_t failures = 0U;

#define CHECK(expression)                                                      \
  do {                                                                         \
    checks += 1U;                                                              \
    if (!(expression)) {                                                       \
      failures += 1U;                                                          \
      fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #expression); \
    }                                                                          \
  } while (0)

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : (uint64_t)strlen(value);
  return result;
}

static void unused_handler(void *context, coakka_http_request_t *request) {
  (void)context;
  (void)request;
}

int main(void) {
  static const struct {
    coakka_http_result_code_t code;
    const char *name;
  } names[] = {
      {COAKKA_HTTP_RESULT_OK, "ok"},
      {COAKKA_HTTP_RESULT_INVALID_ARGUMENT, "invalid_argument"},
      {COAKKA_HTTP_RESULT_INVALID_STATE, "invalid_state"},
      {COAKKA_HTTP_RESULT_LIMIT_EXCEEDED, "limit_exceeded"},
      {COAKKA_HTTP_RESULT_QUEUE_FULL, "queue_full"},
      {COAKKA_HTTP_RESULT_TIMEOUT, "timeout"},
      {COAKKA_HTTP_RESULT_CLOSED, "closed"},
      {COAKKA_HTTP_RESULT_CANCELLED, "cancelled"},
      {COAKKA_HTTP_RESULT_OUT_OF_MEMORY, "out_of_memory"},
      {COAKKA_HTTP_RESULT_UNSUPPORTED, "unsupported"},
      {COAKKA_HTTP_RESULT_SYSTEM_ERROR, "system_error"},
      {COAKKA_HTTP_RESULT_INTERNAL, "internal"},
      {COAKKA_HTTP_RESULT_STALE_EXCHANGE, "stale_exchange"},
      {COAKKA_HTTP_RESULT_RETAINED, "retained"},
      {COAKKA_HTTP_RESULT_NOT_FOUND, "not_found"},
      {COAKKA_HTTP_RESULT_UNAVAILABLE, "unavailable"},
  };
  coakka_http_result_t result;
  coakka_http_server_options_t options;
  coakka_http_server_tuning_t tuning;
  coakka_http_compression_t compression;
  coakka_http_route_t routes[2];
  coakka_http_static_mount_t static_mount;
  coakka_http_file_authority_t file_authority;
  coakka_http_response_t response;
  coakka_http_service_plan_request_t plan_request;
  coakka_http_service_plan_t plan;
  coakka_http_service_plan_t plan_before;
  coakka_http_runtime_info_t runtime_info;
  coakka_http_health_t health;
  coakka_http_monitor_options_t monitor_options;
  coakka_http_monitor_config_view_t monitor_config;
  coakka_http_monitor_snapshot_view_t monitor_snapshot;
  coakka_http_monitor_policy_view_t monitor_policy;
  coakka_http_monitor_apply_outcome_t monitor_outcome;
  coakka_http_monitor_event_view_t monitor_events[4];
  coakka_http_monitor_event_page_view_t monitor_page;
  coakka_http_route_rebind_t rebind;
  coakka_http_route_rebind_outcome_t rebind_outcome;
  coakka_http_server_t *server = NULL;
  coakka_http_response_stream_t *response_stream = NULL;
  coakka_http_socket_event_t socket_event;
  coakka_http_header_t header;
  size_t index;

  CHECK(coakka_http_abi_version() == COAKKA_HTTP_ABI_VERSION);
#if defined(COAKKA_HTTP_EXPECT_TLS)
  CHECK((coakka_http_features() & COAKKA_HTTP_CAPABILITY_TLS) != 0U);
  CHECK((coakka_http_features() & COAKKA_HTTP_CAPABILITY_MUTUAL_TLS) != 0U);
#endif
#if defined(COAKKA_HTTP_EXPECT_PROTOCOLS)
  CHECK((coakka_http_features() & COAKKA_HTTP_CAPABILITY_HTTP2) != 0U);
  CHECK((coakka_http_features() & COAKKA_HTTP_CAPABILITY_HTTP3) != 0U);
#endif
  for (index = 0U; index < sizeof(names) / sizeof(names[0]); ++index) {
    CHECK(strcmp(coakka_http_result_code_name(names[index].code),
                 names[index].name) == 0);
  }
  CHECK(strcmp(coakka_http_result_code_name(UINT32_C(999)), "unknown") == 0);

  coakka_http_service_plan_request_init(&plan_request);
  coakka_http_service_plan_init(&plan);
  plan_request.required_capabilities =
      COAKKA_HTTP_CAPABILITY_MUTUAL_TLS |
      COAKKA_HTTP_CAPABILITY_SERVER_SENT_EVENTS |
      COAKKA_HTTP_CAPABILITY_OUTBOUND_LOGICAL_TARGET |
      COAKKA_HTTP_CAPABILITY_STATIC_FRONTEND;
  plan_request.host_capabilities = COAKKA_HTTP_CAPABILITY_ALL;
  plan_request.runtime_capabilities = COAKKA_HTTP_CAPABILITY_ALL;
  CHECK(coakka_http_plan_service(&plan_request, &plan).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(plan.provider == COAKKA_HTTP_PROVIDER_HOST);
  CHECK((plan.required_capabilities & COAKKA_HTTP_CAPABILITY_INBOUND) != 0U);
  CHECK((plan.required_capabilities & COAKKA_HTTP_CAPABILITY_TLS) != 0U);
  CHECK((plan.required_capabilities & COAKKA_HTTP_CAPABILITY_RESPONSE_STREAM) !=
        0U);
  CHECK((plan.required_capabilities & COAKKA_HTTP_CAPABILITY_OUTBOUND) != 0U);
  CHECK((plan.required_capabilities & COAKKA_HTTP_CAPABILITY_STATIC_FILES) !=
        0U);

  plan_request.host_capabilities = COAKKA_HTTP_CAPABILITY_INBOUND;
  coakka_http_service_plan_init(&plan);
  CHECK(coakka_http_plan_service(&plan_request, &plan).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(plan.provider == COAKKA_HTTP_PROVIDER_RUNTIME);
  CHECK(plan.missing_host_capabilities != 0U);

  plan_request.runtime_capabilities = COAKKA_HTTP_CAPABILITY_INBOUND;
  coakka_http_service_plan_init(&plan);
  CHECK(coakka_http_plan_service(&plan_request, &plan).code ==
        COAKKA_HTTP_RESULT_UNSUPPORTED);
  CHECK(plan.provider == COAKKA_HTTP_PROVIDER_NONE);
  CHECK(plan.missing_runtime_capabilities != 0U);

  plan_request.reserved = 1U;
  plan.provider = UINT32_C(99);
  plan_before = plan;
  CHECK(coakka_http_plan_service(&plan_request, &plan).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(memcmp(&plan, &plan_before, sizeof(plan)) == 0);
  plan_request.reserved = 0U;

  memset(&result, 0xff, sizeof(result));
  coakka_http_result_init(&result);
  CHECK(result.struct_size == sizeof(result));
  CHECK(result.code == COAKKA_HTTP_RESULT_OK);
  CHECK(result.actual == 0U && result.limit == 0U && result.detail[0] == '\0');

  coakka_http_server_options_init(&options);
  CHECK(options.struct_size == sizeof(options));
  CHECK(options.worker_count > 0U && options.max_connections > 0U);
  CHECK(options.max_active_requests > 0U &&
        options.max_active_requests <= options.max_connections);
  CHECK(options.request_queue_capacity > 0U &&
        options.response_queue_capacity > 0U);
  CHECK(options.max_request_body_bytes > 0U &&
        options.max_response_body_bytes > 0U);
  CHECK(options.request_timeout_ms > 0U &&
        options.response_write_timeout_ms > 0U &&
        options.shutdown_timeout_ms > 0U);
  CHECK(options.max_handler_bindings >= 1U);
  CHECK(options.protocol == COAKKA_HTTP_PROTOCOL_HTTP_1_1);
  CHECK(options.security == COAKKA_HTTP_SECURITY_PLAINTEXT);
  CHECK(options.reserve_websocket == 0U);
  CHECK(options.credential_generation == 0U);
  CHECK(options.credential_id.size == 0U);
  CHECK(options.certificate_chain_file.size == 0U);
  CHECK(options.private_key_file.size == 0U);
  CHECK(options.trust_roots_file.size == 0U);
  CHECK(options.monitor == NULL);

  coakka_http_route_init(&routes[0]);
  CHECK(routes[0].struct_size == sizeof(routes[0]));
  coakka_http_static_mount_init(&static_mount);
  coakka_http_file_authority_init(&file_authority);
  coakka_http_response_init(&response);
  CHECK(response.struct_size == sizeof(response));
  CHECK(response.status_code == 200U);

  CHECK(coakka_http_request_route_id(NULL) == 0U);
  CHECK(coakka_http_request_exchange(NULL).slot == 0U);
  CHECK(coakka_http_request_exchange(NULL).generation == 0U);
  CHECK(coakka_http_request_method(NULL).size == 0U);
  CHECK(coakka_http_request_scheme(NULL).size == 0U);
  CHECK(coakka_http_request_authority(NULL).size == 0U);
  CHECK(coakka_http_request_target(NULL).size == 0U);
  CHECK(coakka_http_request_body(NULL).size == 0U);
  CHECK(coakka_http_request_trailer_count(NULL) == 0U);
  CHECK(coakka_http_request_trailer(NULL, 0U, NULL) == 0U);
  CHECK(coakka_http_request_header_count(NULL) == 0U);
  CHECK(coakka_http_request_header(NULL, 0U, &header) == 0U);
  {
    coakka_http_path_parameter_t path, path_before;
    coakka_http_query_parameter_t query, query_before;
    memset(&path, 0xa5, sizeof(path));
    memset(&query, 0xa5, sizeof(query));
    path_before = path;
    query_before = query;
    CHECK(coakka_http_request_path_parameter_count(NULL) == 0U);
    CHECK(coakka_http_request_query_parameter_count(NULL) == 0U);
    CHECK(coakka_http_request_path_parameter(NULL, 0U, &path) == 0U);
    CHECK(coakka_http_request_query_parameter(NULL, 0U, &query) == 0U);
    CHECK(memcmp(&path, &path_before, sizeof(path)) == 0);
    CHECK(memcmp(&query, &query_before, sizeof(query)) == 0);
  }
  CHECK(coakka_http_request_cancelled(NULL) != 0U);
  CHECK(coakka_http_request_respond(NULL, &response).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_request_start_response_stream(NULL, &response,
                                                  &response_stream)
            .code == COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_request_start_sse(NULL, NULL, 0U, &response_stream).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_request_accept_websocket(NULL, bytes("")).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_response_stream_wait_writable(NULL, 0U).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_response_stream_write(NULL, bytes("chunk")).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_response_stream_write_sse(NULL, NULL).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_response_stream_finish(NULL, NULL, 0U).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  coakka_http_response_stream_release(NULL);
  coakka_http_response_stream_release(&response_stream);
  CHECK(response_stream == NULL);
  coakka_http_socket_event_init(&socket_event);
  CHECK(socket_event.struct_size == sizeof(socket_event));
  CHECK(socket_event.kind == 0U && socket_event.private_lease == 0U &&
        socket_event.private_owner == NULL);
  CHECK(coakka_http_server_take_websocket(NULL, 0U, &socket_event).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_release_websocket(NULL, &socket_event).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_send_websocket(
            NULL, socket_event.session, COAKKA_HTTP_WEBSOCKET_TEXT, bytes("x"))
            .code == COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_close_websocket(NULL, socket_event.session, 1000U,
                                           bytes("done"))
            .code == COAKKA_HTTP_RESULT_INVALID_ARGUMENT);

  routes[0].route_id = 1U;
  routes[0].method = bytes("GET");
  routes[0].path = bytes("/contract");
  routes[0].handler = unused_handler;
  CHECK(coakka_http_server_create(NULL, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_create(&options, NULL, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_create(&options, routes, 0U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);

  routes[1] = routes[0];
  CHECK(coakka_http_server_create(&options, routes, 2U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);

  options.static_mounts = &static_mount;
  options.static_mount_count = COAKKA_HTTP_SERVER_MAX_STATIC_MOUNTS + 1U;
  result = coakka_http_server_create(&options, routes, 1U, &server);
  CHECK(result.code == COAKKA_HTTP_RESULT_LIMIT_EXCEEDED);
  CHECK(result.actual == COAKKA_HTTP_SERVER_MAX_STATIC_MOUNTS + 1U);
  CHECK(result.limit == COAKKA_HTTP_SERVER_MAX_STATIC_MOUNTS);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  options.file_authorities = &file_authority;
  options.file_authority_count = COAKKA_HTTP_SERVER_MAX_FILE_AUTHORITIES + 1U;
  result = coakka_http_server_create(&options, routes, 1U, &server);
  CHECK(result.code == COAKKA_HTTP_RESULT_LIMIT_EXCEEDED);
  CHECK(result.actual == COAKKA_HTTP_SERVER_MAX_FILE_AUTHORITIES + 1U);
  CHECK(result.limit == COAKKA_HTTP_SERVER_MAX_FILE_AUTHORITIES);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  options.protocol = UINT32_C(99);
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  options.reserve_websocket = 2U;
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  options.security = COAKKA_HTTP_SECURITY_TLS;
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  options.worker_count = 0U;
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_LIMIT_EXCEEDED);
  CHECK(server == NULL);
  coakka_http_server_options_init(&options);

  coakka_http_monitor_options_init(&monitor_options);
  coakka_http_server_tuning_init(&tuning);
  CHECK(tuning.struct_size == sizeof(tuning));
  CHECK(tuning.compression == NULL);
  CHECK(tuning.cpu_policy == COAKKA_HTTP_CPU_AUTO);
  CHECK(tuning.request_notification_profile == COAKKA_HTTP_NOTIFICATION_AUTO);
  CHECK(tuning.terminal_notification_profile == COAKKA_HTTP_NOTIFICATION_AUTO);
  options.tuning = &tuning;
  tuning.cpu_policy = UINT32_C(2);
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  tuning.cpu_policy = COAKKA_HTTP_CPU_AUTO;
  tuning.request_notification_profile = UINT32_C(3);
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  tuning.request_notification_profile = COAKKA_HTTP_NOTIFICATION_SMALL;
  tuning.terminal_notification_profile = UINT32_C(3);
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  tuning.terminal_notification_profile = COAKKA_HTTP_NOTIFICATION_MEDIUM;
  memset(&compression, 0, sizeof(compression));
  compression.struct_size = sizeof(compression);
  compression.mode = UINT32_C(999);
  tuning.compression = &compression;
  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(server == NULL);
  compression.mode = COAKKA_HTTP_COMPRESSION_DISABLED;
  monitor_options.collection = COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS;
  monitor_options.event_capacity = 16U;
  monitor_options.max_events_per_read = 4U;
  monitor_options.aggregate_categories =
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_LIFECYCLE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_EXCHANGE |
      COAKKA_HTTP_MONITOR_CATEGORY_BIT_RESPONSE;
  monitor_options.event_categories = monitor_options.aggregate_categories;
  monitor_options.signal_reserved = 1U;
  options.monitor = &monitor_options;

  CHECK(coakka_http_server_create(&options, routes, 1U, &server).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(server != NULL);
  coakka_http_runtime_info_init(&runtime_info);
  CHECK(coakka_http_server_get_runtime_info(server, &runtime_info).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(runtime_info.abi_version == COAKKA_HTTP_ABI_VERSION);
  CHECK(runtime_info.requested_io_backend == COAKKA_HTTP_IO_PLATFORM_DEFAULT);
  CHECK(runtime_info.effective_io_backend == COAKKA_HTTP_IO_PLATFORM_DEFAULT);
  CHECK(runtime_info.core_started == 0U);
  CHECK(runtime_info.cpu.startup_verified == 0U);
  CHECK(runtime_info.cpu.requested_policy == COAKKA_HTTP_CPU_AUTO);
#if defined(__linux__)
  CHECK(runtime_info.cpu.selected_cpu_count >= 1U);
  CHECK(runtime_info.cpu.selected_cpu_count <= 2U);
  CHECK(runtime_info.cpu.available_cpu_count >= runtime_info.cpu.selected_cpu_count);
#else
  CHECK(runtime_info.cpu.placement == COAKKA_HTTP_CPU_UNSUPPORTED);
  CHECK(runtime_info.cpu.available_cpu_count == 0U);
  CHECK(runtime_info.cpu.selected_cpu_count == 0U);
  CHECK(runtime_info.cpu.selected_cpu_ids[0] == UINT32_MAX);
#endif
  CHECK(runtime_info.request_notification_batch_size == 1U);
  CHECK(runtime_info.terminal_notification_batch_size == 4U);
  CHECK(runtime_info.execution.observed != 0U);
  CHECK(runtime_info.execution.configured_event_loops == 1U);
  CHECK(runtime_info.selection.requested_mode == COAKKA_HTTP_EXECUTION_EXACT);
  CHECK(runtime_info.selection.requested_count == 1U);
  CHECK(runtime_info.selection.selected_count == 1U);
  CHECK(runtime_info.selection.reason == COAKKA_HTTP_EXECUTION_EXACT_SELECTED);
  CHECK(runtime_info.execution.active_event_loops == 0U);
  CHECK(runtime_info.execution.draining_event_loops == 0U);
  CHECK(runtime_info.execution.failed_event_loops == 0U);
  coakka_http_health_init(&health);
  CHECK(coakka_http_server_health(server, &health).code ==
        COAKKA_HTTP_RESULT_INVALID_STATE);
  CHECK(coakka_http_server_port(server, &options.port).code ==
        COAKKA_HTTP_RESULT_INVALID_STATE);
  CHECK(coakka_http_server_start(NULL).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_start(server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_port(server, &options.port).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(options.port != 0U);
  CHECK(coakka_http_server_get_runtime_info(server, &runtime_info).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(runtime_info.core_started != 0U);
#if defined(__linux__)
  CHECK(runtime_info.cpu.startup_verified == 1U);
#else
  CHECK(runtime_info.cpu.startup_verified == 0U);
#endif
  CHECK(runtime_info.execution.observed != 0U);
  CHECK(runtime_info.execution.configured_event_loops == 1U);
  CHECK(runtime_info.execution.active_event_loops == 1U);
  CHECK(runtime_info.execution.draining_event_loops == 0U);
  CHECK(runtime_info.execution.failed_event_loops == 0U);
  CHECK(coakka_http_server_health(server, &health).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(health.configured_components > 0U);
  CHECK(health.failed_components == 0U);
  CHECK(coakka_http_server_prepare_handler(server, 2U, NULL, unused_handler)
            .code == COAKKA_HTTP_RESULT_OK);
  coakka_http_route_rebind_init(&rebind);
  rebind.activation_id = 1U;
  rebind.expected_route_generation = 1U;
  rebind.route_id = 1U;
  rebind.expected_binding_revision = 1U;
  rebind.new_handler_binding_id = 2U;
  coakka_http_route_rebind_outcome_init(&rebind_outcome);
  CHECK(
      coakka_http_server_rebind_handler(server, &rebind, 1000U, &rebind_outcome)
          .code == COAKKA_HTTP_RESULT_OK);
  CHECK(rebind_outcome.code == COAKKA_HTTP_ROUTE_REBIND_APPLIED &&
        rebind_outcome.changed != 0U);
  CHECK(rebind_outcome.previous_handler_binding_id == 1U);
  CHECK(rebind_outcome.effective_handler_binding_id == 2U);
  coakka_http_route_rebind_outcome_init(&rebind_outcome);
  CHECK(
      coakka_http_server_rebind_handler(server, &rebind, 1000U, &rebind_outcome)
          .code == COAKKA_HTTP_RESULT_OK);
  CHECK(rebind_outcome.code == COAKKA_HTTP_ROUTE_REBIND_APPLIED &&
        rebind_outcome.replayed != 0U);
  CHECK(coakka_http_server_probe_liveness(server, 0U, &health).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);
  CHECK(coakka_http_server_probe_liveness(server, 1000U, &health).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(health.acknowledged_probe_sequence > 0U);
  CHECK(health.failed_components == 0U);
  coakka_http_monitor_config_view_init(&monitor_config);
  CHECK(coakka_http_server_monitor_config(server, &monitor_config).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(monitor_config.generation != 0U);
  CHECK(monitor_config.policy.collection ==
        COAKKA_HTTP_MONITOR_AGGREGATES_AND_EVENTS);
  CHECK(monitor_config.reserved_event_capacity == 16U);
  CHECK(monitor_config.max_events_per_read == 4U);

  coakka_http_monitor_snapshot_view_init(&monitor_snapshot);
  CHECK(coakka_http_server_monitor_snapshot(server, &monitor_snapshot).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(monitor_snapshot.collecting != 0U);

  for (index = 0U; index < 4U; ++index) {
    coakka_http_monitor_event_view_init(&monitor_events[index]);
  }
  coakka_http_monitor_event_page_init(&monitor_page);
  CHECK(coakka_http_server_monitor_read(server, 0U, 4U, monitor_events, 4U,
                                        &monitor_page)
            .code == COAKKA_HTTP_RESULT_OK);
  CHECK(monitor_page.count != 0U);

  coakka_http_monitor_policy_view_init(&monitor_policy);
  monitor_policy.collection = COAKKA_HTTP_MONITOR_AGGREGATES;
  monitor_policy.notification = COAKKA_HTTP_MONITOR_NOTIFY_POLL;
  monitor_policy.latency = COAKKA_HTTP_MONITOR_LATENCY_NONE;
  monitor_policy.detail = COAKKA_HTTP_MONITOR_DETAIL_NONE;
  monitor_policy.aggregate_categories = monitor_options.aggregate_categories;
  coakka_http_monitor_apply_outcome_init(&monitor_outcome);
  CHECK(coakka_http_server_monitor_apply(server, monitor_config.generation,
                                         &monitor_policy, &monitor_outcome)
            .code == COAKKA_HTTP_RESULT_OK);
  CHECK(monitor_outcome.reason == COAKKA_HTTP_MONITOR_APPLY_REASON_APPLIED);
  CHECK(monitor_outcome.changed != 0U);
  result = coakka_http_server_monitor_wait(server, 0U);
  CHECK(result.code == COAKKA_HTTP_RESULT_OK ||
        result.code == COAKKA_HTTP_RESULT_TIMEOUT);
  CHECK(coakka_http_server_monitor_interrupt(server).code ==
        COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_stop(server).code == COAKKA_HTTP_RESULT_OK);
  {
    const coakka_http_runtime_info_t before = runtime_info;
    CHECK(coakka_http_server_get_runtime_info(server, &runtime_info).code ==
          COAKKA_HTTP_RESULT_CLOSED);
    CHECK(memcmp(&runtime_info, &before, sizeof(before)) == 0);
  }
  CHECK(coakka_http_server_prepare_handler(server, 3U, &checks, unused_handler)
            .code == COAKKA_HTTP_RESULT_INVALID_STATE);
  coakka_http_route_rebind_outcome_init(&rebind_outcome);
  CHECK(
      coakka_http_server_rebind_handler(server, &rebind, 1000U, &rebind_outcome)
          .code == COAKKA_HTTP_RESULT_INVALID_STATE);
  CHECK(coakka_http_server_stop(server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(coakka_http_server_start(server).code ==
        COAKKA_HTTP_RESULT_INVALID_STATE);
  CHECK(coakka_http_server_destroy(&server).code == COAKKA_HTTP_RESULT_OK);
  CHECK(server == NULL);
  CHECK(coakka_http_server_destroy(&server).code ==
        COAKKA_HTTP_RESULT_INVALID_ARGUMENT);

  /* SINGLE is an explicit request, not an alias for an unverified AUTO. */
  coakka_http_server_options_init(&options);
  coakka_http_server_tuning_init(&tuning);
  tuning.cpu_policy = COAKKA_HTTP_CPU_SINGLE;
  options.tuning = &tuning;
  result = coakka_http_server_create(&options, routes, 1U, &server);
#if defined(__linux__)
  CHECK(result.code == COAKKA_HTTP_RESULT_OK);
  CHECK(server != NULL);
  if (server != NULL) {
    CHECK(coakka_http_server_start(server).code == COAKKA_HTTP_RESULT_OK);
    coakka_http_runtime_info_init(&runtime_info);
    CHECK(coakka_http_server_get_runtime_info(server, &runtime_info).code == COAKKA_HTTP_RESULT_OK);
    CHECK(runtime_info.cpu.requested_policy == COAKKA_HTTP_CPU_SINGLE);
    CHECK(runtime_info.cpu.selected_cpu_count == 1U);
    CHECK(runtime_info.cpu.selected_cpu_ids[1] == UINT32_MAX);
    CHECK(runtime_info.cpu.startup_verified == 1U);
    CHECK(coakka_http_server_destroy(&server).code == COAKKA_HTTP_RESULT_OK);
  }
#else
  CHECK(result.code == COAKKA_HTTP_RESULT_UNSUPPORTED);
  CHECK(server == NULL);
#endif

  printf("{\"schema\":\"coakka.http.native-contract.v1\","
         "\"checks\":%llu,\"failures\":%llu,\"status\":\"%s\"}\n",
         (unsigned long long)checks, (unsigned long long)failures,
         failures == 0U ? "pass" : "fail");
  return failures == 0U ? EXIT_SUCCESS : EXIT_FAILURE;
}
