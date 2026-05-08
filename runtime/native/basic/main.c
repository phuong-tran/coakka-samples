#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/utils.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

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

static int wait_for_route_miss(coakka_v2_runtime_t* runtime, coakka_v2_runtime_stats_t* out_stats) {
  for (int attempt = 0; attempt < 100; ++attempt) {
    memset(out_stats, 0, sizeof(*out_stats));
    out_stats->struct_size = sizeof(*out_stats);
    if (coakka_v2_runtime_get_stats(runtime, out_stats) != COAKKA_V2_OK) {
      return 1;
    }
    if (out_stats->route_miss_count >= 1 && out_stats->deadletter_count >= 1) {
      return 0;
    }
    usleep(10000);
  }
  return 1;
}

int main(void) {
  coakka_v2_runtime_info_t info;
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
      .system_name = "runtime-v2-native-c-basic",
      .node_id = "runtime-v2-native-c-node",
      .strict_no_drop = 1,
      .queue_capacity = 16,
  };
  coakka_v2_runtime_t* runtime = coakka_v2_runtime_create(&config);
  if (runtime == NULL) {
    fprintf(stderr, "runtime_create failed\n");
    return 1;
  }

  coakka_v2_host_handles_t handles;
  memset(&handles, 0, sizeof(handles));
  handles.struct_size = sizeof(handles);
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR;
  if (require_ok(coakka_v2_runtime_get_host_handles(runtime, &handles), "get_host_handles")) {
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  const coakka_v2_endpoint_t endpoint = {
      .host = "127.0.0.1",
      .port = 9011,
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = "samples.runtime.native.c.local",
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
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  const char payload[] = "hello-native-c";
  coakka_v2_client_raw_request_spec_t request_spec;
  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "native-c-route-miss-1";
  request_spec.source = "native-c-client";
  request_spec.target = "samples.runtime.native.c.missing";
  request_spec.reply_to = NULL;
  request_spec.payload = (const uint8_t*)payload;
  request_spec.payload_len = strlen(payload);
  request_spec.timeout_ms = 1000;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 1;

  uint8_t* request_frame = NULL;
  size_t request_frame_len = 0;
  if (require_ok(coakka_v2_client_build_raw_request(&request_spec,
                                                    &request_frame,
                                                    &request_frame_len),
                 "build_raw_request") ||
      require_ok(coakka_v2_runtime_submit_envelope(runtime,
                                                   request_frame,
                                                   request_frame_len),
                 "submit_envelope")) {
    coakka_v2_client_bytes_release(request_frame);
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }
  coakka_v2_client_bytes_release(request_frame);

  coakka_v2_runtime_stats_t stats;
  if (wait_for_route_miss(runtime, &stats)) {
    fprintf(stderr, "route miss was not observed\n");
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  printf("coakka_runtime_stats generation=%llu routes=%zu routeMisses=%llu deadletters=%llu language=c\n",
         (unsigned long long)stats.applied_generation,
         stats.route_count,
         (unsigned long long)stats.route_miss_count,
         (unsigned long long)stats.deadletter_count);

  if (require_ok(coakka_v2_runtime_stop(runtime), "runtime_stop")) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }
  coakka_v2_runtime_destroy(runtime);
  close_host_handles(&handles);
  return 0;
}
