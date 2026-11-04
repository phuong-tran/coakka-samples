#include "stream_lane_camera_web_gateway.h"

#include "camera_http_host.h"
#include "camera_json.h"
#include "stream_lane_camera_recorder.h"
#include "stream_lane_camera_web_ui.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <system_error>
#include <thread>
#include <utility>
#include <vector>

namespace coakka::camera_web_gateway_detail {

constexpr size_t kQueueFrames = 4u;
constexpr size_t kAudioFrameBytes = 1920u;
constexpr size_t kMaxWebsocketPayload = 4ull * 1024ull;
constexpr size_t kFrameMetadataCapacity = 384u;
constexpr uint64_t kDrainTimeoutMs = 1000u;
constexpr uint64_t kStopTimeoutMs = 2000u;
constexpr uint64_t kRequestWaitMs = 60ull * 1000ull;
constexpr uint32_t kProfileRequestTimeoutMs = 2000u;
constexpr std::string_view kProfileLogicalTarget = "camera-profile-control";
constexpr auto kStatusInterval = std::chrono::milliseconds(500);
constexpr auto kProfileConfirmationTimeout = std::chrono::seconds(5);

constexpr uint64_t kUiRouteId = 1u;
constexpr uint64_t kHealthRouteId = 2u;
constexpr uint64_t kStreamRouteId = 3u;
constexpr uint64_t kControlRouteId = 4u;
constexpr uint64_t kUiBindingId = 1001u;
constexpr uint64_t kHealthBindingId = 1002u;
constexpr uint64_t kStreamBindingId = 1003u;
constexpr uint64_t kControlBindingId = 1004u;

struct FrameSlot {
  explicit FrameSlot(size_t capacity) : bytes(capacity) {}
  std::vector<uint8_t> bytes;
  size_t size = 0u;
  uint64_t sequence = 0u;
  uint64_t captured_mono_ns = 0u;
};

bool jpeg_dimensions(const uint8_t *data, size_t size, uint32_t *width,
                     uint32_t *height) noexcept {
  if (data == nullptr || size < 4u || width == nullptr || height == nullptr ||
      data[0] != 0xffu || data[1] != 0xd8u) {
    return false;
  }
  size_t offset = 2u;
  while (offset + 1u < size) {
    while (offset < size && data[offset] == 0xffu) {
      ++offset;
    }
    if (offset >= size) {
      return false;
    }
    const uint8_t marker = data[offset++];
    if (marker == 0xd9u || marker == 0xdau) {
      return false;
    }
    if (marker == 0x00u || marker == 0x01u ||
        (marker >= 0xd0u && marker <= 0xd8u)) {
      continue;
    }
    if (offset + 2u > size) {
      return false;
    }
    const size_t segment_size =
        (static_cast<size_t>(data[offset]) << 8u) | data[offset + 1u];
    if (segment_size < 2u || segment_size > size - offset) {
      return false;
    }
    const bool is_start_of_frame = marker >= 0xc0u && marker <= 0xcfu &&
                                   marker != 0xc4u && marker != 0xc8u &&
                                   marker != 0xccu;
    if (is_start_of_frame) {
      if (segment_size < 7u) {
        return false;
      }
      *height =
          (static_cast<uint32_t>(data[offset + 3u]) << 8u) | data[offset + 4u];
      *width =
          (static_cast<uint32_t>(data[offset + 5u]) << 8u) | data[offset + 6u];
      return *width != 0u && *height != 0u;
    }
    offset += segment_size;
  }
  return false;
}

class DisplayQueue {
public:
  explicit DisplayQueue(size_t capacity) {
    slots_.reserve(kQueueFrames);
    for (size_t index = 0u; index < kQueueFrames; ++index) {
      slots_.emplace_back(capacity);
    }
  }

  coakka_v2_status_t push(const uint8_t *data,
                          const coakka_v2_stream_frame_t *frame,
                          bool *became_nonempty) noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    if (became_nonempty == nullptr || !valid_frame(data, frame) ||
        (expected_sequence_ != 0u && frame->sequence != expected_sequence_)) {
      return COAKKA_V2_ERR_IO;
    }
    *became_nonempty = count_ == 0u;
    expected_sequence_ = frame->sequence + 1u;
    source_drops_ += frame->dropped_before;
    if (count_ == slots_.size()) {
      head_ = (head_ + 1u) % slots_.size();
      --count_;
      ++queue_drops_;
    }
    FrameSlot &slot = slots_[(head_ + count_) % slots_.size()];
    std::memcpy(slot.bytes.data(), data, frame->size);
    slot.size = frame->size;
    slot.sequence = frame->sequence;
    slot.captured_mono_ns = frame->captured_mono_ns;
    ++count_;
    ++received_frames_;
    received_bytes_ += frame->size;
    return COAKKA_V2_OK;
  }

  bool take_latest(uint8_t *destination, size_t capacity, size_t *size,
                   uint64_t *sequence, uint64_t *captured_mono_ns) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (count_ == 0u || destination == nullptr || size == nullptr ||
        sequence == nullptr || captured_mono_ns == nullptr) {
      return false;
    }
    const size_t latest = (head_ + count_ - 1u) % slots_.size();
    const FrameSlot &slot = slots_[latest];
    if (slot.size > capacity) {
      return false;
    }
    if (count_ > 1u) {
      queue_drops_ += count_ - 1u;
    }
    std::memcpy(destination, slot.bytes.data(), slot.size);
    *size = slot.size;
    *sequence = slot.sequence;
    *captured_mono_ns = slot.captured_mono_ns;
    head_ = (latest + 1u) % slots_.size();
    count_ = 0u;
    return true;
  }

  void snapshot(uint64_t *frames, uint64_t *bytes, uint64_t *source_drops,
                uint64_t *queue_drops) const {
    std::lock_guard<std::mutex> lock(mutex_);
    *frames = received_frames_;
    *bytes = received_bytes_;
    *source_drops = source_drops_;
    *queue_drops = queue_drops_;
  }

private:
  bool valid_frame(const uint8_t *data,
                   const coakka_v2_stream_frame_t *frame) const noexcept {
    return data != nullptr && frame != nullptr && frame->size >= 4u &&
           frame->size <= slots_[0].bytes.size() && data[0] == 0xffu &&
           data[1] == 0xd8u && data[frame->size - 2u] == 0xffu &&
           data[frame->size - 1u] == 0xd9u;
  }

  mutable std::mutex mutex_;
  std::vector<FrameSlot> slots_;
  size_t head_ = 0u;
  size_t count_ = 0u;
  uint64_t expected_sequence_ = 0u;
  uint64_t received_frames_ = 0u;
  uint64_t received_bytes_ = 0u;
  uint64_t source_drops_ = 0u;
  uint64_t queue_drops_ = 0u;
};

struct Gateway {
  explicit Gateway(const coakka_v2_camera_web_gateway_config_t &value)
      : config(value), display(value.max_frame_bytes),
        recorder(value.max_frame_bytes, value.fps, value.recording_directory,
                 value.ffmpeg_binary),
        frame_bytes(value.max_frame_bytes),
        frame_chunk(value.max_frame_bytes + kFrameMetadataCapacity) {}

  ~Gateway() { stop(); }

  bool start(std::string *error) {
    if (error == nullptr) {
      return false;
    }
    const coakka_http_host_capabilities_t required =
        COAKKA_HTTP_HOST_CAPABILITY_INBOUND |
        COAKKA_HTTP_HOST_CAPABILITY_RESPONSE_STREAM |
        COAKKA_HTTP_HOST_CAPABILITY_WEBSOCKET |
        (profile_outbound_enabled() ? COAKKA_HTTP_HOST_CAPABILITY_OUTBOUND
                                    : 0u);
    if ((coakka_http_host_features() & required) != required) {
      *error = "loaded HTTP Runtime lacks a camera HTTP capability";
      return false;
    }
    if (!recorder.start_worker(error)) {
      return false;
    }

    std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
    if (service != nullptr || stopped.load(std::memory_order_acquire)) {
      *error = "web gateway can only be started once";
      recorder.shutdown();
      return false;
    }
    if (!create_service(error)) {
      recorder.shutdown();
      return false;
    }
    try {
      request_worker = std::thread([this] { run_requests(); });
      websocket_worker = std::thread([this] { run_websockets(); });
    } catch (const std::system_error &failure) {
      *error = "HTTP event reader start failed: " + std::string(failure.what());
      (void)coakka_http_host_service_stop(service);
      if (request_worker.joinable()) {
        request_worker.join();
      }
      if (websocket_worker.joinable()) {
        websocket_worker.join();
      }
      destroy_service();
      recorder.shutdown();
      return false;
    }
    return true;
  }

  void stop() noexcept {
    if (stopped.exchange(true, std::memory_order_acq_rel)) {
      return;
    }
    recorder.shutdown();
    stopping.store(true, std::memory_order_release);

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
        report_failure("begin drain", drain);
      }
      const coakka_http_host_status_t interrupted =
          coakka_http_host_interrupt_requests(active);
      if (interrupted != COAKKA_HTTP_HOST_OK &&
          interrupted != COAKKA_HTTP_HOST_CLOSED) {
        report_failure("interrupt request reader", interrupted);
      }
    }

    coakka_http_host_drain_state_t drain_state =
        COAKKA_HTTP_HOST_DRAIN_NOT_STARTED;
    const coakka_http_host_status_t waited =
        coakka_http_host_service_wait_drained(active, kDrainTimeoutMs,
                                              &drain_state);
    if (waited != COAKKA_HTTP_HOST_OK && waited != COAKKA_HTTP_HOST_TIMEOUT &&
        waited != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("wait for drain", waited);
    }
    const coakka_http_host_status_t stopped_status =
        coakka_http_host_service_stop(active);
    if (stopped_status != COAKKA_HTTP_HOST_OK) {
      report_failure("stop", stopped_status);
    }
    if (request_worker.joinable()) {
      request_worker.join();
    }
    if (websocket_worker.joinable()) {
      websocket_worker.join();
    }
    {
      std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
      destroy_service();
      bound_port.store(0u, std::memory_order_release);
    }
  }

  bool create_service(std::string *error) {
    coakka_http_host_configuration_t configuration;
    coakka_http_host_configuration_init(&configuration);
    configuration.bind_address = coakka::camera_http::bytes(config.bind_host);
    configuration.port = config.bind_port;
    configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
    configuration.security = COAKKA_HTTP_HOST_SECURITY_PLAINTEXT;
    configuration.reserve_websocket = 1u;
    configuration.omit_buffered_success_events = 1u;
    configuration.limits.max_connections = 8u;
    configuration.limits.max_active_requests = 8u;
    configuration.limits.request_queue_capacity = 8u;
    configuration.limits.completion_queue_capacity = 8u;
    configuration.limits.terminal_queue_capacity = 8u;
    configuration.limits.max_header_count = 24u;
    configuration.limits.max_header_bytes = 8u * 1024u;
    configuration.limits.max_request_body_bytes = kMaxWebsocketPayload;
    // One MJPEG response is long-lived, but each accepted DATA chunk must fit
    // the same Core-owned per-response ceiling as the largest admitted frame.
    const uint64_t max_stream_chunk_bytes =
        static_cast<uint64_t>(config.max_frame_bytes) + kFrameMetadataCapacity;
    configuration.limits.max_response_body_bytes = max_stream_chunk_bytes;
    configuration.limits.response_stream_chunk_slots = 2u;
    configuration.limits.response_stream_max_retained_items = 2u;
    configuration.limits.response_stream_max_chunk_bytes =
        max_stream_chunk_bytes;
    configuration.limits.response_stream_max_retained_bytes =
        2ull * max_stream_chunk_bytes;
    configuration.limits.response_stream_max_total_retained_bytes =
        2ull * max_stream_chunk_bytes;
    configuration.limits.handler_timeout_ms = 30ull * 1000ull;
    configuration.limits.drain_timeout_ms = kDrainTimeoutMs;
    configuration.limits.stop_timeout_ms = kStopTimeoutMs;
    configuration.limits.response_write_timeout_ms = 2000u;

    coakka_http_host_outbound_endpoint_t profile_endpoint;
    coakka_http_host_outbound_endpoint_init(&profile_endpoint);
    coakka_http_host_outbound_target_t profile_target;
    coakka_http_host_outbound_target_init(&profile_target);
    const std::string profile_authority =
        config.profile_control_host + ":" +
        std::to_string(config.profile_control_port);
    if (profile_outbound_enabled()) {
      profile_endpoint.weight = 1u;
      profile_endpoint.node_id =
          coakka::camera_http::bytes("camera-profile-endpoint");
      profile_endpoint.connect_host =
          coakka::camera_http::bytes(config.profile_control_host);
      profile_endpoint.connect_port = config.profile_control_port;
      profile_endpoint.http_authority =
          coakka::camera_http::bytes(profile_authority);
      profile_endpoint.security = COAKKA_HTTP_HOST_SECURITY_PLAINTEXT;
      profile_endpoint.available = 1u;
      profile_target.strategy = COAKKA_HTTP_HOST_OUTBOUND_SINGLE_OWNER;
      profile_target.name = coakka::camera_http::bytes(kProfileLogicalTarget);
      profile_target.generation = 1u;
      profile_target.endpoints = &profile_endpoint;
      profile_target.endpoint_count = 1u;
      configuration.outbound_target_count = 1u;
      configuration.outbound_targets = &profile_target;
    }

    std::array<coakka_http_host_route_t, 4u> routes;
    for (coakka_http_host_route_t &route : routes) {
      coakka_http_host_route_init(&route);
      route.method = coakka::camera_http::bytes("GET");
    }
    routes[0].route_id = kUiRouteId;
    routes[0].handler_binding_id = kUiBindingId;
    routes[0].encoded_path_pattern = coakka::camera_http::bytes("/");
    routes[1].route_id = kHealthRouteId;
    routes[1].handler_binding_id = kHealthBindingId;
    routes[1].encoded_path_pattern = coakka::camera_http::bytes("/healthz");
    routes[2].route_id = kStreamRouteId;
    routes[2].handler_binding_id = kStreamBindingId;
    routes[2].encoded_path_pattern =
        coakka::camera_http::bytes("/stream.mjpeg");
    routes[3].route_id = kControlRouteId;
    routes[3].handler_binding_id = kControlBindingId;
    routes[3].encoded_path_pattern = coakka::camera_http::bytes("/ws/control");
    routes[3].flags = COAKKA_HTTP_HOST_ROUTE_WEBSOCKET;

    uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
    coakka_http_host_status_t status = coakka_http_host_service_create(
        &configuration, routes.data(), static_cast<uint32_t>(routes.size()),
        &service, &failed_route);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "HTTP service create failed: " +
               coakka::camera_http::status_text(status);
      service = nullptr;
      return false;
    }
    coakka_http_host_issue_t issue;
    coakka_http_host_issue_init(&issue);
    status = coakka_http_host_service_start(service, &issue);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "HTTP service start failed: " +
               coakka::camera_http::startup_error(status, issue);
      destroy_service();
      return false;
    }
    uint16_t port = 0u;
    status = coakka_http_host_service_port(service, &port);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "HTTP service port query failed: " +
               coakka::camera_http::status_text(status);
      (void)coakka_http_host_service_stop(service);
      destroy_service();
      return false;
    }
    bound_port.store(port, std::memory_order_release);
    return true;
  }

  bool request_profile(const coakka_v2_camera_profile_request_t &request,
                       std::string *error) {
    if (error == nullptr || !profile_outbound_enabled()) {
      return false;
    }
    std::lock_guard<std::mutex> outbound_lock(outbound_mutex);
    if (outbound_reader_failed) {
      *error = "camera profile HTTP reader is unavailable";
      return false;
    }
    const std::string body = "{\"width\":" + std::to_string(request.width) +
                             ",\"height\":" + std::to_string(request.height) +
                             ",\"fps\":" + std::to_string(request.fps) + "}";
    const std::string authorization =
        "Bearer " + config.profile_authorization_token;
    const std::array<coakka_http_host_header_t, 2u> headers{{
        {coakka::camera_http::bytes("authorization"),
         coakka::camera_http::bytes(authorization)},
        {coakka::camera_http::bytes("content-type"),
         coakka::camera_http::bytes("application/json")},
    }};
    coakka_http_host_outbound_request_t outbound;
    coakka_http_host_outbound_request_init(&outbound);
    outbound.timeout_ms = kProfileRequestTimeoutMs;
    outbound.logical_target = coakka::camera_http::bytes(kProfileLogicalTarget);
    outbound.method = coakka::camera_http::bytes("POST");
    outbound.target = coakka::camera_http::bytes("/v1/profile");
    outbound.headers = headers.data();
    outbound.header_count = static_cast<uint32_t>(headers.size());
    outbound.body = coakka::camera_http::bytes(body);

    coakka_http_host_outbound_call_t call{};
    coakka_http_host_status_t status =
        coakka_http_host_submit_outbound(service, &outbound, &call);
    if (status != COAKKA_HTTP_HOST_OK) {
      *error = "camera profile request admission failed: " +
               coakka::camera_http::status_text(status);
      return false;
    }

    coakka_http_host_outbound_event_t event;
    coakka_http_host_outbound_event_init(&event);
    status = coakka_http_host_take_outbound(
        service, static_cast<uint64_t>(kProfileRequestTimeoutMs) + 1000u,
        &event);
    if (status == COAKKA_HTTP_HOST_TIMEOUT) {
      (void)coakka_http_host_cancel_outbound(service, call);
      coakka_http_host_outbound_event_init(&event);
      status = coakka_http_host_take_outbound(service, 1000u, &event);
    }
    if (status != COAKKA_HTTP_HOST_OK) {
      outbound_reader_failed = true;
      *error = "camera profile response wait failed: " +
               coakka::camera_http::status_text(status);
      return false;
    }

    const bool matching_call = event.call.slot == call.slot &&
                               event.call.generation == call.generation;
    const bool accepted = matching_call &&
                          event.reason == COAKKA_HTTP_HOST_OUTBOUND_RESPONSE &&
                          event.response_status == 202u;
    const uint16_t response_status = event.response_status;
    const std::string diagnostic(coakka::camera_http::view(event.diagnostic));
    const coakka_http_host_status_t released =
        coakka_http_host_release_outbound(service, &event);
    if (released != COAKKA_HTTP_HOST_OK) {
      outbound_reader_failed = true;
      *error = "camera profile response release failed: " +
               coakka::camera_http::status_text(released);
      return false;
    }
    if (!matching_call) {
      outbound_reader_failed = true;
      *error = "camera profile response identity did not match its request";
      return false;
    }
    if (!accepted) {
      *error = response_status != 0u
                   ? "camera rejected the requested profile with HTTP " +
                         std::to_string(response_status)
                   : "camera profile request failed: " + diagnostic;
    }
    return accepted;
  }

  void run_requests() noexcept {
    for (;;) {
      coakka_http_host_request_event_t event;
      coakka_http_host_request_event_init(&event);
      const coakka_http_host_status_t status =
          coakka_http_host_take_request(service, kRequestWaitMs, &event);
      if (status == COAKKA_HTTP_HOST_CLOSED) {
        return;
      }
      if (status != COAKKA_HTTP_HOST_OK && status != COAKKA_HTTP_HOST_TIMEOUT) {
        report_failure("take request", status);
        return;
      }
      if (status == COAKKA_HTTP_HOST_OK) {
        bool handler_failed = false;
        try {
          handle_request_event(event);
        } catch (const std::exception &failure) {
          report_worker_exception("request", failure.what());
          handler_failed = true;
        } catch (...) {
          report_worker_exception("request", "unknown exception");
          handler_failed = true;
        }
        if (handler_failed && event.kind == COAKKA_HTTP_HOST_REQUEST) {
          respond(event.exchange, 500u, "text/plain", "internal error\n");
        }
        const coakka_http_host_status_t released =
            coakka_http_host_release_request(service, &event);
        if (released != COAKKA_HTTP_HOST_OK) {
          report_failure("release request", released);
          return;
        }
        if (handler_failed) {
          return;
        }
      }
      try {
        if (stopping.load(std::memory_order_acquire) ||
            !livestream_enabled.load(std::memory_order_acquire)) {
          finish_stream();
        } else {
          pump_stream();
        }
      } catch (const std::exception &failure) {
        report_worker_exception("request", failure.what());
        return;
      } catch (...) {
        report_worker_exception("request", "unknown exception");
        return;
      }
    }
  }

  void handle_request_event(const coakka_http_host_request_event_t &event) {
    if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
      handle_request(event);
      return;
    }
    if (event.kind == COAKKA_HTTP_HOST_RESPONSE_WRITABLE &&
        stream_active.load(std::memory_order_acquire) &&
        coakka::camera_http::same_exchange(event.exchange, stream_exchange)) {
      stream_writable = true;
      return;
    }
    if ((event.kind == COAKKA_HTTP_HOST_REQUEST_CANCELLED ||
         event.kind == COAKKA_HTTP_HOST_REQUEST_TERMINAL) &&
        stream_active.load(std::memory_order_acquire) &&
        coakka::camera_http::same_exchange(event.exchange, stream_exchange)) {
      clear_stream();
    }
  }

  void handle_request(const coakka_http_host_request_event_t &event) {
    switch (event.handler_binding_id) {
    case kUiBindingId:
      respond(event.exchange, 200u, "text/html; charset=utf-8",
              coakka_v2_camera_web_ui_html());
      return;
    case kHealthBindingId:
      respond(event.exchange, 200u, "application/json", R"({"ok":true})");
      return;
    case kStreamBindingId:
      start_stream(event.exchange);
      return;
    case kControlBindingId:
      accept_websocket(event);
      return;
    default:
      respond(event.exchange, 404u, "text/plain", "not found\n");
    }
  }

  void start_stream(coakka_http_host_exchange_t exchange) {
    if (!livestream_enabled.load(std::memory_order_acquire)) {
      respond(exchange, 503u, "text/plain", "livestream is disabled\n");
      return;
    }
    bool expected = false;
    if (!stream_active.compare_exchange_strong(expected, true,
                                               std::memory_order_acq_rel)) {
      respond(exchange, 409u, "text/plain", "a viewer is already active\n");
      return;
    }
    const std::array<coakka_http_host_header_t, 3u> headers{{
        {coakka::camera_http::bytes("content-type"),
         coakka::camera_http::bytes(
             "multipart/x-mixed-replace; boundary=coakka-frame")},
        {coakka::camera_http::bytes("cache-control"),
         coakka::camera_http::bytes("no-store, no-cache, must-revalidate")},
        {coakka::camera_http::bytes("pragma"),
         coakka::camera_http::bytes("no-cache")},
    }};
    coakka_http_host_response_t head;
    coakka_http_host_response_init(&head);
    head.status_code = 200u;
    head.headers = headers.data();
    head.header_count = static_cast<uint32_t>(headers.size());
    const coakka_http_host_status_t status =
        coakka_http_host_start_response_stream(service, exchange, &head);
    if (status != COAKKA_HTTP_HOST_OK) {
      clear_stream();
      if (status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
          status != COAKKA_HTTP_HOST_CLOSED) {
        report_failure("start MJPEG response", status);
      }
      return;
    }
    stream_exchange = exchange;
    stream_writable = false;
  }

  void accept_websocket(const coakka_http_host_request_event_t &event) {
    const auto host = coakka::camera_http::header_value(event, "host");
    const auto origin = coakka::camera_http::header_value(event, "origin");
    // HTTP Runtime projects Host as the request authority. Keep a header
    // fallback for transports that also preserve the original field.
    const std::string_view authority =
        host.has_value() ? *host
                         : coakka::camera_http::view(
                               coakka_http_host_request_authority(&event));
    const std::string expected_origin =
        authority.empty() ? std::string{} : "http://" + std::string(authority);
    bool expected_pending = false;
    const bool reserved = websocket_upgrade_pending.compare_exchange_strong(
        expected_pending, true, std::memory_order_acq_rel);
    if (!reserved || control_connected.load(std::memory_order_acquire) ||
        !origin.has_value() || *origin != expected_origin) {
      if (reserved) {
        websocket_upgrade_pending.store(false, std::memory_order_release);
      }
      respond(event.exchange, 403u, "text/plain", "forbidden\n");
      return;
    }
    const coakka_http_host_status_t status = coakka_http_host_accept_websocket(
        service, event.exchange,
        coakka::camera_http::bytes(std::string_view{}));
    if (status != COAKKA_HTTP_HOST_OK) {
      websocket_upgrade_pending.store(false, std::memory_order_release);
      if (status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
          status != COAKKA_HTTP_HOST_CLOSED) {
        report_failure("accept WebSocket", status);
      }
    }
  }

  void pump_stream() {
    if (!stream_active.load(std::memory_order_acquire) || !stream_writable) {
      return;
    }
    size_t frame_size = 0u;
    uint64_t sequence = 0u;
    uint64_t captured_mono_ns = 0u;
    if (!display.take_latest(frame_bytes.data(), frame_bytes.size(),
                             &frame_size, &sequence, &captured_mono_ns)) {
      return;
    }
    const int header_size = std::snprintf(
        reinterpret_cast<char *>(frame_chunk.data()), kFrameMetadataCapacity,
        "--coakka-frame\r\nContent-Type: image/jpeg\r\n"
        "Content-Length: %zu\r\nX-CoAkka-Sequence: %llu\r\n"
        "X-Captured-Monotonic-Ns: %llu\r\n\r\n",
        frame_size, static_cast<unsigned long long>(sequence),
        static_cast<unsigned long long>(captured_mono_ns));
    if (header_size <= 0 ||
        static_cast<size_t>(header_size) >= kFrameMetadataCapacity ||
        static_cast<size_t>(header_size) + frame_size + 2u >
            frame_chunk.size()) {
      finish_stream();
      return;
    }
    const size_t payload_offset = static_cast<size_t>(header_size);
    std::memcpy(frame_chunk.data() + payload_offset, frame_bytes.data(),
                frame_size);
    frame_chunk[payload_offset + frame_size] = '\r';
    frame_chunk[payload_offset + frame_size + 1u] = '\n';
    const size_t chunk_size = payload_offset + frame_size + 2u;
    const coakka_http_host_status_t status =
        coakka_http_host_write_response_stream(
            service, stream_exchange,
            {frame_chunk.data(), static_cast<size_t>(chunk_size)});
    if (status == COAKKA_HTTP_HOST_OK) {
      stream_writable = false;
      browser_frames.fetch_add(1u, std::memory_order_relaxed);
      browser_bytes.fetch_add(frame_size, std::memory_order_relaxed);
      return;
    }
    if (status == COAKKA_HTTP_HOST_CAPACITY_EXHAUSTED) {
      stream_writable = false;
      return;
    }
    if (status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        status != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("write MJPEG response", status);
    }
    clear_stream();
  }

  void finish_stream() noexcept {
    if (!stream_active.load(std::memory_order_acquire)) {
      return;
    }
    const coakka_http_host_status_t status =
        coakka_http_host_finish_response_stream(service, stream_exchange,
                                                nullptr, 0u);
    if (status != COAKKA_HTTP_HOST_OK &&
        status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        status != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("finish MJPEG response", status);
    }
    clear_stream();
  }

  void clear_stream() noexcept {
    stream_active.store(false, std::memory_order_release);
    stream_writable = false;
    stream_exchange = {};
  }

  void run_websockets() noexcept {
    auto next_status = std::chrono::steady_clock::now() + kStatusInterval;
    coakka_http_host_websocket_t session{};
    bool close_requested = false;
    for (;;) {
      const auto now = std::chrono::steady_clock::now();
      const auto remaining =
          next_status > now
              ? std::chrono::duration_cast<std::chrono::milliseconds>(
                    next_status - now)
              : std::chrono::milliseconds::zero();
      const uint64_t timeout_ms =
          stopping.load(std::memory_order_acquire)
              ? 100u
              : static_cast<uint64_t>(std::max<int64_t>(0, remaining.count()));
      coakka_http_host_websocket_event_t event;
      coakka_http_host_websocket_event_init(&event);
      const coakka_http_host_status_t status =
          coakka_http_host_take_websocket(service, timeout_ms, &event);
      if (status == COAKKA_HTTP_HOST_CLOSED) {
        return;
      }
      if (status != COAKKA_HTTP_HOST_OK && status != COAKKA_HTTP_HOST_TIMEOUT) {
        report_failure("take WebSocket", status);
        return;
      }
      if (status == COAKKA_HTTP_HOST_OK) {
        bool handler_failed = false;
        try {
          handle_websocket_event(event, &session, &close_requested);
        } catch (const std::exception &failure) {
          report_worker_exception("WebSocket", failure.what());
          handler_failed = true;
        } catch (...) {
          report_worker_exception("WebSocket", "unknown exception");
          handler_failed = true;
        }
        const coakka_http_host_status_t released =
            coakka_http_host_release_websocket(service, &event);
        if (released != COAKKA_HTTP_HOST_OK) {
          report_failure("release WebSocket", released);
          return;
        }
        if (handler_failed) {
          abort_websocket(session);
          return;
        }
      }
      try {
        expire_profile_request();
        const auto after_event = std::chrono::steady_clock::now();
        if (!stopping.load(std::memory_order_acquire) &&
            control_connected.load(std::memory_order_acquire) &&
            after_event >= next_status) {
          send_status(session);
          next_status = after_event + kStatusInterval;
        }
      } catch (const std::exception &failure) {
        report_worker_exception("WebSocket", failure.what());
        abort_websocket(session);
        return;
      } catch (...) {
        report_worker_exception("WebSocket", "unknown exception");
        abort_websocket(session);
        return;
      }
      if (stopping.load(std::memory_order_acquire) &&
          control_connected.load(std::memory_order_acquire) &&
          !close_requested) {
        close_requested = true;
        const coakka_http_host_status_t closed =
            coakka_http_host_close_websocket(
                service, session, 1001u,
                coakka::camera_http::bytes("camera gateway stopping"));
        if (closed != COAKKA_HTTP_HOST_OK &&
            closed != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
            closed != COAKKA_HTTP_HOST_CLOSED) {
          report_failure("close WebSocket", closed);
        }
      }
    }
  }

  void handle_websocket_event(const coakka_http_host_websocket_event_t &event,
                              coakka_http_host_websocket_t *session,
                              bool *close_requested) {
    switch (event.kind) {
    case COAKKA_HTTP_HOST_WEBSOCKET_OPEN:
      *session = event.session;
      *close_requested = false;
      websocket_upgrade_pending.store(false, std::memory_order_release);
      control_connected.store(true, std::memory_order_release);
      send_status(*session);
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_TEXT:
      if (!coakka::camera_http::same_websocket(event.session, *session)) {
        return;
      }
      if (event.data.size > kMaxWebsocketPayload) {
        close_websocket(*session, 1009u, "control message is too large");
        *close_requested = true;
        return;
      }
      handle_command(*session, coakka::camera_http::view(event.data));
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_BINARY:
      close_websocket(event.session, 1003u, "binary control is unsupported");
      *close_requested = true;
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_PING:
      send_websocket(event.session, COAKKA_HTTP_HOST_WEBSOCKET_PONG,
                     coakka::camera_http::view(event.data));
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_CLOSE:
      if (coakka::camera_http::same_websocket(event.session, *session)) {
        *session = {};
        *close_requested = false;
        control_connected.store(false, std::memory_order_release);
      }
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_ACCEPT_FAILED:
      websocket_upgrade_pending.store(false, std::memory_order_release);
      return;
    case COAKKA_HTTP_HOST_WEBSOCKET_PONG:
    case COAKKA_HTTP_HOST_WEBSOCKET_WRITABLE:
    default:
      return;
    }
  }

  std::string transport_name() const {
    switch (transport_state.load(std::memory_order_acquire)) {
    case COAKKA_V2_STREAM_STATE_CONNECTING:
      return "connecting";
    case COAKKA_V2_STREAM_STATE_ACTIVE:
      return "active";
    case COAKKA_V2_STREAM_STATE_ENDED:
    case COAKKA_V2_STREAM_STATE_REJECTED:
    case COAKKA_V2_STREAM_STATE_FAILED:
    case COAKKA_V2_STREAM_STATE_CANCELED:
      return "terminal";
    default:
      return "offline";
    }
  }

  bool audio_available() const {
    return audio_transport_state.load(std::memory_order_acquire) ==
           COAKKA_V2_STREAM_STATE_ACTIVE;
  }

  void confirm_profile_frame(const uint8_t *data, size_t size) {
    uint32_t expected_width = 0u;
    uint32_t expected_height = 0u;
    {
      std::lock_guard<std::mutex> lock(profile_mutex);
      if (!profile_request_pending || !profile_request_taken ||
          profile_state != "confirming") {
        return;
      }
      expected_width = requested_width;
      expected_height = requested_height;
    }
    uint32_t frame_width = 0u;
    uint32_t frame_height = 0u;
    if (!jpeg_dimensions(data, size, &frame_width, &frame_height) ||
        frame_width != expected_width || frame_height != expected_height) {
      return;
    }
    std::lock_guard<std::mutex> lock(profile_mutex);
    if (!profile_request_pending || profile_state != "confirming" ||
        requested_width != frame_width || requested_height != frame_height) {
      return;
    }
    profile_width = requested_width;
    profile_height = requested_height;
    // A matching JPEG confirms dimensions only. The Pi's 202 response means
    // queued intent, and a JPEG carries no authoritative frame-rate fact.
    profile_state = "active";
    last_profile_error.clear();
    profile_request_pending = false;
    profile_request_taken = false;
  }

  void expire_profile_request() {
    std::lock_guard<std::mutex> lock(profile_mutex);
    if (!profile_request_pending ||
        std::chrono::steady_clock::now() - profile_requested_at <
            kProfileConfirmationTimeout) {
      return;
    }
    profile_request_pending = false;
    profile_request_taken = false;
    profile_state = "failed";
    last_profile_error = "camera profile confirmation timed out";
  }

  std::string status_json(std::string_view request_id, bool ok,
                          std::string_view error) const {
    uint64_t received_frames = 0u;
    uint64_t received_bytes = 0u;
    uint64_t source_drops = 0u;
    uint64_t display_drops = 0u;
    display.snapshot(&received_frames, &received_bytes, &source_drops,
                     &display_drops);
    const coakka_v2_camera_recorder_snapshot_t recording = recorder.snapshot();
    uint32_t width = 0u;
    uint32_t height = 0u;
    uint32_t fps = 0u;
    std::string profile_status;
    std::string profile_error;
    {
      std::lock_guard<std::mutex> lock(profile_mutex);
      width = profile_width;
      height = profile_height;
      fps = requested_fps;
      profile_status = profile_state;
      profile_error = last_profile_error;
    }
    coakka::camera_json::object_writer_t status(768u);
    status.add_string("type", "status");
    status.add_string("request_id", request_id);
    status.add_bool("ok", ok);
    status.add_string("error", error);
    status.add_string("control",
                      control_connected.load(std::memory_order_acquire)
                          ? "connected"
                          : "disconnected");
    status.add_string("transport", transport_name());
    status.add_bool("livestream_enabled",
                    livestream_enabled.load(std::memory_order_acquire));
    status.add_bool("viewer_connected",
                    stream_active.load(std::memory_order_acquire));
    status.add_bool("audio_available", audio_available());
    status.add_uint64("profile_width", width);
    status.add_uint64("profile_height", height);
    status.add_uint64("profile_requested_fps", fps);
    status.add_string("profile_state", profile_status);
    status.add_string("profile_error", profile_error);
    status.add_uint64("received_frames", received_frames);
    status.add_uint64("received_bytes", received_bytes);
    status.add_uint64("source_drops", source_drops);
    status.add_uint64("display_drops", display_drops);
    status.add_uint64("browser_frames",
                      browser_frames.load(std::memory_order_relaxed));
    status.add_uint64("browser_bytes",
                      browser_bytes.load(std::memory_order_relaxed));
    status.add_string("recording_state", recording.state);
    status.add_string("recording_path", recording.path);
    status.add_string("recording_error", recording.error);
    status.add_uint64("recording_frames", recording.frames);
    status.add_uint64("recording_bytes", recording.bytes);
    status.add_uint64("recording_drops", recording.queue_drops);
    status.add_bool("recording_with_audio", recording.with_audio);
    status.add_uint64("recording_audio_frames", recording.audio_frames);
    status.add_uint64("recording_audio_bytes", recording.audio_bytes);
    status.add_uint64("recording_audio_drops", recording.audio_queue_drops);
    return status.finish();
  }

  void send_status(coakka_http_host_websocket_t session,
                   std::string_view request_id = {}, bool ok = true,
                   std::string_view error = {}) {
    send_websocket(session, COAKKA_HTTP_HOST_WEBSOCKET_TEXT,
                   status_json(request_id, ok, error));
  }

  static bool command_uint32(const coakka::camera_json::object_t &command,
                             const char *name, uint32_t *out) {
    return coakka::camera_json::value_as_uint32(command.find(name), out);
  }

  static bool supported_profile(uint32_t width, uint32_t height, uint32_t fps) {
    return fps == 30u && ((width == 640u && height == 480u) ||
                          (width == 800u && height == 600u) ||
                          (width == 1280u && height == 720u) ||
                          (width == 1920u && height == 1080u));
  }

  void handle_command(coakka_http_host_websocket_t session,
                      std::string_view payload) {
    coakka::camera_json::object_t command;
    std::string json_error;
    const bool parsed =
        coakka::camera_json::parse_object(payload, &command, &json_error);
    std::string id;
    std::string name;
    if (!parsed ||
        !coakka::camera_json::value_as_string(command.find("id"), &id) ||
        !coakka::camera_json::value_as_string(command.find("command"), &name)) {
      send_status(session, {}, false, "expected string id and command");
      return;
    }
    if (id.empty() || id.size() > 64u || name.empty() || name.size() > 64u) {
      send_status(session, id, false, "id or command is out of bounds");
      return;
    }

    bool ok = true;
    std::string error;
    if (name == "status.get") {
    } else if (name == "livestream.start") {
      livestream_enabled.store(true, std::memory_order_release);
      wake_request_reader();
    } else if (name == "livestream.stop") {
      livestream_enabled.store(false, std::memory_order_release);
      wake_request_reader();
    } else if (name == "recording.start") {
      bool with_audio = false;
      const auto *audio = command.find("audio");
      if (audio != nullptr &&
          !coakka::camera_json::value_as_bool(audio, &with_audio)) {
        ok = false;
        error = "audio must be a boolean";
      }
      if (ok && with_audio && !audio_available()) {
        ok = false;
        error = "camera audio is unavailable";
      }
      if (ok) {
        ok = recorder.request_start(with_audio, &error);
      }
    } else if (name == "recording.stop") {
      ok = recorder.request_stop(&error);
    } else if (name == "resolution.set") {
      uint32_t width = 0u;
      uint32_t height = 0u;
      uint32_t fps = 0u;
      const coakka_v2_camera_recorder_snapshot_t recording =
          recorder.snapshot();
      if (recording.state == "starting" || recording.state == "recording" ||
          recording.state == "finalizing") {
        ok = false;
        error = "camera profile cannot change while recording";
      } else if (!command_uint32(command, "width", &width) ||
                 !command_uint32(command, "height", &height) ||
                 !command_uint32(command, "fps", &fps) ||
                 !supported_profile(width, height, fps)) {
        ok = false;
        error = "unsupported camera profile";
      } else {
        std::lock_guard<std::mutex> lock(profile_mutex);
        if (profile_request_pending) {
          ok = false;
          error = "a profile change is already pending";
        } else {
          ++profile_request_sequence;
          requested_width = width;
          requested_height = height;
          requested_fps = fps;
          profile_request_pending = true;
          profile_request_taken = false;
          profile_requested_at = std::chrono::steady_clock::now();
          profile_state = "switching";
          last_profile_error.clear();
        }
      }
    } else if (name == "session.disconnect") {
      disconnect.store(true, std::memory_order_release);
    } else {
      ok = false;
      error = "unknown command";
    }
    send_status(session, id, ok, error);
  }

  void respond(coakka_http_host_exchange_t exchange, uint32_t status,
               std::string_view content_type, std::string_view body) noexcept {
    const coakka_http_host_status_t result = coakka::camera_http::respond(
        service, exchange, status, content_type, body);
    if (result != COAKKA_HTTP_HOST_OK &&
        result != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        result != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("respond", result);
    }
  }

  void send_websocket(coakka_http_host_websocket_t session, uint32_t kind,
                      std::string_view payload) noexcept {
    const coakka_http_host_status_t status = coakka_http_host_send_websocket(
        service, session, kind, coakka::camera_http::bytes(payload));
    if (status == COAKKA_HTTP_HOST_CAPACITY_EXHAUSTED) {
      close_websocket(session, 1013u, "control response queue is full");
      return;
    }
    if (status != COAKKA_HTTP_HOST_OK &&
        status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        status != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("send WebSocket", status);
    }
  }

  void close_websocket(coakka_http_host_websocket_t session, uint32_t code,
                       std::string_view reason) noexcept {
    const coakka_http_host_status_t status = coakka_http_host_close_websocket(
        service, session, code, coakka::camera_http::bytes(reason));
    if (status != COAKKA_HTTP_HOST_OK &&
        status != COAKKA_HTTP_HOST_STALE_EXCHANGE &&
        status != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("close WebSocket", status);
    }
  }

  void abort_websocket(coakka_http_host_websocket_t session) noexcept {
    if (control_connected.exchange(false, std::memory_order_acq_rel)) {
      close_websocket(session, 1011u, "camera control failed");
    }
    websocket_upgrade_pending.store(false, std::memory_order_release);
  }

  void wake_request_reader() noexcept {
    if (stopping.load(std::memory_order_acquire)) {
      return;
    }
    std::lock_guard<std::mutex> lifecycle_lock(lifecycle_mutex);
    if (service == nullptr || stopping.load(std::memory_order_acquire)) {
      return;
    }
    const coakka_http_host_status_t status =
        coakka_http_host_interrupt_requests(service);
    if (status != COAKKA_HTTP_HOST_OK && status != COAKKA_HTTP_HOST_CLOSED) {
      report_failure("interrupt request reader", status);
    }
  }

  bool profile_outbound_enabled() const noexcept {
    return !config.profile_control_host.empty() &&
           config.profile_control_port != 0u &&
           !config.profile_authorization_token.empty();
  }

  void destroy_service() noexcept {
    if (service == nullptr) {
      return;
    }
    const coakka_http_host_status_t status =
        coakka_http_host_service_destroy(&service);
    if (status != COAKKA_HTTP_HOST_OK) {
      report_failure("destroy", status);
    }
  }

  static void report_failure(const char *operation,
                             coakka_http_host_status_t status) noexcept {
    (void)std::fprintf(stderr, "camera gateway HTTP %s failed: %s\n", operation,
                       coakka_http_host_status_name(status));
  }

  static void report_worker_exception(const char *worker,
                                      const char *detail) noexcept {
    (void)std::fprintf(stderr, "camera gateway HTTP %s failed: %s\n", worker,
                       detail != nullptr ? detail : "unknown exception");
  }

  coakka_v2_camera_web_gateway_config_t config;
  DisplayQueue display;
  coakka_v2_camera_recorder_t recorder;
  std::atomic<uint32_t> transport_state{0u};
  std::atomic<uint32_t> audio_transport_state{0u};
  std::atomic<bool> disconnect{false};
  std::atomic<bool> stopping{false};
  std::atomic<bool> stopped{false};
  std::atomic<bool> livestream_enabled{false};
  std::atomic<bool> stream_active{false};
  std::atomic<bool> control_connected{false};
  std::atomic<bool> websocket_upgrade_pending{false};
  coakka_http_host_service_t *service = nullptr;
  coakka_http_host_exchange_t stream_exchange{};
  bool stream_writable = false;
  std::vector<uint8_t> frame_bytes;
  std::vector<uint8_t> frame_chunk;
  std::atomic<uint16_t> bound_port{0u};
  std::atomic<uint64_t> browser_frames{0u};
  std::atomic<uint64_t> browser_bytes{0u};
  mutable std::mutex profile_mutex;
  uint32_t profile_width = 0u;
  uint32_t profile_height = 0u;
  uint32_t requested_width = 0u;
  uint32_t requested_height = 0u;
  uint32_t requested_fps = 0u;
  uint64_t profile_request_sequence = 0u;
  std::chrono::steady_clock::time_point profile_requested_at{};
  bool profile_request_pending = false;
  bool profile_request_taken = false;
  std::string profile_state = "unknown";
  std::string last_profile_error;
  std::thread request_worker;
  std::thread websocket_worker;
  std::mutex lifecycle_mutex;
  std::mutex outbound_mutex;
  bool outbound_reader_failed = false;
};

} // namespace coakka::camera_web_gateway_detail

struct coakka_v2_camera_web_gateway_t::impl_t {
  explicit impl_t(const coakka_v2_camera_web_gateway_config_t &config)
      : gateway(config) {}
  coakka::camera_web_gateway_detail::Gateway gateway;
};

std::unique_ptr<coakka_v2_camera_web_gateway_t>
coakka_v2_camera_web_gateway_t::create(
    const coakka_v2_camera_web_gateway_config_t &config) {
  const bool any_profile_endpoint = !config.profile_control_host.empty() ||
                                    config.profile_control_port != 0u ||
                                    !config.profile_authorization_token.empty();
  const bool complete_profile_endpoint =
      !config.profile_control_host.empty() &&
      config.profile_control_port != 0u &&
      !config.profile_authorization_token.empty();
  if (config.bind_host != "127.0.0.1" || config.max_frame_bytes == 0u ||
      config.fps == 0u || config.recording_directory.empty() ||
      config.ffmpeg_binary.empty() ||
      (any_profile_endpoint && !complete_profile_endpoint)) {
    return nullptr;
  }
  return std::unique_ptr<coakka_v2_camera_web_gateway_t>(
      new coakka_v2_camera_web_gateway_t(std::make_unique<impl_t>(config)));
}

coakka_v2_camera_web_gateway_t::coakka_v2_camera_web_gateway_t(
    std::unique_ptr<impl_t> impl)
    : impl_(std::move(impl)) {}

coakka_v2_camera_web_gateway_t::~coakka_v2_camera_web_gateway_t() { stop(); }

bool coakka_v2_camera_web_gateway_t::start(std::string *error) {
  return impl_ != nullptr && impl_->gateway.start(error);
}

void coakka_v2_camera_web_gateway_t::stop() {
  if (impl_ != nullptr) {
    impl_->gateway.stop();
  }
}

coakka_v2_status_t coakka_v2_camera_web_gateway_t::consume(
    const uint8_t *data, const coakka_v2_stream_frame_t *frame) noexcept {
  if (impl_ == nullptr || data == nullptr || frame == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  bool became_nonempty = false;
  const coakka_v2_status_t result =
      impl_->gateway.display.push(data, frame, &became_nonempty);
  if (result == COAKKA_V2_OK) {
    try {
      impl_->gateway.confirm_profile_frame(data, frame->size);
    } catch (const std::exception &failure) {
      impl_->gateway.report_worker_exception("profile confirmation",
                                             failure.what());
    } catch (...) {
      impl_->gateway.report_worker_exception("profile confirmation",
                                             "unknown exception");
    }
    impl_->gateway.recorder.push(data, frame);
    if (became_nonempty &&
        impl_->gateway.stream_active.load(std::memory_order_acquire)) {
      impl_->gateway.wake_request_reader();
    }
  }
  return result;
}

coakka_v2_status_t coakka_v2_camera_web_gateway_t::consume_audio(
    const uint8_t *data, const coakka_v2_stream_frame_t *frame) noexcept {
  if (impl_ == nullptr || data == nullptr || frame == nullptr ||
      frame->size == 0u ||
      frame->size > coakka::camera_web_gateway_detail::kAudioFrameBytes) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  impl_->gateway.recorder.push_audio(data, frame);
  return COAKKA_V2_OK;
}

void coakka_v2_camera_web_gateway_t::set_transport_state(
    uint32_t state) noexcept {
  if (impl_ != nullptr) {
    impl_->gateway.transport_state.store(state, std::memory_order_release);
  }
}

void coakka_v2_camera_web_gateway_t::set_audio_transport_state(
    uint32_t state) noexcept {
  if (impl_ != nullptr) {
    impl_->gateway.audio_transport_state.store(state,
                                               std::memory_order_release);
  }
}

bool coakka_v2_camera_web_gateway_t::take_profile_request(
    coakka_v2_camera_profile_request_t *request) {
  if (impl_ == nullptr || request == nullptr) {
    return false;
  }
  std::lock_guard<std::mutex> lock(impl_->gateway.profile_mutex);
  if (!impl_->gateway.profile_request_pending ||
      impl_->gateway.profile_request_taken) {
    return false;
  }
  request->width = impl_->gateway.requested_width;
  request->height = impl_->gateway.requested_height;
  request->fps = impl_->gateway.requested_fps;
  request->sequence = impl_->gateway.profile_request_sequence;
  impl_->gateway.profile_request_taken = true;
  return true;
}

bool coakka_v2_camera_web_gateway_t::request_profile(
    const coakka_v2_camera_profile_request_t &request, std::string *error) {
  return impl_ != nullptr && impl_->gateway.request_profile(request, error);
}

void coakka_v2_camera_web_gateway_t::complete_profile_request(
    uint64_t sequence, bool accepted, std::string error) {
  if (impl_ == nullptr) {
    return;
  }
  std::lock_guard<std::mutex> lock(impl_->gateway.profile_mutex);
  if (!impl_->gateway.profile_request_pending ||
      sequence != impl_->gateway.profile_request_sequence) {
    return;
  }
  if (accepted) {
    impl_->gateway.profile_state = "confirming";
    impl_->gateway.last_profile_error.clear();
    return;
  }
  impl_->gateway.profile_state = "failed";
  impl_->gateway.last_profile_error = std::move(error);
  impl_->gateway.profile_request_pending = false;
  impl_->gateway.profile_request_taken = false;
}

bool coakka_v2_camera_web_gateway_t::disconnect_requested() const noexcept {
  return impl_ != nullptr &&
         impl_->gateway.disconnect.load(std::memory_order_acquire);
}

coakka_v2_camera_web_gateway_snapshot_t
coakka_v2_camera_web_gateway_t::snapshot() const {
  coakka_v2_camera_web_gateway_snapshot_t result;
  if (impl_ == nullptr) {
    return result;
  }
  uint64_t source_drops = 0u;
  impl_->gateway.display.snapshot(&result.received_frames,
                                  &result.received_bytes, &source_drops,
                                  &result.display_queue_drops);
  const coakka_v2_camera_recorder_snapshot_t recording =
      impl_->gateway.recorder.snapshot();
  result.bound_port = impl_->gateway.bound_port.load(std::memory_order_acquire);
  result.browser_frames =
      impl_->gateway.browser_frames.load(std::memory_order_relaxed);
  result.browser_bytes =
      impl_->gateway.browser_bytes.load(std::memory_order_relaxed);
  result.recording_frames = recording.frames;
  result.recording_bytes = recording.bytes;
  result.recording_queue_drops = recording.queue_drops;
  result.recording_audio_frames = recording.audio_frames;
  result.recording_audio_bytes = recording.audio_bytes;
  result.recording_audio_queue_drops = recording.audio_queue_drops;
  result.recording_path = recording.path;
  return result;
}
