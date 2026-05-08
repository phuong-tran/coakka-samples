#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/transport.h"
#include "coakka/v2/utils.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

enum {
  k_burst_count = 64,
  k_queue_capacity = 2,
};

static int require_ok(coakka_v2_status_t status, const char* step) {
  if (status == COAKKA_V2_OK) {
    return 0;
  }
  fprintf(stderr, "%s failed: %s\n", step, coakka_v2_status_name(status));
  return 1;
}

static void close_if_open(int* fd) {
  if (fd != NULL && *fd >= 0) {
    close(*fd);
    *fd = -1;
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

static int drain_reader(coakka_v2_frame_reader_t* reader, int* out_count) {
  for (;;) {
    uint8_t* buf = NULL;
    size_t len = 0;
    coakka_v2_status_t rc = coakka_v2_frame_read_try(reader, &buf, &len);
    if (rc == COAKKA_V2_ERR_WOULD_BLOCK) {
      return 0;
    }
    if (rc != COAKKA_V2_OK) {
      fprintf(stderr, "frame_read_try failed: %s\n", coakka_v2_status_name(rc));
      return 1;
    }
    (void)len;
    coakka_v2_frame_release(buf);
    ++(*out_count);
  }
}

static int read_stats(coakka_v2_runtime_t* runtime, coakka_v2_runtime_stats_t* out_stats) {
  memset(out_stats, 0, sizeof(*out_stats));
  out_stats->struct_size = sizeof(*out_stats);
  return require_ok(coakka_v2_runtime_get_stats(runtime, out_stats), "runtime_get_stats");
}

static int build_and_write_request(int fd, int index) {
  char message_id[64];
  char payload[64];
  coakka_v2_client_raw_request_spec_t request_spec;
  uint8_t* request_frame = NULL;
  size_t request_frame_len = 0;
  coakka_v2_status_t rc;

  snprintf(message_id, sizeof(message_id), "native-c-pressure-%d", index);
  snprintf(payload, sizeof(payload), "{\"index\":%d}", index);

  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = message_id;
  request_spec.source = "native-c-pressure-client";
  request_spec.target = "samples.runtime.native.pressure.local";
  request_spec.reply_to = "native-c-pressure-client/replies";
  request_spec.payload = (const uint8_t*)payload;
  request_spec.payload_len = strlen(payload);
  request_spec.timeout_ms = 1000;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 0;

  rc = coakka_v2_client_build_raw_request(&request_spec,
                                          &request_frame,
                                          &request_frame_len);
  if (rc != COAKKA_V2_OK) {
    fprintf(stderr, "build_raw_request failed: %s\n", coakka_v2_status_name(rc));
    return 1;
  }

  rc = coakka_v2_frame_write(fd, request_frame, request_frame_len);
  coakka_v2_client_bytes_release(request_frame);
  if (rc != COAKKA_V2_OK) {
    fprintf(stderr, "frame_write failed: %s\n", coakka_v2_status_name(rc));
    return 1;
  }

  return 0;
}

int main(void) {
  coakka_v2_runtime_info_t info;
  coakka_v2_runtime_t* runtime = NULL;
  coakka_v2_host_handles_t handles;
  coakka_v2_frame_reader_t* response_reader = NULL;
  coakka_v2_frame_reader_t* deadletter_reader = NULL;
  int delivered = 0;
  int rejected = 0;
  int exit_code = 1;

  memset(&info, 0, sizeof(info));
  info.struct_size = sizeof(info);
  if (require_ok(coakka_v2_runtime_get_info(&info), "runtime_get_info")) {
    return 1;
  }
  printf("coakka_runtime_info abi=%u version=%s git=%s language=c\n",
         info.abi_version,
         info.runtime_version,
         info.git_commit);

  const coakka_v2_runtime_config_t config = {
      .system_name = "runtime-v2-native-c-pressure",
      .node_id = "runtime-v2-native-c-pressure-node",
      .strict_no_drop = 1,
      .queue_capacity = k_queue_capacity,
  };
  runtime = coakka_v2_runtime_create(&config);
  if (runtime == NULL) {
    fprintf(stderr, "runtime_create failed\n");
    return 1;
  }

  memset(&handles, 0, sizeof(handles));
  handles.struct_size = sizeof(handles);
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR;
  if (require_ok(coakka_v2_runtime_get_host_handles(runtime, &handles), "get_host_handles")) {
    goto cleanup;
  }

  const coakka_v2_endpoint_t endpoint = {
      .host = "127.0.0.1",
      .port = 9021,
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = "samples.runtime.native.pressure.local",
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

  if (require_ok(coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot), "apply_snapshot") ||
      require_ok(coakka_v2_runtime_start(runtime), "runtime_start")) {
    goto cleanup;
  }

  response_reader = coakka_v2_frame_reader_create(handles.response_read_fd, 64 * 1024);
  deadletter_reader = coakka_v2_frame_reader_create(handles.deadletter_read_fd, 64 * 1024);
  if (response_reader == NULL || deadletter_reader == NULL) {
    fprintf(stderr, "frame_reader_create failed\n");
    goto cleanup;
  }

  for (int index = 0; index < k_burst_count; ++index) {
    if (build_and_write_request(handles.request_write_fd, index)) {
      goto cleanup;
    }
  }

  coakka_v2_runtime_stats_t stats;
  for (int spin = 0; spin < 300 && delivered + rejected < k_burst_count; ++spin) {
    if (drain_reader(response_reader, &delivered) ||
        drain_reader(deadletter_reader, &rejected) ||
        read_stats(runtime, &stats)) {
      goto cleanup;
    }
    usleep(10000);
  }

  if (drain_reader(response_reader, &delivered) ||
      drain_reader(deadletter_reader, &rejected) ||
      read_stats(runtime, &stats)) {
    goto cleanup;
  }

  if (stats.queue_rejected_count < 1u || stats.deadletter_count < 1u) {
    fprintf(stderr, "runtime pressure was not observed\n");
    goto cleanup;
  }
  if (delivered + rejected != k_burst_count) {
    fprintf(stderr,
            "runtime pressure burst did not drain: attempts=%d delivered=%d rejected=%d\n",
            k_burst_count,
            delivered,
            rejected);
    goto cleanup;
  }
  if (stats.ingress_queue_capacity != k_queue_capacity ||
      stats.ingress_queue_high_watermark < 1u ||
      stats.ingress_queue_high_watermark > stats.ingress_queue_capacity) {
    fprintf(stderr, "unexpected ingress queue stats\n");
    goto cleanup;
  }

  printf("coakka_runtime_pressure attempts=%d delivered=%d rejected=%d capacity=%zu highWatermark=%zu language=c\n",
         k_burst_count,
         delivered,
         rejected,
         stats.ingress_queue_capacity,
         stats.ingress_queue_high_watermark);
  printf("coakka_runtime_stats generation=%llu routes=%zu queueRejected=%llu deadletters=%llu language=c\n",
         (unsigned long long)stats.applied_generation,
         stats.route_count,
         (unsigned long long)stats.queue_rejected_count,
         (unsigned long long)stats.deadletter_count);

  exit_code = 0;

cleanup:
  if (runtime != NULL) {
    coakka_v2_runtime_stop(runtime);
  }
  coakka_v2_frame_reader_destroy(response_reader);
  coakka_v2_frame_reader_destroy(deadletter_reader);
  if (runtime != NULL) {
    coakka_v2_runtime_destroy(runtime);
  }
  close_host_handles(&handles);
  return exit_code;
}
