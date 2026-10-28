#include "concurrency_evidence.h"
#include "native_concurrency_fixture.h"
#include "native_ticket_publication.h"
#include "evidence_platform.h"
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#define TARGET_A "samples.runtime.native.evidence.route-a"
#define TARGET_B "samples.runtime.native.evidence.route-b"
enum { PUMP_BATCH = 32, ACTIVITY_WAIT_MS = 5 };

typedef struct producer_shared_t {
  coakka_v2_ask_client_t* client;
  const concurrency_evidence_config_t* config;
  uint64_t deadline;
  atomic_size_t ready;
  atomic_size_t done;
  atomic_int go;
  atomic_uint_fast64_t quota;
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t admitted;
  atomic_uint_fast64_t backpressure;
  atomic_uint_fast64_t unexpected;
  atomic_uint_fast64_t discarded;
} producer_shared_t;

typedef struct producer_t {
  producer_shared_t* shared;
  size_t index;
  native_ticket_publication_t publication;
} producer_t;

typedef struct controller_t {
  native_concurrency_fixture_t fixture;
  producer_t* producers;
  native_ticket_cursor_t* cursors;
  size_t count;
} controller_t;

static uint64_t now_ns(void) { return evidence_platform_monotonic_ns(); }
static int expired(uint64_t deadline) {
  const uint64_t now = now_ns();
  return now == 0 || now >= deadline;
}

static int producer(void* raw) {
  producer_t* self = raw;
  producer_shared_t* shared = self->shared;
  atomic_fetch_add_explicit(&shared->ready, 1, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (expired(shared->deadline)) goto failed;
    evidence_platform_thread_yield();
  }
  for (uint64_t sequence = 0; sequence < shared->config->requests_per_thread; ++sequence) {
    while (sequence >= atomic_load_explicit(&shared->quota, memory_order_acquire)) {
      if (expired(shared->deadline)) goto failed;
      evidence_platform_thread_yield();
    }
    const char* target = shared->config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD &&
        ((sequence + self->index) & 1u) != 0 ? TARGET_B : TARGET_A;
    uint8_t* bytes = NULL;
    size_t size = 0;
    if (native_concurrency_build_request(self->index, sequence, target, &bytes, &size)) goto failed;
    atomic_fetch_add_explicit(&shared->attempted, 1, memory_order_relaxed);
    int accepted = 0;
    for (;;) {
      coakka_v2_ask_ticket_t* ticket = NULL;
      const coakka_v2_status_t status = coakka_v2_ask_client_begin(shared->client, bytes, size, &ticket);
      if (status == COAKKA_V2_OK && ticket != NULL) {
        /* Core accepted this intent even if the fixture subsequently refuses
         * transfer. Discarding observation must never erase that fact. */
        atomic_fetch_add_explicit(&shared->admitted, 1, memory_order_relaxed);
        if (!native_ticket_publication_append(&self->publication, ticket)) {
          coakka_v2_ask_ticket_destroy(ticket);
          atomic_fetch_add_explicit(&shared->discarded, 1, memory_order_relaxed);
          break;
        }
        /* No ticket access after successful publication; control may already
         * have destroyed it. This is ownership, never local Core-state truth. */
        accepted = 1;
        break;
      }
      if (status == COAKKA_V2_ERR_WOULD_BLOCK && ticket == NULL) {
        atomic_fetch_add_explicit(&shared->backpressure, 1, memory_order_relaxed);
        if (!expired(shared->deadline)) { evidence_platform_thread_yield(); continue; }
      }
      if (ticket != NULL) {
        coakka_v2_ask_ticket_destroy(ticket);
        atomic_fetch_add_explicit(&shared->discarded, 1, memory_order_relaxed);
      }
      break;
    }
    coakka_v2_client_bytes_release(bytes);
    if (!accepted || expired(shared->deadline)) goto failed;
  }
  atomic_fetch_add_explicit(&shared->done, 1, memory_order_release);
  return 0;
failed:
  atomic_fetch_add_explicit(&shared->unexpected, 1, memory_order_relaxed);
  atomic_fetch_add_explicit(&shared->done, 1, memory_order_release);
  return 1;
}

static int flush_reply(controller_t* control, concurrency_evidence_result_t* result) {
  native_concurrency_fixture_t* fixture = &control->fixture;
  if (fixture->pending_reply.bytes == NULL) return 0;
  const coakka_v2_status_t status = coakka_v2_runtime_submit_envelope(fixture->core.runtime,
      fixture->pending_reply.bytes, fixture->pending_reply.size);
  if (status == COAKKA_V2_ERR_WOULD_BLOCK) { ++result->reply_submit_backpressure; return 0; }
  if (status != COAKKA_V2_OK) return 1;
  coakka_v2_client_bytes_release(fixture->pending_reply.bytes);
  fixture->pending_reply.bytes = NULL;
  fixture->pending_reply.size = 0;
  ++result->replies_submitted;
  return 0;
}

static int handle_requests(controller_t* control, concurrency_evidence_result_t* result, int wait) {
  native_concurrency_fixture_t* fixture = &control->fixture;
  for (unsigned batch = 0; batch < PUMP_BATCH; ++batch) {
    if (flush_reply(control, result)) return 1;
    if (fixture->pending_reply.bytes != NULL) return 0;
    coakka_v2_owned_frame_t* frame = NULL;
    coakka_v2_status_t status = coakka_v2_request_reader_read(fixture->core.reader,
        wait && batch == 0 ? ACTIVITY_WAIT_MS : 0, &frame);
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) return 0;
    if (status != COAKKA_V2_OK) return 1;
    const coakka_v2_client_raw_reply_spec_t reply = {
        .struct_size = sizeof(reply), .request_buf = coakka_v2_owned_frame_data(frame),
        .request_len = coakka_v2_owned_frame_size(frame),
        .source = "samples.runtime.native.evidence.handler",
        .payload = (const uint8_t*)"ok", .payload_len = 2};
    status = coakka_v2_client_build_raw_reply(&reply, &fixture->pending_reply.bytes, &fixture->pending_reply.size);
    coakka_v2_owned_frame_release(frame);
    if (status != COAKKA_V2_OK) return 1;
    ++result->handler_received;
  }
  return flush_reply(control, result);
}

static int adopt_ticket(coakka_v2_ask_ticket_t* ticket, concurrency_evidence_result_t* result, int* ready, uint32_t wait_ms) {
  uint32_t kind = 0;
  uint8_t *bytes = NULL, *copy = NULL;
  size_t size = 0, copied = 0;
  *ready = 0;
  coakka_v2_status_t status = coakka_v2_ask_ticket_await(ticket, wait_ms, &kind, &bytes, &size);
  if (status == COAKKA_V2_ERR_WOULD_BLOCK) return 0;
  if (status != COAKKA_V2_OK) return 1;
  int invalid = 0;
  if (kind == COAKKA_V2_CLIENT_RESULT_RESPONSE) {
    ++result->response_count;
    status = coakka_v2_client_copy_envelope_payload(bytes, size, COAKKA_V2_CLIENT_ENVELOPE_RESPONSE, &copy, &copied);
    result->native.response_validation.status_known = 1;
    result->native.response_validation.last_status = status;
    invalid = status != COAKKA_V2_OK || copy == NULL || copied != 2 || memcmp(copy, "ok", 2) != 0;
    if (status != COAKKA_V2_OK) ++result->native.response_validation.projection_failures;
    else if (invalid) ++result->native.response_validation.payload_mismatches;
  } else if (kind == COAKKA_V2_CLIENT_RESULT_DEADLETTER) {
    ++result->deadletter_count;
  } else invalid = 1;
  coakka_v2_client_bytes_release(copy);
  coakka_v2_client_bytes_release(bytes);
  *ready = 1;
  return invalid;
}

static int pump(controller_t* control, concurrency_evidence_result_t* result, int wait) {
  if (handle_requests(control, result, wait)) return 1;
  uint32_t wait_ms = wait && control->fixture.pending_reply.bytes != NULL ? ACTIVITY_WAIT_MS : 0;
  /* Bounded producer count, FIFO observation, no total-request rescans. */
  for (size_t index = 0; index < control->count; ++index) {
    for (unsigned batch = 0; batch < PUMP_BATCH; ++batch) {
      coakka_v2_ask_ticket_t* ticket = native_ticket_cursor_front(&control->cursors[index]);
      if (ticket == NULL) break;
      int ready = 0;
      const int failed = adopt_ticket(ticket, result, &ready, wait_ms);
      wait_ms = 0;
      if (ready) coakka_v2_ask_ticket_destroy(native_ticket_cursor_take(&control->cursors[index]));
      if (failed) return 1;
      if (!ready) break;
    }
  }
  return 0;
}

static int probe(controller_t* control, concurrency_evidence_result_t* result,
    const char* target, int expect_response, uint64_t deadline) {
  uint8_t* bytes = NULL;
  size_t size = 0;
  coakka_v2_ask_ticket_t* ticket = NULL;
  const uint64_t responses = result->response_count, deadletters = result->deadletter_count;
  if (native_concurrency_build_request(9999, result->attempted, target, &bytes, &size)) return 1;
  ++result->attempted;
  int failed = 1;
  for (;;) {
    const coakka_v2_status_t status = coakka_v2_ask_client_begin(control->fixture.core.client, bytes, size, &ticket);
    if (status == COAKKA_V2_OK && ticket != NULL) { ++result->admitted; break; }
    if (status != COAKKA_V2_ERR_WOULD_BLOCK || ticket != NULL || expired(deadline)) goto done;
    ++result->admission_backpressure;
    if (pump(control, result, 0)) goto done;
  }
  for (;;) {
    int ready = 0;
    if (pump(control, result, 1) || adopt_ticket(ticket, result, &ready, 0)) goto done;
    if (ready) break;
    if (expired(deadline)) goto done;
  }
  failed = expect_response ? !(result->response_count == responses + 1 && result->deadletter_count == deadletters) :
      !(result->response_count == responses && result->deadletter_count == deadletters + 1);
done:
  coakka_v2_client_bytes_release(bytes);
  coakka_v2_ask_ticket_destroy(ticket);
  return failed;
}

static int workload(const concurrency_evidence_config_t* config,
    concurrency_evidence_result_t* result, const char** error) {
  controller_t control = {0};
  producer_shared_t shared;
  evidence_platform_thread_t* threads = NULL;
  coakka_v2_ask_ticket_t** slots = NULL;
  size_t started = 0;
  int failed = 1;
  int shared_ready = 0;
  int counts_captured = 0;
  native_engine_failure_t failure;
  native_concurrency_fixture_init(&control.fixture);
  const uint64_t start = now_ns();
  const uint64_t deadline = start + config->timeout_ms * UINT64_C(1000000);
#define FAIL(stage_, text_) do { result->failure_stage = (stage_); *error = (text_); goto cleanup; } while (0)
  if (start == 0 || config->thread_count == 0 || config->thread_count > 64 || config->requests_per_thread == 0 ||
      config->requests_per_thread > SIZE_MAX / sizeof(*slots) / config->thread_count)
    FAIL("workload.bounds", "invalid Native workload ownership/clock bounds");
  const size_t per_producer = (size_t)config->requests_per_thread;
  const size_t capacity = config->thread_count * per_producer;
  if (native_concurrency_fixture_open(&control.fixture, config->queue_capacity, "concurrency-main", TARGET_A, &failure) != COAKKA_V2_OK)
    FAIL("workload.runtime-setup", "Native runtime setup failed");
  result->native.facts_known = control.fixture.core.facts.startup_checked && control.fixture.core.facts.delivery_checked;
  result->native.compiled = control.fixture.core.facts.delivery.compiled;
  result->native.request_mode = control.fixture.core.facts.delivery.request_mode;
  result->native.response_mode = control.fixture.core.facts.delivery.response_mode;
  result->native.cpu_policy = control.fixture.core.facts.startup.execution.requested_policy;
  result->native.connection_mode = control.fixture.core.facts.startup.connection.mode;
  result->snapshot_apply_successes = 1;
  coakka_v2_runtime_stats_t stats = {0};
  stats.struct_size = sizeof(stats);
  if (coakka_v2_runtime_get_stats(control.fixture.core.runtime, &stats) != COAKKA_V2_OK ||
      stats.applied_generation != 1 || stats.route_count != 1 || stats.runtime_state != COAKKA_V2_STATE_STARTED)
    FAIL("workload.initial-snapshot-observation", "initial Native snapshot observation failed");
  result->snapshot_observation_successes = 1;
  threads = calloc(config->thread_count, sizeof(*threads));
  control.producers = calloc(config->thread_count, sizeof(*control.producers));
  control.cursors = calloc(config->thread_count, sizeof(*control.cursors));
  slots = calloc(capacity, sizeof(*slots));
  if (threads == NULL || control.producers == NULL || control.cursors == NULL || slots == NULL)
    FAIL("workload.producer-allocation", "Native producer ownership allocation failed");
  result->native.producer_ticket_capacity = capacity;
  control.count = config->thread_count;
  memset(&shared, 0, sizeof(shared));
  shared.client = control.fixture.core.client;
  shared.config = config;
  shared.deadline = deadline;
  atomic_init(&shared.ready, 0); atomic_init(&shared.done, 0); atomic_init(&shared.go, 0);
  atomic_init(&shared.attempted, 0); atomic_init(&shared.admitted, 0);
  atomic_init(&shared.backpressure, 0); atomic_init(&shared.unexpected, 0);
  atomic_init(&shared.discarded, 0);
  atomic_init(&shared.quota, config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD ?
      (config->requests_per_thread + config->generation_count - 1) / config->generation_count : config->requests_per_thread);
  shared_ready = 1;
  for (size_t index = 0; index < control.count; ++index) {
    control.producers[index].shared = &shared;
    control.producers[index].index = index;
    if (!native_ticket_publication_init(&control.producers[index].publication, slots + index * per_producer, per_producer) ||
        !native_ticket_cursor_init(&control.cursors[index], &control.producers[index].publication))
      FAIL("workload.publication-init", "Native publication bounds refused");
  }
  for (; started < control.count; ++started)
    if (evidence_platform_thread_start(&threads[started], producer, &control.producers[started]))
      FAIL("workload.producer-start", "Native producer start failed");
  while (atomic_load_explicit(&shared.ready, memory_order_acquire) < control.count) {
    if (expired(deadline)) FAIL("workload.producer-ready", "Native producer readiness deadline");
    evidence_platform_thread_yield();
  }
  const uint64_t work_start = now_ns();
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD) {
    for (uint64_t generation = 2; generation <= config->generation_count; ++generation) {
      const uint64_t quota_before = (config->requests_per_thread * (generation - 1) + config->generation_count - 1) / config->generation_count;
      const uint64_t threshold = config->thread_count * quota_before;
      while (atomic_load_explicit(&shared.attempted, memory_order_acquire) < threshold) {
        if (atomic_load_explicit(&shared.unexpected, memory_order_acquire) != 0)
          FAIL("workload.producer-failure", "Native producer submission or ownership failure");
        if (pump(&control, result, 1)) FAIL("hot-reload.concurrent-traffic", "Native snapshot traffic pump failed");
        if (expired(deadline)) FAIL("hot-reload.concurrent-traffic", "Native snapshot traffic phase deadline");
      }
      if (native_concurrency_apply_snapshot(control.fixture.core.runtime, generation, (generation & 1u) == 0 ? TARGET_B : TARGET_A) != COAKKA_V2_OK)
        FAIL("hot-reload.snapshot-apply", "Native snapshot apply failed");
      ++result->snapshot_apply_successes;
      memset(&stats, 0, sizeof(stats)); stats.struct_size = sizeof(stats);
      if (coakka_v2_runtime_get_stats(control.fixture.core.runtime, &stats) != COAKKA_V2_OK ||
          stats.applied_generation != generation || stats.route_count != 1 || stats.runtime_state != COAKKA_V2_STATE_STARTED)
        FAIL("hot-reload.snapshot-observation", "Native accepted snapshot not observed");
      ++result->snapshot_observation_successes;
      atomic_store_explicit(&shared.quota,
          (config->requests_per_thread * generation + config->generation_count - 1) / config->generation_count, memory_order_release);
    }
  }
  for (;;) {
    /* Observe the release-done sequence BEFORE final counters. It publishes
     * every producer's accepted prefix and relaxed measurement increments. */
    const size_t done = atomic_load_explicit(&shared.done, memory_order_acquire);
    const uint64_t admitted = atomic_load_explicit(&shared.admitted, memory_order_acquire);
    if (atomic_load_explicit(&shared.unexpected, memory_order_acquire) != 0)
      FAIL("workload.producer-failure", "Native producer submission or ownership failure");
    if (pump(&control, result, 1)) FAIL("workload.final-drain", "Native controller pump failed");
    if (done == control.count && result->response_count + result->deadletter_count == admitted && control.fixture.pending_reply.bytes == NULL) break;
    if (expired(deadline)) FAIL("workload.final-drain", "Native workload final deadline");
  }
  result->workload_elapsed_ns = now_ns() - work_start;
  /* Join before probes/aggregation. Core tickets already retired, immutable
   * publication storage remains alive through every producer's returned call. */
  for (size_t index = 0; index < started; ++index) {
    int code = 1;
    if (evidence_platform_thread_join(&threads[index], &code) || code != 0)
      FAIL("workload.producer-join", "Native producer did not join successfully");
  }
  started = 0;
  result->attempted = atomic_load_explicit(&shared.attempted, memory_order_acquire);
  result->admitted = atomic_load_explicit(&shared.admitted, memory_order_acquire);
  result->admission_backpressure = atomic_load_explicit(&shared.backpressure, memory_order_acquire);
  result->unexpected_submit_statuses = atomic_load_explicit(&shared.unexpected, memory_order_acquire);
  counts_captured = 1;
  if (result->unexpected_submit_statuses != 0 || result->attempted != capacity || result->admitted != capacity ||
      result->response_count + result->deadletter_count != result->admitted ||
      result->handler_received != result->replies_submitted || result->replies_submitted != result->response_count)
    FAIL("workload.invariants", "Native terminal ownership accounting failed");
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD) {
    const char* active = (config->generation_count & 1u) == 0 ? TARGET_B : TARGET_A;
    const char* inactive = (config->generation_count & 1u) == 0 ? TARGET_A : TARGET_B;
    result->stale_apply_status = native_concurrency_apply_snapshot(control.fixture.core.runtime, config->generation_count, active);
    result->native.stale_status_known = 1;
    result->invalid_apply_status = native_concurrency_apply_snapshot(control.fixture.core.runtime, config->generation_count + 1, "");
    result->native.invalid_status_known = 1;
    if (result->stale_apply_status != COAKKA_V2_ERR_STALE_GENERATION || result->invalid_apply_status != COAKKA_V2_ERR_INVALID_ARG)
      FAIL("hot-reload.rejection-contract", "Native snapshot refusal contract failed");
    if (probe(&control, result, active, 1, deadline)) FAIL("hot-reload.active-target-probe", "Native active target probe failed");
    result->active_target_probe_passed = 1;
    if (probe(&control, result, inactive, 0, deadline)) FAIL("hot-reload.inactive-target-probe", "Native inactive target probe failed");
    result->inactive_target_probe_passed = 1;
    if (result->response_count == 0 || result->deadletter_count == 0)
      FAIL("hot-reload.route-outcomes", "Native snapshot cases did not observe both outcomes");
  }
  memset(&stats, 0, sizeof(stats)); stats.struct_size = sizeof(stats);
  if (coakka_v2_runtime_get_stats(control.fixture.core.runtime, &stats) != COAKKA_V2_OK)
    FAIL("workload.final-stats", "Native final stats failed");
  result->final_generation = stats.applied_generation; result->final_route_count = stats.route_count;
  result->final_runtime_state = (uint32_t)stats.runtime_state;
  result->runtime_deadletter_count = stats.deadletter_count; result->queue_rejected_count = stats.queue_rejected_count;
  result->route_miss_count = stats.route_miss_count; result->delivery_failed_count = stats.delivery_failed_count;
  result->native.final_stats_known = 1;
  if (stats.applied_generation != (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD ? config->generation_count : 1) ||
      stats.route_count != 1 || stats.runtime_state != COAKKA_V2_STATE_STARTED)
    FAIL("workload.final-state", "Native snapshot state did not converge");
  if (stats.deadletter_count != result->deadletter_count || stats.delivery_failed_count != 0 ||
      stats.deadletter_count + result->reply_submit_backpressure + result->admission_backpressure !=
          stats.queue_rejected_count + stats.route_miss_count ||
      (config->mode == CONCURRENCY_EVIDENCE_MODE_RACE && stats.route_miss_count != 0))
    FAIL("workload.terminal-accounting", "Native Core counters differ from actual ticket outcomes");
  if (result->snapshot_observation_successes != result->snapshot_apply_successes)
    FAIL("hot-reload.snapshot-observation-count", "Native snapshot observation count mismatch");
  failed = 0;
cleanup:
  if (started != 0) {
    /* Quotas are fixture-owned. Release them before stop/join, rather than
     * leaving a producer parked until the phase deadline during cleanup. */
    atomic_store_explicit(&shared.quota, config->requests_per_thread, memory_order_release);
    atomic_store_explicit(&shared.go, 1, memory_order_release);
    if (control.fixture.core.stop_required && coakka_v2_runtime_stop(control.fixture.core.runtime) == COAKKA_V2_OK)
      control.fixture.core.stop_required = 0;
    for (size_t index = 0; index < started; ++index) {
      if (threads[index].implementation != NULL) (void)evidence_platform_thread_join(&threads[index], NULL);
    }
  }
  if (shared_ready) {
    /* Failure reporting reads after every started producer has joined. Do not
     * print zero admissions merely because the success aggregation was skipped. */
    if (!counts_captured) {
      result->attempted += atomic_load_explicit(&shared.attempted, memory_order_acquire);
      result->admitted += atomic_load_explicit(&shared.admitted, memory_order_acquire);
      result->admission_backpressure += atomic_load_explicit(&shared.backpressure, memory_order_acquire);
      result->unexpected_submit_statuses += atomic_load_explicit(&shared.unexpected, memory_order_acquire);
    }
    result->native.producer_tickets_discarded = atomic_load_explicit(&shared.discarded, memory_order_acquire);
  }
  if (control.cursors != NULL && control.producers != NULL) {
    for (size_t index = 0; index < control.count; ++index) {
      if (control.producers[index].publication.slots == NULL) continue;
      result->native.producer_tickets_published += atomic_load_explicit(&control.producers[index].publication.published, memory_order_acquire);
      result->native.producer_tickets_consumed += control.cursors[index].consumed;
      coakka_v2_ask_ticket_t* ticket;
      while ((ticket = native_ticket_cursor_take(&control.cursors[index])) != NULL) {
        coakka_v2_ask_ticket_destroy(ticket);
        ++result->native.producer_tickets_discarded;
      }
    }
  }
  free(slots); free(control.cursors); free(control.producers); free(threads);
  if (native_concurrency_fixture_close(&control.fixture, &failure) != COAKKA_V2_OK) {
    result->failure_stage = "workload.runtime-close"; *error = "Native fixture stop refused; owner retained"; failed = 1;
  }
#undef FAIL
  return failed;
}

int concurrency_evidence_run(const concurrency_evidence_config_t* config,
    coakka_v2_runtime_info_t* info, concurrency_evidence_result_t* result, const char** error) {
  if (config == NULL || info == NULL || result == NULL || error == NULL) return 1;
  memset(info, 0, sizeof(*info)); memset(result, 0, sizeof(*result)); *error = NULL;
  result->native.enabled = 1; info->struct_size = sizeof(*info);
  if (coakka_v2_runtime_get_info(info) != COAKKA_V2_OK) {
    result->failure_stage = "runtime-info"; *error = "Native runtime metadata failed"; return 1;
  }
  result->native.info_known = 1;
  const uint64_t start = now_ns();
  int failed = workload(config, result, error);
  if (!failed && config->mode == CONCURRENCY_EVIDENCE_MODE_RACE)
    failed = native_concurrency_stop_case(config, result, error) || native_concurrency_lifecycle_case(config, result, error);
  result->total_elapsed_ns = now_ns() - start;
  return failed;
}
