#include "concurrency_evidence.h"
#include "native_concurrency_fixture.h"
#include "evidence_platform.h"
#include <inttypes.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>

#define TARGET_A "samples.runtime.native.evidence.route-a"

static uint64_t now_ns(void) { return evidence_platform_monotonic_ns(); }
static int expired(uint64_t deadline) {
  const uint64_t now = now_ns();
  return now == 0 || now >= deadline;
}

static int join(evidence_platform_thread_t* threads, size_t count) {
  int failed = 0;
  for (size_t index = 0; index < count; ++index) {
    int code = 1;
    if (evidence_platform_thread_join(&threads[index], &code) || code != 0) failed = 1;
  }
  return failed;
}

typedef struct stop_shared_t {
  coakka_v2_runtime_t* runtime;
  uint64_t deadline;
  atomic_size_t ready;
  atomic_int go;
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t admitted;
  atomic_uint_fast64_t backpressure;
  atomic_uint_fast64_t closed;
  atomic_uint_fast64_t unexpected;
} stop_shared_t;
typedef struct stop_context_t { stop_shared_t* shared; size_t index; } stop_context_t;

static int stop_producer(void* raw) {
  stop_context_t* self = raw;
  stop_shared_t* shared = self->shared;
  uint8_t* request = NULL;
  size_t size = 0;
  if (native_concurrency_build_request(self->index, UINT64_C(9000000), TARGET_A, &request, &size)) {
    atomic_fetch_add_explicit(&shared->unexpected, 1, memory_order_relaxed);
    return 1;
  }
  atomic_fetch_add_explicit(&shared->ready, 1, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (expired(shared->deadline)) goto failed;
    evidence_platform_thread_yield();
  }
  for (uint64_t sequence = 0; !expired(shared->deadline); ++sequence) {
    const coakka_v2_status_t status = coakka_v2_runtime_submit_envelope(shared->runtime, request, size);
    atomic_fetch_add_explicit(&shared->attempted, 1, memory_order_relaxed);
    if (status == COAKKA_V2_OK) atomic_fetch_add_explicit(&shared->admitted, 1, memory_order_relaxed);
    else if (status == COAKKA_V2_ERR_WOULD_BLOCK) atomic_fetch_add_explicit(&shared->backpressure, 1, memory_order_relaxed);
    else if (status == COAKKA_V2_ERR_CLOSED) {
      atomic_fetch_add_explicit(&shared->closed, 1, memory_order_relaxed);
      coakka_v2_client_bytes_release(request);
      return 0;
    } else goto failed;
    if ((sequence & 7u) == 0) evidence_platform_thread_yield();
  }
failed:
  atomic_fetch_add_explicit(&shared->unexpected, 1, memory_order_relaxed);
  coakka_v2_client_bytes_release(request);
  return 1;
}

int native_concurrency_stop_case(const concurrency_evidence_config_t* config,
    concurrency_evidence_result_t* result, const char** error) {
  native_concurrency_fixture_t fixture;
  native_engine_failure_t failure;
  evidence_platform_thread_t* threads = NULL;
  stop_context_t* contexts = NULL;
  stop_shared_t shared;
  size_t started = 0;
  int failed = 1;
  native_concurrency_fixture_init(&fixture);
#define FAIL(stage_, text_) do { result->failure_stage = (stage_); *error = (text_); goto cleanup; } while (0)
  if (native_concurrency_fixture_open(&fixture, config->queue_capacity < 32 ? config->queue_capacity : 32,
      "concurrency-stop-race", TARGET_A, &failure) != COAKKA_V2_OK)
    FAIL("race.stop.runtime-setup", "Native stop-race setup failed");
  threads = calloc(config->thread_count, sizeof(*threads));
  contexts = calloc(config->thread_count, sizeof(*contexts));
  if (threads == NULL || contexts == NULL) FAIL("race.stop.allocation", "Native stop-race allocation failed");
  shared.runtime = fixture.core.runtime;
  const uint64_t start = now_ns();
  if (start == 0) FAIL("race.stop.clock", "Native stop-race clock failed");
  shared.deadline = start + config->timeout_ms * UINT64_C(1000000);
  atomic_init(&shared.ready, 0); atomic_init(&shared.go, 0);
  atomic_init(&shared.attempted, 0); atomic_init(&shared.admitted, 0);
  atomic_init(&shared.backpressure, 0); atomic_init(&shared.closed, 0); atomic_init(&shared.unexpected, 0);
  for (; started < config->thread_count; ++started) {
    contexts[started] = (stop_context_t){&shared, started};
    if (evidence_platform_thread_start(&threads[started], stop_producer, &contexts[started]))
      FAIL("race.stop.producer-start", "Native stop-race thread start failed");
  }
  while (atomic_load_explicit(&shared.ready, memory_order_acquire) < config->thread_count) {
    if (atomic_load_explicit(&shared.unexpected, memory_order_acquire) != 0 || expired(shared.deadline))
      FAIL("race.stop.ready", "Native stop-race producer readiness failed");
    evidence_platform_thread_yield();
  }
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  while (atomic_load_explicit(&shared.attempted, memory_order_acquire) < config->thread_count * UINT64_C(8)) {
    if (expired(shared.deadline)) FAIL("race.stop.contention", "Native stop-race contention deadline");
    evidence_platform_thread_yield();
  }
  if (coakka_v2_runtime_stop(fixture.core.runtime) != COAKKA_V2_OK)
    FAIL("race.stop.lifecycle", "Native stop refused during contention");
  /* Exact successful stop retires a cleanup obligation, not a guessed phase. */
  fixture.core.stop_required = 0;
  failed = 0;
cleanup:
  if (started != 0) {
    atomic_store_explicit(&shared.go, 1, memory_order_release);
    if (fixture.core.stop_required && coakka_v2_runtime_stop(fixture.core.runtime) == COAKKA_V2_OK)
      fixture.core.stop_required = 0;
    if (join(threads, started)) { *error = "Native stop-race producer join failed"; result->failure_stage = "race.stop.producer-join"; failed = 1; }
    result->stop_race_attempted = atomic_load_explicit(&shared.attempted, memory_order_acquire);
    result->stop_race_admitted = atomic_load_explicit(&shared.admitted, memory_order_acquire);
    result->stop_race_backpressure = atomic_load_explicit(&shared.backpressure, memory_order_acquire);
    result->stop_race_closed = atomic_load_explicit(&shared.closed, memory_order_acquire);
    result->stop_race_unexpected = atomic_load_explicit(&shared.unexpected, memory_order_acquire);
  }
  if (!failed && (result->stop_race_closed != config->thread_count || result->stop_race_unexpected != 0)) {
    *error = "Native submitters did not converge on Core CLOSED"; result->failure_stage = "race.stop.invariants"; failed = 1;
  }
  free(contexts); free(threads);
  if (native_concurrency_fixture_close(&fixture, &failure) != COAKKA_V2_OK) {
    *error = "Native stop-race close refused; owner retained"; result->failure_stage = "race.stop.close"; failed = 1;
  }
#undef FAIL
  return failed;
}

typedef struct lifecycle_shared_t {
  const concurrency_evidence_config_t* config;
  uint64_t deadline;
  atomic_size_t ready;
  atomic_int go;
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t completed;
  atomic_uint_fast64_t failures;
} lifecycle_shared_t;
typedef struct lifecycle_context_t { lifecycle_shared_t* shared; size_t index; } lifecycle_context_t;

static int lifecycle(void* raw) {
  lifecycle_context_t* self = raw;
  lifecycle_shared_t* shared = self->shared;
  atomic_fetch_add_explicit(&shared->ready, 1, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (expired(shared->deadline)) goto failed;
    evidence_platform_thread_yield();
  }
  for (uint64_t iteration = 0; iteration < shared->config->lifecycle_iterations_per_thread; ++iteration) {
    native_concurrency_fixture_t fixture;
    native_engine_failure_t failure;
    char node[96];
    native_concurrency_fixture_init(&fixture);
    atomic_fetch_add_explicit(&shared->attempted, 1, memory_order_relaxed);
    const int written = snprintf(node, sizeof(node), "lifecycle-%zu-%" PRIu64, self->index, iteration);
    int invalid = written <= 0 || (size_t)written >= sizeof(node);
    if (!invalid) invalid = native_concurrency_fixture_open(&fixture, 16, node, NULL, &failure) != COAKKA_V2_OK;
    if (native_concurrency_fixture_close(&fixture, &failure) != COAKKA_V2_OK) invalid = 1;
    if (invalid || expired(shared->deadline)) goto failed;
    atomic_fetch_add_explicit(&shared->completed, 1, memory_order_relaxed);
  }
  return 0;
failed:
  atomic_fetch_add_explicit(&shared->failures, 1, memory_order_relaxed);
  return 1;
}

int native_concurrency_lifecycle_case(const concurrency_evidence_config_t* config,
    concurrency_evidence_result_t* result, const char** error) {
  evidence_platform_thread_t* threads = calloc(config->thread_count, sizeof(*threads));
  lifecycle_context_t* contexts = calloc(config->thread_count, sizeof(*contexts));
  if (threads == NULL || contexts == NULL) {
    free(contexts); free(threads); *error = "Native lifecycle allocation failed";
    result->failure_stage = "race.lifecycle.allocation"; return 1;
  }
  lifecycle_shared_t shared;
  shared.config = config;
  const uint64_t start = now_ns();
  if (start == 0) { free(contexts); free(threads); *error = "Native lifecycle clock failed"; result->failure_stage = "race.lifecycle.clock"; return 1; }
  shared.deadline = start + config->timeout_ms * UINT64_C(1000000);
  atomic_init(&shared.ready, 0); atomic_init(&shared.go, 0);
  atomic_init(&shared.attempted, 0); atomic_init(&shared.completed, 0); atomic_init(&shared.failures, 0);
  size_t started = 0;
  int failed = 0;
  for (; started < config->thread_count; ++started) {
    contexts[started] = (lifecycle_context_t){&shared, started};
    if (evidence_platform_thread_start(&threads[started], lifecycle, &contexts[started])) {
      *error = "Native lifecycle thread start failed"; result->failure_stage = "race.lifecycle.thread-start"; failed = 1; break;
    }
  }
  while (!failed && atomic_load_explicit(&shared.ready, memory_order_acquire) < config->thread_count) {
    if (expired(shared.deadline)) { *error = "Native lifecycle readiness deadline"; result->failure_stage = "race.lifecycle.ready"; failed = 1; break; }
    evidence_platform_thread_yield();
  }
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  if (join(threads, started)) failed = 1;
  result->lifecycle_attempted = atomic_load_explicit(&shared.attempted, memory_order_acquire);
  result->lifecycle_completed = atomic_load_explicit(&shared.completed, memory_order_acquire);
  result->lifecycle_failures = atomic_load_explicit(&shared.failures, memory_order_acquire);
  free(contexts); free(threads);
  if (failed || result->lifecycle_failures != 0 || result->lifecycle_completed != config->thread_count * config->lifecycle_iterations_per_thread) {
    if (result->failure_stage == NULL) { *error = "Native independent lifecycle invariants failed"; result->failure_stage = "race.lifecycle.invariants"; }
    return 1;
  }
  return 0;
}
