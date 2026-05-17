#include "coakka/v2/control.h"
#include "coakka/v2/client.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/transport.h"
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

static int read_delivered_frame(coakka_v2_frame_reader_t* reader,
                                uint8_t** out_buf,
                                size_t* out_len) {
  for (int i = 0; i < 1000; ++i) {
    coakka_v2_status_t rc = coakka_v2_frame_read_try(reader, out_buf, out_len);
    if (rc == COAKKA_V2_OK) {
      return (*out_buf != NULL && *out_len > 0) ? 0 : 1;
    }
    if (rc != COAKKA_V2_ERR_WOULD_BLOCK) {
      return require_ok(rc, "frame_read_try");
    }
    usleep(1000);
  }
  fprintf(stderr, "timed out waiting for delivered request\n");
  return 1;
}

static int run_raw_roundtrip(coakka_v2_runtime_t* runtime,
                             const coakka_v2_host_handles_t* handles) {
  static const uint8_t request_payload[] = {'h', 'e', 'l', 'l', 'o'};
  static const uint8_t reply_payload[] = {'r', 'e', 'p', 'l', 'y'};
  coakka_v2_ask_client_t* client = NULL;
  coakka_v2_ask_ticket_t* ticket = NULL;
  coakka_v2_frame_reader_t* reader = NULL;
  uint8_t* request_buf = NULL;
  size_t request_len = 0;
  uint8_t* delivered_buf = NULL;
  size_t delivered_len = 0;
  uint8_t* reply_buf = NULL;
  size_t reply_len = 0;
  uint8_t* result_buf = NULL;
  size_t result_len = 0;
  uint32_t result_kind = COAKKA_V2_CLIENT_RESULT_NONE;
  int failed = 0;

  client = coakka_v2_ask_client_create(runtime, handles);
  if (client == NULL) {
    fprintf(stderr, "ask_client_create failed\n");
    return 1;
  }

  coakka_v2_client_raw_request_spec_t request_spec;
  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "req-mojo-basic-raw-1";
  request_spec.source = "mojo-basic-client";
  request_spec.target = "svc.echo";
  request_spec.reply_to = "mojo-basic-client/replies";
  request_spec.payload = request_payload;
  request_spec.payload_len = sizeof(request_payload);
  request_spec.timeout_ms = 1000u;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 0u;

  if (require_ok(coakka_v2_client_build_raw_request(&request_spec, &request_buf, &request_len),
                 "build_raw_request") ||
      request_buf == NULL || request_len == 0) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_ask_client_begin(client, request_buf, request_len, &ticket),
                 "ask_client_begin") ||
      ticket == NULL) {
    failed = 1;
    goto done;
  }

  reader = coakka_v2_frame_reader_create(handles->delivered_request_read_fd, 64u * 1024u);
  if (reader == NULL || read_delivered_frame(reader, &delivered_buf, &delivered_len)) {
    failed = 1;
    goto done;
  }

  coakka_v2_client_raw_reply_spec_t reply_spec;
  memset(&reply_spec, 0, sizeof(reply_spec));
  reply_spec.struct_size = sizeof(reply_spec);
  reply_spec.request_buf = delivered_buf;
  reply_spec.request_len = delivered_len;
  reply_spec.source = "svc.echo";
  reply_spec.payload = reply_payload;
  reply_spec.payload_len = sizeof(reply_payload);
  if (require_ok(coakka_v2_client_build_raw_reply(&reply_spec, &reply_buf, &reply_len),
                 "build_raw_reply") ||
      reply_buf == NULL || reply_len == 0) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_runtime_submit_envelope(runtime, reply_buf, reply_len), "submit_reply")) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_ask_ticket_await(ticket, 1000u, &result_kind, &result_buf, &result_len),
                 "ask_ticket_await") ||
      result_kind != COAKKA_V2_CLIENT_RESULT_RESPONSE || result_buf == NULL || result_len == 0) {
    failed = 1;
    goto done;
  }

done:
  if (result_buf != NULL) coakka_v2_client_bytes_release(result_buf);
  if (reply_buf != NULL) coakka_v2_client_bytes_release(reply_buf);
  if (delivered_buf != NULL) coakka_v2_frame_release(delivered_buf);
  if (reader != NULL) coakka_v2_frame_reader_destroy(reader);
  if (ticket != NULL) coakka_v2_ask_ticket_destroy(ticket);
  if (request_buf != NULL) coakka_v2_client_bytes_release(request_buf);
  coakka_v2_ask_client_destroy(client);
  return failed;
}

static int run_route_miss_deadletter(coakka_v2_runtime_t* runtime,
                                     const coakka_v2_host_handles_t* handles) {
  static const uint8_t request_payload[] = {'m', 'i', 's', 's', 'i', 'n', 'g'};
  coakka_v2_ask_client_t* client = NULL;
  coakka_v2_ask_ticket_t* ticket = NULL;
  uint8_t* request_buf = NULL;
  size_t request_len = 0;
  uint8_t* result_buf = NULL;
  size_t result_len = 0;
  uint32_t result_kind = COAKKA_V2_CLIENT_RESULT_NONE;
  int failed = 0;

  client = coakka_v2_ask_client_create(runtime, handles);
  if (client == NULL) {
    fprintf(stderr, "ask_client_create failed\n");
    return 1;
  }

  coakka_v2_client_raw_request_spec_t request_spec;
  memset(&request_spec, 0, sizeof(request_spec));
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "req-mojo-basic-missing-1";
  request_spec.source = "mojo-basic-client";
  request_spec.target = "svc.missing";
  request_spec.reply_to = "mojo-basic-client/replies";
  request_spec.payload = request_payload;
  request_spec.payload_len = sizeof(request_payload);
  request_spec.timeout_ms = 1000u;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 0u;

  if (require_ok(coakka_v2_client_build_raw_request(&request_spec, &request_buf, &request_len),
                 "build_missing_raw_request") ||
      request_buf == NULL || request_len == 0) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_ask_client_begin(client, request_buf, request_len, &ticket),
                 "ask_missing_client_begin") ||
      ticket == NULL) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_ask_ticket_await(ticket, 1000u, &result_kind, &result_buf, &result_len),
                 "ask_missing_ticket_await") ||
      result_kind != COAKKA_V2_CLIENT_RESULT_DEADLETTER || result_buf == NULL || result_len == 0) {
    failed = 1;
    goto done;
  }

done:
  if (result_buf != NULL) coakka_v2_client_bytes_release(result_buf);
  if (ticket != NULL) coakka_v2_ask_ticket_destroy(ticket);
  if (request_buf != NULL) coakka_v2_client_bytes_release(request_buf);
  coakka_v2_ask_client_destroy(client);
  return failed;
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
  handles.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR |
                  COAKKA_V2_HOST_HANDLES_FLAG_SEPARATE_DELIVERED_REQUEST_LANE;
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
      .target = "svc.echo",
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

  if (run_raw_roundtrip(runtime, &handles) ||
      run_route_miss_deadletter(runtime, &handles)) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  printf("coakka_runtime_stats generation=%llu routes=%zu state=%s rawRoundTrip=ok routeMissDeadletter=ok language=mojo\n",
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
