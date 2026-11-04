#include "stream_lane_camera_profile_http_workaround.h"

#if defined(_WIN32)
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <arpa/inet.h>
#include <csignal>
#include <sys/socket.h>
#include <unistd.h>
#endif

#include <array>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <string>
#include <thread>

namespace {

#if defined(_WIN32)
using Socket = SOCKET;
using SocketLength = int;
constexpr Socket kInvalidSocket = INVALID_SOCKET;
#else
using Socket = int;
using SocketLength = socklen_t;
constexpr Socket kInvalidSocket = -1;
#endif

void close_socket(Socket socket) noexcept {
  if (socket == kInvalidSocket) {
    return;
  }
#if defined(_WIN32)
  (void)closesocket(socket);
#else
  (void)close(socket);
#endif
}

void require(bool condition, const std::string &detail) {
  if (!condition) {
    std::cerr << "camera profile HTTP workaround test failed: " << detail
              << '\n';
    std::exit(1);
  }
}

struct ExchangeResult final {
  bool accepted = false;
  std::string error;
  std::string request;
};

ExchangeResult exchange(uint16_t response_status) {
  const Socket listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  require(listener != kInvalidSocket, "listener socket creation");
  const int enabled = 1;
#if defined(_WIN32)
  require(setsockopt(listener, SOL_SOCKET, SO_REUSEADDR,
                     reinterpret_cast<const char *>(&enabled),
                     static_cast<int>(sizeof(enabled))) == 0,
          "listener reuse option");
#else
  require(setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, &enabled,
                     sizeof(enabled)) == 0,
          "listener reuse option");
#endif
  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(0u);
  require(inet_pton(AF_INET, "127.0.0.1", &address.sin_addr) == 1,
          "loopback parse");
  require(bind(listener, reinterpret_cast<const sockaddr *>(&address),
               static_cast<SocketLength>(sizeof(address))) == 0,
          "listener bind");
  require(listen(listener, 1) == 0, "listener start");
  SocketLength address_size = static_cast<SocketLength>(sizeof(address));
  require(getsockname(listener, reinterpret_cast<sockaddr *>(&address),
                      &address_size) == 0,
          "listener port query");
  const uint16_t port = ntohs(address.sin_port);
  require(port != 0u, "listener port");

  ExchangeResult result;
  std::thread server([&] {
    const Socket connection = accept(listener, nullptr, nullptr);
    if (connection == kInvalidSocket) {
      return;
    }
    std::array<char, 1024u> buffer{};
    while (result.request.size() < 4096u &&
           result.request.find("\"fps\":30}") == std::string::npos) {
      const auto received =
          recv(connection, buffer.data(), static_cast<int>(buffer.size()), 0);
      if (received <= 0) {
        break;
      }
      result.request.append(buffer.data(), static_cast<size_t>(received));
    }
    const std::string response =
        "HTTP/1.1 " + std::to_string(response_status) +
        " Camera\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}";
    (void)send(connection, response.data(), static_cast<int>(response.size()),
               0);
    close_socket(connection);
  });

  result.accepted = coakka_v2_camera_profile_http_workaround_send(
      {"127.0.0.1", port, "test-token", 800u, 600u, 30u, 2000u}, &result.error);
  server.join();
  close_socket(listener);
  return result;
}

} // namespace

int main() {
#if defined(_WIN32)
  WSADATA data{};
  require(WSAStartup(MAKEWORD(2, 2), &data) == 0, "WinSock startup");
#else
  (void)std::signal(SIGPIPE, SIG_IGN);
#endif

  require(
      coakka_v2_camera_profile_http_workaround_required("192.0.2.10") &&
          coakka_v2_camera_profile_http_workaround_required("2001:db8::10") &&
          !coakka_v2_camera_profile_http_workaround_required("127.0.0.1") &&
          !coakka_v2_camera_profile_http_workaround_required("::1") &&
          !coakka_v2_camera_profile_http_workaround_required("camera.test"),
      "target classification");

  std::string validation_error;
  require(!coakka_v2_camera_profile_http_workaround_send(
              {"camera.test", 39093u, "test-token", 800u, 600u, 30u, 2000u},
              &validation_error) &&
              validation_error == "temporary camera profile request is invalid",
          "hostname refusal");
  require(!coakka_v2_camera_profile_http_workaround_send(
              {"127.0.0.1", 39093u, "bad\r\ntoken", 800u, 600u, 30u, 2000u},
              &validation_error) &&
              validation_error == "temporary camera profile request is invalid",
          "authorization header injection refusal");

  const ExchangeResult accepted = exchange(202u);
  require(accepted.accepted, "202 response: " + accepted.error);
  require(
      accepted.request.starts_with("POST /v1/profile HTTP/1.1\r\n") &&
          accepted.request.find("Authorization: Bearer test-token\r\n") !=
              std::string::npos &&
          accepted.request.ends_with(R"({"width":800,"height":600,"fps":30})"),
      "bounded request projection");

  const ExchangeResult rejected = exchange(403u);
  require(!rejected.accepted &&
              rejected.error ==
                  "camera rejected the requested profile with HTTP 403",
          "non-202 response projection");

#if defined(_WIN32)
  (void)WSACleanup();
#endif
  std::cout << "camera profile HTTP 1.1.2 workaround tests passed\n";
  return 0;
}
