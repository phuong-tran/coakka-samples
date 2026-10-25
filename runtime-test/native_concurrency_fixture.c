#include "native_concurrency_fixture.h"
#include "coakka/v2/transport.h"
#include <inttypes.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

static void route_snapshot(uint64_t generation, const char* target,
    coakka_v2_endpoint_t* endpoint, coakka_v2_route_t* route,
    coakka_v2_control_snapshot_t* snapshot) {
  *endpoint = (coakka_v2_endpoint_t){"127.0.0.1", 9401, 1, COAKKA_V2_ENDPOINT_FLAG_LOCAL};
  *route = (coakka_v2_route_t){target, COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER, NULL, 0, endpoint, 1};
  *snapshot = (coakka_v2_control_snapshot_t){generation, target == NULL ? NULL : route, target == NULL ? 0 : 1};
}

void native_concurrency_fixture_init(native_concurrency_fixture_t* fixture) {
  memset(fixture, 0, sizeof(*fixture));
  native_engine_owner_init(&fixture->core);
}

coakka_v2_status_t native_concurrency_fixture_open(
    native_concurrency_fixture_t* fixture, size_t queue_capacity,
    const char* node_id, const char* target, native_engine_failure_t* failure) {
  /* This narrowing check is the fixture's C boundary, not Core validation. */
  if (failure == NULL) return COAKKA_V2_ERR_INVALID_ARG;
  failure->operation = NATIVE_ENGINE_NONE;
  failure->status = COAKKA_V2_OK;
  if (fixture == NULL || queue_capacity > INT_MAX) {
    failure->status = COAKKA_V2_ERR_INVALID_ARG;
    return failure->status;
  }
  const coakka_v2_runtime_config_t config = {"runtime-v2-concurrency-evidence", node_id, 1, (int)queue_capacity};
  coakka_v2_endpoint_t endpoint;
  coakka_v2_route_t route;
  coakka_v2_control_snapshot_t snapshot;
  route_snapshot(1, target, &endpoint, &route, &snapshot);
  return native_engine_owner_open(&fixture->core, &config, &snapshot, failure);
}

coakka_v2_status_t native_concurrency_apply_snapshot(
    coakka_v2_runtime_t* runtime, uint64_t generation, const char* target) {
  coakka_v2_endpoint_t endpoint;
  coakka_v2_route_t route;
  coakka_v2_control_snapshot_t snapshot;
  route_snapshot(generation, target, &endpoint, &route, &snapshot);
  return coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot);
}

int native_concurrency_build_request(size_t producer, uint64_t sequence,
    const char* target, uint8_t** bytes, size_t* size) {
  char id[96];
  uint64_t payload[2] = {(uint64_t)producer, sequence};
  const int written = snprintf(id, sizeof(id), "concurrency-%zu-%" PRIu64, producer, sequence);
  if (written <= 0 || (size_t)written >= sizeof(id)) return 1;
  const coakka_v2_client_raw_request_spec_t request = {
      .struct_size = sizeof(request), .message_id = id,
      .source = "samples.runtime.native.evidence.producer", .target = target,
      .reply_to = "samples.runtime.native.evidence.replies",
      .payload = (const uint8_t*)payload, .payload_len = sizeof(payload),
      .timeout_ms = 5000, .delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT,
      .one_way = 0};
  return coakka_v2_client_build_raw_request(&request, bytes, size) == COAKKA_V2_OK ? 0 : 1;
}

coakka_v2_status_t native_concurrency_fixture_close(
    native_concurrency_fixture_t* fixture, native_engine_failure_t* failure) {
  if (fixture == NULL || failure == NULL) return COAKKA_V2_ERR_INVALID_ARG;
  coakka_v2_client_bytes_release(fixture->pending_reply.bytes);
  fixture->pending_reply.bytes = NULL;
  fixture->pending_reply.size = 0;
  return native_engine_owner_close(&fixture->core, failure);
}
