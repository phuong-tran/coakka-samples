#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>

#if defined(_WIN32)
#define COAKKA_MOJO_EXPORT __declspec(dllexport)
#else
#define COAKKA_MOJO_EXPORT __attribute__((visibility("default")))
#endif

typedef enum coakka_v2_status_t {
  COAKKA_V2_OK = 0
} coakka_v2_status_t;

typedef struct coakka_v2_runtime_t coakka_v2_runtime_t;
typedef struct coakka_v2_ask_client_t coakka_v2_ask_client_t;
typedef struct coakka_v2_ask_ticket_t coakka_v2_ask_ticket_t;
typedef struct coakka_v2_frame_reader_t coakka_v2_frame_reader_t;

typedef struct coakka_v2_runtime_config_t {
  const char* system_name;
  const char* node_id;
  int strict_no_drop;
  int queue_capacity;
} coakka_v2_runtime_config_t;

typedef struct coakka_v2_endpoint_t {
  const char* host;
  uint16_t port;
  uint32_t weight;
  uint32_t flags;
} coakka_v2_endpoint_t;

typedef struct coakka_v2_route_t {
  const char* target;
  int strategy;
  const char* route_key_hint;
  uint32_t flags;
  const coakka_v2_endpoint_t* endpoints;
  size_t endpoint_count;
} coakka_v2_route_t;

typedef struct coakka_v2_control_snapshot_t {
  uint64_t generation;
  const coakka_v2_route_t* routes;
  size_t route_count;
} coakka_v2_control_snapshot_t;

typedef struct coakka_v2_runtime_info_t {
  size_t struct_size;
  uint32_t abi_version;
  uint32_t feature_flags;
  const char* runtime_version;
  const char* git_commit;
} coakka_v2_runtime_info_t;

typedef struct coakka_v2_host_handles_t {
  size_t struct_size;
  uint32_t flags;
  int request_write_fd;
  int response_read_fd;
  int deadletter_read_fd;
  int control_write_fd;
  int monitor_read_fd;
  int delivered_request_read_fd;
} coakka_v2_host_handles_t;

typedef struct coakka_v2_runtime_stats_t {
  size_t struct_size;
  uint64_t applied_generation;
  size_t route_count;
  int runtime_state;
} coakka_v2_runtime_stats_t;

typedef struct coakka_v2_client_raw_request_spec_t {
  size_t struct_size;
  const char* message_id;
  const char* source;
  const char* target;
  const char* reply_to;
  const uint8_t* payload;
  size_t payload_len;
  uint32_t timeout_ms;
  uint32_t delivery_hint;
  uint32_t one_way;
} coakka_v2_client_raw_request_spec_t;

typedef struct coakka_v2_client_raw_reply_spec_t {
  size_t struct_size;
  const uint8_t* request_buf;
  size_t request_len;
  const char* source;
  const uint8_t* payload;
  size_t payload_len;
} coakka_v2_client_raw_reply_spec_t;

extern coakka_v2_status_t coakka_v2_runtime_get_info(coakka_v2_runtime_info_t* out_info);
extern coakka_v2_runtime_t* coakka_v2_runtime_create(const coakka_v2_runtime_config_t* cfg);
extern void coakka_v2_runtime_destroy(coakka_v2_runtime_t* rt);
extern coakka_v2_status_t coakka_v2_runtime_get_host_handles(coakka_v2_runtime_t* rt,
                                                             coakka_v2_host_handles_t* out_handles);
extern coakka_v2_status_t coakka_v2_runtime_apply_control_snapshot(
    coakka_v2_runtime_t* rt,
    const coakka_v2_control_snapshot_t* snapshot);
extern coakka_v2_status_t coakka_v2_runtime_start(coakka_v2_runtime_t* rt);
extern coakka_v2_status_t coakka_v2_runtime_stop(coakka_v2_runtime_t* rt);
extern coakka_v2_status_t coakka_v2_runtime_get_stats(coakka_v2_runtime_t* rt,
                                                      coakka_v2_runtime_stats_t* out_stats);
extern coakka_v2_status_t coakka_v2_runtime_submit_envelope(coakka_v2_runtime_t* rt,
                                                            const uint8_t* buf,
                                                            size_t len);
extern coakka_v2_ask_client_t* coakka_v2_ask_client_create(
    coakka_v2_runtime_t* rt,
    const coakka_v2_host_handles_t* handles);
extern void coakka_v2_ask_client_destroy(coakka_v2_ask_client_t* client);
extern coakka_v2_status_t coakka_v2_ask_client_begin(coakka_v2_ask_client_t* client,
                                                     const uint8_t* request_buf,
                                                     size_t request_len,
                                                     coakka_v2_ask_ticket_t** out_ticket);
extern coakka_v2_status_t coakka_v2_ask_ticket_await(coakka_v2_ask_ticket_t* ticket,
                                                     uint32_t timeout_ms,
                                                     uint32_t* out_result_kind,
                                                     uint8_t** out_buf,
                                                     size_t* out_len);
extern void coakka_v2_ask_ticket_destroy(coakka_v2_ask_ticket_t* ticket);
extern coakka_v2_status_t coakka_v2_client_build_raw_request(
    const coakka_v2_client_raw_request_spec_t* spec,
    uint8_t** out_buf,
    size_t* out_len);
extern coakka_v2_status_t coakka_v2_client_build_raw_reply(
    const coakka_v2_client_raw_reply_spec_t* spec,
    uint8_t** out_buf,
    size_t* out_len);
extern void coakka_v2_client_bytes_release(uint8_t* buf);
extern coakka_v2_frame_reader_t* coakka_v2_frame_reader_create(int fd, size_t max_frame_size);
extern void coakka_v2_frame_reader_destroy(coakka_v2_frame_reader_t* reader);
extern coakka_v2_status_t coakka_v2_frame_read_try(coakka_v2_frame_reader_t* reader,
                                                   uint8_t** out_buf,
                                                   size_t* out_len);
extern void coakka_v2_frame_release(uint8_t* buf);

static int require_ok(coakka_v2_status_t status, const char* step) {
  if (status == COAKKA_V2_OK) {
    return 0;
  }
  fprintf(stderr, "%s failed: %d\n", step, (int)status);
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
    if ((int)rc != -6) {
      fprintf(stderr, "frame_read_try failed: %d\n", (int)rc);
      return 1;
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

  coakka_v2_ask_client_t* client = coakka_v2_ask_client_create(runtime, handles);
  if (client == NULL) {
    fprintf(stderr, "ask_client_create failed\n");
    return 1;
  }

  coakka_v2_client_raw_request_spec_t request_spec = {0};
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "req-mojo-raw-1";
  request_spec.source = "mojo-client";
  request_spec.target = "svc.echo";
  request_spec.reply_to = "mojo-client/replies";
  request_spec.payload = request_payload;
  request_spec.payload_len = sizeof(request_payload);
  request_spec.timeout_ms = 1000u;
  request_spec.delivery_hint = 1u;
  request_spec.one_way = 0u;

  uint8_t* request_buf = NULL;
  size_t request_len = 0;
  coakka_v2_ask_ticket_t* ticket = NULL;
  coakka_v2_frame_reader_t* reader = NULL;
  uint8_t* delivered_buf = NULL;
  size_t delivered_len = 0;
  uint8_t* reply_buf = NULL;
  size_t reply_len = 0;
  uint8_t* result_buf = NULL;
  size_t result_len = 0;
  uint32_t result_kind = 0;
  int failed = 0;

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

  coakka_v2_client_raw_reply_spec_t reply_spec = {0};
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
  if (require_ok(coakka_v2_runtime_submit_envelope(runtime, reply_buf, reply_len),
                 "submit_reply")) {
    failed = 1;
    goto done;
  }
  if (require_ok(coakka_v2_ask_ticket_await(ticket, 1000u, &result_kind, &result_buf, &result_len),
                 "ask_ticket_await") ||
      result_kind != 1u || result_buf == NULL || result_len == 0) {
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

  coakka_v2_ask_client_t* client = coakka_v2_ask_client_create(runtime, handles);
  if (client == NULL) {
    fprintf(stderr, "ask_client_create failed\n");
    return 1;
  }

  coakka_v2_client_raw_request_spec_t request_spec = {0};
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "req-mojo-missing-1";
  request_spec.source = "mojo-client";
  request_spec.target = "svc.missing";
  request_spec.reply_to = "mojo-client/replies";
  request_spec.payload = request_payload;
  request_spec.payload_len = sizeof(request_payload);
  request_spec.timeout_ms = 1000u;
  request_spec.delivery_hint = 1u;
  request_spec.one_way = 0u;

  uint8_t* request_buf = NULL;
  size_t request_len = 0;
  coakka_v2_ask_ticket_t* ticket = NULL;
  uint8_t* result_buf = NULL;
  size_t result_len = 0;
  uint32_t result_kind = 0;
  int failed = 0;

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
      result_kind != 2u || result_buf == NULL || result_len == 0) {
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

COAKKA_MOJO_EXPORT int coakka_mojo_runtime_basic(int ignored) {
  (void)ignored;

  coakka_v2_runtime_info_t info = {0};
  info.struct_size = sizeof(info);
  if (require_ok(coakka_v2_runtime_get_info(&info), "runtime_get_info")) {
    return 1;
  }

  const coakka_v2_runtime_config_t config = {
      .system_name = "mojo-runtime-basic",
      .node_id = "mojo-runtime-basic-node",
      .strict_no_drop = 1,
      .queue_capacity = 32,
  };
  coakka_v2_runtime_t* runtime = coakka_v2_runtime_create(&config);
  if (runtime == NULL) {
    fprintf(stderr, "runtime_create failed\n");
    return 1;
  }

  coakka_v2_host_handles_t handles = {0};
  handles.struct_size = sizeof(handles);
  handles.flags = 1u | 2u;
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
      .flags = 1u,
  };
  const coakka_v2_route_t route = {
      .target = "svc.echo",
      .strategy = 1,
      .route_key_hint = NULL,
      .flags = 0,
      .endpoints = &endpoint,
      .endpoint_count = 1,
  };
  const coakka_v2_control_snapshot_t snapshot = {
      .generation = 1,
      .routes = &route,
      .route_count = 1,
  };

  if (require_ok(coakka_v2_runtime_apply_control_snapshot(runtime, &snapshot),
                 "apply_control_snapshot") ||
      require_ok(coakka_v2_runtime_start(runtime), "runtime_start")) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  coakka_v2_runtime_stats_t stats = {0};
  stats.struct_size = sizeof(stats);
  if (require_ok(coakka_v2_runtime_get_stats(runtime, &stats), "runtime_get_stats")) {
    (void)coakka_v2_runtime_stop(runtime);
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  if (stats.applied_generation != 1 || stats.route_count != 1 || stats.runtime_state != 1) {
    fprintf(stderr,
            "unexpected runtime stats generation=%llu routes=%zu state=%d\n",
            (unsigned long long)stats.applied_generation,
            stats.route_count,
            stats.runtime_state);
    (void)coakka_v2_runtime_stop(runtime);
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  if (run_raw_roundtrip(runtime, &handles) ||
      run_route_miss_deadletter(runtime, &handles)) {
    (void)coakka_v2_runtime_stop(runtime);
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }

  printf("coakka_runtime_mojo_basic abi=%u runtime=%s git=%s generation=%llu routes=%zu state=%d rawRoundTrip=ok routeMissDeadletter=ok\n",
         info.abi_version,
         info.runtime_version,
         info.git_commit,
         (unsigned long long)stats.applied_generation,
         stats.route_count,
         stats.runtime_state);
  fflush(stdout);

  if (require_ok(coakka_v2_runtime_stop(runtime), "runtime_stop")) {
    close_host_handles(&handles);
    coakka_v2_runtime_destroy(runtime);
    return 1;
  }
  coakka_v2_runtime_destroy(runtime);
  close_host_handles(&handles);
  return 0;
}
