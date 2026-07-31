#include "evidence.h"
#include "evidence_platform.h"

#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/transport.h"
#include "coakka/v2/utils.h"

#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
  EVIDENCE_FRAME_OVERHEAD_RESERVE = 256 * 1024,
  EVIDENCE_MIN_READER_FRAME_BYTES = 1024 * 1024,
  EVIDENCE_NATIVE_SUBMIT_PUMP_INTERVAL = 16,
  EVIDENCE_REQUEST_CHANNEL_PUMP_INTERVAL = 4,
  EVIDENCE_ACTIVITY_WAIT_MS = 10,
  EVIDENCE_FINAL_DRAIN_TIMEOUT_MS = 5000,
};

typedef struct evidence_channels_t {
  int request;
  int response;
  int deadletter;
  int control;
  int monitor;
  int delivered_request;
} evidence_channels_t;

typedef struct evidence_handler_state_t {
  uint8_t* pending_reply;
  size_t pending_reply_len;
} evidence_handler_state_t;

static uint64_t monotonic_ns(void) {
  return evidence_platform_monotonic_ns();
}

static uint64_t monotonic_ms(void) {
  return monotonic_ns() / 1000000u;
}

static void close_channel(int* channel) {
  evidence_platform_close_channel(channel);
}

/*
 * Raw fd names belong to the published host-handle ABI. Keep them inside this
 * adapter so the workload and report use channel vocabulary and do not expose
 * the transport representation as an application-level concept.
 */
static void init_host_handles(coakka_v2_host_handles_t* handles) {
  memset(handles, 0, sizeof(*handles));
  handles->struct_size = sizeof(*handles);
  handles->request_write_fd = -1;
  handles->response_read_fd = -1;
  handles->deadletter_read_fd = -1;
  handles->control_write_fd = -1;
  handles->monitor_read_fd = -1;
  handles->delivered_request_read_fd = -1;
}

static void close_host_handles(coakka_v2_host_handles_t* handles) {
  if (handles == NULL) {
    return;
  }
  close_channel(&handles->request_write_fd);
  close_channel(&handles->response_read_fd);
  close_channel(&handles->deadletter_read_fd);
  close_channel(&handles->control_write_fd);
  close_channel(&handles->monitor_read_fd);
  close_channel(&handles->delivered_request_read_fd);
}

static void init_channels(evidence_channels_t* channels) {
  channels->request = -1;
  channels->response = -1;
  channels->deadletter = -1;
  channels->control = -1;
  channels->monitor = -1;
  channels->delivered_request = -1;
}

static void close_channels(evidence_channels_t* channels) {
  if (channels == NULL) {
    return;
  }
  close_channel(&channels->request);
  close_channel(&channels->response);
  close_channel(&channels->deadletter);
  close_channel(&channels->control);
  close_channel(&channels->monitor);
  close_channel(&channels->delivered_request);
}

static int open_runtime_channels(coakka_v2_runtime_t* runtime,
                                 evidence_channels_t* channels) {
  coakka_v2_host_handles_t handles;

  init_host_handles(&handles);
  /*
   * A separate delivered-request lane preserves the REQUEST/RESPONSE boundary:
   * handler work never competes with terminal replies on the same host channel.
   */
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR |
                  COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
  if (coakka_v2_runtime_get_host_handles(runtime, &handles) != COAKKA_V2_OK) {
    close_host_handles(&handles);
    return 1;
  }

  channels->request = handles.request_write_fd;
  channels->response = handles.response_read_fd;
  channels->deadletter = handles.deadletter_read_fd;
  channels->control = handles.control_write_fd;
  channels->monitor = handles.monitor_read_fd;
  channels->delivered_request = handles.delivered_request_read_fd;
  return 0;
}

static int read_stats(coakka_v2_runtime_t* runtime,
                      coakka_v2_runtime_stats_t* out_stats) {
  memset(out_stats, 0, sizeof(*out_stats));
  out_stats->struct_size = sizeof(*out_stats);
  return coakka_v2_runtime_get_stats(runtime, out_stats) == COAKKA_V2_OK ? 0
                                                                        : 1;
}

static void capture_stats(const coakka_v2_runtime_stats_t* stats,
                          evidence_result_t* result) {
  result->runtime_generation = stats->applied_generation;
  result->route_count = stats->route_count;
  result->route_miss_count = stats->route_miss_count;
  result->deadletter_count = stats->deadletter_count;
  result->queue_rejected_count = stats->queue_rejected_count;
  result->ingress_queue_capacity = stats->ingress_queue_capacity;
  result->ingress_queue_high_watermark = stats->ingress_queue_high_watermark;
}

static int drain_terminal_reader(coakka_v2_frame_reader_t* reader,
                                 uint64_t* out_count) {
  for (;;) {
    uint8_t* frame = NULL;
    size_t frame_len = 0;
    const coakka_v2_status_t rc =
        coakka_v2_frame_read_try(reader, &frame, &frame_len);
    if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (rc != COAKKA_V2_OK) {
      return 1;
    }
    (void)frame_len;
    coakka_v2_frame_release(frame);
    ++(*out_count);
  }
}

static int build_echo_reply(const uint8_t* request,
                            size_t request_len,
                            const uint8_t* payload,
                            size_t payload_len,
                            evidence_handler_state_t* handler) {
  coakka_v2_client_raw_reply_spec_t reply_spec;

  memset(&reply_spec, 0, sizeof(reply_spec));
  reply_spec.struct_size = sizeof(reply_spec);
  reply_spec.request_buf = request;
  reply_spec.request_len = request_len;
  reply_spec.source = "samples.runtime.native.evidence.echo";
  reply_spec.payload = payload;
  reply_spec.payload_len = payload_len;

  return coakka_v2_client_build_raw_reply(&reply_spec,
                                          &handler->pending_reply,
                                          &handler->pending_reply_len) ==
                 COAKKA_V2_OK
             ? 0
             : 1;
}

static int flush_pending_reply(coakka_v2_runtime_t* runtime,
                               evidence_handler_state_t* handler,
                               evidence_result_t* result) {
  coakka_v2_status_t rc;
  if (handler->pending_reply == NULL) {
    return 0;
  }

  rc = coakka_v2_runtime_submit_envelope(runtime,
                                         handler->pending_reply,
                                         handler->pending_reply_len);
  if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
    ++result->reply_submit_backpressure;
    return 0;
  }
  if (rc != COAKKA_V2_OK) {
    return 1;
  }
  /*
   * The handler owns one pending reply until runtime admission succeeds. It
   * never sleeps or spins while holding that buffer; a blocked admission gives
   * control back to the pump so terminal channels can make progress.
   */
  coakka_v2_client_bytes_release(handler->pending_reply);
  handler->pending_reply = NULL;
  handler->pending_reply_len = 0;
  ++result->replies_submitted;
  return 0;
}

static int handle_delivered_requests(coakka_v2_runtime_t* runtime,
                                     coakka_v2_frame_reader_t* delivered_reader,
                                     const uint8_t* reply_payload,
                                     size_t reply_payload_len,
                                     evidence_handler_state_t* handler,
                                     evidence_result_t* result) {
  for (;;) {
    uint8_t* request = NULL;
    size_t request_len = 0;

    if (flush_pending_reply(runtime, handler, result)) {
      return 1;
    }
    if (handler->pending_reply != NULL) {
      return 0;
    }

    const coakka_v2_status_t rc =
        coakka_v2_frame_read_try(delivered_reader, &request, &request_len);
    if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (rc != COAKKA_V2_OK) {
      return 1;
    }

    ++result->handler_received;
    if (build_echo_reply(request,
                         request_len,
                         reply_payload,
                         reply_payload_len,
                         handler)) {
      coakka_v2_frame_release(request);
      return 1;
    }
    coakka_v2_frame_release(request);
  }
}

static int pump_runtime(coakka_v2_runtime_t* runtime,
                        coakka_v2_frame_reader_t* delivered_reader,
                        coakka_v2_frame_reader_t* response_reader,
                        coakka_v2_frame_reader_t* deadletter_reader,
                        const uint8_t* reply_payload,
                        size_t reply_payload_len,
                        evidence_handler_state_t* handler,
                        evidence_result_t* result) {
  return handle_delivered_requests(runtime,
                                   delivered_reader,
                                   reply_payload,
                                   reply_payload_len,
                                   handler,
                                   result) ||
         drain_terminal_reader(response_reader, &result->completed) ||
         drain_terminal_reader(deadletter_reader, &result->rejected);
}

static void fill_payload(uint8_t* payload, size_t payload_len) {
  size_t i;
  for (i = 0; i < payload_len; ++i) {
    payload[i] = (uint8_t)('a' + (i % 26));
  }
}

static int build_and_send_request(coakka_v2_runtime_t* runtime,
                                  int request_channel,
                                  int use_request_channel,
                                  uint64_t index,
                                  const uint8_t* payload,
                                  size_t payload_len) {
  char message_id[96];
  coakka_v2_client_raw_request_spec_t request_spec;
  uint8_t* request = NULL;
  size_t request_len = 0;
  coakka_v2_status_t rc;

  snprintf(message_id, sizeof(message_id), "native-evidence-%" PRIu64, index);
  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = message_id;
  request_spec.source = "native-evidence-client";
  request_spec.target = EVIDENCE_TARGET_NAME;
  request_spec.reply_to = "native-evidence-client/replies";
  request_spec.payload = payload;
  request_spec.payload_len = payload_len;
  request_spec.timeout_ms = 1000;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;

  rc = coakka_v2_client_build_raw_request(&request_spec, &request, &request_len);
  if (rc != COAKKA_V2_OK) {
    return 1;
  }
  rc = use_request_channel
           ? coakka_v2_frame_write(request_channel, request, request_len)
           : coakka_v2_runtime_submit_envelope(runtime, request, request_len);
  coakka_v2_client_bytes_release(request);
  return rc == COAKKA_V2_OK ? 0 : 1;
}

static int should_continue(const evidence_config_t* config,
                           uint64_t start_ms,
                           uint64_t attempted) {
  if (config->request_limit != 0 && attempted >= config->request_limit) {
    return 0;
  }
  if (config->duration_ms != 0 &&
      monotonic_ms() - start_ms >= config->duration_ms) {
    return 0;
  }
  return 1;
}

static uint64_t in_flight_count(const evidence_result_t* result) {
  return result->submitted - result->completed - result->rejected;
}

static int wait_for_channel_activity(const evidence_channels_t* channels,
                                     int timeout_ms) {
  const int readable_channels[] = {
      channels->delivered_request,
      channels->response,
      channels->deadletter,
  };
  return evidence_platform_wait_readable(readable_channels,
                                         3u,
                                         (unsigned int)timeout_ms);
}

static int wait_until_drained(coakka_v2_runtime_t* runtime,
                              const evidence_channels_t* channels,
                              coakka_v2_frame_reader_t* delivered_reader,
                              coakka_v2_frame_reader_t* response_reader,
                              coakka_v2_frame_reader_t* deadletter_reader,
                              const uint8_t* reply_payload,
                              size_t reply_payload_len,
                              evidence_handler_state_t* handler,
                              evidence_result_t* result,
                              const char** out_error) {
  coakka_v2_runtime_stats_t stats;
  const uint64_t deadline_ms =
      monotonic_ms() + EVIDENCE_FINAL_DRAIN_TIMEOUT_MS;

  while (monotonic_ms() < deadline_ms) {
    if (pump_runtime(runtime,
                     delivered_reader,
                     response_reader,
                     deadletter_reader,
                     reply_payload,
                     reply_payload_len,
                     handler,
                     result) ||
        read_stats(runtime, &stats)) {
      *out_error = "failed to drain runtime evidence";
      return 1;
    }
    if (result->completed + result->rejected >= result->submitted) {
      capture_stats(&stats, result);
      return 0;
    }
    /*
     * Polling the owned result channels avoids injecting an arbitrary sleep
     * cadence into the measured final-drain window.
     */
    if (wait_for_channel_activity(channels, EVIDENCE_ACTIVITY_WAIT_MS)) {
      *out_error = "failed while waiting for terminal channel activity";
      return 1;
    }
  }

  if (read_stats(runtime, &stats)) {
    *out_error = "failed to read final runtime stats";
    return 1;
  }
  capture_stats(&stats, result);
  *out_error = "runtime did not drain before timeout";
  return 1;
}

static int validate_result(const evidence_config_t* config,
                           const evidence_result_t* result,
                           const char** out_error) {
  if (result->submitted != result->attempted ||
      result->completed + result->rejected != result->submitted) {
    *out_error = "submitted requests did not reach one terminal outcome";
    return 1;
  }
  if (result->runtime_generation != EVIDENCE_ROUTE_GENERATION ||
      result->route_count != 1u ||
      result->route_miss_count != 0u) {
    *out_error = "target route invariants did not hold";
    return 1;
  }
  if (result->handler_received != result->replies_submitted ||
      result->replies_submitted != result->completed) {
    *out_error = "local request/reply path did not complete consistently";
    return 1;
  }
  if (config->mode == EVIDENCE_MODE_PRESSURE) {
    if (result->rejected == 0u ||
        result->deadletter_count != result->rejected ||
        result->queue_rejected_count !=
            result->rejected + result->reply_submit_backpressure ||
        result->ingress_queue_capacity != config->queue_capacity ||
        result->ingress_queue_high_watermark == 0u ||
        result->ingress_queue_high_watermark >
            result->ingress_queue_capacity) {
      *out_error = "pressure mode did not prove bounded queue rejection";
      return 1;
    }
  } else if (result->rejected != 0u ||
             result->deadletter_count != 0u ||
             result->queue_rejected_count != 0u ||
             result->completed != result->submitted) {
    *out_error = "non-pressure mode observed an unexpected terminal rejection";
    return 1;
  }
  return 0;
}

int evidence_run(const evidence_config_t* config,
                 coakka_v2_runtime_info_t* runtime_info,
                 evidence_result_t* result,
                 const char** out_error) {
  coakka_v2_runtime_t* runtime = NULL;
  evidence_channels_t channels;
  coakka_v2_frame_reader_t* delivered_reader = NULL;
  coakka_v2_frame_reader_t* response_reader = NULL;
  coakka_v2_frame_reader_t* deadletter_reader = NULL;
  evidence_handler_state_t handler = {0};
  uint8_t* payload = NULL;
  uint64_t start_ns;
  uint64_t submission_end_ns;
  uint64_t end_ns;
  uint64_t last_drain_ms;
  size_t reader_max_frame_len;
  uint64_t pump_interval;
  int use_request_channel;
  int status = 1;

  init_channels(&channels);
  memset(result, 0, sizeof(*result));
  memset(runtime_info, 0, sizeof(*runtime_info));
  runtime_info->struct_size = sizeof(*runtime_info);
  if (coakka_v2_runtime_get_info(runtime_info) != COAKKA_V2_OK) {
    *out_error = "runtime_get_info failed";
    return 1;
  }

  payload = (uint8_t*)malloc(config->payload_bytes);
  if (payload == NULL) {
    *out_error = "payload allocation failed";
    return 1;
  }
  fill_payload(payload, config->payload_bytes);

  const coakka_v2_runtime_config_t runtime_config = {
      .system_name = "runtime-v2-native-evidence",
      .node_id = "runtime-v2-native-evidence-node",
      .strict_no_drop = 1,
      .queue_capacity = (int)config->queue_capacity,
  };
  runtime = coakka_v2_runtime_create(&runtime_config);
  if (runtime == NULL) {
    *out_error = "runtime_create failed";
    goto cleanup;
  }
  if (open_runtime_channels(runtime, &channels)) {
    *out_error = "open runtime channels failed";
    goto cleanup;
  }

  const coakka_v2_endpoint_t endpoint = {
      .host = "127.0.0.1",
      .port = (uint16_t)(9041u + (evidence_platform_process_id() % 1000u)),
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = EVIDENCE_TARGET_NAME,
      .strategy = COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER,
      .route_key_hint = NULL,
      .flags = COAKKA_V2_ROUTE_FLAG_NONE,
      .endpoints = &endpoint,
      .endpoint_count = 1,
  };
  const coakka_v2_control_snapshot_t snapshot = {
      .generation = EVIDENCE_ROUTE_GENERATION,
      .routes = &route,
      .route_count = 1,
  };

  if (coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot) !=
          COAKKA_V2_OK ||
      coakka_v2_runtime_start(runtime) != COAKKA_V2_OK) {
    *out_error = "runtime start or snapshot apply failed";
    goto cleanup;
  }

  reader_max_frame_len =
      config->payload_bytes + EVIDENCE_FRAME_OVERHEAD_RESERVE;
  if (reader_max_frame_len < EVIDENCE_MIN_READER_FRAME_BYTES) {
    reader_max_frame_len = EVIDENCE_MIN_READER_FRAME_BYTES;
  }
  delivered_reader =
      coakka_v2_frame_reader_create(channels.delivered_request,
                                    reader_max_frame_len);
  response_reader =
      coakka_v2_frame_reader_create(channels.response, reader_max_frame_len);
  deadletter_reader =
      coakka_v2_frame_reader_create(channels.deadletter, reader_max_frame_len);
  if (delivered_reader == NULL ||
      response_reader == NULL ||
      deadletter_reader == NULL) {
    *out_error = "frame_reader_create failed";
    goto cleanup;
  }

  /*
   * Runtime setup is intentionally outside the measurement. The clock starts
   * immediately before the first request envelope is built.
   */
  start_ns = monotonic_ns();
  last_drain_ms = start_ns / 1000000u;
  use_request_channel = config->mode == EVIDENCE_MODE_PRESSURE;
  /*
   * The framed pressure path uses a small burst so host channels are drained
   * before platform pipe capacity becomes an accidental workload limit.
   */
  pump_interval = use_request_channel
                      ? EVIDENCE_REQUEST_CHANNEL_PUMP_INTERVAL
                      : EVIDENCE_NATIVE_SUBMIT_PUMP_INTERVAL;
  while (should_continue(config,
                         start_ns / 1000000u,
                         result->attempted)) {
    while (config->max_in_flight != 0 &&
           in_flight_count(result) >= config->max_in_flight) {
      const uint64_t terminal_before =
          result->completed + result->rejected;
      if (pump_runtime(runtime,
                       delivered_reader,
                       response_reader,
                       deadletter_reader,
                       payload,
                       config->payload_bytes,
                       &handler,
                       result)) {
        *out_error = "failed to drain in-flight evidence";
        goto cleanup;
      }
      if (result->completed + result->rejected == terminal_before &&
          wait_for_channel_activity(&channels, EVIDENCE_ACTIVITY_WAIT_MS)) {
        *out_error = "failed while waiting for in-flight channel activity";
        goto cleanup;
      }
    }

    ++result->attempted;
    if (build_and_send_request(runtime,
                               channels.request,
                               use_request_channel,
                               result->attempted,
                               payload,
                               config->payload_bytes)) {
      *out_error = use_request_channel
                       ? "request build or channel write failed"
                       : "request build or native submit failed";
      goto cleanup;
    }
    ++result->submitted;

    if ((result->submitted % pump_interval) == 0u ||
        monotonic_ms() - last_drain_ms >= EVIDENCE_ACTIVITY_WAIT_MS) {
      if (pump_runtime(runtime,
                       delivered_reader,
                       response_reader,
                       deadletter_reader,
                       payload,
                       config->payload_bytes,
                       &handler,
                       result)) {
        *out_error = "failed to drain intermediate evidence";
        goto cleanup;
      }
      last_drain_ms = monotonic_ms();
    }
  }

  submission_end_ns = monotonic_ns();
  if (wait_until_drained(runtime,
                         &channels,
                         delivered_reader,
                         response_reader,
                         deadletter_reader,
                         payload,
                         config->payload_bytes,
                         &handler,
                         result,
                         out_error)) {
    goto cleanup;
  }
  end_ns = monotonic_ns();
  result->submission_window_ns = submission_end_ns - start_ns;
  result->final_drain_ns = end_ns - submission_end_ns;
  result->total_elapsed_ns = end_ns - start_ns;

  if (validate_result(config, result, out_error)) {
    goto cleanup;
  }
  status = 0;

cleanup:
  if (runtime != NULL) {
    coakka_v2_runtime_stop(runtime);
  }
  coakka_v2_frame_reader_destroy(delivered_reader);
  coakka_v2_frame_reader_destroy(response_reader);
  coakka_v2_frame_reader_destroy(deadletter_reader);
  if (runtime != NULL) {
    coakka_v2_runtime_destroy(runtime);
  }
  close_channels(&channels);
  coakka_v2_client_bytes_release(handler.pending_reply);
  free(payload);
  return status;
}
