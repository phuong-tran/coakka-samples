/* Current-engine public-C workload. Core owns admission, matching and waits;
 * this serialized fixture owns only its finite workload and retained tickets. */
#include "evidence.h"
#include "evidence_platform.h"
#include "native_engine_owner.h"
#include "native_ticket_window.h"
#include "coakka/v2/transport.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { PUMP_BATCH = 16, ACTIVITY_WAIT_MS = 10, FINAL_DRAIN_MS = 5000 };

typedef struct pending_reply_t { uint8_t* bytes; size_t size; } pending_reply_t;

static uint64_t now_ms(void) { return evidence_platform_monotonic_ns() / 1000000u; }

static coakka_v2_status_t observed(evidence_result_t* result, coakka_v2_status_t status) {
  result->native.status_observed = 1;
  result->native.last_status = status;
  return status;
}

static int flush_reply(native_engine_owner_t* owner, pending_reply_t* pending,
                       evidence_result_t* result) {
  if (pending->bytes == NULL) return 0;
  const coakka_v2_status_t status = observed(result,
      coakka_v2_runtime_submit_envelope(owner->runtime, pending->bytes, pending->size));
  if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
    ++result->reply_submit_backpressure;
    return 0;
  }
  if (status != COAKKA_V2_OK) return 1;
  coakka_v2_client_bytes_release(pending->bytes);
  pending->bytes = NULL;
  pending->size = 0;
  ++result->replies_submitted;
  return 0;
}

static int poll_tickets(native_ticket_window_t* window, const uint8_t* payload,
                        size_t payload_size, unsigned wait_ms,
                        evidence_result_t* result) {
  while (window->count != 0) {
    uint32_t kind = 0;
    uint8_t *bytes = NULL, *copy = NULL;
    size_t size = 0, copied = 0;
    coakka_v2_ask_ticket_t* ticket = native_ticket_window_front(window);
    coakka_v2_status_t status = observed(result,
        coakka_v2_ask_ticket_await(ticket, wait_ms, &kind, &bytes, &size));
    wait_ms = 0;
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) return 0;
    if (status != COAKKA_V2_OK) return 1;
    int invalid = 0;
    if (kind == COAKKA_V2_CLIENT_RESULT_RESPONSE) {
      status = observed(result, coakka_v2_client_copy_envelope_payload(bytes, size,
          COAKKA_V2_CLIENT_ENVELOPE_RESPONSE, &copy, &copied));
      invalid = status != COAKKA_V2_OK || copied != payload_size || (copied != 0 && copy == NULL) ||
                (copied != 0 && memcmp(copy, payload, copied) != 0);
      if (!invalid) ++result->completed;
    } else if (kind == COAKKA_V2_CLIENT_RESULT_DEADLETTER) {
      ++result->rejected;
    } else {
      invalid = 1;
    }
    coakka_v2_client_bytes_release(copy);
    coakka_v2_client_bytes_release(bytes);
    coakka_v2_ask_ticket_destroy(native_ticket_window_take(window));
    if (invalid) return 1;
  }
  return 0;
}

static int pump(native_engine_owner_t* owner, native_ticket_window_t* window,
                pending_reply_t* pending, const uint8_t* payload,
                size_t payload_size, int wait, evidence_result_t* result) {
  if (flush_reply(owner, pending, result)) return 1;
  for (unsigned index = 0; index < PUMP_BATCH && pending->bytes == NULL; ++index) {
    coakka_v2_owned_frame_t* frame = NULL;
    /* Only Core interprets delivery modes/doorbells. A borrowed frame lives
     * through reply construction, never across a retained pending reply. */
    coakka_v2_status_t status = observed(result, coakka_v2_request_reader_read(
        owner->reader, wait && index == 0 ? ACTIVITY_WAIT_MS : 0, &frame));
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) break;
    if (status != COAKKA_V2_OK) return 1;
    coakka_v2_client_raw_reply_spec_t reply = {0};
    reply.struct_size = sizeof(reply);
    reply.request_buf = coakka_v2_owned_frame_data(frame);
    reply.request_len = coakka_v2_owned_frame_size(frame);
    reply.source = "samples.runtime.native.evidence.echo";
    reply.payload = payload;
    reply.payload_len = payload_size;
    status = observed(result, coakka_v2_client_build_raw_reply(&reply, &pending->bytes, &pending->size));
    coakka_v2_owned_frame_release(frame);
    if (status != COAKKA_V2_OK) return 1;
    ++result->handler_received;
    if (flush_reply(owner, pending, result)) return 1;
  }
  /* FIFO observation is bounded O(1) per retired ticket; Core still matches
   * out-of-order replies. No full-window rescans or inferred completions. */
  return poll_tickets(window, payload, payload_size,
      wait && pending->bytes != NULL ? ACTIVITY_WAIT_MS : 0, result);
}

static int submit(native_engine_owner_t* owner, native_ticket_window_t* window,
                  const uint8_t* payload, size_t payload_size,
                  evidence_result_t* result) {
  char id[96];
  uint8_t* bytes = NULL;
  size_t size = 0;
  coakka_v2_ask_ticket_t* ticket = NULL;
  coakka_v2_client_raw_request_spec_t request = {0};
  const int printed = snprintf(id, sizeof(id), "native-evidence-%" PRIu64, result->attempted);
  if (printed < 0 || (size_t)printed >= sizeof(id)) return 1;
  request.struct_size = sizeof(request);
  request.message_id = id;
  request.source = "native-evidence-client";
  request.target = EVIDENCE_TARGET_NAME;
  request.reply_to = "native-evidence-client/replies";
  request.payload = payload;
  request.payload_len = payload_size;
  request.timeout_ms = 1000;
  coakka_v2_status_t status = observed(result,
      coakka_v2_client_build_raw_request(&request, &bytes, &size));
  if (status != COAKKA_V2_OK) return 1;
  status = observed(result, coakka_v2_ask_client_begin(owner->client, bytes, size, &ticket));
  coakka_v2_client_bytes_release(bytes);
  if (status == COAKKA_V2_ERR_WOULD_BLOCK && ticket == NULL) {
    ++result->native.synchronously_refused;
    return 0;
  }
  if (status != COAKKA_V2_OK || ticket == NULL) {
    coakka_v2_ask_ticket_destroy(ticket);
    return 1;
  }
  if (!native_ticket_window_push(window, ticket)) {
    coakka_v2_ask_ticket_destroy(ticket);
    return 1;
  }
  ++result->submitted;
  if (window->count > result->native.ticket_high_watermark)
    result->native.ticket_high_watermark = window->count;
  return 0;
}

static int validate(const evidence_config_t* config, const evidence_result_t* result,
                    const char** error) {
  if (result->attempted != result->submitted + result->native.synchronously_refused ||
      result->submitted != result->completed + result->rejected ||
      result->handler_received != result->replies_submitted || result->replies_submitted != result->completed ||
      result->deadletter_count != result->rejected ||
      result->queue_rejected_count != result->native.synchronously_refused + result->rejected + result->reply_submit_backpressure) {
    *error = "Native admission/terminal/counter conservation failed";
    return 1;
  }
  if (result->runtime_generation != EVIDENCE_ROUTE_GENERATION || result->route_count != 1 || result->route_miss_count != 0 ||
      result->ingress_queue_capacity != config->queue_capacity ||
      result->ingress_queue_high_watermark > result->ingress_queue_capacity) {
    *error = "Native route/queue bounds failed";
    return 1;
  }
  if (config->mode == EVIDENCE_MODE_PRESSURE) {
    if (result->native.synchronously_refused + result->rejected == 0 || result->ingress_queue_high_watermark == 0) {
      *error = "Native pressure did not prove a bounded admission refusal";
      return 1;
    }
  } else if (result->native.synchronously_refused != 0 || result->rejected != 0 || result->reply_submit_backpressure != 0) {
    *error = "non-pressure Native workload observed refusal";
    return 1;
  }
  return 0;
}

int evidence_run(const evidence_config_t* config, coakka_v2_runtime_info_t* info,
                 evidence_result_t* result, const char** error) {
  native_engine_owner_t owner;
  native_engine_failure_t failure;
  native_ticket_window_t window = {0};
  pending_reply_t pending = {0};
  uint8_t* payload = NULL;
  uint64_t start_ns = 0, submission_end_ns = 0;
  int status = 1;
  native_engine_owner_init(&owner);
  memset(result, 0, sizeof(*result));
  memset(info, 0, sizeof(*info));
  result->native.enabled = 1;
  info->struct_size = sizeof(*info);
  if (observed(result, coakka_v2_runtime_get_info(info)) != COAKKA_V2_OK) {
    *error = "runtime_get_info failed";
    return 1;
  }
  result->native.info_known = 1;
  uint64_t capacity = config->max_in_flight;
  if (config->request_limit != 0 && (capacity == 0 || config->request_limit < capacity)) capacity = config->request_limit;
  if (capacity == 0 || capacity > NATIVE_WORKLOAD_MAX_TICKETS || capacity > SIZE_MAX / sizeof(*window.slots)) {
    *error = "Native workload requires a finite ticket bound of at most 500K";
    return 1;
  }
  window.capacity = (size_t)capacity;
  result->native.ticket_capacity = window.capacity;
  window.slots = calloc(window.capacity, sizeof(*window.slots));
  payload = malloc(config->payload_bytes);
  if (window.slots == NULL || payload == NULL) { *error = "workload allocation failed"; goto cleanup; }
  if (!native_ticket_window_init(&window, window.slots, window.capacity)) { *error = "ticket window initialization failed"; goto cleanup; }
  for (size_t index = 0; index < config->payload_bytes; ++index) payload[index] = (uint8_t)('a' + index % 26);
  const coakka_v2_runtime_config_t base = {"runtime-v2-native-evidence", "runtime-v2-native-evidence-node", 1, (int)config->queue_capacity};
  const coakka_v2_endpoint_t endpoint = {"127.0.0.1", 0, 1, COAKKA_V2_ENDPOINT_FLAG_LOCAL};
  const coakka_v2_route_t route = {EVIDENCE_TARGET_NAME, COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER, NULL, 0, &endpoint, 1};
  const coakka_v2_control_snapshot_t routes = {EVIDENCE_ROUTE_GENERATION, &route, 1};
  if (observed(result, native_engine_owner_open(&owner, &base, &routes, &failure)) != COAKKA_V2_OK) {
    *error = "Native fixture acquisition failed"; goto cleanup;
  }
  result->native.facts_known = owner.facts.startup_checked && owner.facts.delivery_checked;
  result->native.request_mode = owner.facts.delivery.request_mode;
  result->native.response_mode = owner.facts.delivery.response_mode;
  result->native.cpu_policy = owner.facts.startup.execution.requested_policy;
  result->native.connection_mode = owner.facts.startup.connection.mode;
  start_ns = evidence_platform_monotonic_ns();
  if (start_ns == 0) { *error = "workload clock failed"; goto cleanup; }
  for (;;) {
    const uint64_t current_ms = now_ms();
    if (current_ms == 0) { *error = "workload clock failed"; goto cleanup; }
    if ((config->request_limit != 0 && result->attempted >= config->request_limit) ||
        (config->duration_ms != 0 && current_ms - start_ns / 1000000u >= config->duration_ms)) break;
    const uint64_t guard_deadline = current_ms + FINAL_DRAIN_MS;
    while (window.count == window.capacity) {
      if (pump(&owner, &window, &pending, payload, config->payload_bytes, 1, result)) { *error = "Native in-flight pump failed"; goto cleanup; }
      const uint64_t checked_ms = now_ms();
      if (checked_ms == 0 || checked_ms >= guard_deadline) { *error = "Native in-flight drain deadline"; goto cleanup; }
    }
    ++result->attempted;
    if (submit(&owner, &window, payload, config->payload_bytes, result)) { *error = "Native request admission failed"; goto cleanup; }
    if (config->mode != EVIDENCE_MODE_PRESSURE && result->attempted % PUMP_BATCH == 0 &&
        pump(&owner, &window, &pending, payload, config->payload_bytes, 0, result)) { *error = "Native intermediate pump failed"; goto cleanup; }
  }
  submission_end_ns = evidence_platform_monotonic_ns();
  const uint64_t deadline = submission_end_ns / 1000000u + FINAL_DRAIN_MS;
  while (window.count != 0 || pending.bytes != NULL) {
    const uint64_t checked_ms = now_ms();
    if (checked_ms == 0 || checked_ms >= deadline) { *error = "Native runtime did not drain before timeout"; goto cleanup; }
    if (pump(&owner, &window, &pending, payload, config->payload_bytes, 1, result)) { *error = "Native final pump failed"; goto cleanup; }
  }
  const uint64_t end_ns = evidence_platform_monotonic_ns();
  if (submission_end_ns < start_ns || end_ns < submission_end_ns) { *error = "workload clock failed"; goto cleanup; }
  result->submission_window_ns = submission_end_ns - start_ns;
  result->final_drain_ns = end_ns - submission_end_ns;
  result->total_elapsed_ns = end_ns - start_ns;
  coakka_v2_runtime_stats_t stats = {0};
  stats.struct_size = sizeof(stats);
  if (observed(result, coakka_v2_runtime_get_stats(owner.runtime, &stats)) != COAKKA_V2_OK) { *error = "Native stats failed"; goto cleanup; }
  result->runtime_generation = stats.applied_generation;
  result->route_count = stats.route_count;
  result->route_miss_count = stats.route_miss_count;
  result->deadletter_count = stats.deadletter_count;
  result->queue_rejected_count = stats.queue_rejected_count;
  result->ingress_queue_capacity = stats.ingress_queue_capacity;
  result->ingress_queue_high_watermark = stats.ingress_queue_high_watermark;
  status = validate(config, result, error);
cleanup:
  /* Retire ticket/result matching before client/handles/runtime. This is local
   * observation cancellation, never a claim that Core revoked execution. */
  if (window.slots != NULL) {
    for (size_t index = 0; index < window.capacity; ++index) coakka_v2_ask_ticket_destroy(window.slots[index]);
  }
  free(window.slots);
  coakka_v2_client_bytes_release(pending.bytes);
  free(payload);
  if (native_engine_owner_close(&owner, &failure) != COAKKA_V2_OK) {
    if (failure.operation == NATIVE_ENGINE_STOP) observed(result, failure.status);
    *error = "Native stop refused; remaining owner retained until process exit";
    status = 1;
  }
  return status;
}
