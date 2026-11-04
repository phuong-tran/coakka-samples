#include "stream_lane_camera_control_server.h"

#include "camera_http_host.h"
#include "camera_json.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdio>
#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <system_error>
#include <thread>
#include <utility>

namespace {

constexpr uint64_t kProfileRouteId = 1u;
constexpr uint64_t kProfileBindingId = 1001u;
constexpr uint64_t kMaxRequestBytes = 8ull * 1024ull;
constexpr uint64_t kDrainTimeoutMs = 1000u;
constexpr uint64_t kStopTimeoutMs = 2000u;

bool constant_time_equal(std::string_view left, std::string_view right) {
  const size_t compared = std::max(left.size(), right.size());
  size_t difference = left.size() ^ right.size();
  for (size_t index = 0u; index < compared; ++index) {
    const unsigned char left_byte =
        index < left.size() ? static_cast<unsigned char>(left[index]) : 0u;
    const unsigned char right_byte =
        index < right.size() ? static_cast<unsigned char>(right[index]) : 0u;
    difference |= left_byte ^ right_byte;
  }
  return difference == 0u;
}

bool read_uint32(const coakka::camera_json::object_t &body, const char *name,
                 uint32_t *out) {
  return coakka::camera_json::value_as_uint32(body.find(name), out);
}

struct Server {
  explicit Server(coakka_v2_camera_control_server_config_t value)
      : config(std::move(value)) {}

  ~Server() { stop(); }

  bool start(std::string *error) {
    if (error == nullptr) {
      return false;
    }
    std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
    if (service != nullptr || stopped.load(std::memory_order_acquire)) {
      *error = "control server can only be started once";
      return false;
    }

    coakka_http_host_configuration_t configuration;
    coakka_http_host_configuration_init(&configuration);
    configuration.bind_address = coakka::camera_http::bytes(config.bind_host);
    configuration.port = config.bind_port;
    configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
    configuration.security = COAKKA_HTTP_HOST_SECURITY_PLAINTEXT;
    configuration.omit_buffered_success_events = 1u;
    configuration.limits.max_connections = 4u;
    configuration.limits.max_active_requests = 4u;
    configuration.limits.request_queue_capacity = 4u;
    configuration.limits.completion_queue_capacity = 4u;
    configuration.limits.terminal_queue_capacity = 4u;
    configuration.limits.max_header_count = 16u;
    configuration.limits.max_header_bytes = 4u * 1024u;
    configuration.limits.max_request_body_bytes = kMaxRequestBytes;
    configuration.limits.max_response_body_bytes = 16ull * 1024ull;
    configuration.limits.handler_timeout_ms = 2000u;
    configuration.limits.drain_timeout_ms = kDrainTimeoutMs;
    configuration.limits.stop_timeout_ms = kStopTimeoutMs;

    coakka_http_host_route_t route;
    coakka_http_host_route_init(&route);
    route.route_id = kProfileRouteId;
    route.handler_binding_id = kProfileBindingId;
    route.method = coakka::camera_http::bytes("POST");
    route.encoded_path_pattern = coakka::camera_http::bytes("/v1/profile");
    coakka_http_host_body_policy_init(&route.body_policy);
    route.body_policy.enabled = 1u;
    route.body_policy.accept_other = 1u;
    route.body_policy.max_body_bytes = kMaxRequestBytes;

    uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
    coakka_http_host_status_t status = coakka_http_host_service_create(
        &configuration, &route, 1u, &service, &failed_route);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "control service create failed: " +
               coakka::camera_http::status_text(status);
      service = nullptr;
      return false;
    }
    coakka_http_host_issue_t issue;
    coakka_http_host_issue_init(&issue);
    status = coakka_http_host_service_start(service, &issue);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "control service start failed: " +
               coakka::camera_http::startup_error(status, issue);
      destroy_service();
      return false;
    }
    status = coakka_http_host_service_port(service, &port);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "control service port query failed: " +
               coakka::camera_http::status_text(status);
      (void)coakka_http_host_service_stop(service);
      destroy_service();
      return false;
    }
    try {
      worker = std::thread([this] { run(); });
    } catch (const std::system_error &failure) {
      *error = "control reader start failed: " + std::string(failure.what());
      (void)coakka_http_host_service_stop(service);
      destroy_service();
      port = 0u;
      return false;
    }
    return true;
  }

  void stop() noexcept {
    if (stopped.exchange(true, std::memory_order_acq_rel)) {
      return;
    }
    coakka_http_host_service_t *active = nullptr;
    {
      std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
      active = service;
      if (active == nullptr) {
        return;
      }
      const coakka_http_host_status_t drain =
          coakka_http_host_service_begin_drain(active);
      if (drain != COAKKA_HTTP_HOST_OK && drain != COAKKA_HTTP_HOST_CLOSED) {
        report_shutdown_failure("begin drain", drain);
      }
    }

    coakka_http_host_drain_state_t drain_state =
        COAKKA_HTTP_HOST_DRAIN_NOT_STARTED;
    const coakka_http_host_status_t waited =
        coakka_http_host_service_wait_drained(active, kDrainTimeoutMs,
                                              &drain_state);
    if (waited != COAKKA_HTTP_HOST_OK && waited != COAKKA_HTTP_HOST_TIMEOUT &&
        waited != COAKKA_HTTP_HOST_CLOSED) {
      report_shutdown_failure("wait for drain", waited);
    }
    const coakka_http_host_status_t stopped_status =
        coakka_http_host_service_stop(active);
    if (stopped_status != COAKKA_HTTP_HOST_OK) {
      report_shutdown_failure("stop", stopped_status);
    }
    if (worker.joinable()) {
      worker.join();
    }
    {
      std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
      destroy_service();
      port = 0u;
    }
  }

  void run() noexcept {
    for (;;) {
      coakka_http_host_request_event_t event;
      coakka_http_host_request_event_init(&event);
      const coakka_http_host_status_t status =
          coakka_http_host_take_request(service, 1000u, &event);
      if (status == COAKKA_HTTP_HOST_TIMEOUT) {
        continue;
      }
      if (status == COAKKA_HTTP_HOST_CLOSED) {
        return;
      }
      if (status != COAKKA_HTTP_HOST_OK) {
        report_shutdown_failure("take request", status);
        return;
      }
      if (event.kind == COAKKA_HTTP_HOST_REQUEST &&
          event.handler_binding_id == kProfileBindingId) {
        try {
          handle_request(event);
        } catch (const std::exception &failure) {
          report_worker_exception(failure.what());
          respond_json(event.exchange, 500u,
                       R"({"ok":false,"error":"internal error"})");
        } catch (...) {
          report_worker_exception("unknown exception");
          respond_json(event.exchange, 500u,
                       R"({"ok":false,"error":"internal error"})");
        }
      }
      const coakka_http_host_status_t released =
          coakka_http_host_release_request(service, &event);
      if (released != COAKKA_HTTP_HOST_OK) {
        report_shutdown_failure("release request", released);
        return;
      }
    }
  }

  void handle_request(const coakka_http_host_request_event_t &event) {
    const auto authorization =
        coakka::camera_http::header_value(event, "authorization");
    constexpr std::string_view bearer_prefix = "Bearer ";
    const bool valid_authorization =
        authorization.has_value() &&
        authorization->starts_with(bearer_prefix) &&
        constant_time_equal(authorization->substr(bearer_prefix.size()),
                            config.authorization_token);
    if (!valid_authorization) {
      respond_json(event.exchange, 403u, R"({"ok":false,"error":"forbidden"})");
      return;
    }

    const std::string_view raw_body =
        coakka::camera_http::view(coakka_http_host_request_body(&event));
    coakka::camera_json::object_t body;
    std::string json_error;
    const bool parsed =
        coakka::camera_json::parse_object(raw_body, &body, &json_error);
    uint32_t width = 0u;
    uint32_t height = 0u;
    uint32_t fps = 0u;
    if (!parsed || !read_uint32(body, "width", &width) ||
        !read_uint32(body, "height", &height) ||
        !read_uint32(body, "fps", &fps)) {
      respond_json(event.exchange, 400u,
                   R"({"ok":false,"error":"invalid profile"})");
      return;
    }

    std::array<char, 192u> handler_error{};
    if (!config.profile_handler(config.handler_context, width, height, fps,
                                handler_error.data(), handler_error.size())) {
      coakka::camera_json::object_writer_t response;
      response.add_bool("ok", false);
      response.add_string("error", handler_error.data());
      respond_json(event.exchange, 409u, response.finish());
      return;
    }
    respond_json(event.exchange, 202u, R"({"ok":true})");
  }

  void respond_json(coakka_http_host_exchange_t exchange, uint32_t status,
                    std::string_view body) noexcept {
    const coakka_http_host_status_t result = coakka::camera_http::respond(
        service, exchange, status, "application/json", body);
    if (result != COAKKA_HTTP_HOST_OK &&
        result != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        result != COAKKA_HTTP_HOST_CLOSED) {
      report_shutdown_failure("respond", result);
    }
  }

  void destroy_service() noexcept {
    if (service == nullptr) {
      return;
    }
    const coakka_http_host_status_t status =
        coakka_http_host_service_destroy(&service);
    if (status != COAKKA_HTTP_HOST_OK) {
      report_shutdown_failure("destroy", status);
    }
  }

  static void
  report_shutdown_failure(const char *operation,
                          coakka_http_host_status_t status) noexcept {
    (void)std::fprintf(stderr, "camera control HTTP %s failed: %s\n", operation,
                       coakka_http_host_status_name(status));
  }

  static void report_worker_exception(const char *detail) noexcept {
    (void)std::fprintf(stderr, "camera control HTTP request failed: %s\n",
                       detail != nullptr ? detail : "unknown exception");
  }

  coakka_v2_camera_control_server_config_t config;
  coakka_http_host_service_t *service = nullptr;
  std::thread worker;
  std::mutex lifecycle_mutex;
  std::atomic<bool> stopped{false};
  uint16_t port = 0u;
};

} // namespace

struct coakka_v2_camera_control_server_t::impl_t {
  explicit impl_t(const coakka_v2_camera_control_server_config_t &config)
      : server(config) {}
  Server server;
};

std::unique_ptr<coakka_v2_camera_control_server_t>
coakka_v2_camera_control_server_t::create(
    const coakka_v2_camera_control_server_config_t &config) {
  if (config.bind_host.empty() || config.authorization_token.empty() ||
      config.profile_handler == nullptr) {
    return nullptr;
  }
  return std::unique_ptr<coakka_v2_camera_control_server_t>(
      new coakka_v2_camera_control_server_t(std::make_unique<impl_t>(config)));
}

coakka_v2_camera_control_server_t::coakka_v2_camera_control_server_t(
    std::unique_ptr<impl_t> impl)
    : impl_(std::move(impl)) {}

coakka_v2_camera_control_server_t::~coakka_v2_camera_control_server_t() {
  stop();
}

bool coakka_v2_camera_control_server_t::start(std::string *error) {
  return impl_ != nullptr && impl_->server.start(error);
}

void coakka_v2_camera_control_server_t::stop() {
  if (impl_ != nullptr) {
    impl_->server.stop();
  }
}

uint16_t coakka_v2_camera_control_server_t::bound_port() const noexcept {
  return impl_ != nullptr ? impl_->server.port : 0u;
}
