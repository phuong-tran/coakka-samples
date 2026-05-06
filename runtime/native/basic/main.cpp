#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/utils.h"

#include <chrono>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unistd.h>

namespace {

void require_ok(coakka_v2_status_t status, const char* step) {
  if (status != COAKKA_V2_OK) {
    throw std::runtime_error(std::string(step) + " failed: " + coakka_v2_status_name(status));
  }
}

void close_if_open(int& fd) noexcept {
  if (fd >= 0) {
    close(fd);
    fd = -1;
  }
}

class Runtime {
public:
  explicit Runtime(const coakka_v2_runtime_config_t& config) : runtime_(coakka_v2_runtime_create(&config)) {
    if (runtime_ == nullptr) {
      throw std::runtime_error("runtime_create failed");
    }
    handles_.struct_size = sizeof(handles_);
    handles_.flags = COAKKA_V2_HOST_HANDLES_FLAG_ENABLE_MONITOR;
    require_ok(coakka_v2_runtime_get_host_handles(runtime_, &handles_), "get_host_handles");
  }

  ~Runtime() {
    if (runtime_ != nullptr) {
      (void)coakka_v2_runtime_stop(runtime_);
      coakka_v2_runtime_destroy(runtime_);
      close_if_open(handles_.request_write_fd);
      close_if_open(handles_.response_read_fd);
      close_if_open(handles_.deadletter_read_fd);
      close_if_open(handles_.control_write_fd);
      close_if_open(handles_.monitor_read_fd);
      close_if_open(handles_.delivered_request_read_fd);
    }
  }

  Runtime(const Runtime&) = delete;
  Runtime& operator=(const Runtime&) = delete;

  coakka_v2_runtime_t* get() const noexcept {
    return runtime_;
  }

private:
  coakka_v2_runtime_t* runtime_;
  coakka_v2_host_handles_t handles_{};
};

coakka_v2_runtime_stats_t wait_for_route_miss(coakka_v2_runtime_t* runtime) {
  for (int attempt = 0; attempt < 100; ++attempt) {
    coakka_v2_runtime_stats_t stats{};
    stats.struct_size = sizeof(stats);
    require_ok(coakka_v2_runtime_get_stats(runtime, &stats), "runtime_get_stats");
    if (stats.route_miss_count >= 1 && stats.deadletter_count >= 1) {
      return stats;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }
  throw std::runtime_error("route miss was not observed");
}

}  // namespace

int main() {
  coakka_v2_runtime_info_t info{};
  info.struct_size = sizeof(info);
  require_ok(coakka_v2_runtime_get_info(&info), "runtime_get_info");
  std::cout << "coakka_runtime_info abi=" << info.abi_version
            << " version=" << info.runtime_version
            << " git=" << info.git_commit
            << " backend=" << info.southbound_backend
            << " language=cpp\n";

  const coakka_v2_runtime_config_t config = {
      .system_name = "runtime-v2-native-cpp-basic",
      .node_id = "runtime-v2-native-cpp-node",
      .strict_no_drop = 1,
      .queue_capacity = 16,
  };
  Runtime runtime(config);

  const coakka_v2_endpoint_t endpoint = {
      .host = "127.0.0.1",
      .port = 9012,
      .weight = 1,
      .flags = COAKKA_V2_ENDPOINT_FLAG_LOCAL,
  };
  const coakka_v2_route_t route = {
      .target = "samples.runtime.native.cpp.local",
      .strategy = COAKKA_V2_ROUTE_STRATEGY_SINGLE_OWNER,
      .route_key_hint = nullptr,
      .flags = COAKKA_V2_ROUTE_FLAG_NONE,
      .endpoints = &endpoint,
      .endpoint_count = 1,
  };
  const coakka_v2_control_snapshot_t snapshot = {
      .generation = 1,
      .routes = &route,
      .route_count = 1,
  };

  require_ok(coakka_v2_runtime_apply_control_snapshot(runtime.get(), &snapshot), "apply_snapshot");
  require_ok(coakka_v2_runtime_start(runtime.get()), "runtime_start");

  const std::string payload = "hello-native-cpp";
  coakka_v2_client_raw_request_spec_t request_spec{};
  request_spec.struct_size = sizeof(request_spec);
  request_spec.message_id = "native-cpp-route-miss-1";
  request_spec.source = "native-cpp-client";
  request_spec.target = "samples.runtime.native.cpp.missing";
  request_spec.reply_to = nullptr;
  request_spec.payload = reinterpret_cast<const uint8_t*>(payload.data());
  request_spec.payload_len = payload.size();
  request_spec.timeout_ms = 1000;
  request_spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_ROUTER_DEFAULT;
  request_spec.one_way = 1;

  uint8_t* request_frame = nullptr;
  size_t request_frame_len = 0;
  require_ok(coakka_v2_client_build_raw_request(&request_spec,
                                                &request_frame,
                                                &request_frame_len),
             "build_raw_request");
  require_ok(coakka_v2_runtime_submit_envelope(runtime.get(),
                                               request_frame,
                                               request_frame_len),
             "submit_envelope");
  coakka_v2_client_bytes_release(request_frame);

  const auto stats = wait_for_route_miss(runtime.get());
  std::cout << "coakka_runtime_stats generation=" << stats.applied_generation
            << " routes=" << stats.route_count
            << " routeMisses=" << stats.route_miss_count
            << " deadletters=" << stats.deadletter_count
            << " language=cpp\n";

  require_ok(coakka_v2_runtime_stop(runtime.get()), "runtime_stop");
  return 0;
}
