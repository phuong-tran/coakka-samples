#include "concurrency_evidence.h"
#include "evidence_platform.h"

#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/transport.h"

#include <inttypes.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CONCURRENCY_TARGET_A "samples.runtime.native.evidence.route-a"
#define CONCURRENCY_TARGET_B "samples.runtime.native.evidence.route-b"

enum {
  CONCURRENCY_READER_MAX_FRAME_BYTES = 1024 * 1024,
  CONCURRENCY_PUMP_BATCH = 32,
  CONCURRENCY_WAIT_MS = 5,
  CONCURRENCY_STOP_QUEUE_CAPACITY = 32,
};

typedef struct concurrency_fixture_t {
  /* The control thread exclusively owns the exported readers and reply state. */
  coakka_v2_runtime_t* runtime;
  coakka_v2_host_handles_t handles;
  coakka_v2_frame_reader_t* delivered_reader;
  coakka_v2_frame_reader_t* response_reader;
  coakka_v2_frame_reader_t* deadletter_reader;
  uint8_t* pending_reply;
  size_t pending_reply_len;
  int started;
} concurrency_fixture_t;

typedef struct producer_shared_t {
  coakka_v2_runtime_t* runtime;
  const concurrency_evidence_config_t* config;
  uint64_t deadline_ns;
  atomic_size_t ready;
  atomic_size_t done;
  atomic_int go;
  /* Synchronizes each traffic quota with an observed snapshot generation. */
  atomic_uint_fast64_t allowed_requests_per_thread;
  /* Measurement-only counters; they do not publish non-atomic state. */
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t admitted;
  atomic_uint_fast64_t backpressure;
  atomic_uint_fast64_t unexpected;
} producer_shared_t;

typedef struct producer_context_t {
  producer_shared_t* shared;
  size_t thread_index;
} producer_context_t;

typedef struct stop_shared_t {
  coakka_v2_runtime_t* runtime;
  uint64_t deadline_ns;
  atomic_size_t ready;
  atomic_int go;
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t admitted;
  atomic_uint_fast64_t backpressure;
  atomic_uint_fast64_t closed;
  atomic_uint_fast64_t unexpected;
} stop_shared_t;

typedef struct stop_context_t {
  stop_shared_t* shared;
  size_t thread_index;
} stop_context_t;

typedef struct lifecycle_shared_t {
  const concurrency_evidence_config_t* config;
  uint64_t deadline_ns;
  atomic_size_t ready;
  atomic_int go;
  atomic_uint_fast64_t attempted;
  atomic_uint_fast64_t completed;
  atomic_uint_fast64_t failures;
} lifecycle_shared_t;

typedef struct lifecycle_context_t {
  lifecycle_shared_t* shared;
  size_t thread_index;
} lifecycle_context_t;

static uint64_t monotonic_ns(void) {
  return evidence_platform_monotonic_ns();
}

static int deadline_expired(uint64_t deadline_ns) {
  const uint64_t now = monotonic_ns();
  return now == 0u || now >= deadline_ns;
}

static void init_handles(coakka_v2_host_handles_t* handles) {
  memset(handles, 0, sizeof(*handles));
  handles->struct_size = sizeof(*handles);
  handles->flags = COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
  handles->request_write_fd = -1;
  handles->response_read_fd = -1;
  handles->deadletter_read_fd = -1;
  handles->control_write_fd = -1;
  handles->monitor_read_fd = -1;
  handles->delivered_request_read_fd = -1;
}

static void close_handles(coakka_v2_host_handles_t* handles) {
  int* channels[6];
  channels[0] = &handles->request_write_fd;
  channels[1] = &handles->response_read_fd;
  channels[2] = &handles->deadletter_read_fd;
  channels[3] = &handles->control_write_fd;
  channels[4] = &handles->monitor_read_fd;
  channels[5] = &handles->delivered_request_read_fd;
  evidence_platform_close_channels(channels,
                                   sizeof(channels) / sizeof(channels[0]));
}

static coakka_v2_status_t apply_snapshot(coakka_v2_runtime_t* runtime,
                                         uint64_t generation,
                                         const char* target) {
  coakka_v2_endpoint_t endpoint;
  coakka_v2_route_t route;
  coakka_v2_control_snapshot_t snapshot;

  memset(&endpoint, 0, sizeof(endpoint));
  endpoint.host = "127.0.0.1";
  endpoint.port = 9401u;
  endpoint.weight = 1u;
  endpoint.flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL;
  memset(&route, 0, sizeof(route));
  route.target = target;
  route.strategy = COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER;
  route.endpoints = &endpoint;
  route.endpoint_count = 1u;
  memset(&snapshot, 0, sizeof(snapshot));
  snapshot.generation = generation;
  snapshot.routes = &route;
  snapshot.route_count = 1u;
  return coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot);
}

static coakka_v2_status_t apply_empty_snapshot(coakka_v2_runtime_t* runtime) {
  coakka_v2_control_snapshot_t snapshot;
  memset(&snapshot, 0, sizeof(snapshot));
  snapshot.generation = 1u;
  return coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot);
}

static int fixture_open(concurrency_fixture_t* fixture,
                        size_t queue_capacity,
                        const char* node_id,
                        const char* initial_target,
                        const char** out_error) {
  coakka_v2_runtime_config_t runtime_config;

  memset(fixture, 0, sizeof(*fixture));
  init_handles(&fixture->handles);
  memset(&runtime_config, 0, sizeof(runtime_config));
  runtime_config.system_name = "runtime-v2-concurrency-evidence";
  runtime_config.node_id = node_id;
  runtime_config.strict_no_drop = 1;
  runtime_config.queue_capacity = (int)queue_capacity;
  fixture->runtime = coakka_v2_runtime_create(&runtime_config);
  if (fixture->runtime == NULL) {
    *out_error = "runtime create failed";
    return 1;
  }
  if (coakka_v2_runtime_get_host_handles(fixture->runtime, &fixture->handles) !=
          COAKKA_V2_OK ||
      apply_snapshot(fixture->runtime, 1u, initial_target) != COAKKA_V2_OK ||
      coakka_v2_runtime_start(fixture->runtime) != COAKKA_V2_OK) {
    *out_error = "runtime setup failed";
    return 1;
  }
  fixture->started = 1;
  fixture->delivered_reader = coakka_v2_frame_reader_create(
      fixture->handles.delivered_request_read_fd,
      CONCURRENCY_READER_MAX_FRAME_BYTES);
  fixture->response_reader = coakka_v2_frame_reader_create(
      fixture->handles.response_read_fd, CONCURRENCY_READER_MAX_FRAME_BYTES);
  fixture->deadletter_reader = coakka_v2_frame_reader_create(
      fixture->handles.deadletter_read_fd,
      CONCURRENCY_READER_MAX_FRAME_BYTES);
  if (fixture->delivered_reader == NULL || fixture->response_reader == NULL ||
      fixture->deadletter_reader == NULL) {
    *out_error = "runtime channel reader setup failed";
    return 1;
  }
  return 0;
}

static void fixture_close(concurrency_fixture_t* fixture) {
  if (fixture->runtime != NULL && fixture->started) {
    (void)coakka_v2_runtime_stop(fixture->runtime);
    fixture->started = 0;
  }
  coakka_v2_frame_reader_destroy(fixture->delivered_reader);
  coakka_v2_frame_reader_destroy(fixture->response_reader);
  coakka_v2_frame_reader_destroy(fixture->deadletter_reader);
  if (fixture->runtime != NULL) {
    coakka_v2_runtime_destroy(fixture->runtime);
  }
  close_handles(&fixture->handles);
  coakka_v2_client_bytes_release(fixture->pending_reply);
  memset(fixture, 0, sizeof(*fixture));
}

static int drain_reader(coakka_v2_frame_reader_t* reader,
                        uint64_t* counter) {
  size_t batch;
  for (batch = 0u; batch < CONCURRENCY_PUMP_BATCH; ++batch) {
    uint8_t* frame = NULL;
    size_t frame_len = 0u;
    const coakka_v2_status_t status =
        coakka_v2_frame_read_try(reader, &frame, &frame_len);
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (status != COAKKA_V2_OK) {
      return 1;
    }
    (void)frame_len;
    coakka_v2_frame_release(frame);
    ++(*counter);
  }
  return 0;
}

static int flush_reply(concurrency_fixture_t* fixture,
                       concurrency_evidence_result_t* result) {
  coakka_v2_status_t status;
  if (fixture->pending_reply == NULL) {
    return 0;
  }
  status = coakka_v2_runtime_submit_envelope(fixture->runtime,
                                             fixture->pending_reply,
                                             fixture->pending_reply_len);
  if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
    ++result->reply_submit_backpressure;
    return 0;
  }
  if (status != COAKKA_V2_OK) {
    return 1;
  }
  coakka_v2_client_bytes_release(fixture->pending_reply);
  fixture->pending_reply = NULL;
  fixture->pending_reply_len = 0u;
  ++result->replies_submitted;
  return 0;
}

static int handle_requests(concurrency_fixture_t* fixture,
                           concurrency_evidence_result_t* result) {
  static const uint8_t reply_payload[] = {'o', 'k'};
  size_t batch;

  for (batch = 0u; batch < CONCURRENCY_PUMP_BATCH; ++batch) {
    uint8_t* request = NULL;
    size_t request_len = 0u;
    coakka_v2_client_raw_reply_spec_t reply_spec;
    coakka_v2_status_t status;

    if (flush_reply(fixture, result)) {
      return 1;
    }
    if (fixture->pending_reply != NULL) {
      return 0;
    }
    status = coakka_v2_frame_read_try(fixture->delivered_reader,
                                      &request,
                                      &request_len);
    if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (status != COAKKA_V2_OK) {
      return 1;
    }
    ++result->handler_received;
    memset(&reply_spec, 0, sizeof(reply_spec));
    reply_spec.struct_size = sizeof(reply_spec);
    reply_spec.request_buf = request;
    reply_spec.request_len = request_len;
    reply_spec.source = "samples.runtime.native.evidence.handler";
    reply_spec.payload = reply_payload;
    reply_spec.payload_len = sizeof(reply_payload);
    status = coakka_v2_client_build_raw_reply(&reply_spec,
                                              &fixture->pending_reply,
                                              &fixture->pending_reply_len);
    coakka_v2_frame_release(request);
    if (status != COAKKA_V2_OK) {
      return 1;
    }
  }
  return 0;
}

static int pump_once(concurrency_fixture_t* fixture,
                     concurrency_evidence_result_t* result) {
  /* Producer threads submit only; this control thread owns all three lanes. */
  return handle_requests(fixture, result) ||
         drain_reader(fixture->response_reader, &result->response_count) ||
         drain_reader(fixture->deadletter_reader, &result->deadletter_count);
}

static int wait_for_activity(const concurrency_fixture_t* fixture) {
  const int channels[3] = {
      fixture->handles.delivered_request_read_fd,
      fixture->handles.response_read_fd,
      fixture->handles.deadletter_read_fd,
  };
  return evidence_platform_wait_readable(channels,
                                         sizeof(channels) / sizeof(channels[0]),
                                         CONCURRENCY_WAIT_MS);
}

static int build_request(size_t thread_index,
                         uint64_t request_index,
                         const char* target,
                         uint8_t** out_buf,
                         size_t* out_len) {
  char message_id[96];
  uint64_t payload[2];
  coakka_v2_client_raw_request_spec_t request_spec;
  const int written = snprintf(message_id,
                               sizeof(message_id),
                               "concurrency-%zu-%" PRIu64,
                               thread_index,
                               request_index);
  if (written <= 0 || written >= (int)sizeof(message_id)) {
    return 1;
  }
  payload[0] = (uint64_t)thread_index;
  payload[1] = request_index;
  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = message_id;
  request_spec.source = "samples.runtime.native.evidence.producer";
  request_spec.target = target;
  request_spec.reply_to = "samples.runtime.native.evidence.replies";
  request_spec.payload = (const uint8_t*)payload;
  request_spec.payload_len = sizeof(payload);
  request_spec.timeout_ms = 5000u;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  return coakka_v2_client_build_raw_request(&request_spec, out_buf, out_len) ==
                 COAKKA_V2_OK
             ? 0
             : 1;
}

static int producer_thread(void* raw) {
  producer_context_t* context = (producer_context_t*)raw;
  producer_shared_t* shared = context->shared;
  uint64_t request_index;

  /* The release/acquire gate starts every producer from the same phase. */
  atomic_fetch_add_explicit(&shared->ready, 1u, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (deadline_expired(shared->deadline_ns)) {
      atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
      atomic_fetch_add_explicit(&shared->done, 1u, memory_order_release);
      return 1;
    }
    evidence_platform_thread_yield();
  }

  for (request_index = 0u;
       request_index < shared->config->requests_per_thread;
       ++request_index) {
    uint8_t* request = NULL;
    size_t request_len = 0u;
    int submit_failed = 0;
    const char* target = CONCURRENCY_TARGET_A;
    if (shared->config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD) {
      /* The control thread raises this quota only after observing the new
       * generation through the public stats ABI. */
      while (request_index >= atomic_load_explicit(
                                  &shared->allowed_requests_per_thread,
                                  memory_order_acquire)) {
        if (deadline_expired(shared->deadline_ns)) {
          atomic_fetch_add_explicit(&shared->unexpected,
                                    1u,
                                    memory_order_relaxed);
          goto producer_done;
        }
        evidence_platform_thread_yield();
      }
    }
    if (shared->config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD &&
        ((request_index + (uint64_t)context->thread_index) & 1u) != 0u) {
      target = CONCURRENCY_TARGET_B;
    }
    if (build_request(context->thread_index,
                      request_index,
                      target,
                      &request,
                      &request_len)) {
      atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
      break;
    }
    atomic_fetch_add_explicit(&shared->attempted, 1u, memory_order_relaxed);
    for (;;) {
      const coakka_v2_status_t status =
          coakka_v2_runtime_submit_envelope(shared->runtime,
                                            request,
                                            request_len);
      if (status == COAKKA_V2_OK) {
        atomic_fetch_add_explicit(&shared->admitted, 1u, memory_order_relaxed);
        break;
      }
      if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
        atomic_fetch_add_explicit(&shared->backpressure,
                                  1u,
                                  memory_order_relaxed);
        if (!deadline_expired(shared->deadline_ns)) {
          evidence_platform_thread_yield();
          continue;
        }
      }
      atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
      submit_failed = 1;
      break;
    }
    coakka_v2_client_bytes_release(request);
    if (submit_failed || deadline_expired(shared->deadline_ns)) {
      break;
    }
  }
producer_done:
  atomic_fetch_add_explicit(&shared->done, 1u, memory_order_release);
  return 0;
}

static int start_producers(const concurrency_evidence_config_t* config,
                           coakka_v2_runtime_t* runtime,
                           uint64_t deadline_ns,
                           evidence_platform_thread_t* threads,
                           producer_context_t* contexts,
                           producer_shared_t* shared) {
  size_t index;
  memset(shared, 0, sizeof(*shared));
  shared->runtime = runtime;
  shared->config = config;
  shared->deadline_ns = deadline_ns;
  atomic_init(&shared->ready, 0u);
  atomic_init(&shared->done, 0u);
  atomic_init(&shared->go, 0);
  /* Keep the first traffic slice bounded so producers cannot run past the
   * first snapshot transition before the control thread observes it. */
  atomic_init(
      &shared->allowed_requests_per_thread,
      config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD
          ? (config->requests_per_thread + config->generation_count - 1u) /
                config->generation_count
          : config->requests_per_thread);
  atomic_init(&shared->attempted, 0u);
  atomic_init(&shared->admitted, 0u);
  atomic_init(&shared->backpressure, 0u);
  atomic_init(&shared->unexpected, 0u);
  for (index = 0u; index < config->thread_count; ++index) {
    contexts[index].shared = shared;
    contexts[index].thread_index = index;
    if (evidence_platform_thread_start(&threads[index],
                                       producer_thread,
                                       &contexts[index])) {
      atomic_store_explicit(&shared->go, 1, memory_order_release);
      while (index > 0u) {
        --index;
        (void)evidence_platform_thread_join(&threads[index], NULL);
      }
      return 1;
    }
  }
  return 0;
}

static int join_threads(evidence_platform_thread_t* threads,
                        size_t thread_count) {
  size_t index;
  int failed = 0;
  for (index = 0u; index < thread_count; ++index) {
    int result = 0;
    if (evidence_platform_thread_join(&threads[index], &result) || result != 0) {
      failed = 1;
    }
  }
  return failed;
}

static int wait_until_producers_ready(const producer_shared_t* shared,
                                      size_t thread_count) {
  while (atomic_load_explicit(&shared->ready, memory_order_acquire) <
         thread_count) {
    if (deadline_expired(shared->deadline_ns)) {
      return 1;
    }
    evidence_platform_thread_yield();
  }
  return 0;
}

static int pump_until_attempted(concurrency_fixture_t* fixture,
                                concurrency_evidence_result_t* result,
                                const producer_shared_t* shared,
                                uint64_t threshold) {
  while (atomic_load_explicit(&shared->attempted, memory_order_acquire) <
         threshold) {
    if (pump_once(fixture, result) || deadline_expired(shared->deadline_ns) ||
        wait_for_activity(fixture)) {
      return 1;
    }
  }
  return 0;
}

static int pump_until_workload_complete(concurrency_fixture_t* fixture,
                                        concurrency_evidence_result_t* result,
                                        const producer_shared_t* shared,
                                        size_t thread_count) {
  for (;;) {
    const uint64_t admitted =
        atomic_load_explicit(&shared->admitted, memory_order_acquire);
    const size_t done = atomic_load_explicit(&shared->done, memory_order_acquire);
    if (pump_once(fixture, result)) {
      return 1;
    }
    if (done == thread_count &&
        result->response_count + result->deadletter_count == admitted &&
        fixture->pending_reply == NULL) {
      return 0;
    }
    if (deadline_expired(shared->deadline_ns) || wait_for_activity(fixture)) {
      return 1;
    }
  }
}

static int run_probe(concurrency_fixture_t* fixture,
                     concurrency_evidence_result_t* result,
                     const char* target,
                     int expect_response,
                     uint64_t deadline_ns) {
  uint8_t* request = NULL;
  size_t request_len = 0u;
  uint64_t responses_before = result->response_count;
  uint64_t deadletters_before = result->deadletter_count;

  if (build_request(9999u,
                    result->attempted,
                    target,
                    &request,
                    &request_len)) {
    return 1;
  }
  ++result->attempted;
  for (;;) {
    const coakka_v2_status_t status =
        coakka_v2_runtime_submit_envelope(fixture->runtime,
                                          request,
                                          request_len);
    if (status == COAKKA_V2_OK) {
      ++result->admitted;
      break;
    }
    if (status != COAKKA_V2_ERR_WOULD_BLOCK || deadline_expired(deadline_ns) ||
        pump_once(fixture, result)) {
      coakka_v2_client_bytes_release(request);
      return 1;
    }
    ++result->admission_backpressure;
  }
  coakka_v2_client_bytes_release(request);
  while (result->response_count + result->deadletter_count ==
         responses_before + deadletters_before) {
    if (pump_once(fixture, result) || deadline_expired(deadline_ns) ||
        wait_for_activity(fixture)) {
      return 1;
    }
  }
  return expect_response
             ? !(result->response_count == responses_before + 1u &&
                 result->deadletter_count == deadletters_before)
             : !(result->response_count == responses_before &&
                 result->deadletter_count == deadletters_before + 1u);
}

static int run_main_workload(const concurrency_evidence_config_t* config,
                             concurrency_evidence_result_t* result,
                             const char** out_error) {
  concurrency_fixture_t fixture;
  evidence_platform_thread_t* threads = NULL;
  producer_context_t* contexts = NULL;
  producer_shared_t shared;
  coakka_v2_runtime_stats_t stats;
  uint64_t generation;
  uint64_t total_requests;
  uint64_t deadline_ns;
  uint64_t start_ns;
  int failed = 1;

  memset(&fixture, 0, sizeof(fixture));
  total_requests = (uint64_t)config->thread_count * config->requests_per_thread;
  deadline_ns = monotonic_ns() + config->timeout_ms * UINT64_C(1000000);
  if (fixture_open(&fixture,
                   config->queue_capacity,
                   "concurrency-main",
                   CONCURRENCY_TARGET_A,
                   out_error)) {
    result->failure_stage = "workload.runtime-setup";
    goto cleanup;
  }
  result->snapshot_apply_successes = 1u;
  memset(&stats, 0, sizeof(stats));
  stats.struct_size = sizeof(stats);
  if (coakka_v2_runtime_get_stats(fixture.runtime, &stats) != COAKKA_V2_OK ||
      stats.applied_generation != 1u || stats.route_count != 1u ||
      stats.runtime_state != COAKKA_V2_STATE_STARTED) {
    *out_error = "initial snapshot observation failed";
    result->failure_stage = "workload.initial-snapshot-observation";
    goto cleanup;
  }
  result->snapshot_observation_successes = 1u;
  threads = (evidence_platform_thread_t*)calloc(config->thread_count,
                                                 sizeof(*threads));
  contexts =
      (producer_context_t*)calloc(config->thread_count, sizeof(*contexts));
  if (threads == NULL || contexts == NULL) {
    *out_error = "producer allocation failed";
    result->failure_stage = "workload.producer-allocation";
    goto cleanup;
  }
  if (start_producers(config,
                      fixture.runtime,
                      deadline_ns,
                      threads,
                      contexts,
                      &shared)) {
    *out_error = "producer startup failed";
    result->failure_stage = "workload.producer-start";
    goto cleanup;
  }
  if (wait_until_producers_ready(&shared, config->thread_count)) {
    *out_error = "producer readiness timed out";
    result->failure_stage = "workload.producer-ready";
    goto join_cleanup;
  }
  start_ns = monotonic_ns();
  atomic_store_explicit(&shared.go, 1, memory_order_release);

  if (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD) {
    /* attempted is sampled before submit, so each apply overlaps requests at
     * the old/new snapshot boundary without assuming which complete snapshot
     * an in-flight request will resolve against. */
    for (generation = 2u; generation <= config->generation_count;
         ++generation) {
      const uint64_t requests_per_thread_before_apply =
          (config->requests_per_thread * (generation - 1u) +
           config->generation_count - 1u) /
          config->generation_count;
      const uint64_t threshold =
          (uint64_t)config->thread_count * requests_per_thread_before_apply;
      const char* target =
          (generation & 1u) == 0u ? CONCURRENCY_TARGET_B : CONCURRENCY_TARGET_A;
      if (pump_until_attempted(&fixture, result, &shared, threshold)) {
        *out_error = "hot-reload traffic threshold timed out";
        result->failure_stage = "hot-reload.concurrent-traffic";
        goto join_cleanup;
      }
      if (apply_snapshot(fixture.runtime, generation, target) != COAKKA_V2_OK) {
        *out_error = "newer snapshot apply failed";
        result->failure_stage = "hot-reload.snapshot-apply";
        goto join_cleanup;
      }
      ++result->snapshot_apply_successes;
      memset(&stats, 0, sizeof(stats));
      stats.struct_size = sizeof(stats);
      if (coakka_v2_runtime_get_stats(fixture.runtime, &stats) != COAKKA_V2_OK ||
          stats.applied_generation != generation || stats.route_count != 1u ||
          stats.runtime_state != COAKKA_V2_STATE_STARTED) {
        *out_error = "applied snapshot was not observable as active";
        result->failure_stage = "hot-reload.snapshot-observation";
        goto join_cleanup;
      }
      ++result->snapshot_observation_successes;
      atomic_store_explicit(
          &shared.allowed_requests_per_thread,
          (config->requests_per_thread * generation +
           config->generation_count - 1u) /
              config->generation_count,
          memory_order_release);
    }
  }

  if (pump_until_workload_complete(&fixture,
                                   result,
                                   &shared,
                                   config->thread_count)) {
    *out_error = "concurrent workload did not drain before deadline";
    result->failure_stage = "workload.final-drain";
    goto join_cleanup;
  }
  result->workload_elapsed_ns = monotonic_ns() - start_ns;

join_cleanup:
  /* On every failure path, stop first to release submitters, then join before
   * fixture_close destroys the runtime and the contexts they reference. */
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  if (result->failure_stage != NULL && fixture.started) {
    (void)coakka_v2_runtime_stop(fixture.runtime);
    fixture.started = 0;
  }
  /* The runtime handle remains valid until every concurrent submit returns. */
  if (join_threads(threads, config->thread_count)) {
    *out_error = "producer join failed";
    result->failure_stage = "workload.producer-join";
    goto cleanup;
  }
  result->attempted +=
      atomic_load_explicit(&shared.attempted, memory_order_acquire);
  result->admitted +=
      atomic_load_explicit(&shared.admitted, memory_order_acquire);
  result->admission_backpressure +=
      atomic_load_explicit(&shared.backpressure, memory_order_acquire);
  result->unexpected_submit_statuses +=
      atomic_load_explicit(&shared.unexpected, memory_order_acquire);
  if (result->failure_stage != NULL) {
    goto cleanup;
  }
  if (result->unexpected_submit_statuses != 0u ||
      result->attempted != total_requests || result->admitted != total_requests ||
      result->response_count + result->deadletter_count != result->admitted ||
      result->handler_received != result->replies_submitted ||
      result->replies_submitted != result->response_count) {
    *out_error = "concurrent terminal-outcome invariants failed";
    result->failure_stage = "workload.invariants";
    goto cleanup;
  }
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD) {
    const char* active_target =
        (config->generation_count & 1u) == 0u ? CONCURRENCY_TARGET_B
                                              : CONCURRENCY_TARGET_A;
    const char* inactive_target =
        (config->generation_count & 1u) == 0u ? CONCURRENCY_TARGET_A
                                              : CONCURRENCY_TARGET_B;
    result->stale_apply_status = (int32_t)apply_snapshot(
        fixture.runtime, config->generation_count, active_target);
    result->invalid_apply_status = (int32_t)apply_snapshot(
        fixture.runtime, config->generation_count + 1u, "");
    if (result->stale_apply_status != COAKKA_V2_ERR_STALE_GENERATION ||
        result->invalid_apply_status != COAKKA_V2_ERR_INVALID_ARG) {
      *out_error = "stale or invalid snapshot rejection contract failed";
      result->failure_stage = "hot-reload.rejection-contract";
      goto cleanup;
    }
    if (run_probe(&fixture, result, active_target, 1, deadline_ns)) {
      *out_error = "active target did not converge after hot reload";
      result->failure_stage = "hot-reload.active-target-probe";
      goto cleanup;
    }
    result->active_target_probe_passed = 1;
    if (run_probe(&fixture, result, inactive_target, 0, deadline_ns)) {
      *out_error = "replaced target remained active after hot reload";
      result->failure_stage = "hot-reload.inactive-target-probe";
      goto cleanup;
    }
    result->inactive_target_probe_passed = 1;
    if (result->response_count == 0u || result->deadletter_count == 0u) {
      *out_error = "hot-reload workload did not exercise both route outcomes";
      result->failure_stage = "hot-reload.route-outcomes";
      goto cleanup;
    }
  }

  memset(&stats, 0, sizeof(stats));
  stats.struct_size = sizeof(stats);
  if (coakka_v2_runtime_get_stats(fixture.runtime, &stats) != COAKKA_V2_OK) {
    *out_error = "final runtime stats read failed";
    result->failure_stage = "workload.final-stats";
    goto cleanup;
  }
  result->final_generation = stats.applied_generation;
  result->final_route_count = stats.route_count;
  result->final_runtime_state = (uint32_t)stats.runtime_state;
  result->runtime_deadletter_count = stats.deadletter_count;
  result->queue_rejected_count = stats.queue_rejected_count;
  result->route_miss_count = stats.route_miss_count;
  result->delivery_failed_count = stats.delivery_failed_count;
  generation = config->mode == CONCURRENCY_EVIDENCE_MODE_HOT_RELOAD
                   ? config->generation_count
                   : 1u;
  if (result->final_generation != generation || result->final_route_count != 1u ||
      result->final_runtime_state != (uint32_t)COAKKA_V2_STATE_STARTED) {
    *out_error = "final snapshot or lifecycle state did not converge";
    result->failure_stage = "workload.final-state";
    goto cleanup;
  }
  if (result->runtime_deadletter_count != result->deadletter_count ||
      result->delivery_failed_count != 0u) {
    *out_error = "terminal lane and runtime deadletter counters diverged";
    result->failure_stage = "workload.terminal-accounting";
    goto cleanup;
  }
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_RACE) {
    if (result->route_miss_count != 0u ||
        result->runtime_deadletter_count + result->reply_submit_backpressure +
                result->admission_backpressure !=
            result->queue_rejected_count) {
      *out_error = "race workload observed a non-pressure deadletter";
      result->failure_stage = "race.terminal-accounting";
      goto cleanup;
    }
  } else if (result->runtime_deadletter_count +
                 result->reply_submit_backpressure +
                 result->admission_backpressure !=
             result->queue_rejected_count + result->route_miss_count) {
    *out_error = "hot-reload deadletters were not route misses or queue pressure";
    result->failure_stage = "hot-reload.terminal-accounting";
    goto cleanup;
  }
  if (result->snapshot_observation_successes !=
      result->snapshot_apply_successes) {
    *out_error = "not every accepted snapshot became observable";
    result->failure_stage = "hot-reload.snapshot-observation-count";
    goto cleanup;
  }
  failed = 0;

cleanup:
  free(contexts);
  free(threads);
  fixture_close(&fixture);
  return failed;
}

static int stop_producer_thread(void* raw) {
  stop_context_t* context = (stop_context_t*)raw;
  stop_shared_t* shared = context->shared;
  uint8_t* request = NULL;
  size_t request_len = 0u;
  uint64_t request_index = 0u;

  if (build_request(context->thread_index,
                    UINT64_C(9000000),
                    CONCURRENCY_TARGET_A,
                    &request,
                    &request_len)) {
    atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
    return 1;
  }
  atomic_fetch_add_explicit(&shared->ready, 1u, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (deadline_expired(shared->deadline_ns)) {
      atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
      coakka_v2_client_bytes_release(request);
      return 1;
    }
    evidence_platform_thread_yield();
  }
  while (!deadline_expired(shared->deadline_ns)) {
    const coakka_v2_status_t status =
        coakka_v2_runtime_submit_envelope(shared->runtime,
                                          request,
                                          request_len);
    ++request_index;
    atomic_fetch_add_explicit(&shared->attempted, 1u, memory_order_relaxed);
    if (status == COAKKA_V2_OK) {
      atomic_fetch_add_explicit(&shared->admitted, 1u, memory_order_relaxed);
    } else if (status == COAKKA_V2_ERR_WOULD_BLOCK) {
      atomic_fetch_add_explicit(&shared->backpressure,
                                1u,
                                memory_order_relaxed);
    } else if (status == COAKKA_V2_ERR_CLOSED) {
      atomic_fetch_add_explicit(&shared->closed, 1u, memory_order_relaxed);
      coakka_v2_client_bytes_release(request);
      return 0;
    } else {
      atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
      coakka_v2_client_bytes_release(request);
      return 1;
    }
    if ((request_index & 7u) == 0u) {
      evidence_platform_thread_yield();
    }
  }
  atomic_fetch_add_explicit(&shared->unexpected, 1u, memory_order_relaxed);
  coakka_v2_client_bytes_release(request);
  return 1;
}

static int run_stop_race(const concurrency_evidence_config_t* config,
                         concurrency_evidence_result_t* result,
                         const char** out_error) {
  concurrency_fixture_t fixture;
  evidence_platform_thread_t* threads = NULL;
  stop_context_t* contexts = NULL;
  stop_shared_t shared;
  uint64_t threshold;
  size_t index;
  int failed = 1;

  memset(&fixture, 0, sizeof(fixture));
  if (fixture_open(&fixture,
                   config->queue_capacity < CONCURRENCY_STOP_QUEUE_CAPACITY
                       ? config->queue_capacity
                       : CONCURRENCY_STOP_QUEUE_CAPACITY,
                   "concurrency-stop-race",
                   CONCURRENCY_TARGET_A,
                   out_error)) {
    result->failure_stage = "race.stop.runtime-setup";
    goto cleanup;
  }
  threads = (evidence_platform_thread_t*)calloc(config->thread_count,
                                                 sizeof(*threads));
  contexts = (stop_context_t*)calloc(config->thread_count, sizeof(*contexts));
  if (threads == NULL || contexts == NULL) {
    *out_error = "stop-race producer allocation failed";
    result->failure_stage = "race.stop.allocation";
    goto cleanup;
  }
  memset(&shared, 0, sizeof(shared));
  shared.runtime = fixture.runtime;
  shared.deadline_ns = monotonic_ns() + config->timeout_ms * UINT64_C(1000000);
  atomic_init(&shared.ready, 0u);
  atomic_init(&shared.go, 0);
  atomic_init(&shared.attempted, 0u);
  atomic_init(&shared.admitted, 0u);
  atomic_init(&shared.backpressure, 0u);
  atomic_init(&shared.closed, 0u);
  atomic_init(&shared.unexpected, 0u);
  for (index = 0u; index < config->thread_count; ++index) {
    contexts[index].shared = &shared;
    contexts[index].thread_index = index;
    if (evidence_platform_thread_start(&threads[index],
                                       stop_producer_thread,
                                       &contexts[index])) {
      *out_error = "stop-race producer start failed";
      result->failure_stage = "race.stop.producer-start";
      atomic_store_explicit(&shared.go, 1, memory_order_release);
      if (fixture.started) {
        (void)coakka_v2_runtime_stop(fixture.runtime);
        fixture.started = 0;
      }
      while (index > 0u) {
        --index;
        (void)evidence_platform_thread_join(&threads[index], NULL);
      }
      goto cleanup;
    }
  }
  while (atomic_load_explicit(&shared.ready, memory_order_acquire) <
         config->thread_count) {
    if (deadline_expired(shared.deadline_ns)) {
      *out_error = "stop-race producers did not become ready";
      result->failure_stage = "race.stop.ready";
      goto join_cleanup;
    }
    evidence_platform_thread_yield();
  }
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  threshold = (uint64_t)config->thread_count * 8u;
  while (atomic_load_explicit(&shared.attempted, memory_order_acquire) <
         threshold) {
    if (deadline_expired(shared.deadline_ns)) {
      *out_error = "stop-race contention threshold timed out";
      result->failure_stage = "race.stop.contention";
      goto join_cleanup;
    }
    evidence_platform_thread_yield();
  }
  if (coakka_v2_runtime_stop(fixture.runtime) != COAKKA_V2_OK) {
    *out_error = "runtime stop failed during submit contention";
    result->failure_stage = "race.stop.lifecycle";
    goto join_cleanup;
  }
  fixture.started = 0;

join_cleanup:
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  if (fixture.started) {
    if (coakka_v2_runtime_stop(fixture.runtime) == COAKKA_V2_OK) {
      fixture.started = 0;
    }
  }
  /* Destruction is sequenced after every submitter has returned from the
   * public ABI; the success invariant below additionally requires CLOSED. */
  if (join_threads(threads, config->thread_count)) {
    *out_error = "stop-race producer join failed";
    result->failure_stage = "race.stop.producer-join";
    goto cleanup;
  }
  result->stop_race_attempted =
      atomic_load_explicit(&shared.attempted, memory_order_acquire);
  result->stop_race_admitted =
      atomic_load_explicit(&shared.admitted, memory_order_acquire);
  result->stop_race_backpressure =
      atomic_load_explicit(&shared.backpressure, memory_order_acquire);
  result->stop_race_closed =
      atomic_load_explicit(&shared.closed, memory_order_acquire);
  result->stop_race_unexpected =
      atomic_load_explicit(&shared.unexpected, memory_order_acquire);
  if (result->failure_stage == NULL &&
      (result->stop_race_closed != config->thread_count ||
       result->stop_race_unexpected != 0u)) {
    *out_error = "submitters did not converge on CLOSED during stop";
    result->failure_stage = "race.stop.invariants";
  }
  if (result->failure_stage == NULL) {
    failed = 0;
  }

cleanup:
  free(contexts);
  free(threads);
  fixture_close(&fixture);
  return failed;
}

static int lifecycle_thread(void* raw) {
  lifecycle_context_t* context = (lifecycle_context_t*)raw;
  lifecycle_shared_t* shared = context->shared;
  uint64_t iteration;

  atomic_fetch_add_explicit(&shared->ready, 1u, memory_order_release);
  while (!atomic_load_explicit(&shared->go, memory_order_acquire)) {
    if (deadline_expired(shared->deadline_ns)) {
      atomic_fetch_add_explicit(&shared->failures, 1u, memory_order_relaxed);
      return 1;
    }
    evidence_platform_thread_yield();
  }
  for (iteration = 0u;
       iteration < shared->config->lifecycle_iterations_per_thread;
       ++iteration) {
    /* Each thread owns a distinct runtime and every exported channel for the
     * full create/start/stop/destroy lifecycle. No handle crosses threads. */
    coakka_v2_runtime_t* runtime = NULL;
    coakka_v2_host_handles_t handles;
    coakka_v2_runtime_config_t runtime_config;
    char node_id[96];
    int started = 0;
    int failed = 0;

    atomic_fetch_add_explicit(&shared->attempted, 1u, memory_order_relaxed);
    const int written = snprintf(node_id,
                                 sizeof(node_id),
                                 "lifecycle-%zu-%" PRIu64,
                                 context->thread_index,
                                 iteration);
    if (written <= 0 || written >= (int)sizeof(node_id)) {
      failed = 1;
    }
    init_handles(&handles);
    memset(&runtime_config, 0, sizeof(runtime_config));
    runtime_config.system_name = "runtime-v2-lifecycle-race";
    runtime_config.node_id = node_id;
    runtime_config.strict_no_drop = 1;
    runtime_config.queue_capacity = 16;
    if (!failed) {
      runtime = coakka_v2_runtime_create(&runtime_config);
      failed = runtime == NULL;
    }
    if (!failed &&
        (coakka_v2_runtime_get_host_handles(runtime, &handles) != COAKKA_V2_OK ||
         apply_empty_snapshot(runtime) != COAKKA_V2_OK ||
         coakka_v2_runtime_start(runtime) != COAKKA_V2_OK)) {
      failed = 1;
    } else if (!failed) {
      started = 1;
    }
    if (started && coakka_v2_runtime_stop(runtime) != COAKKA_V2_OK) {
      failed = 1;
    }
    if (runtime != NULL) {
      coakka_v2_runtime_destroy(runtime);
    }
    close_handles(&handles);
    if (failed || deadline_expired(shared->deadline_ns)) {
      atomic_fetch_add_explicit(&shared->failures, 1u, memory_order_relaxed);
      return 1;
    }
    atomic_fetch_add_explicit(&shared->completed, 1u, memory_order_relaxed);
  }
  return 0;
}

static int run_lifecycle_race(const concurrency_evidence_config_t* config,
                              concurrency_evidence_result_t* result,
                              const char** out_error) {
  evidence_platform_thread_t* threads;
  lifecycle_context_t* contexts;
  lifecycle_shared_t shared;
  size_t index;
  int failed;

  threads = (evidence_platform_thread_t*)calloc(config->thread_count,
                                                 sizeof(*threads));
  contexts =
      (lifecycle_context_t*)calloc(config->thread_count, sizeof(*contexts));
  if (threads == NULL || contexts == NULL) {
    free(contexts);
    free(threads);
    *out_error = "lifecycle-race allocation failed";
    result->failure_stage = "race.lifecycle.allocation";
    return 1;
  }
  memset(&shared, 0, sizeof(shared));
  shared.config = config;
  shared.deadline_ns = monotonic_ns() + config->timeout_ms * UINT64_C(1000000);
  atomic_init(&shared.ready, 0u);
  atomic_init(&shared.go, 0);
  atomic_init(&shared.attempted, 0u);
  atomic_init(&shared.completed, 0u);
  atomic_init(&shared.failures, 0u);
  for (index = 0u; index < config->thread_count; ++index) {
    contexts[index].shared = &shared;
    contexts[index].thread_index = index;
    if (evidence_platform_thread_start(&threads[index],
                                       lifecycle_thread,
                                       &contexts[index])) {
      *out_error = "lifecycle-race thread start failed";
      result->failure_stage = "race.lifecycle.thread-start";
      atomic_store_explicit(&shared.go, 1, memory_order_release);
      while (index > 0u) {
        --index;
        (void)evidence_platform_thread_join(&threads[index], NULL);
      }
      free(contexts);
      free(threads);
      return 1;
    }
  }
  while (atomic_load_explicit(&shared.ready, memory_order_acquire) <
         config->thread_count) {
    if (deadline_expired(shared.deadline_ns)) {
      break;
    }
    evidence_platform_thread_yield();
  }
  atomic_store_explicit(&shared.go, 1, memory_order_release);
  failed = join_threads(threads, config->thread_count);
  result->lifecycle_attempted =
      atomic_load_explicit(&shared.attempted, memory_order_acquire);
  result->lifecycle_completed =
      atomic_load_explicit(&shared.completed, memory_order_acquire);
  result->lifecycle_failures =
      atomic_load_explicit(&shared.failures, memory_order_acquire);
  free(contexts);
  free(threads);
  if (failed || result->lifecycle_failures != 0u ||
      result->lifecycle_completed !=
          (uint64_t)config->thread_count *
              config->lifecycle_iterations_per_thread) {
    *out_error = "independent runtime lifecycle race failed";
    result->failure_stage = "race.lifecycle.invariants";
    return 1;
  }
  return 0;
}

int concurrency_evidence_run(const concurrency_evidence_config_t* config,
                             coakka_v2_runtime_info_t* runtime_info,
                             concurrency_evidence_result_t* result,
                             const char** out_error) {
  uint64_t start_ns;
  if (config == NULL || runtime_info == NULL || result == NULL ||
      out_error == NULL) {
    return 1;
  }
  memset(runtime_info, 0, sizeof(*runtime_info));
  runtime_info->struct_size = sizeof(*runtime_info);
  memset(result, 0, sizeof(*result));
  *out_error = NULL;
  if (coakka_v2_runtime_get_info(runtime_info) != COAKKA_V2_OK) {
    *out_error = "runtime metadata read failed";
    result->failure_stage = "runtime-info";
    return 1;
  }
  start_ns = monotonic_ns();
  if (run_main_workload(config, result, out_error)) {
    result->total_elapsed_ns = monotonic_ns() - start_ns;
    return 1;
  }
  if (config->mode == CONCURRENCY_EVIDENCE_MODE_RACE &&
      (run_stop_race(config, result, out_error) ||
       run_lifecycle_race(config, result, out_error))) {
    result->total_elapsed_ns = monotonic_ns() - start_ns;
    return 1;
  }
  result->total_elapsed_ns = monotonic_ns() - start_ns;
  return 0;
}
