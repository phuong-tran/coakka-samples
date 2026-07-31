#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/transport.h"
#include "coakka/v2/utils.h"

#include <ctype.h>
#include <errno.h>
#include <inttypes.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

enum {
  k_default_smoke_requests = 128,
  k_default_pressure_requests = 512,
  k_default_stress_requests = 2000,
  k_default_soak_seconds = 10,
  k_default_payload_bytes = 64 * 1024,
  k_default_pressure_payload_bytes = 4 * 1024,
  k_max_pressure_payload_bytes = 16 * 1024,
  k_default_queue_capacity = 1024,
  k_pressure_queue_capacity = 2,
  k_max_payload_bytes = 3 * 1024 * 1024,
  k_max_request_limit = 500 * 1000,
};

typedef enum evidence_mode_t {
  MODE_SMOKE,
  MODE_PRESSURE,
  MODE_STRESS,
  MODE_SOAK,
} evidence_mode_t;

typedef struct evidence_config_t {
  evidence_mode_t mode;
  size_t payload_bytes;
  uint64_t request_limit;
  uint64_t duration_ms;
  size_t queue_capacity;
  uint64_t max_in_flight;
} evidence_config_t;

typedef struct evidence_result_t {
  uint64_t attempted;
  uint64_t submitted;
  uint64_t handler_received;
  uint64_t replies_submitted;
  uint64_t reply_submit_backpressure;
  uint64_t completed;
  uint64_t rejected;
  uint64_t submission_window_ns;
  uint64_t final_drain_ns;
  uint64_t total_elapsed_ns;
  uint64_t runtime_generation;
  uint64_t route_miss_count;
  uint64_t deadletter_count;
  uint64_t queue_rejected_count;
  size_t route_count;
  size_t ingress_queue_capacity;
  size_t ingress_queue_high_watermark;
} evidence_result_t;

typedef struct evidence_channels_t {
  int request_channel;
  int response_channel;
  int deadletter_channel;
  int control_channel;
  int monitor_channel;
  int delivered_request_channel;
} evidence_channels_t;

static const char* mode_name(evidence_mode_t mode) {
  switch (mode) {
    case MODE_SMOKE:
      return "smoke";
    case MODE_PRESSURE:
      return "pressure";
    case MODE_STRESS:
      return "stress";
    case MODE_SOAK:
      return "soak";
  }
  return "unknown";
}

static const char* submission_path_name(evidence_mode_t mode) {
  return mode == MODE_PRESSURE ? "request-channel" : "native-submit";
}

static const char* operating_system_name(void) {
#if defined(__APPLE__)
  return "macos";
#elif defined(__linux__)
  return "linux";
#elif defined(_WIN32)
  return "windows";
#else
  return "unknown";
#endif
}

static const char* architecture_name(void) {
#if defined(__aarch64__) || defined(_M_ARM64)
  return "aarch64";
#elif defined(__x86_64__) || defined(_M_X64)
  return "x86_64";
#elif defined(__arm__) || defined(_M_ARM)
  return "arm";
#else
  return "unknown";
#endif
}

static const char* build_profile_name(void) {
#ifdef COAKKA_EVIDENCE_BUILD_PROFILE
  return COAKKA_EVIDENCE_BUILD_PROFILE;
#else
  return "unknown";
#endif
}

static const char* compiler_name(void) {
#ifdef COAKKA_EVIDENCE_COMPILER
  return COAKKA_EVIDENCE_COMPILER;
#else
  return "unknown";
#endif
}

static const char* execution_path_name(void) {
  const char* value = getenv("COAKKA_EVIDENCE_EXECUTION_PATH");
  return value != NULL && *value != '\0' ? value : "direct";
}

static long logical_cpu_count(void) {
#ifdef _SC_NPROCESSORS_ONLN
  long count = sysconf(_SC_NPROCESSORS_ONLN);
  return count > 0 ? count : 0;
#else
  return 0;
#endif
}

static int parse_mode(const char* text, evidence_mode_t* out_mode) {
  if (strcmp(text, "smoke") == 0) {
    *out_mode = MODE_SMOKE;
    return 0;
  }
  if (strcmp(text, "pressure") == 0) {
    *out_mode = MODE_PRESSURE;
    return 0;
  }
  if (strcmp(text, "stress") == 0) {
    *out_mode = MODE_STRESS;
    return 0;
  }
  if (strcmp(text, "soak") == 0) {
    *out_mode = MODE_SOAK;
    return 0;
  }
  return 1;
}

static uint64_t now_ns(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return ((uint64_t)ts.tv_sec * 1000000000u) + (uint64_t)ts.tv_nsec;
}

static uint64_t now_ms(void) {
  return now_ns() / 1000000u;
}

static int parse_u64(const char* text, uint64_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  if (text == NULL || *text == '\0') {
    return 1;
  }
  parsed = strtoull(text, &end, 10);
  if (end == text || *end != '\0') {
    return 1;
  }
  *out_value = (uint64_t)parsed;
  return 0;
}

static int parse_size(const char* text, size_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  unsigned long long multiplier = 1;
  if (text == NULL || *text == '\0') {
    return 1;
  }
  parsed = strtoull(text, &end, 10);
  if (end == text) {
    return 1;
  }
  if (*end != '\0') {
    char suffix = (char)tolower((unsigned char)*end);
    if (end[1] != '\0') {
      return 1;
    }
    if (suffix == 'k') {
      multiplier = 1024ull;
    } else if (suffix == 'm') {
      multiplier = 1024ull * 1024ull;
    } else {
      return 1;
    }
  }
  if (parsed > (unsigned long long)SIZE_MAX / multiplier) {
    return 1;
  }
  *out_value = (size_t)(parsed * multiplier);
  return 0;
}

static int parse_duration_ms(const char* text, uint64_t* out_value) {
  char* end = NULL;
  unsigned long long parsed;
  unsigned long long multiplier = 1000;
  if (text == NULL || *text == '\0') {
    return 1;
  }
  parsed = strtoull(text, &end, 10);
  if (end == text) {
    return 1;
  }
  if (*end != '\0') {
    if (strcmp(end, "ms") == 0) {
      multiplier = 1;
    } else if (strcmp(end, "s") == 0) {
      multiplier = 1000;
    } else if (strcmp(end, "m") == 0) {
      multiplier = 60 * 1000;
    } else {
      return 1;
    }
  }
  *out_value = (uint64_t)parsed * (uint64_t)multiplier;
  return 0;
}

static int require_value(int index, int argc, char** argv, const char** out_value) {
  if (index + 1 >= argc) {
    return 1;
  }
  *out_value = argv[index + 1];
  return 0;
}

static void init_config(evidence_config_t* config) {
  memset(config, 0, sizeof(*config));
  config->mode = MODE_SMOKE;
  config->payload_bytes = k_default_payload_bytes;
  config->request_limit = k_default_smoke_requests;
  config->duration_ms = 0;
  config->queue_capacity = k_default_queue_capacity;
  config->max_in_flight = 64;
}

static int parse_args(int argc, char** argv, evidence_config_t* config, const char** out_error) {
  int index = 1;
  int payload_explicit = 0;
  init_config(config);

  if (index < argc && argv[index][0] != '-') {
    if (parse_mode(argv[index], &config->mode)) {
      *out_error = "unknown mode";
      return 1;
    }
    ++index;
  }

  switch (config->mode) {
    case MODE_SMOKE:
      config->request_limit = k_default_smoke_requests;
      config->queue_capacity = k_default_queue_capacity;
      break;
    case MODE_PRESSURE:
      config->request_limit = k_default_pressure_requests;
      config->payload_bytes = k_default_pressure_payload_bytes;
      config->queue_capacity = k_pressure_queue_capacity;
      config->max_in_flight = 0;
      break;
    case MODE_STRESS:
      config->request_limit = k_default_stress_requests;
      config->queue_capacity = k_default_queue_capacity;
      break;
    case MODE_SOAK:
      config->request_limit = 0;
      config->duration_ms = (uint64_t)k_default_soak_seconds * 1000u;
      config->queue_capacity = k_default_queue_capacity;
      break;
  }

  while (index < argc) {
    const char* value = NULL;
    if (strcmp(argv[index], "--payload") == 0) {
      if (require_value(index, argc, argv, &value) || parse_size(value, &config->payload_bytes)) {
        *out_error = "invalid --payload";
        return 1;
      }
      payload_explicit = 1;
      index += 2;
    } else if (strcmp(argv[index], "--requests") == 0) {
      if (require_value(index, argc, argv, &value) || parse_u64(value, &config->request_limit)) {
        *out_error = "invalid --requests";
        return 1;
      }
      index += 2;
    } else if (strcmp(argv[index], "--duration") == 0) {
      if (require_value(index, argc, argv, &value) || parse_duration_ms(value, &config->duration_ms)) {
        *out_error = "invalid --duration";
        return 1;
      }
      index += 2;
    } else if (strcmp(argv[index], "--queue-capacity") == 0) {
      if (require_value(index, argc, argv, &value) || parse_size(value, &config->queue_capacity)) {
        *out_error = "invalid --queue-capacity";
        return 1;
      }
      index += 2;
    } else if (strcmp(argv[index], "--max-in-flight") == 0) {
      if (require_value(index, argc, argv, &value) || parse_u64(value, &config->max_in_flight)) {
        *out_error = "invalid --max-in-flight";
        return 1;
      }
      index += 2;
    } else if (strcmp(argv[index], "--help") == 0 || strcmp(argv[index], "-h") == 0) {
      *out_error = "help requested";
      return 2;
    } else {
      *out_error = "unknown option";
      return 1;
    }
  }

  if (config->payload_bytes == 0 || config->payload_bytes > (size_t)k_max_payload_bytes) {
    *out_error = "payload must be between 1 byte and 3M";
    return 1;
  }
  if (config->mode == MODE_PRESSURE &&
      payload_explicit &&
      config->payload_bytes > (size_t)k_max_pressure_payload_bytes) {
    *out_error = "pressure mode payload must be 16K or smaller; use smoke, stress, or soak for large-payload evidence";
    return 1;
  }
  if (config->queue_capacity == 0) {
    *out_error = "queue capacity must be greater than zero";
    return 1;
  }
  if (config->mode == MODE_SOAK && config->duration_ms == 0 && config->request_limit == 0) {
    *out_error = "soak requires --duration or --requests";
    return 1;
  }
  if (config->mode != MODE_SOAK && config->request_limit == 0) {
    *out_error = "mode requires at least one request";
    return 1;
  }
  if (config->request_limit > (uint64_t)k_max_request_limit) {
    *out_error = "request limit must be 500K or smaller";
    return 1;
  }
  return 0;
}

static void close_if_open(int* channel) {
  if (channel != NULL && *channel >= 0) {
    close(*channel);
    *channel = -1;
  }
}

static void close_host_handles(coakka_v2_host_handles_t* handles) {
  if (handles == NULL) {
    return;
  }
  close_if_open(&handles->request_write_fd);
  close_if_open(&handles->response_read_fd);
  close_if_open(&handles->deadletter_read_fd);
  close_if_open(&handles->control_write_fd);
  close_if_open(&handles->monitor_read_fd);
  close_if_open(&handles->delivered_request_read_fd);
}

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

static void init_evidence_channels(evidence_channels_t* channels) {
  channels->request_channel = -1;
  channels->response_channel = -1;
  channels->deadletter_channel = -1;
  channels->control_channel = -1;
  channels->monitor_channel = -1;
  channels->delivered_request_channel = -1;
}

static void close_evidence_channels(evidence_channels_t* channels) {
  if (channels == NULL) {
    return;
  }
  close_if_open(&channels->request_channel);
  close_if_open(&channels->response_channel);
  close_if_open(&channels->deadletter_channel);
  close_if_open(&channels->control_channel);
  close_if_open(&channels->monitor_channel);
  close_if_open(&channels->delivered_request_channel);
}

static int open_runtime_channels(coakka_v2_runtime_t* runtime,
                                 evidence_channels_t* channels) {
  coakka_v2_host_handles_t handles;
  init_host_handles(&handles);
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR |
                  COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
  if (coakka_v2_runtime_get_host_handles(runtime, &handles) != COAKKA_V2_OK) {
    close_host_handles(&handles);
    return 1;
  }

  channels->request_channel = handles.request_write_fd;
  channels->response_channel = handles.response_read_fd;
  channels->deadletter_channel = handles.deadletter_read_fd;
  channels->control_channel = handles.control_write_fd;
  channels->monitor_channel = handles.monitor_read_fd;
  channels->delivered_request_channel = handles.delivered_request_read_fd;
  return 0;
}

static int read_stats(coakka_v2_runtime_t* runtime, coakka_v2_runtime_stats_t* out_stats) {
  coakka_v2_status_t rc;
  memset(out_stats, 0, sizeof(*out_stats));
  out_stats->struct_size = sizeof(*out_stats);
  rc = coakka_v2_runtime_get_stats(runtime, out_stats);
  return rc == COAKKA_V2_OK ? 0 : 1;
}

static int drain_reader(coakka_v2_frame_reader_t* reader, uint64_t* out_count) {
  for (;;) {
    uint8_t* buf = NULL;
    size_t len = 0;
    coakka_v2_status_t rc = coakka_v2_frame_read_try(reader, &buf, &len);
    if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (rc != COAKKA_V2_OK) {
      return 1;
    }
    (void)len;
    coakka_v2_frame_release(buf);
    ++(*out_count);
  }
}

static int handle_delivered_requests(coakka_v2_runtime_t* runtime,
                                     coakka_v2_frame_reader_t* delivered_reader,
                                     const uint8_t* reply_payload,
                                     size_t reply_payload_len,
                                     evidence_result_t* result) {
  for (;;) {
    uint8_t* delivered_buf = NULL;
    size_t delivered_len = 0;
    uint8_t* reply_buf = NULL;
    size_t reply_len = 0;
    coakka_v2_client_raw_reply_spec_t reply_spec;
    coakka_v2_status_t rc =
        coakka_v2_frame_read_try(delivered_reader, &delivered_buf, &delivered_len);
    if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (rc != COAKKA_V2_OK) {
      return 1;
    }

    ++result->handler_received;
    memset(&reply_spec, 0, sizeof(reply_spec));
    reply_spec.struct_size = sizeof(reply_spec);
    reply_spec.request_buf = delivered_buf;
    reply_spec.request_len = delivered_len;
    reply_spec.source = "samples.runtime.native.evidence.echo";
    reply_spec.payload = reply_payload;
    reply_spec.payload_len = reply_payload_len;

    rc = coakka_v2_client_build_raw_reply(&reply_spec, &reply_buf, &reply_len);
    if (rc == COAKKA_V2_OK) {
      int retry;
      for (retry = 0; retry < 5000; ++retry) {
        rc = coakka_v2_runtime_submit_envelope(runtime, reply_buf, reply_len);
        if (rc == COAKKA_V2_OK) {
          break;
        }
        if (rc != COAKKA_V2_ERR_WOULD_BLOCK) {
          break;
        }
        ++result->reply_submit_backpressure;
        usleep(1000);
      }
    }
    coakka_v2_client_bytes_release(reply_buf);
    coakka_v2_frame_release(delivered_buf);
    if (rc != COAKKA_V2_OK) {
      return 1;
    }
    ++result->replies_submitted;
  }
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
  uint8_t* request_frame = NULL;
  size_t request_frame_len = 0;
  coakka_v2_status_t rc;

  snprintf(message_id, sizeof(message_id), "native-evidence-%" PRIu64, index);

  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = message_id;
  request_spec.source = "native-evidence-client";
  request_spec.target = "samples.runtime.native.evidence.local";
  request_spec.reply_to = "native-evidence-client/replies";
  request_spec.payload = payload;
  request_spec.payload_len = payload_len;
  request_spec.timeout_ms = 1000;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 0;

  rc = coakka_v2_client_build_raw_request(&request_spec, &request_frame, &request_frame_len);
  if (rc != COAKKA_V2_OK) {
    return 1;
  }

  if (use_request_channel) {
    rc = coakka_v2_frame_write(request_channel, request_frame, request_frame_len);
  } else {
    rc = coakka_v2_runtime_submit_envelope(runtime, request_frame, request_frame_len);
  }
  coakka_v2_client_bytes_release(request_frame);
  return rc == COAKKA_V2_OK ? 0 : 1;
}

static int should_continue(const evidence_config_t* config, uint64_t start_ms, uint64_t attempted) {
  if (config->request_limit != 0 && attempted >= config->request_limit) {
    return 0;
  }
  if (config->duration_ms != 0 && now_ms() - start_ms >= config->duration_ms) {
    return 0;
  }
  return 1;
}

static uint64_t in_flight_count(const evidence_result_t* result) {
  return result->submitted - result->completed - result->rejected;
}

static int pump_runtime(coakka_v2_runtime_t* runtime,
                        coakka_v2_frame_reader_t* delivered_reader,
                        coakka_v2_frame_reader_t* response_reader,
                        coakka_v2_frame_reader_t* deadletter_reader,
                        const uint8_t* reply_payload,
                        size_t reply_payload_len,
                        evidence_result_t* result) {
  return handle_delivered_requests(runtime,
                                   delivered_reader,
                                   reply_payload,
                                   reply_payload_len,
                                   result) ||
         drain_reader(response_reader, &result->completed) ||
         drain_reader(deadletter_reader, &result->rejected);
}

static int wait_for_channel_activity(const evidence_channels_t* channels,
                                     int timeout_ms) {
  struct pollfd poll_channels[3];
  int rc;
  memset(poll_channels, 0, sizeof(poll_channels));
  poll_channels[0].fd = channels->delivered_request_channel;
  poll_channels[0].events = POLLIN;
  poll_channels[1].fd = channels->response_channel;
  poll_channels[1].events = POLLIN;
  poll_channels[2].fd = channels->deadletter_channel;
  poll_channels[2].events = POLLIN;
  do {
    rc = poll(poll_channels, 3u, timeout_ms);
  } while (rc < 0 && errno == EINTR);
  return rc < 0 ? 1 : 0;
}

static int wait_until_drained(coakka_v2_runtime_t* runtime,
                              const evidence_channels_t* channels,
                              coakka_v2_frame_reader_t* delivered_reader,
                              coakka_v2_frame_reader_t* response_reader,
                              coakka_v2_frame_reader_t* deadletter_reader,
                              const uint8_t* reply_payload,
                              size_t reply_payload_len,
                              evidence_result_t* result,
                              const char** out_error) {
  coakka_v2_runtime_stats_t stats;
  int spin;
  for (spin = 0; spin < 500; ++spin) {
    if (pump_runtime(runtime,
                     delivered_reader,
                     response_reader,
                     deadletter_reader,
                     reply_payload,
                     reply_payload_len,
                     result) ||
        read_stats(runtime, &stats)) {
      *out_error = "failed to drain runtime evidence";
      return 1;
    }
    if (result->completed + result->rejected >= result->submitted) {
      result->runtime_generation = stats.applied_generation;
      result->route_count = stats.route_count;
      result->route_miss_count = stats.route_miss_count;
      result->deadletter_count = stats.deadletter_count;
      result->queue_rejected_count = stats.queue_rejected_count;
      result->ingress_queue_capacity = stats.ingress_queue_capacity;
      result->ingress_queue_high_watermark = stats.ingress_queue_high_watermark;
      return 0;
    }
    if (wait_for_channel_activity(channels, 10)) {
      *out_error = "failed while waiting for terminal channel activity";
      return 1;
    }
  }
  if (read_stats(runtime, &stats)) {
    *out_error = "failed to read final runtime stats";
    return 1;
  }
  result->runtime_generation = stats.applied_generation;
  result->route_count = stats.route_count;
  result->route_miss_count = stats.route_miss_count;
  result->deadletter_count = stats.deadletter_count;
  result->queue_rejected_count = stats.queue_rejected_count;
  result->ingress_queue_capacity = stats.ingress_queue_capacity;
  result->ingress_queue_high_watermark = stats.ingress_queue_high_watermark;
  *out_error = "runtime did not drain before timeout";
  return 1;
}

static int run_evidence(const evidence_config_t* config,
                        coakka_v2_runtime_info_t* runtime_info,
                        evidence_result_t* result,
                        const char** out_error) {
  coakka_v2_runtime_t* runtime = NULL;
  evidence_channels_t channels;
  coakka_v2_frame_reader_t* delivered_reader = NULL;
  coakka_v2_frame_reader_t* response_reader = NULL;
  coakka_v2_frame_reader_t* deadletter_reader = NULL;
  uint8_t* payload = NULL;
  uint64_t start_ns;
  uint64_t submission_end_ns;
  uint64_t end_ns;
  uint64_t last_drain_ms;
  int status = 1;
  size_t reader_max_frame_len;
  int use_request_channel;

  init_evidence_channels(&channels);
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
      .queue_capacity = config->queue_capacity,
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
      .port = (uint16_t)(9041u + ((unsigned int)getpid() % 1000u)),
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = "samples.runtime.native.evidence.local",
      .strategy = COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER,
      .route_key_hint = NULL,
      .flags = COAKKA_V2_ROUTE_FLAG_NONE,
      .endpoints = &endpoint,
      .endpoint_count = 1,
  };
  const coakka_v2_control_snapshot_t snapshot = {
      .generation = 1,
      .routes = &route,
      .route_count = 1,
  };

  if (coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot) != COAKKA_V2_OK ||
      coakka_v2_runtime_start(runtime) != COAKKA_V2_OK) {
    *out_error = "runtime start or snapshot apply failed";
    goto cleanup;
  }

  reader_max_frame_len = config->payload_bytes + (256u * 1024u);
  if (reader_max_frame_len < 1024u * 1024u) {
    reader_max_frame_len = 1024u * 1024u;
  }
  delivered_reader =
      coakka_v2_frame_reader_create(channels.delivered_request_channel, reader_max_frame_len);
  response_reader = coakka_v2_frame_reader_create(channels.response_channel, reader_max_frame_len);
  deadletter_reader = coakka_v2_frame_reader_create(channels.deadletter_channel, reader_max_frame_len);
  if (delivered_reader == NULL || response_reader == NULL || deadletter_reader == NULL) {
    *out_error = "frame_reader_create failed";
    goto cleanup;
  }

  start_ns = now_ns();
  last_drain_ms = start_ns / 1000000u;
  use_request_channel = config->mode == MODE_PRESSURE ? 1 : 0;
  while (should_continue(config, start_ns / 1000000u, result->attempted)) {
    while (config->max_in_flight != 0 && in_flight_count(result) >= config->max_in_flight) {
      uint64_t before = result->completed + result->rejected;
      if (pump_runtime(runtime,
                       delivered_reader,
                       response_reader,
                       deadletter_reader,
                       payload,
                       config->payload_bytes,
                       result)) {
        *out_error = "failed to drain in-flight evidence";
        goto cleanup;
      }
      if (result->completed + result->rejected == before) {
        if (wait_for_channel_activity(&channels, 10)) {
          *out_error = "failed while waiting for in-flight channel activity";
          goto cleanup;
        }
      }
    }

    ++result->attempted;
    if (build_and_send_request(runtime,
                               channels.request_channel,
                               use_request_channel,
                               result->attempted,
                               payload,
                               config->payload_bytes)) {
      *out_error = use_request_channel ? "request build or channel write failed"
                                       : "request build or native submit failed";
      goto cleanup;
    }
    ++result->submitted;

    if ((result->submitted % 16u) == 0u || now_ms() - last_drain_ms >= 10u) {
      if (pump_runtime(runtime,
                       delivered_reader,
                       response_reader,
                       deadletter_reader,
                       payload,
                       config->payload_bytes,
                       result)) {
        *out_error = "failed to drain intermediate evidence";
        goto cleanup;
      }
      last_drain_ms = now_ms();
    }
  }

  submission_end_ns = now_ns();
  if (wait_until_drained(runtime,
                         &channels,
                         delivered_reader,
                         response_reader,
                         deadletter_reader,
                         payload,
                         config->payload_bytes,
                         result,
                         out_error)) {
    goto cleanup;
  }
  end_ns = now_ns();
  result->submission_window_ns = submission_end_ns - start_ns;
  result->final_drain_ns = end_ns - submission_end_ns;
  result->total_elapsed_ns = end_ns - start_ns;

  if (result->submitted != result->attempted ||
      result->completed + result->rejected != result->submitted) {
    *out_error = "submitted requests did not reach one terminal outcome";
    goto cleanup;
  }
  if (result->runtime_generation != 1u ||
      result->route_count != 1u ||
      result->route_miss_count != 0u) {
    *out_error = "target route invariants did not hold";
    goto cleanup;
  }
  if (result->handler_received != result->replies_submitted ||
      result->replies_submitted != result->completed) {
    *out_error = "local request/reply path did not complete consistently";
    goto cleanup;
  }
  if (config->mode == MODE_PRESSURE) {
    if (result->rejected == 0u ||
        result->deadletter_count != result->rejected ||
        result->queue_rejected_count !=
            result->rejected + result->reply_submit_backpressure ||
        result->ingress_queue_capacity != config->queue_capacity ||
        result->ingress_queue_high_watermark == 0u ||
        result->ingress_queue_high_watermark > result->ingress_queue_capacity) {
      *out_error = "pressure mode did not prove bounded queue rejection";
      goto cleanup;
    }
  } else if (result->rejected != 0u ||
             result->deadletter_count != 0u ||
             result->queue_rejected_count != 0u ||
             result->completed != result->submitted) {
    *out_error = "non-pressure mode observed an unexpected terminal rejection";
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
  close_evidence_channels(&channels);
  free(payload);
  return status;
}

static void print_json_string(const char* text) {
  const unsigned char* p = (const unsigned char*)text;
  putchar('"');
  while (*p != '\0') {
    if (*p == '"' || *p == '\\') {
      putchar('\\');
      putchar((int)*p);
    } else if (*p == '\n') {
      fputs("\\n", stdout);
    } else if (*p == '\r') {
      fputs("\\r", stdout);
    } else if (*p == '\t') {
      fputs("\\t", stdout);
    } else if (*p < 0x20u) {
      printf("\\u%04x", (unsigned int)*p);
    } else {
      putchar((int)*p);
    }
    ++p;
  }
  putchar('"');
}

static void print_help_json(void) {
  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.evidence.help.v1\",\n");
  printf("  \"usage\": \"bash run.sh runtime/evidence/native [smoke|pressure|stress|soak] [--payload 64K] [--requests 128] [--duration 10s] [--queue-capacity 1024] [--max-in-flight 64]\",\n");
  printf("  \"payloadPresets\": [\"64K\", \"128K\", \"256K\", \"512K\", \"1M\", \"2M\", \"3M\"],\n");
  printf("  \"pressurePayloadLimit\": \"16K\",\n");
  printf("  \"requestLimitMax\": 500000\n");
  printf("}\n");
}

static void print_result_json(const evidence_config_t* config,
                              const coakka_v2_runtime_info_t* info,
                              const evidence_result_t* result,
                              const char* status,
                              const char* error) {
  double submission_window_ms = (double)result->submission_window_ns / 1000000.0;
  double final_drain_ms = (double)result->final_drain_ns / 1000000.0;
  double total_elapsed_ms = (double)result->total_elapsed_ns / 1000000.0;
  double completed_round_trips_per_second = 0.0;
  double terminal_outcomes_per_second = 0.0;
  double completed_payload_mib_per_second = 0.0;
  double completed_round_trip_payload_mib_per_second = 0.0;
  if (result->total_elapsed_ns > 0u) {
    const double total_seconds = (double)result->total_elapsed_ns / 1000000000.0;
    completed_round_trips_per_second = (double)result->completed / total_seconds;
    terminal_outcomes_per_second =
        (double)(result->completed + result->rejected) / total_seconds;
    completed_payload_mib_per_second =
        ((double)result->completed * (double)config->payload_bytes) /
        (1024.0 * 1024.0 * total_seconds);
    completed_round_trip_payload_mib_per_second =
        completed_payload_mib_per_second * 2.0;
  }

  printf("{\n");
  printf("  \"schema\": \"coakka.runtime.native.evidence.v1\",\n");
  printf("  \"status\": ");
  print_json_string(status);
  printf(",\n");
  printf("  \"mode\": ");
  print_json_string(mode_name(config->mode));
  printf(",\n");
  if (error != NULL) {
    printf("  \"error\": ");
    print_json_string(error);
    printf(",\n");
  }
  printf("  \"runtime\": {\n");
  printf("    \"abi\": %u,\n", info->abi_version);
  printf("    \"version\": ");
  print_json_string(info->runtime_version != NULL ? info->runtime_version : "");
  printf(",\n");
  printf("    \"git\": ");
  print_json_string(info->git_commit != NULL ? info->git_commit : "");
  printf("\n");
  printf("  },\n");
  printf("  \"environment\": {\n");
  printf("    \"os\": ");
  print_json_string(operating_system_name());
  printf(",\n");
  printf("    \"arch\": ");
  print_json_string(architecture_name());
  printf(",\n");
  printf("    \"logicalCpus\": %ld,\n", logical_cpu_count());
  printf("    \"buildProfile\": ");
  print_json_string(build_profile_name());
  printf(",\n");
  printf("    \"compiler\": ");
  print_json_string(compiler_name());
  printf(",\n");
  printf("    \"executionPath\": ");
  print_json_string(execution_path_name());
  printf("\n");
  printf("  },\n");
  printf("  \"config\": {\n");
  printf("    \"submissionPath\": ");
  print_json_string(submission_path_name(config->mode));
  printf(",\n");
  printf("    \"payloadBytes\": %zu,\n", config->payload_bytes);
  printf("    \"requestLimit\": %" PRIu64 ",\n", config->request_limit);
  printf("    \"durationMs\": %" PRIu64 ",\n", config->duration_ms);
  printf("    \"queueCapacity\": %zu,\n", config->queue_capacity);
  printf("    \"maxInFlight\": %" PRIu64 ",\n", config->max_in_flight);
  printf("    \"strictNoDrop\": true\n");
  printf("  },\n");
  printf("  \"target\": {\n");
  printf("    \"name\": \"samples.runtime.native.evidence.local\",\n");
  printf("    \"routeStrategy\": \"single-owner\",\n");
  printf("    \"endpointKind\": \"local\",\n");
  printf("    \"requestPath\": \"caller-build-request -> %s -> bounded-ingress -> route-snapshot(generation=1) -> target(samples.runtime.native.evidence.local) -> local-handler-handoff -> echo-handler\",\n",
         submission_path_name(config->mode));
  printf("    \"replyPath\": \"echo-handler -> build-reply -> native-submit -> terminal-response-routing -> caller-response-drain\"\n");
  printf("  },\n");
  printf("  \"result\": {\n");
  printf("    \"attempted\": %" PRIu64 ",\n", result->attempted);
  printf("    \"submitted\": %" PRIu64 ",\n", result->submitted);
  printf("    \"handlerReceived\": %" PRIu64 ",\n", result->handler_received);
  printf("    \"repliesSubmitted\": %" PRIu64 ",\n", result->replies_submitted);
  printf("    \"replySubmitBackpressure\": %" PRIu64 ",\n",
         result->reply_submit_backpressure);
  printf("    \"completed\": %" PRIu64 ",\n", result->completed);
  printf("    \"rejected\": %" PRIu64 ",\n", result->rejected);
  printf("    \"terminalOutcomes\": %" PRIu64 ",\n",
         result->completed + result->rejected);
  printf("    \"deadletters\": %" PRIu64 ",\n", result->deadletter_count);
  printf("    \"queueRejected\": %" PRIu64 ",\n", result->queue_rejected_count);
  printf("    \"routeMisses\": %" PRIu64 ",\n", result->route_miss_count);
  printf("    \"routeGeneration\": %" PRIu64 ",\n", result->runtime_generation);
  printf("    \"routeCount\": %zu,\n", result->route_count);
  printf("    \"ingressQueueCapacity\": %zu,\n", result->ingress_queue_capacity);
  printf("    \"ingressQueueHighWatermark\": %zu\n", result->ingress_queue_high_watermark);
  printf("  },\n");
  printf("  \"timing\": {\n");
  printf("    \"clock\": \"monotonic\",\n");
  printf("    \"startsBefore\": \"first request envelope build\",\n");
  printf("    \"endsAfter\": \"final response/deadletter drain\",\n");
  printf("    \"submissionWindowMs\": %.3f,\n", submission_window_ms);
  printf("    \"finalDrainMs\": %.3f,\n", final_drain_ms);
  printf("    \"totalElapsedMs\": %.3f\n", total_elapsed_ms);
  printf("  },\n");
  printf("  \"throughput\": {\n");
  printf("    \"completedRoundTripsPerSecond\": %.3f,\n",
         completed_round_trips_per_second);
  printf("    \"terminalOutcomesPerSecond\": %.3f,\n", terminal_outcomes_per_second);
  printf("    \"completedRequestPayloadMiBPerSecond\": %.3f,\n",
         completed_payload_mib_per_second);
  printf("    \"completedRoundTripPayloadMiBPerSecond\": %.3f,\n",
         completed_round_trip_payload_mib_per_second);
  printf("    \"scope\": \"local native public-ABI request/reply path on this machine\"\n");
  printf("  },\n");
  printf("  \"notes\": [\n");
  printf("    \"This is a repeatable local scenario, not a cross-machine benchmark or production SLO.\",\n");
  printf("    \"Completed throughput includes request build, runtime delivery, echo reply build, terminal response routing, and final drain.\",\n");
  printf("    \"The harness uses only the published native C ABI and emits no per-request output.\"\n");
  printf("  ]\n");
  printf("}\n");
}

int main(int argc, char** argv) {
  evidence_config_t config;
  evidence_result_t result;
  coakka_v2_runtime_info_t runtime_info;
  const char* error = NULL;
  int parse_status;
  int run_status;

  parse_status = parse_args(argc, argv, &config, &error);
  if (parse_status == 2) {
    print_help_json();
    return 0;
  }
  if (parse_status != 0) {
    memset(&runtime_info, 0, sizeof(runtime_info));
    memset(&result, 0, sizeof(result));
    print_result_json(&config, &runtime_info, &result, "fail", error);
    return 2;
  }

  run_status = run_evidence(&config, &runtime_info, &result, &error);
  print_result_json(&config, &runtime_info, &result, run_status == 0 ? "pass" : "fail", error);
  return run_status == 0 ? 0 : 1;
}
