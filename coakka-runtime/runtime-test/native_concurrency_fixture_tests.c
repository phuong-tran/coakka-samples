/* Actual SDK preflight of the shared draft, not concurrent workload evidence. */
#include "native_concurrency_fixture.h"
#include "coakka/v2/transport.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#ifdef NDEBUG
#error "Native concurrency fixture controls require active assertions"
#endif

static void ask(native_concurrency_fixture_t* fixture, uint64_t sequence, int response_expected) {
  uint8_t *request = NULL, *reply = NULL, *result = NULL, *copy = NULL;
  size_t request_size = 0, reply_size = 0, result_size = 0, copy_size = 0;
  uint32_t kind = 0;
  coakka_v2_owned_frame_t* frame = NULL;
  coakka_v2_ask_ticket_t* ticket = NULL;
  assert(native_concurrency_build_request(9999, sequence, "fixture.echo", &request, &request_size) == 0);
  assert(coakka_v2_ask_client_begin(fixture->core.client, request, request_size, &ticket) == COAKKA_V2_OK);
  coakka_v2_client_bytes_release(request);
  if (response_expected) {
    assert(coakka_v2_request_reader_read(fixture->core.reader, 1000, &frame) == COAKKA_V2_OK);
    coakka_v2_client_raw_reply_spec_t response = {0};
    response.struct_size = sizeof(response);
    response.request_buf = coakka_v2_owned_frame_data(frame);
    response.request_len = coakka_v2_owned_frame_size(frame);
    response.source = "fixture.echo";
    response.payload = (const uint8_t*)"ok";
    response.payload_len = 2;
    assert(coakka_v2_client_build_raw_reply(&response, &reply, &reply_size) == COAKKA_V2_OK);
    coakka_v2_owned_frame_release(frame);
    assert(coakka_v2_runtime_submit_envelope(fixture->core.runtime, reply, reply_size) == COAKKA_V2_OK);
    coakka_v2_client_bytes_release(reply);
  }
  assert(coakka_v2_ask_ticket_await(ticket, 1000, &kind, &result, &result_size) == COAKKA_V2_OK);
  assert(kind == (uint32_t)(response_expected ? COAKKA_V2_CLIENT_RESULT_RESPONSE : COAKKA_V2_CLIENT_RESULT_DEADLETTER));
  if (response_expected) {
    assert(coakka_v2_client_copy_envelope_payload(result, result_size, COAKKA_V2_CLIENT_ENVELOPE_RESPONSE, &copy, &copy_size) == COAKKA_V2_OK);
    assert(copy != NULL && copy_size == 2 && memcmp(copy, "ok", 2) == 0);
    coakka_v2_client_bytes_release(copy);
  }
  coakka_v2_client_bytes_release(result);
  coakka_v2_ask_ticket_destroy(ticket);
}

int main(void) {
  coakka_v2_runtime_info_t info = {0};
  info.struct_size = sizeof(info);
  assert(coakka_v2_runtime_get_info(&info) == COAKKA_V2_OK && info.abi_version == 1);
  assert(strcmp(info.git_commit, "1522b4a9f30da1039ef8c7d198bbecfc749a2456") == 0);
  native_concurrency_fixture_t fixture;
  native_engine_failure_t failure;
  native_concurrency_fixture_init(&fixture);
  assert(native_concurrency_fixture_open(NULL, 16, "probe", NULL, &failure) == COAKKA_V2_ERR_INVALID_ARG);
  assert(failure.operation == NATIVE_ENGINE_NONE && failure.status == COAKKA_V2_ERR_INVALID_ARG);
  assert(native_concurrency_fixture_open(&fixture, (size_t)INT_MAX + 1u, "probe", NULL, &failure) == COAKKA_V2_ERR_INVALID_ARG);
  assert(fixture.core.runtime == NULL && failure.operation == NATIVE_ENGINE_NONE);
  for (unsigned cycle = 0; cycle < 16; ++cycle) {
    const char* target = cycle % 2 == 0 ? NULL : "fixture.echo";
    assert(native_concurrency_fixture_open(&fixture, 16, "concurrency-fixture-probe", target, &failure) == COAKKA_V2_OK);
    assert(fixture.core.facts.startup_checked && fixture.core.facts.delivery_checked);
    assert(fixture.core.facts.startup.execution.requested_policy == COAKKA_V2_STARTUP_CPU_AUTO);
    assert(fixture.core.facts.startup.connection.mode == COAKKA_V2_TCP_CONNECTION_MULTIPLEXED);
    assert(fixture.core.facts.delivery.compiled == 1);
    assert(fixture.core.facts.delivery.request_mode == COAKKA_V2_STARTUP_DELIVERY_OWNED_QUEUE);
    assert(fixture.core.facts.delivery.response_mode == COAKKA_V2_STARTUP_DELIVERY_OWNED_QUEUE);
    ask(&fixture, cycle * 2u, target != NULL);
    assert(native_concurrency_apply_snapshot(fixture.core.runtime, 1, "fixture.echo") == COAKKA_V2_ERR_STALE_GENERATION);
    assert(native_concurrency_apply_snapshot(fixture.core.runtime, 2, "") == COAKKA_V2_ERR_INVALID_ARG);
    coakka_v2_runtime_stats_t stats = {0};
    stats.struct_size = sizeof(stats);
    assert(coakka_v2_runtime_get_stats(fixture.core.runtime, &stats) == COAKKA_V2_OK);
    assert(stats.applied_generation == 1 && stats.route_count == (target == NULL ? 0u : 1u));
    assert(native_concurrency_apply_snapshot(fixture.core.runtime, 2, "fixture.echo") == COAKKA_V2_OK);
    ask(&fixture, cycle * 2u + 1u, 1);
    assert(native_concurrency_fixture_close(&fixture, &failure) == COAKKA_V2_OK);
    assert(native_concurrency_fixture_close(&fixture, &failure) == COAKKA_V2_OK);
  }
  puts("PASS:16 Native draft fixture lifecycles;24responses/8route-miss tickets;stale/invalid atomic preservation;AUTO/MUX;no concurrency activation");
  return 0;
}
