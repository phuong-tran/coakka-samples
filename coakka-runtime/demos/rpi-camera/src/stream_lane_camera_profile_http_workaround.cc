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
#include <cerrno>
#include <fcntl.h>
#include <netdb.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

#include <algorithm>
#include <array>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <new>
#include <string>
#include <string_view>

namespace {

constexpr size_t kMaximumTokenBytes = 1024u;
constexpr size_t kMaximumRequestBytes = 4096u;
constexpr size_t kMaximumResponseHeadBytes = 4096u;

using Clock = std::chrono::steady_clock;
using Deadline = Clock::time_point;

#if defined(_WIN32)
using NativeSocket = SOCKET;
constexpr NativeSocket kInvalidSocket = INVALID_SOCKET;
#else
using NativeSocket = int;
constexpr NativeSocket kInvalidSocket = -1;
#endif

class SocketRuntime final {
public:
  SocketRuntime() noexcept {
#if defined(_WIN32)
    WSADATA data{};
    ready_ = WSAStartup(MAKEWORD(2, 2), &data) == 0;
#endif
  }

  ~SocketRuntime() {
#if defined(_WIN32)
    if (ready_) {
      (void)WSACleanup();
    }
#endif
  }

  SocketRuntime(const SocketRuntime &) = delete;
  SocketRuntime &operator=(const SocketRuntime &) = delete;

  [[nodiscard]] bool ready() const noexcept { return ready_; }

private:
  bool ready_ = true;
};

class OwnedSocket final {
public:
  OwnedSocket() = default;
  explicit OwnedSocket(NativeSocket value) noexcept : value_(value) {}
  ~OwnedSocket() { reset(); }

  OwnedSocket(const OwnedSocket &) = delete;
  OwnedSocket &operator=(const OwnedSocket &) = delete;

  OwnedSocket(OwnedSocket &&other) noexcept : value_(other.release()) {}
  OwnedSocket &operator=(OwnedSocket &&other) noexcept {
    if (this != &other) {
      reset(other.release());
    }
    return *this;
  }

  [[nodiscard]] NativeSocket get() const noexcept { return value_; }
  [[nodiscard]] bool valid() const noexcept { return value_ != kInvalidSocket; }

  void reset(NativeSocket value = kInvalidSocket) noexcept {
    if (valid()) {
#if defined(_WIN32)
      (void)closesocket(value_);
#else
      (void)close(value_);
#endif
    }
    value_ = value;
  }

private:
  [[nodiscard]] NativeSocket release() noexcept {
    const NativeSocket value = value_;
    value_ = kInvalidSocket;
    return value;
  }

  NativeSocket value_ = kInvalidSocket;
};

class AddressList final {
public:
  ~AddressList() {
    if (value != nullptr) {
      freeaddrinfo(value);
    }
  }

  AddressList() = default;
  AddressList(const AddressList &) = delete;
  AddressList &operator=(const AddressList &) = delete;

  addrinfo *value = nullptr;
};

[[nodiscard]] int socket_error() noexcept {
#if defined(_WIN32)
  return WSAGetLastError();
#else
  return errno;
#endif
}

[[nodiscard]] bool interrupted(int error) noexcept {
#if defined(_WIN32)
  return error == WSAEINTR;
#else
  return error == EINTR;
#endif
}

[[nodiscard]] bool would_block(int error) noexcept {
#if defined(_WIN32)
  return error == WSAEWOULDBLOCK || error == WSAEINPROGRESS ||
         error == WSAEALREADY;
#else
  return error == EWOULDBLOCK || error == EAGAIN || error == EINPROGRESS;
#endif
}

[[nodiscard]] bool already_connected(int error) noexcept {
#if defined(_WIN32)
  return error == WSAEISCONN;
#else
  return error == EISCONN;
#endif
}

[[nodiscard]] bool set_nonblocking(NativeSocket socket) noexcept {
#if defined(_WIN32)
  u_long enabled = 1u;
  return ioctlsocket(socket, FIONBIO, &enabled) == 0;
#else
  const int flags = fcntl(socket, F_GETFL, 0);
  return flags >= 0 && fcntl(socket, F_SETFL, flags | O_NONBLOCK) == 0;
#endif
}

[[nodiscard]] bool set_no_sigpipe(NativeSocket socket) noexcept {
#if defined(__APPLE__)
  const int enabled = 1;
  return setsockopt(socket, SOL_SOCKET, SO_NOSIGPIPE, &enabled,
                    static_cast<socklen_t>(sizeof(enabled))) == 0;
#else
  (void)socket;
  return true;
#endif
}

[[nodiscard]] int numeric_address_family(std::string_view host) noexcept {
  if (host.empty() || host.size() >= INET6_ADDRSTRLEN) {
    return AF_UNSPEC;
  }
  std::array<char, INET6_ADDRSTRLEN> text{};
  std::memcpy(text.data(), host.data(), host.size());
  in_addr ipv4{};
  if (inet_pton(AF_INET, text.data(), &ipv4) == 1) {
    return AF_INET;
  }
  in6_addr ipv6{};
  return inet_pton(AF_INET6, text.data(), &ipv6) == 1 ? AF_INET6 : AF_UNSPEC;
}

[[nodiscard]] bool numeric_loopback(std::string_view host) noexcept {
  if (host.empty() || host.size() >= INET6_ADDRSTRLEN) {
    return false;
  }
  std::array<char, INET6_ADDRSTRLEN> text{};
  std::memcpy(text.data(), host.data(), host.size());
  in_addr ipv4{};
  if (inet_pton(AF_INET, text.data(), &ipv4) == 1) {
    return (ntohl(ipv4.s_addr) >> 24u) == 127u;
  }
  in6_addr ipv6{};
  if (inet_pton(AF_INET6, text.data(), &ipv6) != 1) {
    return false;
  }
  const auto *bytes = reinterpret_cast<const uint8_t *>(&ipv6);
  return std::all_of(bytes, bytes + 15u,
                     [](uint8_t byte) { return byte == 0u; }) &&
         bytes[15] == 1u;
}

[[nodiscard]] bool valid_token(std::string_view token) noexcept {
  return !token.empty() && token.size() <= kMaximumTokenBytes &&
         std::all_of(token.begin(), token.end(), [](char character) {
           const auto byte = static_cast<unsigned char>(character);
           return byte >= 0x21u && byte <= 0x7eu;
         });
}

[[nodiscard]] bool wait_socket(NativeSocket socket, bool writable,
                               Deadline deadline, std::string *error) {
#if !defined(_WIN32)
  if (socket < 0 || socket >= FD_SETSIZE) {
    *error = "temporary camera profile socket exceeds select capacity";
    return false;
  }
#endif
  for (;;) {
    const auto now = Clock::now();
    if (now >= deadline) {
      *error = "temporary camera profile request timed out";
      return false;
    }
    const auto remaining =
        std::chrono::duration_cast<std::chrono::microseconds>(deadline - now);
    timeval timeout{};
    timeout.tv_sec = static_cast<long>(remaining.count() / 1'000'000);
    timeout.tv_usec = static_cast<long>(remaining.count() % 1'000'000);
    fd_set read_set;
    fd_set write_set;
    FD_ZERO(&read_set);
    FD_ZERO(&write_set);
    if (writable) {
      FD_SET(socket, &write_set);
    } else {
      FD_SET(socket, &read_set);
    }
#if defined(_WIN32)
    const int result =
        select(0, writable ? nullptr : &read_set,
               writable ? &write_set : nullptr, nullptr, &timeout);
#else
    const int result =
        select(socket + 1, writable ? nullptr : &read_set,
               writable ? &write_set : nullptr, nullptr, &timeout);
#endif
    if (result > 0) {
      return true;
    }
    if (result == 0) {
      *error = "temporary camera profile request timed out";
      return false;
    }
    if (!interrupted(socket_error())) {
      *error = "temporary camera profile socket wait failed";
      return false;
    }
  }
}

[[nodiscard]] bool connect_socket(NativeSocket socket, const addrinfo &address,
                                  Deadline deadline, std::string *error) {
  for (;;) {
    if (connect(socket, address.ai_addr,
#if defined(_WIN32)
                static_cast<int>(address.ai_addrlen)
#else
                address.ai_addrlen
#endif
                    ) == 0) {
      return true;
    }
    const int connect_error = socket_error();
    if (already_connected(connect_error)) {
      return true;
    }
    if (interrupted(connect_error)) {
      continue;
    }
    if (!would_block(connect_error) ||
        !wait_socket(socket, true, deadline, error)) {
      return false;
    }
    int completion_error = 0;
#if defined(_WIN32)
    int completion_size = static_cast<int>(sizeof(completion_error));
    if (getsockopt(socket, SOL_SOCKET, SO_ERROR,
                   reinterpret_cast<char *>(&completion_error),
                   &completion_size) != 0) {
#else
    socklen_t completion_size = sizeof(completion_error);
    if (getsockopt(socket, SOL_SOCKET, SO_ERROR, &completion_error,
                   &completion_size) != 0) {
#endif
      *error = "temporary camera profile connect status failed";
      return false;
    }
    if (completion_error == 0) {
      return true;
    }
    *error = "temporary camera profile connection failed";
    return false;
  }
}

[[nodiscard]] OwnedSocket open_socket(std::string_view host, uint16_t port,
                                      Deadline deadline, std::string *error) {
  const std::string host_text(host);
  const std::string service_text = std::to_string(port);
  addrinfo hints{};
  hints.ai_family = AF_UNSPEC;
  hints.ai_socktype = SOCK_STREAM;
  hints.ai_protocol = IPPROTO_TCP;
  hints.ai_flags = AI_NUMERICHOST | AI_NUMERICSERV;
  AddressList addresses;
  if (getaddrinfo(host_text.c_str(), service_text.c_str(), &hints,
                  &addresses.value) != 0) {
    *error = "temporary camera profile target is not a numeric address";
    return {};
  }
  for (const addrinfo *address = addresses.value; address != nullptr;
       address = address->ai_next) {
    OwnedSocket candidate(
        socket(address->ai_family, address->ai_socktype, address->ai_protocol));
    if (!candidate.valid() || !set_nonblocking(candidate.get()) ||
        !set_no_sigpipe(candidate.get())) {
      continue;
    }
    if (connect_socket(candidate.get(), *address, deadline, error)) {
      return candidate;
    }
  }
  if (error->empty()) {
    *error = "temporary camera profile connection failed";
  }
  return {};
}

[[nodiscard]] bool send_all(NativeSocket socket, std::string_view bytes,
                            Deadline deadline, std::string *error) {
  size_t offset = 0u;
  while (offset < bytes.size()) {
    if (!wait_socket(socket, true, deadline, error)) {
      return false;
    }
    const size_t available = bytes.size() - offset;
    const auto chunk = static_cast<int>(std::min<size_t>(
        available, static_cast<size_t>(std::numeric_limits<int>::max())));
#if defined(__linux__)
    const auto written =
        send(socket, bytes.data() + offset, chunk, MSG_NOSIGNAL);
#else
    const auto written = send(socket, bytes.data() + offset, chunk, 0);
#endif
    if (written > 0) {
      offset += static_cast<size_t>(written);
      continue;
    }
    const int write_error = socket_error();
    if (written < 0 && (interrupted(write_error) || would_block(write_error))) {
      continue;
    }
    *error = "temporary camera profile request write failed";
    return false;
  }
  return true;
}

[[nodiscard]] bool read_response(NativeSocket socket, Deadline deadline,
                                 std::string *error) {
  std::string response;
  response.reserve(kMaximumResponseHeadBytes);
  std::array<char, 1024u> chunk{};
  while (response.find("\r\n\r\n") == std::string::npos) {
    if (response.size() == kMaximumResponseHeadBytes ||
        !wait_socket(socket, false, deadline, error)) {
      return false;
    }
    const size_t available =
        std::min(chunk.size(), kMaximumResponseHeadBytes - response.size());
    const auto received =
        recv(socket, chunk.data(), static_cast<int>(available), 0);
    if (received > 0) {
      response.append(chunk.data(), static_cast<size_t>(received));
      continue;
    }
    if (received == 0) {
      *error = "temporary camera profile response ended before its head";
      return false;
    }
    const int read_error = socket_error();
    if (interrupted(read_error) || would_block(read_error)) {
      continue;
    }
    *error = "temporary camera profile response read failed";
    return false;
  }

  const size_t line_end = response.find("\r\n");
  const std::string_view status_line(response.data(), line_end);
  const bool http_version = status_line.starts_with("HTTP/1.1 ") ||
                            status_line.starts_with("HTTP/1.0 ");
  if (!http_version || status_line.size() < 12u || status_line[9] < '0' ||
      status_line[9] > '9' || status_line[10] < '0' || status_line[10] > '9' ||
      status_line[11] < '0' || status_line[11] > '9') {
    *error = "temporary camera profile response status is malformed";
    return false;
  }
  const uint16_t status = static_cast<uint16_t>((status_line[9] - '0') * 100 +
                                                (status_line[10] - '0') * 10 +
                                                (status_line[11] - '0'));
  if (status != 202u) {
    *error = "camera rejected the requested profile with HTTP " +
             std::to_string(status);
    return false;
  }
  return true;
}

[[nodiscard]] bool
send_request(const coakka_v2_camera_profile_http_workaround_request_t &request,
             std::string *error) {
  if (numeric_address_family(request.host) == AF_UNSPEC || request.port == 0u ||
      request.width == 0u || request.height == 0u || request.fps == 0u ||
      request.timeout_ms == 0u || !valid_token(request.authorization_token)) {
    *error = "temporary camera profile request is invalid";
    return false;
  }
  const std::string body = "{\"width\":" + std::to_string(request.width) +
                           ",\"height\":" + std::to_string(request.height) +
                           ",\"fps\":" + std::to_string(request.fps) + "}";
  std::string authority;
  if (numeric_address_family(request.host) == AF_INET6) {
    authority = "[" + std::string(request.host) + "]";
  } else {
    authority = request.host;
  }
  authority += ":" + std::to_string(request.port);
  const std::string wire =
      "POST /v1/profile HTTP/1.1\r\nHost: " + authority +
      "\r\nAuthorization: Bearer " + std::string(request.authorization_token) +
      "\r\nContent-Type: application/json\r\nConnection: close\r\n"
      "Content-Length: " +
      std::to_string(body.size()) + "\r\n\r\n" + body;
  if (wire.size() > kMaximumRequestBytes) {
    *error = "temporary camera profile request exceeded its byte limit";
    return false;
  }

  SocketRuntime runtime;
  if (!runtime.ready()) {
    *error = "temporary camera profile socket runtime failed";
    return false;
  }
  const Deadline deadline =
      Clock::now() + std::chrono::milliseconds(request.timeout_ms);
  OwnedSocket socket = open_socket(request.host, request.port, deadline, error);
  return socket.valid() && send_all(socket.get(), wire, deadline, error) &&
         read_response(socket.get(), deadline, error);
}

} // namespace

bool coakka_v2_camera_profile_http_workaround_required(
    std::string_view host) noexcept {
  return numeric_address_family(host) != AF_UNSPEC && !numeric_loopback(host);
}

bool coakka_v2_camera_profile_http_workaround_send(
    const coakka_v2_camera_profile_http_workaround_request_t &request,
    std::string *error) {
  if (error == nullptr) {
    return false;
  }
  error->clear();
  try {
    return send_request(request, error);
  } catch (const std::bad_alloc &) {
    *error = "temporary camera profile request allocation failed";
    return false;
  }
}
