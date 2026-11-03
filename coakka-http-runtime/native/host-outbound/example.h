#ifndef COAKKA_HTTP_SAMPLE_HOST_OUTBOUND_H
#define COAKKA_HTTP_SAMPLE_HOST_OUTBOUND_H

#include <coakka/http/host.h>

#include <stdio.h>
#include <string.h>

static coakka_http_host_bytes_t sample_text(const char *text) {
  const coakka_http_host_bytes_t bytes = {(const uint8_t *)text, strlen(text)};
  return bytes;
}

static int sample_check(coakka_http_host_status_t status, const char *operation) {
  if (status == COAKKA_HTTP_HOST_OK) return 1;
  fprintf(stderr, "%s: %s\n", operation, coakka_http_host_status_name(status));
  return 0;
}

static void sample_route(coakka_http_host_route_t *route, uint64_t id,
                         const char *path) {
  coakka_http_host_route_init(route);
  route->route_id = id;
  route->handler_binding_id = id;
  route->method = sample_text("GET");
  route->encoded_path_pattern = sample_text(path);
}

static int sample_open(const coakka_http_host_configuration_t *config,
                       const coakka_http_host_route_t *route,
                       coakka_http_host_service_t **owner) {
  uint32_t failed = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
  coakka_http_host_issue_t issue;
  if (!sample_check(coakka_http_host_service_create(
          config, route, route != NULL ? 1U : 0U, owner, &failed), "create service")) {
    if (failed != COAKKA_HTTP_HOST_FAILED_INDEX_NONE)
      fprintf(stderr, "rejected route index: %u\n", (unsigned)failed);
    return 0;
  }
  coakka_http_host_issue_init(&issue);
  return sample_check(coakka_http_host_service_start(*owner, &issue), "start service");
}

static int sample_open_upstream(coakka_http_host_service_t **owner) {
  coakka_http_host_configuration_t config;
  coakka_http_host_route_t route;
  coakka_http_host_configuration_init(&config);
  config.bind_address = sample_text("127.0.0.1");
  /* This finite buffered fixture retains no per-exchange handler state.
   * Normal buffered success therefore needs no terminal notification. */
  config.omit_buffered_success_events = 1U;
  sample_route(&route, 1U, "/source");
  return sample_open(&config, &route, owner);
}

static int sample_open_client(const coakka_http_host_service_t *upstream,
                              coakka_http_host_service_t **owner) {
  coakka_http_host_configuration_t config;
  coakka_http_host_outbound_endpoint_t endpoint;
  coakka_http_host_outbound_target_t target;
  uint16_t port = 0U;
  if (!sample_check(coakka_http_host_service_port(upstream, &port), "upstream port"))
    return 0;

  coakka_http_host_outbound_endpoint_init(&endpoint);
  endpoint.node_id = sample_text("local-source");
  endpoint.connect_host = sample_text("127.0.0.1");
  endpoint.connect_port = port;
  endpoint.http_authority = sample_text("localhost");
  endpoint.available = 1U;
  coakka_http_host_outbound_target_init(&target);
  target.name = sample_text("sample.upstream");
  target.generation = 1U;
  target.endpoints = &endpoint;
  target.endpoint_count = 1U;
  coakka_http_host_configuration_init(&config);
  config.outbound_targets = &target;
  config.outbound_target_count = 1U;
  /* Construction copies the complete topology before these locals expire. */
  return sample_open(&config, NULL, owner);
}

static int sample_reply(coakka_http_host_service_t *upstream, uint64_t binding) {
  coakka_http_host_request_event_t request;
  coakka_http_host_response_t response;
  int responded = 0;
  coakka_http_host_request_event_init(&request);
  if (!sample_check(coakka_http_host_take_request(upstream, 5000U, &request),
                    "take upstream request")) return 0;
  if (request.kind == COAKKA_HTTP_HOST_REQUEST && request.handler_binding_id == binding) {
    coakka_http_host_response_init(&response);
    response.status_code = 200U;
    response.body = sample_text("outbound-ready");
    responded = sample_check(coakka_http_host_respond(upstream, request.exchange, &response),
                             "submit upstream response");
  } else {
    fputs("unexpected request event or captured binding\n", stderr);
  }
  return sample_check(coakka_http_host_release_request(upstream, &request),
                      "release upstream request") && responded;
}

static int sample_exchange(coakka_http_host_service_t *upstream,
                           coakka_http_host_service_t *client, const char *path,
                           uint32_t expected_status, uint64_t expected_binding) {
  coakka_http_host_outbound_request_t request;
  coakka_http_host_outbound_call_t call;
  coakka_http_host_outbound_event_t terminal;
  int matches;
  coakka_http_host_outbound_request_init(&request);
  request.logical_target = sample_text("sample.upstream");
  request.method = sample_text("GET");
  request.target = sample_text(path);
  request.timeout_ms = 3000U;
  if (!sample_check(coakka_http_host_submit_outbound(client, &request, &call),
                    "submit outbound request")) return 0;
  /* A known route needs an application response. The fixture's sole reader
   * services it before waiting for the terminal. Core answers an unmatched
   * path directly, without a handler lease. No extra worker is needed. */
  if (expected_binding != 0U && !sample_reply(upstream, expected_binding)) return 0;

  coakka_http_host_outbound_event_init(&terminal);
  if (!sample_check(coakka_http_host_take_outbound(client, 5000U, &terminal),
                    "take outbound terminal")) return 0;
  matches = terminal.call.slot == call.slot &&
            terminal.call.generation == call.generation &&
            terminal.reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE &&
            terminal.response_status == expected_status;
  if (matches && expected_status == 200U) {
    const coakka_http_host_bytes_t expected = sample_text("outbound-ready");
    matches = terminal.response_body.data != NULL &&
              terminal.response_body.size == expected.size &&
              memcmp(terminal.response_body.data, expected.data, expected.size) == 0;
  }
  if (!matches) fputs("unexpected outbound identity, reason, status or body\n", stderr);
  /* The body is valid only until its exact terminal is released. HTTP 404 is
   * still a delivered response, which the typed terminal reason confirms. */
  return sample_check(coakka_http_host_release_outbound(client, &terminal),
                      "release outbound terminal") && matches;
}

static int sample_publish(coakka_http_host_service_t *upstream) {
  coakka_http_host_route_t route;
  coakka_http_host_route_state_t previous;
  coakka_http_host_route_identity_t identity;
  coakka_http_host_publication_request_t change;
  coakka_http_host_publication_outcome_t applied, replay, stale;

  coakka_http_host_route_identity_init(&identity);
  if (!sample_check(coakka_http_host_route_snapshot(upstream, &previous, 1U, &identity),
                    "read route identity")) return 0;
  sample_route(&route, 2U, "/published");
  coakka_http_host_publication_request_init(&change);
  change.activation_id = 1U;
  change.expected_route_generation = identity.route_generation;
  change.expected_metadata_generation = identity.metadata_generation;
  change.expected_binding_change_sequence = identity.binding_change_sequence;
  change.routes = &route;
  change.route_count = 1U;
  coakka_http_host_publication_outcome_init(&applied);
  if (!sample_check(coakka_http_host_publish_routes(upstream, &change, &applied),
                    "publish routes")) return 0;
  if (applied.code != COAKKA_HTTP_HOST_CONTROL_APPLIED || !applied.changed || applied.replayed) {
    fputs("route publication did not apply\n", stderr);
    return 0;
  }

  /* Exact replay uses the original identity and payload. A fresh activation
   * with the old expected generations must be refused by Core. */
  coakka_http_host_publication_outcome_init(&replay);
  if (!sample_check(coakka_http_host_publish_routes(upstream, &change, &replay),
                    "replay publication")) return 0;
  change.activation_id = 2U;
  coakka_http_host_publication_outcome_init(&stale);
  if (!sample_check(coakka_http_host_publish_routes(upstream, &change, &stale),
                    "submit stale publication")) return 0;
  if (replay.code != COAKKA_HTTP_HOST_CONTROL_APPLIED || !replay.replayed ||
      replay.operation_digest != applied.operation_digest ||
      replay.effective_route_generation != applied.effective_route_generation ||
      stale.code != COAKKA_HTTP_HOST_CONTROL_GENERATION_MISMATCH || stale.changed ||
      stale.effective_route_generation != applied.effective_route_generation ||
      stale.effective_metadata_generation != applied.effective_metadata_generation ||
      stale.effective_binding_change_sequence != applied.effective_binding_change_sequence) {
    fputs("unexpected route publication outcome\n", stderr);
    return 0;
  }
  puts("host-route-publication=pass");
  return 1;
}

static int sample_run(coakka_http_host_service_t *upstream,
                      coakka_http_host_service_t *client) {
  return sample_exchange(upstream, client, "/source", 200U, 1U) &&
         sample_exchange(upstream, client, "/missing", 404U, 0U) &&
         sample_publish(upstream) &&
         sample_exchange(upstream, client, "/published", 200U, 2U) &&
         sample_exchange(upstream, client, "/source", 404U, 0U);
}

#endif
