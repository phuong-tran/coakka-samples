#include "coakka/v2/stream_lane.h"
#include "stream_lane_camera_lane_owner.h"
#if defined(COAKKA_V2_CAMERA_PI_APP)
#include "alsa_pcm_audio_source.h"
#include "stream_lane_camera_control_server.h"
#include "v4l2_mjpeg_camera_source.h"
#elif defined(COAKKA_V2_CAMERA_HOST_APP)
#include "stream_lane_camera_web_gateway.h"
#else
#error "Select exactly one camera app role"
#endif

#if defined(COAKKA_V2_CAMERA_HOST_APP)
#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <cerrno>
#include <fcntl.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#endif
#endif

#include <array>
#include <charconv>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#if defined(COAKKA_V2_CAMERA_HOST_APP)
#include <filesystem>
#endif
#include <iostream>
#include <memory>
#if defined(COAKKA_V2_CAMERA_PI_APP)
#include <mutex>
#endif
#include <string>
#include <string_view>

#ifndef COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT
#define COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT "development"
#endif

namespace {

constexpr uint64_t kMjpegFrameFormat = UINT64_C(0x4d4a504547303031);
constexpr uint64_t kPcmS16leFrameFormat = UINT64_C(0x50434d5331364c45);
#if defined(COAKKA_V2_CAMERA_HOST_APP)
constexpr uint32_t kWindowFrameCount = 4u;
constexpr uint32_t kAudioWindowFrames = 16u;
constexpr uint32_t kAudioFrameBytes = 1920u;
#endif
constexpr uint32_t kLaneMaxFrameBytes = COAKKA_V2_STREAM_LANE_MAX_FRAME_BYTES;
constexpr uint32_t kIoTimeoutMs = 30000u;
constexpr uint32_t kWaitQuantumMs = 250u;
constexpr char kPublicSourceMarker[] =
    "COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT=" COAKKA_CAMERA_PUBLIC_SOURCE_COMMIT;
#if defined(COAKKA_V2_CAMERA_PI_APP)
constexpr uint32_t kCameraPollTimeoutMs = 100u;
constexpr uint32_t kCameraBufferCount = 4u;
#endif

volatile std::sig_atomic_t interrupted = 0;

extern "C" void handle_signal(int) { interrupted = 1; }

bool terminal_state(uint32_t state) noexcept {
  return state == COAKKA_V2_STREAM_STATE_ENDED ||
         state == COAKKA_V2_STREAM_STATE_REJECTED ||
         state == COAKKA_V2_STREAM_STATE_FAILED ||
         state == COAKKA_V2_STREAM_STATE_CANCELED;
}

template <typename Value>
bool parse_unsigned(const char *text, Value *out) noexcept {
  if (text == nullptr || text[0] == '\0' || out == nullptr) {
    return false;
  }
  Value value = 0;
  const char *end = text + std::strlen(text);
  const auto parsed = std::from_chars(text, end, value);
  if (parsed.ec != std::errc{} || parsed.ptr != end) {
    return false;
  }
  *out = value;
  return true;
}

enum class CliParseResult { ok, help, error };

struct NamedOptionReader {
  int argc;
  char **argv;
  int index = 1;
  std::array<std::string_view, 16u> seen{};
  size_t seen_count = 0u;

  bool next(std::string_view *name, std::string_view *value,
            bool *help_requested, std::string *error) {
    const std::string_view argument(argv[index++]);
    if (argument == "--help" || argument == "-h") {
      *help_requested = true;
      return true;
    }
    if (!argument.starts_with("--") || argument.size() == 2u) {
      *error = "expected a named option, got '" + std::string(argument) + "'";
      return false;
    }
    const size_t equals = argument.find('=');
    *name = argument.substr(2u, equals == std::string_view::npos
                                    ? std::string_view::npos
                                    : equals - 2u);
    if (equals == std::string_view::npos) {
      if (index >= argc || std::string_view(argv[index]).starts_with("--")) {
        *error = "missing value for --" + std::string(*name);
        return false;
      }
      *value = argv[index++];
    } else {
      *value = argument.substr(equals + 1u);
    }
    if (value->empty()) {
      *error = "empty value for --" + std::string(*name);
      return false;
    }
    for (size_t seen_index = 0u; seen_index < seen_count; ++seen_index) {
      if (seen[seen_index] == *name) {
        *error = "duplicate option --" + std::string(*name);
        return false;
      }
    }
    if (seen_count >= seen.size()) {
      *error = "too many command-line options";
      return false;
    }
    seen[seen_count++] = *name;
    return true;
  }
};

#if defined(COAKKA_V2_CAMERA_PI_APP)
coakka_v2_stream_lane_config_t publisher_config(const char *host, uint16_t port,
                                                bool audio_enabled) {
  coakka_v2_stream_lane_config_t config{};
  config.struct_size = sizeof(config);
  config.flags = COAKKA_V2_STREAM_LANE_ENABLE_PUBLISHER;
  config.bind_host = host;
  config.bind_port = port;
  config.capacity = audio_enabled ? 2u : 1u;
  config.publisher_worker_count = audio_enabled ? 2u : 1u;
  config.max_frame_bytes = kLaneMaxFrameBytes;
  config.max_window_bytes = COAKKA_V2_STREAM_LANE_MAX_WINDOW_BYTES;
  config.io_timeout_ms = kIoTimeoutMs;
  config.source_retry_ms = 5u;
  config.progress_frames = 30u;
  config.progress_interval_ms = 250u;
  config.pressure_after_ms = 100u;
  config.stalled_after_ms = 1000u;
  config.recovery_after_ms = 500u;
  config.pressure_observation_ms = 100u;
  return config;
}
#else
coakka_v2_stream_lane_config_t subscriber_config(uint32_t max_frame_bytes,
                                                 bool audio_enabled) {
  coakka_v2_stream_lane_config_t config{};
  config.struct_size = sizeof(config);
  config.flags = COAKKA_V2_STREAM_LANE_ENABLE_SUBSCRIBER;
  config.capacity = audio_enabled ? 2u : 1u;
  config.subscriber_worker_count = audio_enabled ? 2u : 1u;
  config.max_frame_bytes = max_frame_bytes;
  config.max_window_bytes = max_frame_bytes * kWindowFrameCount;
  config.io_timeout_ms = kIoTimeoutMs;
  config.progress_frames = 30u;
  config.progress_interval_ms = 250u;
  config.pressure_after_ms = 100u;
  config.stalled_after_ms = 1000u;
  config.recovery_after_ms = 500u;
  config.pressure_observation_ms = 100u;
  return config;
}
#endif

#if defined(COAKKA_V2_CAMERA_HOST_APP)
#if defined(_WIN32)
using socket_handle_t = SOCKET;
using socket_length_t = int;
constexpr socket_handle_t kInvalidSocket = INVALID_SOCKET;
void close_socket(socket_handle_t fd) { (void)closesocket(fd); }
#else
using socket_handle_t = int;
using socket_length_t = socklen_t;
constexpr socket_handle_t kInvalidSocket = -1;
void close_socket(socket_handle_t fd) { (void)close(fd); }
#endif

bool set_socket_nonblocking(socket_handle_t fd, bool enabled) {
#if defined(_WIN32)
  u_long mode = enabled ? 1u : 0u;
  return ioctlsocket(fd, FIONBIO, &mode) == 0;
#else
  const int flags = fcntl(fd, F_GETFL, 0);
  return flags >= 0 &&
         fcntl(fd, F_SETFL,
               enabled ? (flags | O_NONBLOCK) : (flags & ~O_NONBLOCK)) == 0;
#endif
}

bool connect_with_timeout(socket_handle_t fd, const sockaddr *address,
                          socket_length_t address_size) {
  if (!set_socket_nonblocking(fd, true)) {
    return false;
  }
  const int result = connect(fd, address, address_size);
  if (result == 0) {
    return set_socket_nonblocking(fd, false);
  }
#if defined(_WIN32)
  if (WSAGetLastError() != WSAEWOULDBLOCK) {
    return false;
  }
#else
  if (errno != EINPROGRESS) {
    return false;
  }
#endif
  fd_set writable;
  FD_ZERO(&writable);
  FD_SET(fd, &writable);
  timeval timeout{2, 0};
  const int selected =
      select(static_cast<int>(fd + 1), nullptr, &writable, nullptr, &timeout);
  int socket_error = 0;
#if defined(_WIN32)
  int error_size = sizeof(socket_error);
  const int read_error =
      getsockopt(fd, SOL_SOCKET, SO_ERROR,
                 reinterpret_cast<char *>(&socket_error), &error_size);
#else
  socklen_t error_size = sizeof(socket_error);
  const int read_error =
      getsockopt(fd, SOL_SOCKET, SO_ERROR, &socket_error, &error_size);
#endif
  return selected > 0 && read_error == 0 && socket_error == 0 &&
         set_socket_nonblocking(fd, false);
}

bool send_all(socket_handle_t fd, std::string_view bytes) {
  size_t offset = 0u;
  while (offset < bytes.size()) {
    const auto sent = send(fd, bytes.data() + offset,
                           static_cast<int>(bytes.size() - offset), 0);
    if (sent <= 0) {
#if !defined(_WIN32)
      if (sent < 0 && errno == EINTR) {
        continue;
      }
#endif
      return false;
    }
    offset += static_cast<size_t>(sent);
  }
  return true;
}

bool request_camera_profile(const char *host, uint16_t port, const char *token,
                            const coakka_v2_camera_profile_request_t &profile,
                            std::string *error) {
  addrinfo hints{};
  hints.ai_family = AF_INET;
  hints.ai_socktype = SOCK_STREAM;
  addrinfo *addresses = nullptr;
  const std::string port_text = std::to_string(port);
  if (getaddrinfo(host, port_text.c_str(), &hints, &addresses) != 0) {
    *error = "camera control address resolution failed";
    return false;
  }
  socket_handle_t fd = kInvalidSocket;
  for (addrinfo *address = addresses; address != nullptr;
       address = address->ai_next) {
    fd = socket(address->ai_family, address->ai_socktype, address->ai_protocol);
    if (fd == kInvalidSocket) {
      continue;
    }
#if defined(_WIN32)
    const DWORD timeout_ms = 2000u;
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO,
                     reinterpret_cast<const char *>(&timeout_ms),
                     sizeof(timeout_ms));
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO,
                     reinterpret_cast<const char *>(&timeout_ms),
                     sizeof(timeout_ms));
#else
    timeval timeout{2, 0};
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
#endif
    if (connect_with_timeout(
            fd, address->ai_addr,
            static_cast<socket_length_t>(address->ai_addrlen))) {
      break;
    }
    close_socket(fd);
    fd = kInvalidSocket;
  }
  freeaddrinfo(addresses);
  if (fd == kInvalidSocket) {
    *error = "camera control connection failed";
    return false;
  }
  const std::string body = "{\"width\":" + std::to_string(profile.width) +
                           ",\"height\":" + std::to_string(profile.height) +
                           ",\"fps\":" + std::to_string(profile.fps) + "}";
  const std::string request =
      "POST /v1/profile HTTP/1.1\r\nHost: " + std::string(host) + ":" +
      port_text + "\r\nAuthorization: Bearer " + token +
      "\r\nContent-Type: application/json\r\nConnection: close\r\n"
      "Content-Length: " +
      std::to_string(body.size()) + "\r\n\r\n" + body;
  std::array<char, 512u> response{};
  const bool sent = send_all(fd, request);
  size_t received = 0u;
  bool header_complete = false;
  while (sent && received < response.size()) {
    const auto count = recv(fd, response.data() + received,
                            static_cast<int>(response.size() - received), 0);
    if (count <= 0) {
#if !defined(_WIN32)
      if (count < 0 && errno == EINTR) {
        continue;
      }
#endif
      break;
    }
    received += static_cast<size_t>(count);
    if (std::string_view(response.data(), received).find("\r\n\r\n") !=
        std::string_view::npos) {
      header_complete = true;
      break;
    }
  }
  close_socket(fd);
  if (!sent || received == 0u || !header_complete) {
    *error = "camera control response failed";
    return false;
  }
  const std::string_view reply(response.data(), received);
  if (!reply.starts_with("HTTP/1.1 202 ")) {
    *error = "camera rejected the requested profile";
    return false;
  }
  return true;
}

struct SubscriberControl {
  coakka_v2_camera_web_gateway_t *gateway = nullptr;
  const char *remote_host = nullptr;
  uint16_t remote_control_port = 0u;
  const char *token = nullptr;
  const char *audio_session_id = nullptr;
};
#endif

bool wait_terminal(coakka_v2_stream_lane_t *lane, const char *session_id,
                   const char *role, uint32_t direction,
                   coakka_v2_stream_session_snapshot_t *out
#if defined(COAKKA_V2_CAMERA_HOST_APP)
                   ,
                   SubscriberControl *control = nullptr) {
#else
) {
#endif
  out->struct_size = sizeof(*out);
  if (coakka_v2_stream_lane_get_session(lane, session_id, direction, out) !=
      COAKKA_V2_OK) {
    return false;
  }
  uint32_t last_pressure_state = UINT32_MAX;
  uint32_t last_reason_bits = UINT32_MAX;
  bool cancel_sent = false;
  while (!terminal_state(out->state)) {
#if defined(COAKKA_V2_CAMERA_HOST_APP)
    if (control != nullptr) {
      control->gateway->set_transport_state(out->state);
      coakka_v2_stream_session_snapshot_t audio{};
      audio.struct_size = sizeof(audio);
      if (control->audio_session_id != nullptr &&
          coakka_v2_stream_lane_get_session(
              lane, control->audio_session_id,
              COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE, &audio) == COAKKA_V2_OK) {
        control->gateway->set_audio_transport_state(audio.state);
      }
      coakka_v2_camera_profile_request_t profile;
      if (control->gateway->take_profile_request(&profile)) {
        std::string error;
        const bool accepted = request_camera_profile(
            control->remote_host, control->remote_control_port, control->token,
            profile, &error);
        control->gateway->complete_profile_request(profile.sequence, accepted,
                                                   std::move(error));
      }
    }
#endif
    if (!cancel_sent &&
        (interrupted != 0 ||
#if defined(COAKKA_V2_CAMERA_HOST_APP)
         (control != nullptr && control->gateway->disconnect_requested())
#else
         false
#endif
             )) {
      (void)coakka_v2_stream_lane_cancel_session(lane, session_id, direction);
      cancel_sent = true;
    }
    const uint64_t after = out->update_sequence;
    out->struct_size = sizeof(*out);
    const coakka_v2_status_t rc = coakka_v2_stream_lane_wait_session(
        lane, session_id, direction, after, kWaitQuantumMs, out);
    if (rc != COAKKA_V2_OK && rc != COAKKA_V2_ERR_WOULD_BLOCK) {
      return false;
    }
    coakka_v2_stream_pressure_snapshot_t pressure{};
    pressure.struct_size = sizeof(pressure);
    if (coakka_v2_stream_lane_get_pressure(lane, session_id, direction,
                                           &pressure) == COAKKA_V2_OK &&
        (pressure.state != last_pressure_state ||
         pressure.reason_bits != last_reason_bits)) {
      std::cout << "STREAM_PRESSURE role=" << role
                << " state=" << pressure.state
                << " reasons=" << pressure.reason_bits
                << " delivery_bps=" << pressure.observed_delivery_bps
                << " credit=" << pressure.available_credit_bytes
                << " window=" << pressure.window_capacity_bytes
                << " operation_ns=" << pressure.current_operation_ns << '\n'
                << std::flush;
      last_pressure_state = pressure.state;
      last_reason_bits = pressure.reason_bits;
    }
  }
#if defined(COAKKA_V2_CAMERA_HOST_APP)
  if (control != nullptr) {
    control->gateway->set_transport_state(out->state);
  }
#endif
  return true;
}

void print_result(const char *role,
                  const coakka_v2_stream_session_snapshot_t &snapshot) {
  std::cout << "STREAM_RESULT role=" << role << " state=" << snapshot.state
            << " result=" << snapshot.result << " frames=" << snapshot.frames
            << " bytes=" << snapshot.bytes
            << " dropped=" << snapshot.dropped_frames << " detail=\""
            << snapshot.detail << "\"\n";
}

bool clean_terminal(const coakka_v2_stream_session_snapshot_t &snapshot) {
  return (snapshot.state == COAKKA_V2_STREAM_STATE_ENDED &&
          snapshot.result == COAKKA_V2_STREAM_RESULT_OK) ||
         (snapshot.state == COAKKA_V2_STREAM_STATE_CANCELED &&
          snapshot.result == COAKKA_V2_STREAM_RESULT_CANCELED_BY_HOST);
}

#if defined(COAKKA_V2_CAMERA_PI_APP)
class SwitchableCameraSource {
public:
  SwitchableCameraSource(std::string device, uint32_t width, uint32_t height,
                         uint32_t fps)
      : device_(std::move(device)), width_(width), height_(height), fps_(fps) {}

  ~SwitchableCameraSource() { coakka_v2_v4l2_mjpeg_source_close(source_); }

  bool open(std::string *error) {
    source_ = open_profile(width_, height_, fps_, error);
    return source_ != nullptr;
  }

  bool request(uint32_t width, uint32_t height, uint32_t fps,
               std::string *error) {
    if (fps != 30u || !((width == 640u && height == 480u) ||
                        (width == 800u && height == 600u) ||
                        (width == 1280u && height == 720u) ||
                        (width == 1920u && height == 1080u))) {
      *error = "profile is not in the bounded camera profile set";
      return false;
    }
    std::lock_guard<std::mutex> lock(request_mutex_);
    if (profile_pending_) {
      *error = "camera profile change is already pending";
      return false;
    }
    requested_width_ = width;
    requested_height_ = height;
    requested_fps_ = fps;
    profile_pending_ = true;
    return true;
  }

  int snapshot(coakka_v2_v4l2_mjpeg_snapshot_t *snapshot) const {
    if (coakka_v2_v4l2_mjpeg_source_snapshot(source_, snapshot) != 0) {
      return -1;
    }
    snapshot->delivered_frames += completed_frames_;
    snapshot->delivered_bytes += completed_bytes_;
    snapshot->dropped_frames += completed_drops_;
    return 0;
  }

  static coakka_v2_status_t next(void *context, uint8_t *destination,
                                 size_t capacity,
                                 coakka_v2_stream_frame_t *out_frame) noexcept {
    auto *self = static_cast<SwitchableCameraSource *>(context);
    if (self == nullptr) {
      return COAKKA_V2_ERR_INVALID_ARG;
    }
    if (!self->apply_pending_profile()) {
      return COAKKA_V2_ERR_IO;
    }
    return coakka_v2_v4l2_mjpeg_source_next(self->source_, destination,
                                            capacity, out_frame);
  }

private:
  coakka_v2_v4l2_mjpeg_source_t *open_profile(uint32_t width, uint32_t height,
                                              uint32_t fps,
                                              std::string *error) const {
    coakka_v2_v4l2_mjpeg_config_t config{};
    config.device = device_.c_str();
    config.width = width;
    config.height = height;
    config.fps = fps;
    config.buffer_count = kCameraBufferCount;
    config.poll_timeout_ms = kCameraPollTimeoutMs;
    std::array<char, 256u> detail{};
    coakka_v2_v4l2_mjpeg_source_t *source =
        coakka_v2_v4l2_mjpeg_source_open(&config, detail.data(), detail.size());
    if (source == nullptr) {
      *error = detail.data();
    }
    return source;
  }

  bool apply_pending_profile() {
    uint32_t width = 0u;
    uint32_t height = 0u;
    uint32_t fps = 0u;
    {
      std::lock_guard<std::mutex> lock(request_mutex_);
      if (!profile_pending_) {
        return true;
      }
      width = requested_width_;
      height = requested_height_;
      fps = requested_fps_;
      profile_pending_ = false;
    }
    coakka_v2_v4l2_mjpeg_snapshot_t completed{};
    if (coakka_v2_v4l2_mjpeg_source_snapshot(source_, &completed) == 0) {
      completed_frames_ += completed.delivered_frames;
      completed_bytes_ += completed.delivered_bytes;
      completed_drops_ += completed.dropped_frames;
    }
    coakka_v2_v4l2_mjpeg_source_close(source_);
    source_ = nullptr;
    std::string error;
    source_ = open_profile(width, height, fps, &error);
    if (source_ == nullptr) {
      source_ = open_profile(width_, height_, fps_, &error);
      return source_ != nullptr;
    }
    width_ = width;
    height_ = height;
    fps_ = fps;
    std::cout << "CAMERA_PROFILE profile=" << width_ << 'x' << height_ << '@'
              << fps_ << '\n'
              << std::flush;
    return true;
  }

  std::string device_;
  uint32_t width_;
  uint32_t height_;
  uint32_t fps_;
  coakka_v2_v4l2_mjpeg_source_t *source_ = nullptr;
  std::mutex request_mutex_;
  bool profile_pending_ = false;
  uint32_t requested_width_ = 0u;
  uint32_t requested_height_ = 0u;
  uint32_t requested_fps_ = 0u;
  uint64_t completed_frames_ = 0u;
  uint64_t completed_bytes_ = 0u;
  uint64_t completed_drops_ = 0u;
};

bool request_camera_profile_on_source(void *context, uint32_t width,
                                      uint32_t height, uint32_t fps,
                                      char *error, size_t error_capacity) {
  auto *source = static_cast<SwitchableCameraSource *>(context);
  std::string detail;
  const bool accepted =
      source != nullptr && source->request(width, height, fps, &detail);
  if (!accepted && error != nullptr && error_capacity != 0u) {
    (void)std::snprintf(error, error_capacity, "%s", detail.c_str());
  }
  return accepted;
}

struct AudioSourceOwner {
  coakka_v2_alsa_pcm_source_t *value = nullptr;
  ~AudioSourceOwner() { coakka_v2_alsa_pcm_source_close(value); }
};

bool publish_camera(const char *device, const char *audio_device,
                    const char *bind_host, uint16_t bind_port,
                    const char *session_id, const char *token, uint32_t width,
                    uint32_t height, uint32_t fps) {
  SwitchableCameraSource camera(device, width, height, fps);
  std::string error;
  if (!camera.open(&error)) {
    std::cerr << "camera open failed: " << error << '\n';
    return false;
  }
  coakka_v2_v4l2_mjpeg_snapshot_t camera_snapshot{};
  if (camera.snapshot(&camera_snapshot) != 0 ||
      camera_snapshot.max_frame_bytes == 0u ||
      camera_snapshot.max_frame_bytes > kLaneMaxFrameBytes) {
    std::cerr << "camera frame budget is not valid for Stream Lane\n";
    return false;
  }
  const bool audio_enabled = std::string_view(audio_device) != "off";
  AudioSourceOwner audio;
  if (audio_enabled) {
    std::array<char, 256u> audio_detail{};
    audio.value = coakka_v2_alsa_pcm_source_open(
        audio_device, audio_detail.data(), audio_detail.size());
    if (audio.value == nullptr) {
      std::cerr << "audio open failed: " << audio_detail.data() << '\n';
      return false;
    }
  }

  const auto config = publisher_config(bind_host, bind_port, audio_enabled);
  coakka::v2::camera::StreamLaneOwner lane{
      coakka_v2_stream_lane_create(&config)};
  if (lane.get() == nullptr) {
    std::cerr << "publisher lane creation failed\n";
    return false;
  }
  coakka_v2_stream_publish_spec_t video_publish{};
  video_publish.struct_size = sizeof(video_publish);
  video_publish.session_id = session_id;
  video_publish.authorization_token = token;
  video_publish.format_id = kMjpegFrameFormat;
  video_publish.max_frame_bytes = kLaneMaxFrameBytes;
  video_publish.source_next = SwitchableCameraSource::next;
  video_publish.source_context = &camera;
  const std::string audio_session_id = std::string(session_id) + ".audio";
  coakka_v2_stream_publish_spec_t audio_publish{};
  audio_publish.struct_size = sizeof(audio_publish);
  audio_publish.session_id = audio_session_id.c_str();
  audio_publish.authorization_token = token;
  audio_publish.format_id = kPcmS16leFrameFormat;
  audio_publish.max_frame_bytes = COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES;
  audio_publish.source_next = coakka_v2_alsa_pcm_source_next;
  audio_publish.source_context = audio.value;
  const coakka_v2_status_t start_rc = coakka_v2_stream_lane_start(lane.get());
  if (start_rc != COAKKA_V2_OK) {
    std::cerr << "publisher lane start failed: status=" << start_rc << '\n';
    return false;
  }
  const coakka_v2_status_t video_prepare_rc =
      coakka_v2_stream_lane_prepare_publish(lane.get(), &video_publish);
  if (video_prepare_rc != COAKKA_V2_OK) {
    std::cerr << "video publisher prepare failed: status=" << video_prepare_rc
              << '\n';
    return false;
  }
  if (audio_enabled) {
    const coakka_v2_status_t audio_prepare_rc =
        coakka_v2_stream_lane_prepare_publish(lane.get(), &audio_publish);
    if (audio_prepare_rc != COAKKA_V2_OK) {
      std::cerr << "audio publisher prepare failed: status="
                << audio_prepare_rc << '\n';
      return false;
    }
  }
  uint16_t port = 0u;
  const coakka_v2_status_t bound_port_rc =
      coakka_v2_stream_lane_get_bound_port(lane.get(), &port);
  if (bound_port_rc != COAKKA_V2_OK) {
    std::cerr << "publisher bound-port lookup failed: status=" << bound_port_rc
              << '\n';
    return false;
  }
  if (port == UINT16_MAX) {
    std::cerr << "publisher port leaves no room for camera control\n";
    return false;
  }
  coakka_v2_camera_control_server_config_t control_config;
  control_config.bind_host = bind_host;
  control_config.bind_port = static_cast<uint16_t>(port + 1u);
  control_config.authorization_token = token;
  control_config.handler_context = &camera;
  control_config.profile_handler = request_camera_profile_on_source;
  auto control = coakka_v2_camera_control_server_t::create(control_config);
  if (control == nullptr || !control->start(&error)) {
    std::cerr << "camera control start failed: " << error << '\n';
    return false;
  }
  std::cout << "CAMERA_READY device=" << device
            << " profile=" << camera_snapshot.width << 'x'
            << camera_snapshot.height << '@' << camera_snapshot.fps
            << " format=mjpeg max_frame=" << kLaneMaxFrameBytes
            << " buffers=" << kCameraBufferCount << '\n'
            << "AUDIO_READY enabled=" << (audio_enabled ? "true" : "false");
  if (audio_enabled) {
    std::cout << " device=" << audio_device << " format=s16le"
              << " rate=" << COAKKA_V2_CAMERA_AUDIO_SAMPLE_RATE
              << " channels=" << COAKKA_V2_CAMERA_AUDIO_CHANNELS
              << " frame_bytes=" << COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES;
  }
  std::cout << '\n'
            << "CAMERA_CONTROL host=" << bind_host
            << " port=" << control->bound_port() << '\n'
            << "STREAM_GRANT host=" << bind_host << " port=" << port
            << " session=" << session_id << " format=" << kMjpegFrameFormat
            << " max_frame=" << kLaneMaxFrameBytes
            << " window=" << COAKKA_V2_STREAM_LANE_MAX_WINDOW_BYTES << '\n'
            << std::flush;
  if (audio_enabled) {
    std::cout << "AUDIO_GRANT session=" << audio_session_id
              << " format=" << kPcmS16leFrameFormat
              << " max_frame=" << COAKKA_V2_CAMERA_AUDIO_FRAME_BYTES << '\n'
              << std::flush;
  }

  coakka_v2_stream_session_snapshot_t stream_snapshot{};
  const bool waited =
      wait_terminal(lane.get(), session_id, "camera-service",
                    COAKKA_V2_STREAM_DIRECTION_PUBLISH, &stream_snapshot);
  if (audio_enabled) {
    (void)coakka_v2_stream_lane_cancel_session(
        lane.get(), audio_session_id.c_str(),
        COAKKA_V2_STREAM_DIRECTION_PUBLISH);
  }
  control->stop();
  (void)coakka_v2_stream_lane_stop(lane.get());
  (void)camera.snapshot(&camera_snapshot);
  coakka_v2_alsa_pcm_snapshot_t audio_snapshot{};
  if (audio_enabled) {
    (void)coakka_v2_alsa_pcm_source_snapshot(audio.value, &audio_snapshot);
  }
  if (waited) {
    print_result("publisher", stream_snapshot);
  }
  std::cout << "CAMERA_RESULT frames=" << camera_snapshot.delivered_frames
            << " bytes=" << camera_snapshot.delivered_bytes
            << " dropped=" << camera_snapshot.dropped_frames << '\n'
            << "AUDIO_RESULT enabled=" << (audio_enabled ? "true" : "false")
            << " frames=" << audio_snapshot.delivered_frames
            << " bytes=" << audio_snapshot.delivered_bytes
            << " overruns=" << audio_snapshot.recovered_overruns << '\n';
  return waited && clean_terminal(stream_snapshot);
}
#endif

#if defined(COAKKA_V2_CAMERA_HOST_APP)
coakka_v2_status_t
consume_frame(void *context, const uint8_t *data,
              const coakka_v2_stream_frame_t *frame) noexcept {
  auto *gateway = static_cast<coakka_v2_camera_web_gateway_t *>(context);
  return gateway != nullptr ? gateway->consume(data, frame)
                            : COAKKA_V2_ERR_INVALID_ARG;
}

coakka_v2_status_t
consume_audio_frame(void *context, const uint8_t *data,
                    const coakka_v2_stream_frame_t *frame) noexcept {
  auto *gateway = static_cast<coakka_v2_camera_web_gateway_t *>(context);
  return gateway != nullptr ? gateway->consume_audio(data, frame)
                            : COAKKA_V2_ERR_INVALID_ARG;
}

bool subscribe_web(const char *remote_host, uint16_t remote_port,
                   const char *web_host, uint16_t web_port,
                   const char *session_id, const char *token,
                   uint32_t max_frame_bytes, bool audio_enabled,
                   const char *recording_directory, const char *ffmpeg_binary) {
  coakka_v2_camera_web_gateway_config_t gateway_config;
  gateway_config.bind_host = web_host;
  gateway_config.bind_port = web_port;
  gateway_config.max_frame_bytes = max_frame_bytes;
  gateway_config.initial_width = 1280u;
  gateway_config.initial_height = 720u;
  gateway_config.fps = 30u;
  gateway_config.recording_directory = recording_directory;
  gateway_config.ffmpeg_binary = ffmpeg_binary;
  auto gateway = coakka_v2_camera_web_gateway_t::create(gateway_config);
  std::string error;
  if (gateway == nullptr || !gateway->start(&error)) {
    std::cerr << "web gateway start failed: " << error << '\n';
    return false;
  }
  const auto ready = gateway->snapshot();
  std::cout << "WEB_READY url=http://" << web_host << ':' << ready.bound_port
            << "/ control=/ws/control stream=/stream.mjpeg queue_frames=4"
            << " recording_dir=\"" << recording_directory << "\"\n"
            << std::flush;

  const auto config = subscriber_config(max_frame_bytes, audio_enabled);
  coakka::v2::camera::StreamLaneOwner lane{
      coakka_v2_stream_lane_create(&config)};
  if (lane.get() == nullptr ||
      coakka_v2_stream_lane_start(lane.get()) != COAKKA_V2_OK) {
    std::cerr << "subscriber lane creation/start failed\n";
    gateway->stop();
    return false;
  }
  coakka_v2_stream_subscribe_spec_t subscribe{};
  subscribe.struct_size = sizeof(subscribe);
  subscribe.session_id = session_id;
  subscribe.authorization_token = token;
  subscribe.remote_host = remote_host;
  subscribe.remote_port = remote_port;
  subscribe.format_id = kMjpegFrameFormat;
  subscribe.max_frame_bytes = max_frame_bytes;
  subscribe.initial_window_bytes = max_frame_bytes * kWindowFrameCount;
  subscribe.timeout_ms = kIoTimeoutMs;
  subscribe.consume = consume_frame;
  subscribe.consumer_context = gateway.get();
  gateway->set_transport_state(COAKKA_V2_STREAM_STATE_CONNECTING);
  if (coakka_v2_stream_lane_subscribe(lane.get(), &subscribe) != COAKKA_V2_OK) {
    std::cerr << "subscriber submit failed\n";
    gateway->stop();
    return false;
  }
  const std::string audio_session_id = std::string(session_id) + ".audio";
  coakka_v2_stream_subscribe_spec_t audio_subscribe{};
  audio_subscribe.struct_size = sizeof(audio_subscribe);
  audio_subscribe.session_id = audio_session_id.c_str();
  audio_subscribe.authorization_token = token;
  audio_subscribe.remote_host = remote_host;
  audio_subscribe.remote_port = remote_port;
  audio_subscribe.format_id = kPcmS16leFrameFormat;
  audio_subscribe.max_frame_bytes = kAudioFrameBytes;
  audio_subscribe.initial_window_bytes = kAudioFrameBytes * kAudioWindowFrames;
  audio_subscribe.timeout_ms = kIoTimeoutMs;
  audio_subscribe.consume = consume_audio_frame;
  audio_subscribe.consumer_context = gateway.get();
  if (audio_enabled) {
    gateway->set_audio_transport_state(COAKKA_V2_STREAM_STATE_CONNECTING);
    if (coakka_v2_stream_lane_subscribe(lane.get(), &audio_subscribe) !=
        COAKKA_V2_OK) {
      std::cerr << "audio subscriber submit failed\n";
      (void)coakka_v2_stream_lane_cancel_session(
          lane.get(), session_id, COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE);
      (void)coakka_v2_stream_lane_stop(lane.get());
      gateway->stop();
      return false;
    }
  }

  coakka_v2_stream_session_snapshot_t snapshot{};
  SubscriberControl control{gateway.get(), remote_host,
                            static_cast<uint16_t>(remote_port + 1u), token,
                            audio_enabled ? audio_session_id.c_str() : nullptr};
  const bool waited =
      wait_terminal(lane.get(), session_id, "livestream-service",
                    COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE, &snapshot, &control);
  if (audio_enabled) {
    (void)coakka_v2_stream_lane_cancel_session(
        lane.get(), audio_session_id.c_str(),
        COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE);
  }
  (void)coakka_v2_stream_lane_stop(lane.get());
  gateway->stop();
  if (waited) {
    print_result("subscriber", snapshot);
  }
  const auto result = gateway->snapshot();
  std::cout << "WEB_RESULT received_frames=" << result.received_frames
            << " received_bytes=" << result.received_bytes
            << " display_drops=" << result.display_queue_drops
            << " browser_frames=" << result.browser_frames
            << " browser_bytes=" << result.browser_bytes
            << " recording_frames=" << result.recording_frames
            << " recording_bytes=" << result.recording_bytes
            << " recording_drops=" << result.recording_queue_drops
            << " recording_audio_frames=" << result.recording_audio_frames
            << " recording_audio_bytes=" << result.recording_audio_bytes
            << " recording_audio_drops=" << result.recording_audio_queue_drops
            << " recording_path=\"" << result.recording_path << "\"\n";
  return waited && clean_terminal(snapshot);
}
#endif

#if defined(COAKKA_V2_CAMERA_HOST_APP)
std::string default_recording_directory() {
  return (std::filesystem::temp_directory_path() / "coakka-camera-recordings")
      .string();
}

const char *default_ffmpeg_binary() {
#if defined(_WIN32)
  return "ffmpeg.exe";
#elif defined(__APPLE__)
  return "/opt/homebrew/bin/ffmpeg";
#else
  return "/usr/bin/ffmpeg";
#endif
}
#endif

bool valid_camera_id(std::string_view value) noexcept {
  if (value.empty() || value.size() > 96u) {
    return false;
  }
  for (const char character : value) {
    const bool alpha = (character >= 'a' && character <= 'z') ||
                       (character >= 'A' && character <= 'Z');
    const bool digit = character >= '0' && character <= '9';
    if (!alpha && !digit && character != '-' && character != '_' &&
        character != '.') {
      return false;
    }
  }
  return true;
}

bool read_token_from_environment(const std::string &environment_name,
                                 std::string *token, std::string *error) {
  if (environment_name.empty()) {
    *error = "token environment-variable name is empty";
    return false;
  }
  const char *value = std::getenv(environment_name.c_str());
  if (value == nullptr || value[0] == '\0') {
    *error = "environment variable " + environment_name + " is not set";
    return false;
  }
  *token = value;
  return true;
}

#if defined(COAKKA_V2_CAMERA_PI_APP)
struct PiOptions {
  std::string camera_id;
  std::string video_device;
  std::string audio_device = "off";
  std::string bind_host = "0.0.0.0";
  uint16_t port = 0u;
  std::string token_environment = "CAMERA_TOKEN";
  std::string token;
  uint32_t width = 1280u;
  uint32_t height = 720u;
  uint32_t fps = 30u;
};

CliParseResult parse_pi_options(int argc, char **argv, PiOptions *options,
                                std::string *error) {
  NamedOptionReader reader{argc, argv};
  bool have_camera_id = false;
  bool have_video_device = false;
  bool have_video_index = false;
  bool have_port = false;
  while (reader.index < argc) {
    std::string_view name;
    std::string_view value;
    bool help_requested = false;
    if (!reader.next(&name, &value, &help_requested, error)) {
      return CliParseResult::error;
    }
    if (help_requested) {
      return CliParseResult::help;
    }
    if (name == "camera-id") {
      options->camera_id = value;
      have_camera_id = true;
    } else if (name == "video-device") {
      options->video_device = value;
      have_video_device = true;
    } else if (name == "video-index") {
      uint32_t index = 0u;
      if (!parse_unsigned(value.data(), &index) || index > 255u) {
        *error = "--video-index must be in range 0..255";
        return CliParseResult::error;
      }
      options->video_device = "/dev/video" + std::to_string(index);
      have_video_index = true;
    } else if (name == "audio-device") {
      options->audio_device = value;
    } else if (name == "bind-host") {
      options->bind_host = value;
    } else if (name == "port") {
      uint32_t port = 0u;
      if (!parse_unsigned(value.data(), &port) || port == 0u ||
          port >= UINT16_MAX) {
        *error = "--port must be in range 1..65534; port + 1 is control";
        return CliParseResult::error;
      }
      options->port = static_cast<uint16_t>(port);
      have_port = true;
    } else if (name == "token-env") {
      options->token_environment = value;
    } else if (name == "width") {
      if (!parse_unsigned(value.data(), &options->width) ||
          options->width == 0u) {
        *error = "--width must be a positive integer";
        return CliParseResult::error;
      }
    } else if (name == "height") {
      if (!parse_unsigned(value.data(), &options->height) ||
          options->height == 0u) {
        *error = "--height must be a positive integer";
        return CliParseResult::error;
      }
    } else if (name == "fps") {
      if (!parse_unsigned(value.data(), &options->fps) || options->fps == 0u) {
        *error = "--fps must be a positive integer";
        return CliParseResult::error;
      }
    } else {
      *error = "unknown option --" + std::string(name);
      return CliParseResult::error;
    }
  }
  if (!have_camera_id || !valid_camera_id(options->camera_id)) {
    *error = "--camera-id is required and must use [A-Za-z0-9._-]";
    return CliParseResult::error;
  }
  if (have_video_device == have_video_index) {
    *error = "set exactly one of --video-device or --video-index";
    return CliParseResult::error;
  }
  if (!have_port) {
    *error = "--port is required";
    return CliParseResult::error;
  }
  if (!read_token_from_environment(options->token_environment, &options->token,
                                   error)) {
    return CliParseResult::error;
  }
  return CliParseResult::ok;
}
#else
struct HostOptions {
  std::string camera_id;
  std::string publisher_host;
  uint16_t publisher_port = 0u;
  uint16_t web_port = 8091u;
  std::string token_environment = "CAMERA_TOKEN";
  std::string token;
  uint32_t max_frame_bytes = kLaneMaxFrameBytes;
  bool audio_enabled = false;
  std::string recording_directory = default_recording_directory();
  std::string ffmpeg_binary = default_ffmpeg_binary();
};

CliParseResult parse_host_options(int argc, char **argv, HostOptions *options,
                                  std::string *error) {
  NamedOptionReader reader{argc, argv};
  bool have_camera_id = false;
  bool have_publisher_host = false;
  bool have_publisher_port = false;
  while (reader.index < argc) {
    std::string_view name;
    std::string_view value;
    bool help_requested = false;
    if (!reader.next(&name, &value, &help_requested, error)) {
      return CliParseResult::error;
    }
    if (help_requested) {
      return CliParseResult::help;
    }
    if (name == "camera-id") {
      options->camera_id = value;
      have_camera_id = true;
    } else if (name == "publisher-host") {
      options->publisher_host = value;
      have_publisher_host = true;
    } else if (name == "publisher-port") {
      uint32_t port = 0u;
      if (!parse_unsigned(value.data(), &port) || port == 0u ||
          port >= UINT16_MAX) {
        *error = "--publisher-port must be in range 1..65534";
        return CliParseResult::error;
      }
      options->publisher_port = static_cast<uint16_t>(port);
      have_publisher_port = true;
    } else if (name == "web-port") {
      uint32_t port = 0u;
      if (!parse_unsigned(value.data(), &port) || port == 0u ||
          port > UINT16_MAX) {
        *error = "--web-port must be in range 1..65535";
        return CliParseResult::error;
      }
      options->web_port = static_cast<uint16_t>(port);
    } else if (name == "token-env") {
      options->token_environment = value;
    } else if (name == "max-frame-bytes") {
      if (!parse_unsigned(value.data(), &options->max_frame_bytes) ||
          options->max_frame_bytes != kLaneMaxFrameBytes) {
        *error = "--max-frame-bytes must be 4194304 for this build";
        return CliParseResult::error;
      }
    } else if (name == "audio") {
      if (value != "on" && value != "off") {
        *error = "--audio must be on or off";
        return CliParseResult::error;
      }
      options->audio_enabled = value == "on";
    } else if (name == "recording-directory") {
      options->recording_directory = value;
    } else if (name == "ffmpeg-binary") {
      options->ffmpeg_binary = value;
    } else {
      *error = "unknown option --" + std::string(name);
      return CliParseResult::error;
    }
  }
  if (!have_camera_id || !valid_camera_id(options->camera_id)) {
    *error = "--camera-id is required and must use [A-Za-z0-9._-]";
    return CliParseResult::error;
  }
  if (!have_publisher_host) {
    *error = "--publisher-host is required";
    return CliParseResult::error;
  }
  if (!have_publisher_port) {
    *error = "--publisher-port is required";
    return CliParseResult::error;
  }
  if (options->max_frame_bytes >
      COAKKA_V2_STREAM_LANE_MAX_WINDOW_BYTES / kWindowFrameCount) {
    *error = "frame budget exceeds the subscriber window";
    return CliParseResult::error;
  }
  if (!read_token_from_environment(options->token_environment, &options->token,
                                   error)) {
    return CliParseResult::error;
  }
  return CliParseResult::ok;
}
#endif

void usage(const char *program, std::ostream &stream) {
#if defined(COAKKA_V2_CAMERA_PI_APP)
  stream << "Usage: " << program << " [options]\n\n"
         << "Required:\n"
         << "  --camera-id ID           Logical ID shared with the host app\n"
         << "  --video-device PATH      Stable V4L2 path such as "
            "/dev/v4l/by-id/...\n"
         << "  --video-index N          Shorthand for /dev/videoN (use one "
            "video selector)\n"
         << "  --port PORT              Stream port; camera control uses PORT "
            "+ 1\n\n"
         << "Optional:\n"
         << "  --audio-device PATH|off  ALSA device (default: off)\n"
         << "  --bind-host HOST         Listener address (default: 0.0.0.0)\n"
         << "  --token-env NAME         Bearer-token variable (default: "
            "CAMERA_TOKEN)\n"
         << "  --width PIXELS           Initial width (default: 1280)\n"
         << "  --height PIXELS          Initial height (default: 720)\n"
         << "  --fps FPS                Initial FPS (default: 30)\n"
         << "  --help                    Show this help\n";
#else
  stream
      << "Usage: " << program << " [options]\n\n"
      << "Required:\n"
      << "  --camera-id ID           Logical ID used by the Pi app\n"
      << "  --publisher-host HOST    Raspberry Pi address or hostname\n"
      << "  --publisher-port PORT    Pi Stream port; control uses PORT + 1\n\n"
      << "Optional:\n"
      << "  --web-port PORT          Loopback browser port (default: 8091)\n"
      << "  --token-env NAME         Bearer-token variable (default: "
         "CAMERA_TOKEN)\n"
      << "  --max-frame-bytes BYTES  Must be 4194304 (default: 4194304)\n"
      << "  --audio on|off           Subscribe to audio (default: off)\n"
      << "  --recording-directory P  Recording output directory\n"
      << "  --ffmpeg-binary PATH     FFmpeg executable path\n"
      << "  --help                    Show this help\n";
#endif
  stream << "Build source: " << kPublicSourceMarker << '\n';
}

} // namespace

int main(int argc, char **argv) {
#if defined(_WIN32) && defined(COAKKA_V2_CAMERA_HOST_APP)
  WSADATA winsock{};
  if (WSAStartup(MAKEWORD(2, 2), &winsock) != 0) {
    std::cerr << "WinSock initialization failed\n";
    return 1;
  }
  struct WinsockOwner {
    ~WinsockOwner() { (void)WSACleanup(); }
  } winsock_owner;
#endif
  std::signal(SIGINT, handle_signal);
  std::signal(SIGTERM, handle_signal);
#if !defined(_WIN32)
  std::signal(SIGPIPE, SIG_IGN);
#endif

#if defined(COAKKA_V2_CAMERA_PI_APP)
  PiOptions options;
  std::string error;
  const CliParseResult parsed = parse_pi_options(argc, argv, &options, &error);
  if (parsed == CliParseResult::help) {
    usage(argc > 0 ? argv[0] : "coakka_camera_pi", std::cout);
    return 0;
  }
  if (parsed == CliParseResult::error) {
    std::cerr << "argument error: " << error << "\n\n";
    usage(argc > 0 ? argv[0] : "coakka_camera_pi", std::cerr);
    return 2;
  }
  return publish_camera(
             options.video_device.c_str(), options.audio_device.c_str(),
             options.bind_host.c_str(), options.port, options.camera_id.c_str(),
             options.token.c_str(), options.width, options.height, options.fps)
             ? 0
             : 1;
#else
  HostOptions options;
  std::string error;
  const CliParseResult parsed =
      parse_host_options(argc, argv, &options, &error);
  if (parsed == CliParseResult::help) {
    usage(argc > 0 ? argv[0] : "coakka_camera_host", std::cout);
    return 0;
  }
  if (parsed == CliParseResult::error) {
    std::cerr << "argument error: " << error << "\n\n";
    usage(argc > 0 ? argv[0] : "coakka_camera_host", std::cerr);
    return 2;
  }
  return subscribe_web(options.publisher_host.c_str(), options.publisher_port,
                       "127.0.0.1", options.web_port, options.camera_id.c_str(),
                       options.token.c_str(), options.max_frame_bytes,
                       options.audio_enabled,
                       options.recording_directory.c_str(),
                       options.ffmpeg_binary.c_str())
             ? 0
             : 1;
#endif
}
