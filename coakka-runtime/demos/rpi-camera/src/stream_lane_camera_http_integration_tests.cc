#include "stream_lane_camera_control_server.h"
#include "stream_lane_camera_web_gateway.h"

#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <cerrno>
#include <csignal>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#endif

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <filesystem>
#include <iostream>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

namespace {

#if defined(_WIN32)
using Socket = SOCKET;
constexpr Socket kInvalidSocket = INVALID_SOCKET;
#else
using Socket = int;
constexpr Socket kInvalidSocket = -1;
#endif

class SocketOwner {
public:
  explicit SocketOwner(Socket socket = kInvalidSocket) : socket_(socket) {}
  ~SocketOwner() { reset(); }
  SocketOwner(const SocketOwner &) = delete;
  SocketOwner &operator=(const SocketOwner &) = delete;

  Socket get() const noexcept { return socket_; }

  void reset(Socket socket = kInvalidSocket) noexcept {
    if (socket_ != kInvalidSocket) {
#if defined(_WIN32)
      (void)closesocket(socket_);
#else
      (void)close(socket_);
#endif
    }
    socket_ = socket;
  }

private:
  Socket socket_;
};

void require(bool condition, std::string_view detail) {
  if (!condition) {
    std::cerr << detail << '\n';
    std::exit(1);
  }
}

Socket connect_loopback(uint16_t port) {
  const Socket socket_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (socket_fd == kInvalidSocket) {
    return kInvalidSocket;
  }
#if defined(_WIN32)
  const DWORD timeout_ms = 3000u;
  (void)setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO,
                   reinterpret_cast<const char *>(&timeout_ms),
                   sizeof(timeout_ms));
  (void)setsockopt(socket_fd, SOL_SOCKET, SO_SNDTIMEO,
                   reinterpret_cast<const char *>(&timeout_ms),
                   sizeof(timeout_ms));
#else
  const timeval timeout{3, 0};
  (void)setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   sizeof(timeout));
  (void)setsockopt(socket_fd, SOL_SOCKET, SO_SNDTIMEO, &timeout,
                   sizeof(timeout));
#endif
  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  if (inet_pton(AF_INET, "127.0.0.1", &address.sin_addr) != 1 ||
      connect(socket_fd, reinterpret_cast<const sockaddr *>(&address),
              sizeof(address)) != 0) {
    SocketOwner cleanup(socket_fd);
    return kInvalidSocket;
  }
  return socket_fd;
}

bool send_all(Socket socket_fd, const uint8_t *data, size_t size) {
  size_t offset = 0u;
  while (offset < size) {
#if defined(_WIN32)
    const int sent =
        send(socket_fd, reinterpret_cast<const char *>(data + offset),
             static_cast<int>(size - offset), 0);
    if (sent == SOCKET_ERROR) {
      return false;
    }
#else
    const ssize_t sent = send(socket_fd, data + offset, size - offset, 0);
    if (sent < 0 && errno == EINTR) {
      continue;
    }
#endif
    if (sent <= 0) {
      return false;
    }
    offset += static_cast<size_t>(sent);
  }
  return true;
}

bool send_all(Socket socket_fd, std::string_view data) {
  return send_all(socket_fd, reinterpret_cast<const uint8_t *>(data.data()),
                  data.size());
}

bool receive_more(Socket socket_fd, std::vector<uint8_t> *buffer) {
  std::array<uint8_t, 4096u> chunk{};
#if defined(_WIN32)
  const int received = recv(socket_fd, reinterpret_cast<char *>(chunk.data()),
                            static_cast<int>(chunk.size()), 0);
  if (received == SOCKET_ERROR) {
    return false;
  }
#else
  ssize_t received = 0;
  do {
    received = recv(socket_fd, chunk.data(), chunk.size(), 0);
  } while (received < 0 && errno == EINTR);
#endif
  if (received <= 0) {
    return false;
  }
  buffer->insert(buffer->end(), chunk.begin(),
                 chunk.begin() + static_cast<size_t>(received));
  return true;
}

bool buffer_contains(const std::vector<uint8_t> &buffer,
                     std::string_view expected) {
  return std::search(buffer.begin(), buffer.end(), expected.begin(),
                     expected.end()) != buffer.end();
}

std::string request_http(uint16_t port, std::string_view request) {
  SocketOwner socket_fd(connect_loopback(port));
  if (socket_fd.get() == kInvalidSocket ||
      !send_all(socket_fd.get(), request)) {
    return {};
  }
  std::vector<uint8_t> response;
  response.reserve(4096u);
  while (response.size() < 64ull * 1024ull &&
         receive_more(socket_fd.get(), &response)) {
  }
  return {reinterpret_cast<const char *>(response.data()), response.size()};
}

std::string request_http_head(uint16_t port, std::string_view request) {
  SocketOwner socket_fd(connect_loopback(port));
  if (socket_fd.get() == kInvalidSocket ||
      !send_all(socket_fd.get(), request)) {
    return {};
  }
  std::vector<uint8_t> response;
  response.reserve(1024u);
  while (response.size() < 16ull * 1024ull &&
         !buffer_contains(response, "\r\n\r\n") &&
         receive_more(socket_fd.get(), &response)) {
  }
  return {reinterpret_cast<const char *>(response.data()), response.size()};
}

bool send_client_text(Socket socket_fd, std::string_view text) {
  if (text.size() > 125u) {
    return false;
  }
  constexpr std::array<uint8_t, 4u> mask{{0x13u, 0x37u, 0x5au, 0xc3u}};
  std::vector<uint8_t> frame(2u + mask.size() + text.size());
  frame[0] = 0x81u;
  frame[1] = static_cast<uint8_t>(0x80u | text.size());
  std::copy(mask.begin(), mask.end(), frame.begin() + 2u);
  for (size_t index = 0u; index < text.size(); ++index) {
    frame[6u + index] =
        static_cast<uint8_t>(text[index]) ^ mask[index % mask.size()];
  }
  return send_all(socket_fd, frame.data(), frame.size());
}

bool take_server_text(Socket socket_fd, std::vector<uint8_t> *buffer,
                      std::string *text) {
  for (;;) {
    if (buffer->size() < 2u && !receive_more(socket_fd, buffer)) {
      return false;
    }
    if (buffer->size() < 2u) {
      continue;
    }
    const uint8_t opcode = (*buffer)[0] & 0x0fu;
    uint64_t payload_size = (*buffer)[1] & 0x7fu;
    size_t header_size = 2u;
    if (((*buffer)[1] & 0x80u) != 0u) {
      return false;
    }
    if (payload_size == 126u) {
      while (buffer->size() < 4u) {
        if (!receive_more(socket_fd, buffer)) {
          return false;
        }
      }
      payload_size = (static_cast<uint64_t>((*buffer)[2]) << 8u) |
                     static_cast<uint64_t>((*buffer)[3]);
      header_size = 4u;
    } else if (payload_size == 127u) {
      return false;
    }
    if (payload_size > 16ull * 1024ull) {
      return false;
    }
    const size_t total = header_size + static_cast<size_t>(payload_size);
    while (buffer->size() < total) {
      if (!receive_more(socket_fd, buffer)) {
        return false;
      }
    }
    text->assign(reinterpret_cast<const char *>(buffer->data() + header_size),
                 static_cast<size_t>(payload_size));
    buffer->erase(
        buffer->begin(),
        buffer->begin() +
            static_cast<std::vector<uint8_t>::difference_type>(total));
    if (opcode == 0x1u) {
      return true;
    }
    if (opcode == 0x8u) {
      return false;
    }
  }
}

void exercise_gateway() {
  const std::filesystem::path recordings =
      std::filesystem::temp_directory_path() /
      "coakka-camera-http-integration-test";
  std::error_code filesystem_error;
  std::filesystem::remove_all(recordings, filesystem_error);

  coakka_v2_camera_web_gateway_config_t config;
  config.bind_host = "127.0.0.1";
  config.bind_port = 0u;
  config.max_frame_bytes = 4096u;
  config.fps = 30u;
  config.recording_directory = recordings.string();
  config.ffmpeg_binary = "ffmpeg-not-invoked-by-http-integration-test";
  auto gateway = coakka_v2_camera_web_gateway_t::create(config);
  require(gateway != nullptr, "gateway create failed");
  std::string error;
  require(gateway->start(&error), "gateway start failed: " + error);
  const uint16_t port = gateway->snapshot().bound_port;
  require(port != 0u, "gateway did not publish its bound port");

  const std::string health =
      request_http(port, "GET /healthz HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                         "Connection: close\r\n\r\n");
  require(health.starts_with("HTTP/1.1 200 ") &&
              health.find(R"({"ok":true})") != std::string::npos,
          "health route did not traverse the native HTTP package");

  SocketOwner websocket(connect_loopback(port));
  require(websocket.get() != kInvalidSocket, "WebSocket connect failed");
  const std::string authority = "127.0.0.1:" + std::to_string(port);
  const std::string rejected_origin = request_http_head(
      port,
      "GET /ws/control HTTP/1.1\r\nHost: " + authority +
          "\r\nOrigin: http://not-the-camera-edge\r\nUpgrade: websocket\r\n"
          "Connection: Upgrade\r\n"
          "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
          "Sec-WebSocket-Version: 13\r\n\r\n");
  require(rejected_origin.starts_with("HTTP/1.1 403 "),
          "WebSocket route accepted a foreign Origin");
  const std::string upgrade =
      "GET /ws/control HTTP/1.1\r\nHost: " + authority + "\r\nOrigin: http://" +
      authority +
      "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
      "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
      "Sec-WebSocket-Version: 13\r\n\r\n";
  require(send_all(websocket.get(), upgrade), "WebSocket upgrade send failed");
  std::vector<uint8_t> websocket_buffer;
  while (!buffer_contains(websocket_buffer, "\r\n\r\n")) {
    require(receive_more(websocket.get(), &websocket_buffer),
            "WebSocket upgrade response failed");
  }
  constexpr std::string_view head_delimiter = "\r\n\r\n";
  const auto head_end =
      std::search(websocket_buffer.begin(), websocket_buffer.end(),
                  head_delimiter.begin(), head_delimiter.end());
  require(head_end != websocket_buffer.end(), "WebSocket head is incomplete");
  const size_t head_size =
      static_cast<size_t>(head_end - websocket_buffer.begin()) + 4u;
  const std::string head(
      reinterpret_cast<const char *>(websocket_buffer.data()), head_size);
  require(head.starts_with("HTTP/1.1 101 "),
          "WebSocket route did not upgrade: " + head);
  websocket_buffer.erase(
      websocket_buffer.begin(),
      websocket_buffer.begin() +
          static_cast<std::vector<uint8_t>::difference_type>(head_size));

  std::string status;
  require(take_server_text(websocket.get(), &websocket_buffer, &status) &&
              status.find(R"("livestream_enabled":false)") != std::string::npos,
          "WebSocket did not publish initial camera status");
  const std::string duplicate_control = request_http_head(port, upgrade);
  require(duplicate_control.starts_with("HTTP/1.1 403 "),
          "WebSocket route accepted a second control session");
  constexpr std::string_view start_command =
      R"({"id":"start-1","command":"livestream.start"})";
  require(send_client_text(websocket.get(), start_command),
          "livestream command send failed");
  bool start_acknowledged = false;
  for (unsigned attempt = 0u; attempt < 4u && !start_acknowledged; ++attempt) {
    require(take_server_text(websocket.get(), &websocket_buffer, &status),
            "livestream command response failed");
    start_acknowledged =
        status.find(R"("request_id":"start-1")") != std::string::npos &&
        status.find(R"("ok":true)") != std::string::npos;
  }
  require(start_acknowledged, "livestream command was not acknowledged");

  SocketOwner stream(connect_loopback(port));
  require(stream.get() != kInvalidSocket, "MJPEG connect failed");
  const std::string stream_request =
      "GET /stream.mjpeg HTTP/1.1\r\nHost: " + authority + "\r\n\r\n";
  require(send_all(stream.get(), stream_request), "MJPEG request send failed");
  std::this_thread::sleep_for(std::chrono::milliseconds(20));
  constexpr std::array<uint8_t, 4u> jpeg{{0xffu, 0xd8u, 0xffu, 0xd9u}};
  coakka_v2_stream_frame_t frame{};
  frame.size = jpeg.size();
  frame.sequence = 1u;
  frame.captured_mono_ns = 42u;
  frame.flags = COAKKA_V2_STREAM_LANE_FRAME_FLAG_KEYFRAME;
  require(gateway->consume(jpeg.data(), &frame) == COAKKA_V2_OK,
          "MJPEG frame admission failed");
  std::vector<uint8_t> stream_response;
  while (!buffer_contains(stream_response, "--coakka-frame") ||
         !buffer_contains(stream_response, "X-CoAkka-Sequence: 1")) {
    require(stream_response.size() < 64ull * 1024ull &&
                receive_more(stream.get(), &stream_response),
            "MJPEG response did not deliver the admitted frame");
  }
  stream.reset();
  websocket.reset();
  gateway->stop();
  require(gateway->snapshot().browser_frames >= 1u,
          "gateway did not account for the accepted browser frame");
  std::filesystem::remove_all(recordings, filesystem_error);
}

struct ProfileCapture {
  std::atomic<uint32_t> width{0u};
  std::atomic<uint32_t> height{0u};
  std::atomic<uint32_t> fps{0u};
};

bool accept_profile(void *context, uint32_t width, uint32_t height,
                    uint32_t fps, char *error, size_t error_capacity) {
  auto *capture = static_cast<ProfileCapture *>(context);
  if (capture == nullptr || error == nullptr || error_capacity == 0u) {
    return false;
  }
  capture->width.store(width, std::memory_order_release);
  capture->height.store(height, std::memory_order_release);
  capture->fps.store(fps, std::memory_order_release);
  error[0] = '\0';
  return true;
}

std::string profile_request(uint16_t port, std::string_view token,
                            std::string_view body) {
  return request_http(
      port, "POST /v1/profile HTTP/1.1\r\nHost: 127.0.0.1\r\n"
            "Authorization: Bearer " +
                std::string(token) +
                "\r\nContent-Type: application/json\r\nConnection: close\r\n"
                "Content-Length: " +
                std::to_string(body.size()) + "\r\n\r\n" + std::string(body));
}

void exercise_control_server() {
  ProfileCapture capture;
  coakka_v2_camera_control_server_config_t config;
  config.bind_host = "127.0.0.1";
  config.bind_port = 0u;
  config.authorization_token = "camera-test-token";
  config.handler_context = &capture;
  config.profile_handler = accept_profile;
  auto server = coakka_v2_camera_control_server_t::create(config);
  require(server != nullptr, "control server create failed");
  std::string error;
  require(server->start(&error), "control server start failed: " + error);
  const uint16_t port = server->bound_port();
  require(port != 0u, "control server did not publish its bound port");

  const std::filesystem::path recordings =
      std::filesystem::temp_directory_path() /
      "coakka-camera-outbound-integration-test";
  std::error_code filesystem_error;
  std::filesystem::remove_all(recordings, filesystem_error);
  coakka_v2_camera_web_gateway_config_t gateway_config;
  gateway_config.bind_host = "127.0.0.1";
  gateway_config.bind_port = 0u;
  gateway_config.max_frame_bytes = 4096u;
  gateway_config.fps = 30u;
  gateway_config.recording_directory = recordings.string();
  gateway_config.ffmpeg_binary = "ffmpeg-not-invoked-by-outbound-test";
  gateway_config.profile_control_host = "127.0.0.1";
  gateway_config.profile_control_port = port;
  gateway_config.profile_authorization_token = "camera-test-token";
  auto gateway = coakka_v2_camera_web_gateway_t::create(gateway_config);
  require(gateway != nullptr, "outbound gateway create failed");
  require(gateway->start(&error), "outbound gateway start failed: " + error);
  const coakka_v2_camera_profile_request_t profile{1280u, 720u, 30u, 1u};
  require(gateway->request_profile(profile, &error),
          "native outbound profile request failed: " + error);
  require(capture.width.load(std::memory_order_acquire) == 1280u &&
              capture.height.load(std::memory_order_acquire) == 720u &&
              capture.fps.load(std::memory_order_acquire) == 30u,
          "native outbound request did not reach the camera handler");
  gateway->stop();
  std::filesystem::remove_all(recordings, filesystem_error);

  const std::string forbidden = profile_request(
      port, "wrong-token", R"({"width":640,"height":480,"fps":30})");
  require(forbidden.starts_with("HTTP/1.1 403 "),
          "control server accepted the wrong bearer token");
  const std::string malformed =
      profile_request(port, "camera-test-token", R"({"width":"large"})");
  require(malformed.starts_with("HTTP/1.1 400 "),
          "control server accepted a malformed profile body");
  server->stop();
  server->stop();
}

} // namespace

int run_tests() {
#if defined(_WIN32)
  WSADATA winsock{};
  require(WSAStartup(MAKEWORD(2, 2), &winsock) == 0,
          "WinSock initialization failed");
  struct WinsockOwner {
    ~WinsockOwner() { (void)WSACleanup(); }
  } winsock_owner;
#else
  (void)std::signal(SIGPIPE, SIG_IGN);
#endif
  exercise_gateway();
  exercise_control_server();
  return 0;
}

int main() {
  try {
    return run_tests();
  } catch (const std::exception &failure) {
    (void)std::fprintf(stderr, "camera HTTP integration test failed: %s\n",
                       failure.what());
  } catch (...) {
    (void)std::fprintf(
        stderr, "camera HTTP integration test failed: unknown exception\n");
  }
  return 1;
}
