#include "test_threads.h"

#include <coakka/http/http.h>

#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct outbound_race {
  coakka_http_server_t *server;
  atomic_uint stop_claimed;
  coakka_http_result_t reader_results[2];
  coakka_http_result_t stop_result;
  coakka_http_client_terminal_t terminals[2];
} outbound_race_t;

typedef struct outbound_worker {
  outbound_race_t *race;
  uint32_t reader;
} outbound_worker_t;

static coakka_http_bytes_t bytes(const char *value) {
  coakka_http_bytes_t result;
  result.data = (const uint8_t *)value;
  result.size = value == NULL ? 0U : (uint64_t)strlen(value);
  return result;
}

static int bytes_equal(coakka_http_bytes_t value, const char *expected) {
  const size_t expected_size = strlen(expected);
  return value.size == (uint64_t)expected_size &&
         (expected_size == 0U ||
          memcmp(value.data, expected, expected_size) == 0);
}

static int ascii_equal_folded(coakka_http_bytes_t value, const char *expected) {
  const size_t expected_size = strlen(expected);
  size_t index;
  if (value.size != (uint64_t)expected_size) {
    return 0;
  }
  for (index = 0U; index < expected_size; ++index) {
    uint8_t left = value.data[index];
    uint8_t right = (uint8_t)expected[index];
    if (left >= (uint8_t)'A' && left <= (uint8_t)'Z') {
      left = (uint8_t)(left + ((uint8_t)'a' - (uint8_t)'A'));
    }
    if (right >= (uint8_t)'A' && right <= (uint8_t)'Z') {
      right = (uint8_t)(right + ((uint8_t)'a' - (uint8_t)'A'));
    }
    if (left != right) {
      return 0;
    }
  }
  return 1;
}

static void unused_handler(void *context, coakka_http_request_t *request) {
  coakka_http_response_t response;
  (void)context;
  coakka_http_response_init(&response);
  response.status_code = 404U;
  response.body = bytes("unused");
  (void)coakka_http_request_respond(request, &response);
}

static void upstream_handler(void *context, coakka_http_request_t *request) {
  coakka_http_response_t response;
  coakka_http_header_t header;
  atomic_uint *failures = (atomic_uint *)context;

  coakka_http_response_init(&response);
  header.name = bytes("x-outbound");
  header.value = bytes("yes");
  response.status_code = 201U;
  response.headers = &header;
  response.header_count = 1U;
  response.body = bytes("outbound-ready");
  if (coakka_http_request_respond(request, &response).code !=
      COAKKA_HTTP_RESULT_OK) {
    (void)atomic_fetch_add_explicit(failures, 1U, memory_order_relaxed);
  }
}

static int outbound_race_worker(void *opaque) {
  outbound_worker_t *worker = (outbound_worker_t *)opaque;
  outbound_race_t *race = worker->race;
  coakka_http_result_t *reader_result = &race->reader_results[worker->reader];
  unsigned int expected = 0U;

  coakka_http_outbound_terminal_init(&race->terminals[worker->reader]);
  *reader_result = coakka_http_server_take_outbound(
      race->server, COAKKA_HTTP_TIMEOUT_FOREVER,
      &race->terminals[worker->reader]);
  if (reader_result->code == COAKKA_HTTP_RESULT_INVALID_STATE) {
    if (atomic_compare_exchange_strong_explicit(&race->stop_claimed, &expected,
                                                1U, memory_order_acq_rel,
                                                memory_order_acquire)) {
      race->stop_result = coakka_http_server_stop(race->server);
      return race->stop_result.code == COAKKA_HTTP_RESULT_OK ? 0 : -1;
    }
    return 0;
  }
  return reader_result->code == COAKKA_HTTP_RESULT_CLOSED ? 0 : -1;
}

static int run_stop_wait_race(coakka_http_server_t *server,
                              coakka_http_result_t *out_waiter,
                              coakka_http_result_t *out_second_reader,
                              coakka_http_result_t *out_stop) {
  outbound_race_t race;
  outbound_worker_t workers[2];
  void *contexts[2];
  int result;

  memset(&race, 0, sizeof(race));
  race.server = server;
  atomic_init(&race.stop_claimed, 0U);
  workers[0].race = &race;
  workers[0].reader = 0U;
  workers[1].race = &race;
  workers[1].reader = 1U;
  contexts[0] = &workers[0];
  contexts[1] = &workers[1];
  result = coakka_http_test_run_threads(outbound_race_worker, contexts, 2U);
  *out_waiter = race.reader_results[0];
  *out_second_reader = race.reader_results[1];
  *out_stop = race.stop_result;
  if (result != 0 ||
      atomic_load_explicit(&race.stop_claimed, memory_order_acquire) != 1U) {
    return -1;
  }
  return ((out_waiter->code == COAKKA_HTTP_RESULT_INVALID_STATE &&
           out_second_reader->code == COAKKA_HTTP_RESULT_CLOSED) ||
          (out_second_reader->code == COAKKA_HTTP_RESULT_INVALID_STATE &&
           out_waiter->code == COAKKA_HTTP_RESULT_CLOSED))
             ? 0
             : -1;
}

int main(void) {
  coakka_http_server_options_t upstream_options;
  coakka_http_server_options_t caller_options;
  coakka_http_route_t upstream_route;
  coakka_http_route_t caller_route;
  coakka_http_outbound_endpoint_t endpoint;
  coakka_http_outbound_target_t target;
  coakka_http_client_request_t request;
  coakka_http_client_terminal_t terminal;
  coakka_http_outbound_id_t call = {0U, 0U};
  coakka_http_server_t *upstream = NULL;
  coakka_http_server_t *caller = NULL;
  coakka_http_result_t result;
  coakka_http_result_t waiter_result;
  coakka_http_result_t second_reader_result;
  coakka_http_result_t stop_result;
  coakka_http_header_t header;
  atomic_uint handler_failures;
  uint16_t upstream_port = 0U;
  uint32_t index;
  int found_header = 0;
  int status = EXIT_FAILURE;

#define REQUIRE(expression)                                                    \
  do {                                                                         \
    if (!(expression)) {                                                       \
      fprintf(stderr, "requirement failed at line %d: %s\n", __LINE__,         \
              #expression);                                                    \
      goto cleanup;                                                            \
    }                                                                          \
  } while (0)

  if ((coakka_http_features() &
       (COAKKA_HTTP_CAPABILITY_OUTBOUND |
        COAKKA_HTTP_CAPABILITY_OUTBOUND_LOGICAL_TARGET)) !=
      (COAKKA_HTTP_CAPABILITY_OUTBOUND |
       COAKKA_HTTP_CAPABILITY_OUTBOUND_LOGICAL_TARGET)) {
#if defined(COAKKA_HTTP_EXPECT_OUTBOUND)
    fprintf(stderr,
            "installed Core lacks the required outbound capabilities\n");
    return EXIT_FAILURE;
#else
    puts("{\"schema\":\"coakka.http.native-outbound.v1\","
         "\"status\":\"skipped\"}");
    return 77; /* CTest skip, never a successful feature qualification. */
#endif
  }

  atomic_init(&handler_failures, 0U);
  coakka_http_server_options_init(&upstream_options);
  upstream_options.shutdown_timeout_ms = 2000U;
  coakka_http_route_init(&upstream_route);
  upstream_route.route_id = 1U;
  upstream_route.method = bytes("GET");
  upstream_route.path = bytes("/upstream");
  upstream_route.context = &handler_failures;
  upstream_route.handler = upstream_handler;
  result = coakka_http_server_create(&upstream_options, &upstream_route, 1U,
                                     &upstream);
  REQUIRE(result.code == COAKKA_HTTP_RESULT_OK && upstream != NULL);
  REQUIRE(coakka_http_server_start(upstream).code == COAKKA_HTTP_RESULT_OK);
  REQUIRE(coakka_http_server_port(upstream, &upstream_port).code ==
          COAKKA_HTTP_RESULT_OK);
  REQUIRE(upstream_port != 0U);

  coakka_http_outbound_endpoint_init(&endpoint);
  REQUIRE(endpoint.connection_strategy_generation == 1U);
  endpoint.node_id = bytes("loopback-node");
  endpoint.connect_host = bytes("127.0.0.1");
  endpoint.connect_port = upstream_port;
  endpoint.http_authority = bytes("127.0.0.1");

  coakka_http_outbound_target_init(&target);
  target.name = bytes("loopback.service");
  target.generation = 1U;
  target.endpoints = &endpoint;
  target.endpoint_count = 1U;

  coakka_http_server_options_init(&caller_options);
  caller_options.shutdown_timeout_ms = 2000U;
  caller_options.outbound_targets = &target;
  caller_options.outbound_target_count = 1U;
  coakka_http_route_init(&caller_route);
  caller_route.route_id = 2U;
  caller_route.method = bytes("GET");
  caller_route.path = bytes("/unused");
  caller_route.handler = unused_handler;
  result =
      coakka_http_server_create(&caller_options, &caller_route, 1U, &caller);
  REQUIRE(result.code == COAKKA_HTTP_RESULT_OK && caller != NULL);
  REQUIRE(coakka_http_server_start(caller).code == COAKKA_HTTP_RESULT_OK);

  coakka_http_outbound_request_init(&request);
  request.timeout_ms = 3000U;
  request.logical_target = bytes("loopback.service");
  request.method = bytes("GET");
  request.target = bytes("/upstream");
  result = coakka_http_server_outbound_submit(caller, &request, &call);
  REQUIRE(result.code == COAKKA_HTTP_RESULT_OK && call.generation != 0U);

  coakka_http_outbound_terminal_init(&terminal);
  result = coakka_http_server_take_outbound(caller, 5000U, &terminal);
  REQUIRE(result.code == COAKKA_HTTP_RESULT_OK);
  REQUIRE(terminal.reason == COAKKA_HTTP_OUTBOUND_TERMINAL_RESPONSE);
  REQUIRE(terminal.call.slot == call.slot &&
          terminal.call.generation == call.generation);
  REQUIRE(bytes_equal(terminal.logical_target, "loopback.service"));
  REQUIRE(bytes_equal(terminal.selected_node_id, "loopback-node"));
  REQUIRE(terminal.target_generation == 1U);
  REQUIRE(terminal.response_status == 201U);
  REQUIRE(bytes_equal(terminal.response_body, "outbound-ready"));
  for (index = 0U; index < terminal.response_header_count; ++index) {
    result =
        coakka_http_server_outbound_header(caller, &terminal, index, &header);
    REQUIRE(result.code == COAKKA_HTTP_RESULT_OK);
    if (ascii_equal_folded(header.name, "x-outbound") != 0 &&
        bytes_equal(header.value, "yes") != 0) {
      found_header = 1;
    }
  }
  REQUIRE(found_header != 0);
  REQUIRE(coakka_http_server_release_outbound(caller, &terminal).code ==
          COAKKA_HTTP_RESULT_OK);
  REQUIRE(terminal.private_lease == 0U && terminal.private_owner == NULL);

  REQUIRE(run_stop_wait_race(caller, &waiter_result, &second_reader_result,
                             &stop_result) == 0);
  REQUIRE(waiter_result.code == COAKKA_HTTP_RESULT_INVALID_STATE ||
          second_reader_result.code == COAKKA_HTTP_RESULT_INVALID_STATE);
  REQUIRE(stop_result.code == COAKKA_HTTP_RESULT_OK);
  REQUIRE(waiter_result.code == COAKKA_HTTP_RESULT_CLOSED ||
          second_reader_result.code == COAKKA_HTTP_RESULT_CLOSED);
  REQUIRE(atomic_load_explicit(&handler_failures, memory_order_relaxed) == 0U);
  status = EXIT_SUCCESS;

cleanup:
  if (caller != NULL) {
    (void)coakka_http_server_stop(caller);
    (void)coakka_http_server_destroy(&caller);
  }
  if (upstream != NULL) {
    (void)coakka_http_server_stop(upstream);
    (void)coakka_http_server_destroy(&upstream);
  }
  if (status == EXIT_SUCCESS) {
    puts("{\"schema\":\"coakka.http.native-outbound.v1\","
         "\"status\":\"pass\"}");
  }
  return status;

#undef REQUIRE
}
