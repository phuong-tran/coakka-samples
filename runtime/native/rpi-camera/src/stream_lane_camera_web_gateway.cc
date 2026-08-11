#include "stream_lane_camera_web_gateway.h"

#include "runtime_addon_web_wire.h"
#include "stream_lane_camera_recorder.h"
#include "stream_lane_camera_web_ui.h"

#include <google/protobuf/struct.pb.h>
#include <google/protobuf/util/json_util.h>

#include <uv.h>

#if defined(_WIN32)
#include <winsock2.h>
#else
#include <arpa/inet.h>
#endif
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <system_error>
#include <thread>
#include <utility>
#include <vector>

namespace {

using coakka::runtime_addon_web::header_contains_token;
using coakka::runtime_addon_web::http_request_t;
using coakka::runtime_addon_web::make_http_response_text;
using coakka::runtime_addon_web::make_websocket_upgrade_response;
using coakka::runtime_addon_web::parse_request_from_text;
using coakka::runtime_addon_web::request_path_without_query;
using coakka::runtime_addon_web::try_extract_http_request_bytes;
using coakka::runtime_addon_web::websocket_frame;

constexpr size_t kQueueFrames = 4u;
constexpr size_t kAudioFrameBytes = 1920u;
constexpr size_t kMaxHttpBytes = 16u * 1024u;
constexpr size_t kMaxWebsocketPayload = 4u * 1024u;
constexpr size_t kMaxControlWrites = 8u;
constexpr size_t kMaxClients = 8u;
constexpr auto kProfileConfirmationTimeout = std::chrono::seconds(5);

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
  explicit DisplayQueue(size_t capacity) { initialize(capacity); }

  void set_async(uv_async_t *async) {
    std::lock_guard<std::mutex> lock(mutex_);
    async_ = async;
  }

  coakka_v2_status_t push(const uint8_t *data,
                          const coakka_v2_stream_frame_t *frame) noexcept {
    uv_async_t *async = nullptr;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      if (!valid_frame(data, frame) ||
          (expected_sequence_ != 0u && frame->sequence != expected_sequence_)) {
        return COAKKA_V2_ERR_IO;
      }
      expected_sequence_ = frame->sequence + 1u;
      source_drops_ += frame->dropped_before;
      if (count_ == slots_.size()) {
        head_ = (head_ + 1u) % slots_.size();
        --count_;
        ++queue_drops_;
      }
      FrameSlot &slot = slots_[(head_ + count_) % slots_.size()];
      copy_frame(slot, data, frame);
      ++count_;
      ++received_frames_;
      received_bytes_ += frame->size;
      async = async_;
    }
    if (async != nullptr) {
      (void)uv_async_send(async);
    }
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
  void initialize(size_t capacity) {
    slots_.reserve(kQueueFrames);
    for (size_t index = 0u; index < kQueueFrames; ++index) {
      slots_.emplace_back(capacity);
    }
  }

  bool valid_frame(const uint8_t *data,
                   const coakka_v2_stream_frame_t *frame) const noexcept {
    return data != nullptr && frame != nullptr && frame->size >= 4u &&
           frame->size <= slots_[0].bytes.size() && data[0] == 0xffu &&
           data[1] == 0xd8u && data[frame->size - 2u] == 0xffu &&
           data[frame->size - 1u] == 0xd9u;
  }

  static void copy_frame(FrameSlot &slot, const uint8_t *data,
                         const coakka_v2_stream_frame_t *frame) noexcept {
    std::memcpy(slot.bytes.data(), data, frame->size);
    slot.size = frame->size;
    slot.sequence = frame->sequence;
    slot.captured_mono_ns = frame->captured_mono_ns;
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
  uv_async_t *async_ = nullptr;
};

enum class ClientMode { Http, Stream, Websocket };

struct Gateway;

struct WebClient {
  uv_tcp_t handle{};
  Gateway *gateway = nullptr;
  std::array<char, 8192u> read_buffer{};
  std::string input;
  ClientMode mode = ClientMode::Http;
  size_t pending_control_writes = 0u;
  bool stream_headers_sent = false;
  bool closing = false;
};

struct OwnedWrite {
  uv_write_t request{};
  WebClient *client = nullptr;
  std::string payload;
  bool close_after = false;
  bool stream_headers = false;
  bool control_write = false;
};

struct Gateway {
  explicit Gateway(const coakka_v2_camera_web_gateway_config_t &value)
      : config(value), display(value.max_frame_bytes),
        recorder(value.max_frame_bytes, value.fps, value.recording_directory,
                 value.ffmpeg_binary),
        frame_bytes(value.max_frame_bytes) {
    profile_width = value.initial_width;
    profile_height = value.initial_height;
    profile_fps = value.fps;
    frame_write.data = this;
  }

  bool start(std::string *error) {
    if (!recorder.start_worker(error)) {
      return false;
    }
    try {
      worker = std::thread([this] { run(); });
    } catch (const std::system_error &failure) {
      *error = "web worker start failed: " + std::string(failure.what());
      recorder.shutdown();
      return false;
    }
    std::unique_lock<std::mutex> lock(ready_mutex);
    ready_condition.wait(lock, [this] { return ready; });
    if (start_result != 0) {
      *error = uv_strerror(start_result);
      recorder.shutdown();
      return false;
    }
    return true;
  }

  void stop() {
    if (stopped.exchange(true, std::memory_order_acq_rel)) {
      return;
    }
    recorder.shutdown();
    stopping.store(true, std::memory_order_release);
    if (ready && start_result == 0) {
      (void)uv_async_send(&async);
    }
    if (worker.joinable()) {
      worker.join();
    }
  }

  static void alloc_read(uv_handle_t *handle, size_t,
                         uv_buf_t *buffer) noexcept {
    auto *client = static_cast<WebClient *>(handle->data);
    *buffer =
        uv_buf_init(client->read_buffer.data(),
                    static_cast<unsigned int>(client->read_buffer.size()));
  }

  static void delete_client(uv_handle_t *handle) noexcept {
    auto *client = static_cast<WebClient *>(handle->data);
    Gateway *gateway = client->gateway;
    gateway->clients.erase(
        std::remove(gateway->clients.begin(), gateway->clients.end(), client),
        gateway->clients.end());
    delete client;
  }

  static void close_client(WebClient *client) noexcept {
    if (client == nullptr || client->closing) {
      return;
    }
    client->closing = true;
    Gateway *gateway = client->gateway;
    if (gateway->stream_client == client) {
      gateway->stream_client = nullptr;
    }
    if (gateway->control_client == client) {
      gateway->control_client = nullptr;
    }
    (void)uv_read_stop(reinterpret_cast<uv_stream_t *>(&client->handle));
    uv_close(reinterpret_cast<uv_handle_t *>(&client->handle), delete_client);
  }

  static void owned_write_complete(uv_write_t *request, int status) noexcept {
    std::unique_ptr<OwnedWrite> write(static_cast<OwnedWrite *>(request->data));
    WebClient *client = write->client;
    if (write->control_write && client != nullptr &&
        client->pending_control_writes != 0u) {
      --client->pending_control_writes;
    }
    if (status < 0 || write->close_after) {
      close_client(client);
      return;
    }
    if (write->stream_headers && client != nullptr && !client->closing) {
      client->stream_headers_sent = true;
      client->gateway->pump_stream();
    }
  }

  static bool send_owned(WebClient *client, std::string payload,
                         bool close_after = false, bool stream_headers = false,
                         bool control_write = false) {
    if (client == nullptr || client->closing ||
        (control_write &&
         client->pending_control_writes >= kMaxControlWrites)) {
      return false;
    }
    auto write = std::make_unique<OwnedWrite>();
    write->client = client;
    write->payload = std::move(payload);
    write->close_after = close_after;
    write->stream_headers = stream_headers;
    write->control_write = control_write;
    write->request.data = write.get();
    uv_buf_t buffer =
        uv_buf_init(write->payload.data(),
                    static_cast<unsigned int>(write->payload.size()));
    if (control_write) {
      ++client->pending_control_writes;
    }
    const int result = uv_write(
        &write->request, reinterpret_cast<uv_stream_t *>(&client->handle),
        &buffer, 1u, owned_write_complete);
    if (result != 0) {
      if (control_write) {
        --client->pending_control_writes;
      }
      return false;
    }
    (void)write.release();
    return true;
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
      if (!profile_request_pending || !profile_request_taken) {
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
    if (!profile_request_pending || requested_width != frame_width ||
        requested_height != frame_height) {
      return;
    }
    profile_width = requested_width;
    profile_height = requested_height;
    profile_fps = requested_fps;
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
      fps = profile_fps;
      profile_status = profile_state;
      profile_error = last_profile_error;
    }
    google::protobuf::Struct status;
    auto &fields = *status.mutable_fields();
    fields["type"].set_string_value("status");
    fields["request_id"].set_string_value(std::string(request_id));
    fields["ok"].set_bool_value(ok);
    fields["error"].set_string_value(std::string(error));
    fields["control"].set_string_value(
        control_client != nullptr ? "connected" : "disconnected");
    fields["transport"].set_string_value(transport_name());
    fields["livestream_enabled"].set_bool_value(livestream_enabled);
    fields["viewer_connected"].set_bool_value(stream_client != nullptr);
    fields["audio_available"].set_bool_value(audio_available());
    fields["profile_width"].set_number_value(width);
    fields["profile_height"].set_number_value(height);
    fields["profile_fps"].set_number_value(fps);
    fields["profile_state"].set_string_value(profile_status);
    fields["profile_error"].set_string_value(profile_error);
    fields["received_frames"].set_number_value(received_frames);
    fields["received_bytes"].set_number_value(received_bytes);
    fields["source_drops"].set_number_value(source_drops);
    fields["display_drops"].set_number_value(display_drops);
    fields["browser_frames"].set_number_value(
        browser_frames.load(std::memory_order_relaxed));
    fields["browser_bytes"].set_number_value(
        browser_bytes.load(std::memory_order_relaxed));
    fields["recording_state"].set_string_value(recording.state);
    fields["recording_path"].set_string_value(recording.path);
    fields["recording_error"].set_string_value(recording.error);
    fields["recording_frames"].set_number_value(recording.frames);
    fields["recording_bytes"].set_number_value(recording.bytes);
    fields["recording_drops"].set_number_value(recording.queue_drops);
    fields["recording_with_audio"].set_bool_value(recording.with_audio);
    fields["recording_audio_frames"].set_number_value(recording.audio_frames);
    fields["recording_audio_bytes"].set_number_value(recording.audio_bytes);
    fields["recording_audio_drops"].set_number_value(
        recording.audio_queue_drops);
    std::string json;
    google::protobuf::util::JsonPrintOptions options;
    options.preserve_proto_field_names = true;
    (void)google::protobuf::util::MessageToJsonString(status, &json, options);
    return json;
  }

  void send_status(WebClient *client, std::string_view request_id = {},
                   bool ok = true, std::string_view error = {}) {
    if (!send_owned(client,
                    websocket_frame(0x1u, status_json(request_id, ok, error)),
                    false, false, true)) {
      close_client(client);
    }
  }

  static bool command_uint32(const google::protobuf::Struct &command,
                             const char *name, uint32_t *out) {
    const auto found = command.fields().find(name);
    if (found == command.fields().end() ||
        found->second.kind_case() != google::protobuf::Value::kNumberValue) {
      return false;
    }
    const double value = found->second.number_value();
    if (value < 1.0 || value > static_cast<double>(UINT32_MAX) ||
        value != static_cast<double>(static_cast<uint32_t>(value))) {
      return false;
    }
    *out = static_cast<uint32_t>(value);
    return true;
  }

  static bool supported_profile(uint32_t width, uint32_t height, uint32_t fps) {
    return fps == 30u && ((width == 640u && height == 480u) ||
                          (width == 800u && height == 600u) ||
                          (width == 1280u && height == 720u) ||
                          (width == 1920u && height == 1080u));
  }

  void handle_command(WebClient *client, std::string_view payload) {
    google::protobuf::Struct command;
    google::protobuf::util::JsonParseOptions options;
    options.ignore_unknown_fields = false;
    const auto parsed = google::protobuf::util::JsonStringToMessage(
        std::string(payload), &command, options);
    std::string id;
    std::string name;
    const auto &fields = command.fields();
    const auto id_it = fields.find("id");
    const auto command_it = fields.find("command");
    if (!parsed.ok() || id_it == fields.end() || command_it == fields.end() ||
        id_it->second.kind_case() != google::protobuf::Value::kStringValue ||
        command_it->second.kind_case() !=
            google::protobuf::Value::kStringValue) {
      send_status(client, {}, false, "expected string id and command");
      return;
    }
    id = id_it->second.string_value();
    name = command_it->second.string_value();
    if (id.empty() || id.size() > 64u || name.empty() || name.size() > 64u) {
      send_status(client, id, false, "id or command is out of bounds");
      return;
    }

    bool ok = true;
    std::string error;
    if (name == "status.get") {
    } else if (name == "livestream.start") {
      livestream_enabled = true;
    } else if (name == "livestream.stop") {
      livestream_enabled = false;
      close_client(stream_client);
    } else if (name == "recording.start") {
      bool with_audio = false;
      const auto audio = fields.find("audio");
      if (audio != fields.end()) {
        if (audio->second.kind_case() != google::protobuf::Value::kBoolValue) {
          ok = false;
          error = "audio must be a boolean";
        } else {
          with_audio = audio->second.bool_value();
        }
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
    send_status(client, id, ok, error);
  }

  bool parse_websocket_frames(WebClient *client) {
    while (client->input.size() >= 2u) {
      const auto *bytes =
          reinterpret_cast<const uint8_t *>(client->input.data());
      const bool fin = (bytes[0] & 0x80u) != 0u;
      const uint8_t opcode = bytes[0] & 0x0fu;
      const bool masked = (bytes[1] & 0x80u) != 0u;
      uint64_t payload_size = bytes[1] & 0x7fu;
      size_t header_size = 2u;
      if ((bytes[0] & 0x70u) != 0u || !fin || !masked) {
        return false;
      }
      if (payload_size == 126u) {
        if (client->input.size() < 4u) {
          return true;
        }
        payload_size = (static_cast<uint64_t>(bytes[2]) << 8u) | bytes[3];
        header_size = 4u;
      } else if (payload_size == 127u) {
        return false;
      }
      if (payload_size > kMaxWebsocketPayload) {
        return false;
      }
      if (opcode >= 0x8u && payload_size > 125u) {
        return false;
      }
      const size_t total = header_size + 4u + static_cast<size_t>(payload_size);
      if (client->input.size() < total) {
        return true;
      }
      const uint8_t *mask = bytes + header_size;
      std::string payload(payload_size, '\0');
      for (size_t index = 0u; index < payload.size(); ++index) {
        payload[index] = static_cast<char>(bytes[header_size + 4u + index] ^
                                           mask[index % 4u]);
      }
      client->input.erase(0u, total);
      if (opcode == 0x1u) {
        handle_command(client, payload);
      } else if (opcode == 0x8u) {
        return false;
      } else if (opcode == 0x9u) {
        if (!send_owned(client, websocket_frame(0xau, payload), false, false,
                        true)) {
          return false;
        }
      } else if (opcode != 0xau) {
        return false;
      }
    }
    return true;
  }

  void handle_http(WebClient *client) {
    size_t request_size = 0u;
    std::string error;
    if (!try_extract_http_request_bytes(client->input, &request_size, &error)) {
      return;
    }
    if (!error.empty() || request_size > kMaxHttpBytes) {
      (void)send_owned(client,
                       make_http_response_text(400, "text/plain", error), true);
      return;
    }
    http_request_t request;
    if (!parse_request_from_text(client->input.substr(0u, request_size),
                                 &request, &error)) {
      (void)send_owned(client,
                       make_http_response_text(400, "text/plain", error), true);
      return;
    }
    client->input.erase(0u, request_size);
    const std::string path = request_path_without_query(request.path);
    if (request.method != "GET") {
      (void)send_owned(client, make_http_response_text(405, "text/plain", ""),
                       true);
      return;
    }
    if (path == "/") {
      const std::string body(coakka_v2_camera_web_ui_html());
      (void)send_owned(
          client,
          make_http_response_text(200, "text/html; charset=utf-8", body), true);
      return;
    }
    if (path == "/healthz") {
      (void)send_owned(
          client,
          make_http_response_text(200, "application/json", "{\"ok\":true}"),
          true);
      return;
    }
    if (path == "/stream.mjpeg") {
      if (!livestream_enabled || stream_client != nullptr) {
        const int status = livestream_enabled ? 409 : 503;
        (void)send_owned(
            client, make_http_response_text(status, "text/plain", ""), true);
        return;
      }
      stream_client = client;
      client->mode = ClientMode::Stream;
      if (!send_owned(
              client,
              "HTTP/1.1 200 OK\r\nCache-Control: no-store, no-cache, "
              "must-revalidate\r\nPragma: no-cache\r\nConnection: close\r\n"
              "Content-Type: multipart/x-mixed-replace; "
              "boundary=coakka-frame\r\n\r\n",
              false, true)) {
        close_client(client);
      }
      return;
    }
    if (path == "/ws/control") {
      const auto key = request.headers.find("sec-websocket-key");
      const auto origin = request.headers.find("origin");
      const auto host = request.headers.find("host");
      const std::string expected_origin = host == request.headers.end()
                                              ? std::string{}
                                              : "http://" + host->second;
      const bool valid =
          control_client == nullptr &&
          header_contains_token(request.headers, "upgrade", "websocket") &&
          header_contains_token(request.headers, "connection", "upgrade") &&
          key != request.headers.end() && host != request.headers.end() &&
          origin != request.headers.end() &&
          origin->second == expected_origin &&
          request.headers.find("sec-websocket-version") !=
              request.headers.end() &&
          request.headers.at("sec-websocket-version") == "13";
      if (!valid) {
        (void)send_owned(client, make_http_response_text(403, "text/plain", ""),
                         true);
        return;
      }
      client->mode = ClientMode::Websocket;
      control_client = client;
      if (!send_owned(client, make_websocket_upgrade_response(key->second))) {
        close_client(client);
        return;
      }
      send_status(client);
      if (!client->input.empty() && !parse_websocket_frames(client)) {
        close_client(client);
      }
      return;
    }
    (void)send_owned(client, make_http_response_text(404, "text/plain", ""),
                     true);
  }

  static void read_client(uv_stream_t *stream, ssize_t read,
                          const uv_buf_t *buffer) noexcept {
    auto *client = static_cast<WebClient *>(stream->data);
    if (read < 0) {
      close_client(client);
      return;
    }
    if (read == 0) {
      return;
    }
    client->input.append(buffer->base, static_cast<size_t>(read));
    if (client->input.size() > kMaxHttpBytes) {
      close_client(client);
      return;
    }
    bool valid = true;
    if (client->mode == ClientMode::Websocket) {
      valid = client->gateway->parse_websocket_frames(client);
    } else if (client->mode == ClientMode::Http) {
      client->gateway->handle_http(client);
    }
    if (!valid) {
      close_client(client);
    }
  }

  static void accept_client(uv_stream_t *server, int status) noexcept {
    auto *gateway = static_cast<Gateway *>(server->data);
    if (status < 0 || gateway->stopping.load(std::memory_order_acquire)) {
      return;
    }
    auto client = std::make_unique<WebClient>();
    client->gateway = gateway;
    client->handle.data = client.get();
    if (uv_tcp_init(&gateway->loop, &client->handle) != 0) {
      return;
    }
    WebClient *owned = client.release();
    if (uv_accept(server, reinterpret_cast<uv_stream_t *>(&owned->handle)) !=
        0) {
      close_client(owned);
      return;
    }
    if (gateway->clients.size() >= kMaxClients) {
      close_client(owned);
      return;
    }
    gateway->clients.push_back(owned);
    if (uv_read_start(reinterpret_cast<uv_stream_t *>(&owned->handle),
                      alloc_read, read_client) != 0) {
      close_client(owned);
    }
  }

  static void frame_write_complete(uv_write_t *request, int status) noexcept {
    auto *gateway = static_cast<Gateway *>(request->data);
    WebClient *completed_client = gateway->frame_write_client;
    gateway->frame_write_client = nullptr;
    gateway->frame_write_in_flight = false;
    if (status < 0) {
      close_client(completed_client);
      return;
    }
    gateway->browser_frames.fetch_add(1u, std::memory_order_relaxed);
    gateway->browser_bytes.fetch_add(gateway->frame_size,
                                     std::memory_order_relaxed);
    gateway->pump_stream();
  }

  void pump_stream() {
    if (frame_write_in_flight || stream_client == nullptr ||
        stream_client->closing || !stream_client->stream_headers_sent) {
      return;
    }
    uint64_t sequence = 0u;
    uint64_t captured_mono_ns = 0u;
    if (!display.take_latest(frame_bytes.data(), frame_bytes.size(),
                             &frame_size, &sequence, &captured_mono_ns)) {
      return;
    }
    const int header_size = std::snprintf(
        frame_header.data(), frame_header.size(),
        "--coakka-frame\r\nContent-Type: image/jpeg\r\nContent-Length: %zu\r\n"
        "X-CoAkka-Sequence: %llu\r\nX-Captured-Monotonic-Ns: %llu\r\n\r\n",
        frame_size, static_cast<unsigned long long>(sequence),
        static_cast<unsigned long long>(captured_mono_ns));
    if (header_size <= 0 ||
        static_cast<size_t>(header_size) >= frame_header.size()) {
      close_client(stream_client);
      return;
    }
    std::array<uv_buf_t, 3u> buffers{
        uv_buf_init(frame_header.data(),
                    static_cast<unsigned int>(header_size)),
        uv_buf_init(reinterpret_cast<char *>(frame_bytes.data()),
                    static_cast<unsigned int>(frame_size)),
        uv_buf_init(const_cast<char *>("\r\n"), 2u)};
    frame_write_in_flight = true;
    frame_write_client = stream_client;
    if (uv_write(&frame_write,
                 reinterpret_cast<uv_stream_t *>(&stream_client->handle),
                 buffers.data(), static_cast<unsigned int>(buffers.size()),
                 frame_write_complete) != 0) {
      frame_write_in_flight = false;
      frame_write_client = nullptr;
      close_client(stream_client);
    }
  }

  static void status_tick(uv_timer_t *timer) noexcept {
    auto *gateway = static_cast<Gateway *>(timer->data);
    gateway->expire_profile_request();
    if (gateway->control_client != nullptr &&
        gateway->control_client->pending_control_writes < 2u) {
      gateway->send_status(gateway->control_client);
    }
  }

  static void async_ready(uv_async_t *handle) noexcept {
    auto *gateway = static_cast<Gateway *>(handle->data);
    if (gateway->stopping.load(std::memory_order_acquire)) {
      gateway->display.set_async(nullptr);
      const std::vector<WebClient *> clients = gateway->clients;
      for (WebClient *client : clients) {
        close_client(client);
      }
      uv_close(reinterpret_cast<uv_handle_t *>(&gateway->server), nullptr);
      uv_close(reinterpret_cast<uv_handle_t *>(&gateway->timer), nullptr);
      uv_close(reinterpret_cast<uv_handle_t *>(&gateway->async), nullptr);
      return;
    }
    gateway->pump_stream();
  }

  void publish_ready(int result) {
    std::lock_guard<std::mutex> lock(ready_mutex);
    start_result = result;
    ready = true;
    ready_condition.notify_one();
  }

  void run() {
    int result = uv_loop_init(&loop);
    if (result != 0) {
      publish_ready(result);
      return;
    }
    server.data = this;
    async.data = this;
    timer.data = this;
    result = uv_tcp_init(&loop, &server);
    if (result == 0) {
      result = uv_async_init(&loop, &async, async_ready);
    }
    if (result == 0) {
      result = uv_timer_init(&loop, &timer);
    }
    sockaddr_in address{};
    if (result == 0) {
      result =
          uv_ip4_addr(config.bind_host.c_str(), config.bind_port, &address);
    }
    if (result == 0) {
      result = uv_tcp_bind(&server,
                           reinterpret_cast<const sockaddr *>(&address), 0u);
    }
    if (result == 0) {
      result =
          uv_listen(reinterpret_cast<uv_stream_t *>(&server), 8, accept_client);
    }
    if (result == 0) {
      int length = sizeof(address);
      result = uv_tcp_getsockname(
          &server, reinterpret_cast<sockaddr *>(&address), &length);
      bound_port = ntohs(address.sin_port);
    }
    if (result == 0) {
      result = uv_timer_start(&timer, status_tick, 500u, 500u);
    }
    if (result != 0) {
      uv_walk(
          &loop,
          [](uv_handle_t *handle, void *) {
            if (!uv_is_closing(handle)) {
              uv_close(handle, nullptr);
            }
          },
          nullptr);
      (void)uv_run(&loop, UV_RUN_DEFAULT);
      (void)uv_loop_close(&loop);
      publish_ready(result);
      return;
    }
    display.set_async(&async);
    publish_ready(0);
    (void)uv_run(&loop, UV_RUN_DEFAULT);
    (void)uv_loop_close(&loop);
  }

  coakka_v2_camera_web_gateway_config_t config;
  DisplayQueue display;
  coakka_v2_camera_recorder_t recorder;
  std::atomic<uint32_t> transport_state{0u};
  std::atomic<uint32_t> audio_transport_state{0u};
  std::atomic<bool> disconnect{false};
  std::atomic<bool> stopping{false};
  std::atomic<bool> stopped{false};
  uv_loop_t loop{};
  uv_tcp_t server{};
  uv_async_t async{};
  uv_timer_t timer{};
  std::vector<WebClient *> clients;
  WebClient *stream_client = nullptr;
  WebClient *control_client = nullptr;
  uv_write_t frame_write{};
  WebClient *frame_write_client = nullptr;
  std::array<char, 192u> frame_header{};
  std::vector<uint8_t> frame_bytes;
  size_t frame_size = 0u;
  bool frame_write_in_flight = false;
  bool livestream_enabled = false;
  uint16_t bound_port = 0u;
  std::atomic<uint64_t> browser_frames{0u};
  std::atomic<uint64_t> browser_bytes{0u};
  mutable std::mutex profile_mutex;
  uint32_t profile_width = 0u;
  uint32_t profile_height = 0u;
  uint32_t profile_fps = 0u;
  uint32_t requested_width = 0u;
  uint32_t requested_height = 0u;
  uint32_t requested_fps = 0u;
  uint64_t profile_request_sequence = 0u;
  std::chrono::steady_clock::time_point profile_requested_at{};
  bool profile_request_pending = false;
  bool profile_request_taken = false;
  std::string profile_state = "active";
  std::string last_profile_error;
  std::thread worker;
  std::mutex ready_mutex;
  std::condition_variable ready_condition;
  bool ready = false;
  int start_result = UV_EINVAL;
};

} // namespace

struct coakka_v2_camera_web_gateway_t::impl_t {
  explicit impl_t(const coakka_v2_camera_web_gateway_config_t &config)
      : gateway(config) {}
  Gateway gateway;
};

std::unique_ptr<coakka_v2_camera_web_gateway_t>
coakka_v2_camera_web_gateway_t::create(
    const coakka_v2_camera_web_gateway_config_t &config) {
  if (config.bind_host != "127.0.0.1" || config.max_frame_bytes == 0u ||
      config.fps == 0u || config.recording_directory.empty() ||
      config.ffmpeg_binary.empty()) {
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
  return impl_ != nullptr && error != nullptr && impl_->gateway.start(error);
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
  const coakka_v2_status_t result = impl_->gateway.display.push(data, frame);
  if (result == COAKKA_V2_OK) {
    impl_->gateway.confirm_profile_frame(data, frame->size);
    impl_->gateway.recorder.push(data, frame);
  }
  return result;
}

coakka_v2_status_t coakka_v2_camera_web_gateway_t::consume_audio(
    const uint8_t *data, const coakka_v2_stream_frame_t *frame) noexcept {
  if (impl_ == nullptr || data == nullptr || frame == nullptr ||
      frame->size == 0u || frame->size > kAudioFrameBytes) {
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
  result.bound_port = impl_->gateway.bound_port;
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
