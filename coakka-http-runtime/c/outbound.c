/* A self-contained logical-target HTTP client example using only the installed
 * C API. Two loopback services keep it reproducible without Internet access.
 * The main thread is the sole terminal reader; every lease is released before
 * another take or service shutdown. No application transport thread is added.
 */
#include "../native/sample_options.h"
#include <stdatomic.h>

static atomic_int failed = 0;

static int checked(coakka_http_result_t value, const char *operation) {
  if (value.code == COAKKA_HTTP_RESULT_OK) return 1;
  fprintf(stderr, "%s: %s (%s)\n", operation,
          coakka_http_result_code_name(value.code), value.detail);
  atomic_store(&failed, 1);
  return 0;
}

static void source(void *context, coakka_http_request_t *request) {
  coakka_http_response_t response;
  (void)context;
  coakka_http_response_init(&response);
  response.body = sample_bytes("outbound-ready");
  (void)checked(coakka_http_request_respond(request, &response), "source response");
}

/* HTTP 404 is still an observed HTTP response, not a transport failure. Read
 * the typed terminal reason first; status alone does not describe delivery.
 * All strings and body bytes below are borrowed only until exact release.
 */
static int exchange(coakka_http_server_t *client, const char *path, uint16_t status) {
  coakka_http_client_request_t request;
  coakka_http_client_terminal_t terminal;
  coakka_http_outbound_id_t call;
  int matches;
  coakka_http_outbound_request_init(&request);
  request.logical_target = sample_bytes("sample.upstream");
  request.method = sample_bytes("GET");
  request.target = sample_bytes(path);
  request.timeout_ms = 3000;
  if (!checked(coakka_http_server_outbound_submit(client, &request, &call), "submit")) return 0;
  coakka_http_outbound_terminal_init(&terminal);
  if (!checked(coakka_http_server_take_outbound(client, 5000, &terminal), "take terminal")) return 0;
  matches = terminal.call.slot == call.slot && terminal.call.generation == call.generation &&
            terminal.reason == COAKKA_HTTP_OUTBOUND_TERMINAL_RESPONSE &&
            terminal.response_status == status;
  if (status == 200) {
    const char expected[] = "outbound-ready";
    matches = matches && terminal.response_body.size == sizeof(expected) - 1 &&
              memcmp(terminal.response_body.data, expected, sizeof(expected) - 1) == 0;
  }
  if (!matches) {
    /* This is an operator diagnostic, never an automatic client response. */
    fprintf(stderr, "unexpected terminal: reason=%u phase=%u status=%u\n",
            terminal.reason, terminal.phase, (unsigned)terminal.response_status);
    atomic_store(&failed, 1);
  }
  if (!checked(coakka_http_server_release_outbound(client, &terminal), "release terminal")) return 0;
  return matches;
}

/* Structural replacement is not a handler-only update: this candidate is the
 * complete next route table. This isolated example has no other control writer.
 * Generation 1 is an explicit compare-and-apply precondition, never a reported
 * observation; an unexpected current generation fails this example closed.
 */
static int publish_routes(coakka_http_server_t *server) {
  coakka_http_core_route_t route;
  coakka_http_route_publication_t change;
  coakka_http_route_publication_outcome_t applied, replay, rejected;
  if (!checked(coakka_http_server_prepare_handler(server, 2, NULL, source), "prepare route")) return 0;
  coakka_http_core_route_init(&route);
  route.route_id = 2; route.handler_binding_id = 2;
  route.method = sample_bytes("GET"); route.path = sample_bytes("/published");
  coakka_http_route_publication_init(&change);
  change.activation_id = 1; change.expected_route_generation = 1;
  change.expected_metadata_generation = 1; change.expected_binding_change_sequence = 1;
  change.routes = &route; change.route_count = 1;
  coakka_http_route_publication_outcome_init(&applied);
  if (!checked(coakka_http_server_publish_routes(server, &change, 2000, &applied), "publish") ||
      applied.code != COAKKA_HTTP_ROUTE_MANIFEST_APPLIED || !applied.changed || applied.replayed) return 0;

  /* Retry only the identical operation, including its activation identity and
   * payload. Core's bounded replay record, not the app, decides the outcome.
   * This proves ordinary replay, not an injected ambiguous-delivery failure.
   */
  coakka_http_route_publication_outcome_init(&replay);
  if (!checked(coakka_http_server_publish_routes(server, &change, 2000, &replay), "replay") ||
      replay.code != COAKKA_HTTP_ROUTE_MANIFEST_APPLIED || !replay.replayed ||
      replay.operation_digest != applied.operation_digest ||
      replay.effective_route_generation != applied.effective_route_generation) return 0;
  change.activation_id = 2;
  coakka_http_route_publication_outcome_init(&rejected);
  if (!checked(coakka_http_server_publish_routes(server, &change, 2000, &rejected), "stale publication") ||
      rejected.code != COAKKA_HTTP_ROUTE_MANIFEST_GENERATION_MISMATCH || rejected.changed ||
      rejected.effective_route_generation != applied.effective_route_generation ||
      rejected.effective_metadata_generation != applied.effective_metadata_generation ||
      rejected.effective_binding_change_sequence != applied.effective_binding_change_sequence) return 0;
  puts("native-route-publication=pass");
  return 1;
}

int main(void) {
  coakka_http_server_t *upstream = NULL, *client = NULL;
  coakka_http_server_options_t options;
  coakka_http_route_t route;
  coakka_http_outbound_endpoint_t endpoint;
  coakka_http_outbound_target_t target;
  uint16_t port = 0;

  coakka_http_server_options_init(&options);
  options.bind_address = sample_bytes("127.0.0.1");
  coakka_http_route_init(&route);
  route.route_id = 1; route.method = sample_bytes("GET");
  route.path = sample_bytes("/source"); route.handler = source;
  if (!checked(coakka_http_server_create(&options, &route, 1, &upstream), "create upstream") ||
      !checked(coakka_http_server_start(upstream), "start upstream") ||
      !checked(coakka_http_server_port(upstream, &port), "upstream port")) goto cleanup;

  coakka_http_outbound_endpoint_init(&endpoint);
  endpoint.node_id = sample_bytes("local-source");
  endpoint.connect_host = sample_bytes("127.0.0.1");
  endpoint.connect_port = port;
  endpoint.http_authority = sample_bytes("localhost");
  coakka_http_outbound_target_init(&target);
  target.name = sample_bytes("sample.upstream"); target.generation = 1;
  target.endpoints = &endpoint; target.endpoint_count = 1;
  options.outbound_targets = &target; options.outbound_target_count = 1;
  /* create copies the topology; Core owns name resolution and transport state.
   * Omitted resource options select Core's defaults, not a sample-owned table.
   */
  if (!checked(coakka_http_server_create(&options, &route, 1, &client), "create client") ||
      !checked(coakka_http_server_start(client), "start client")) goto cleanup;
  if (!exchange(client, "/source", 200) || !exchange(client, "/missing", 404)) goto cleanup;
  if (!publish_routes(upstream)) { atomic_store(&failed, 1); goto cleanup; }
  if (!exchange(client, "/published", 200) || !exchange(client, "/source", 404)) goto cleanup;

cleanup:
  /* The caller owns both lifetimes; stop the client before its upstream. */
  if (client != NULL) {
    (void)checked(coakka_http_server_stop(client), "stop client");
    (void)checked(coakka_http_server_destroy(&client), "destroy client");
  }
  if (upstream != NULL) {
    (void)checked(coakka_http_server_stop(upstream), "stop upstream");
    (void)checked(coakka_http_server_destroy(&upstream), "destroy upstream");
  }
  if (atomic_load(&failed)) return EXIT_FAILURE;
  puts("native-outbound-smoke=pass");
  return EXIT_SUCCESS;
}
