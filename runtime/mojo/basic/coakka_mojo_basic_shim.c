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

int coakka_mojo_basic_run(int ignored) {
  (void)ignored;
  coakka_v2_runtime_info_t info;
  memset(&info, 0, sizeof(info));
  info.struct_size = sizeof(info);
  if (require_ok(coakka_v2_runtime_get_info(&info), "runtime_get_info")) {
    return 1;
  }
  printf("coakka_runtime_info abi=%u version=%s git=%s language=mojo\n",
         info.abi_version,
         info.runtime_version,
         info.git_commit);

  const coakka_v2_runtime_config_t config = {
      .system_name = "runtime-v2-mojo-basic",
      .node_id = "runtime-v2-mojo-node",
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
  handles.request_write_fd = -1;
  handles.response_read_fd = -1;
  handles.deadletter_read_fd = -1;
  handles.control_write_fd = -1;
  handles.monitor_read_fd = -1;
  handles.delivered_request_read_fd = -1;
  if (require_ok(coakka_v2_runtime_get_host_handles(runtime, &handles), "get_host_handles")) {
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  const coakka_v2_endpoint_t endpoint = {
      .host = "127.0.0.1",
      .port = 9042,
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = "samples.runtime.mojo.local",
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

  coakka_v2_runtime_stats_t stats;
  memset(&stats, 0, sizeof(stats));
  stats.struct_size = sizeof(stats);
  if (require_ok(coakka_v2_runtime_get_stats(runtime, &stats), "runtime_get_stats")) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }
  if (stats.applied_generation != 1 || stats.route_count != 1 ||
      stats.runtime_state != COAKKA_V2_STATE_STARTED) {
    fprintf(stderr,
            "unexpected runtime stats generation=%llu routes=%zu state=%d\n",
            (unsigned long long)stats.applied_generation,
            stats.route_count,
            (int)stats.runtime_state);
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  printf("coakka_runtime_stats generation=%llu routes=%zu state=%s language=mojo\n",
         (unsigned long long)stats.applied_generation,
         stats.route_count,
         coakka_v2_runtime_state_name(stats.runtime_state));

  if (require_ok(coakka_v2_runtime_stop(runtime), "runtime_stop")) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }
  coakka_v2_runtime_destroy(runtime);
  close_host_handles(&handles);
  return 0;
}
