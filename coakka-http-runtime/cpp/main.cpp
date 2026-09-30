/**
 * @file main.cpp
 * @brief C++20 RAII sample over the host-inlined public C host API.
 */
#include <coakka/http/host.h>

#include <array>
#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>

#if defined(_WIN32)
#include <winsock2.h>
#include <ws2tcpip.h>
using Socket = SOCKET;
constexpr Socket kInvalidSocket = INVALID_SOCKET;
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
using Socket = int;
constexpr Socket kInvalidSocket = -1;
#endif

namespace {

volatile std::sig_atomic_t stopping = 0;

[[nodiscard]] coakka_http_host_bytes_t bytes(std::string_view value) noexcept {
  return {reinterpret_cast<const std::uint8_t *>(value.data()), value.size()};
}

void require_ok(coakka_http_host_status_t status, std::string_view operation) {
  if (status != COAKKA_HTTP_HOST_OK) {
    throw std::runtime_error(std::string(operation) + ": " +
                             coakka_http_host_status_name(status));
  }
}

void require_start(coakka_http_host_service_t *service,
                   std::string_view operation) {
  coakka_http_host_issue_t issue{};
  coakka_http_host_issue_init(&issue);
  if (coakka_http_host_service_start(service, &issue) != COAKKA_HTTP_HOST_OK) {
    throw std::runtime_error(std::string(operation) + ": " + issue.detail);
  }
}

void close_socket(Socket value) noexcept {
#if defined(_WIN32)
  (void)closesocket(value);
#else
  (void)close(value);
#endif
}

class SocketSystem final {
public:
  SocketSystem() {
#if defined(_WIN32)
    WSADATA data{};
    if (WSAStartup(MAKEWORD(2, 2), &data) != 0) {
      throw std::runtime_error("socket startup failed");
    }
#endif
  }
  ~SocketSystem() {
#if defined(_WIN32)
    (void)WSACleanup();
#endif
  }
};

/** Issue one bounded loopback request and return its complete wire response. */
[[nodiscard]] std::string exchange(std::uint16_t port, std::string_view wire) {
  const Socket connection = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
  if (connection == kInvalidSocket) {
    throw std::runtime_error("socket creation failed");
  }
#if defined(_WIN32)
  const DWORD timeout = 5000U;
  (void)setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO,
                   reinterpret_cast<const char *>(&timeout), sizeof(timeout));
#else
  const timeval timeout{5, 0};
  (void)setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, &timeout,
                   sizeof(timeout));
#endif
  sockaddr_in endpoint{};
  endpoint.sin_family = AF_INET;
  endpoint.sin_port = htons(port);
  endpoint.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (connect(connection, reinterpret_cast<const sockaddr *>(&endpoint),
              sizeof(endpoint)) != 0) {
    close_socket(connection);
    throw std::runtime_error("loopback connect failed");
  }
  std::size_t sent = 0U;
  while (sent < wire.size()) {
#if defined(_WIN32)
    const int count = send(connection, wire.data() + sent,
                           static_cast<int>(wire.size() - sent), 0);
#else
    const auto count =
        send(connection, wire.data() + sent, wire.size() - sent, 0);
#endif
    if (count <= 0) {
      close_socket(connection);
      throw std::runtime_error("loopback send failed");
    }
    sent += static_cast<std::size_t>(count);
  }
  std::string response;
  std::array<char, 4096> buffer{};
  for (;;) {
#if defined(_WIN32)
    const int count =
        recv(connection, buffer.data(), static_cast<int>(buffer.size()), 0);
#else
    const auto count = recv(connection, buffer.data(), buffer.size(), 0);
#endif
    if (count <= 0) {
      break;
    }
    response.append(buffer.data(), static_cast<std::size_t>(count));
  }
  close_socket(connection);
  return response;
}

/** Sole RAII owner for one native runtime and its inbound reader. */
class HttpService final {
public:
  HttpService(std::string asset_root, bool request_io_uring)
      : asset_root_(std::move(asset_root)) {
    coakka_http_host_configuration_t configuration{};
    coakka_http_host_configuration_init(&configuration);
    configuration.use_io_uring = request_io_uring ? 1U : 0U;
    configuration.monitor.initial_collection =
        COAKKA_HTTP_HOST_MONITOR_AGGREGATES_AND_EVENTS;
    configuration.monitor.event_capacity = 32U;
    configuration.monitor.max_events_per_read = 8U;
    configuration.monitor.categories =
        COAKKA_HTTP_HOST_MONITOR_LIFECYCLE | COAKKA_HTTP_HOST_MONITOR_EXCHANGE;

    coakka_http_host_static_mount_t mount{};
    coakka_http_host_static_mount_init(&mount);
    mount.flags = COAKKA_HTTP_HOST_STATIC_SPA_FALLBACK;
    mount.url_prefix = bytes("/app");
    mount.root_path = bytes(asset_root_);
    mount.index_file = bytes("index.html");
    mount.spa_fallback_file = bytes("index.html");
    configuration.static_mounts = &mount;
    configuration.static_mount_count = 1U;
    configuration.max_total_static_assets = 64U;

    coakka_http_host_file_authority_t authority{};
    coakka_http_host_file_authority_init(&authority);
    authority.authority_id = 82U;
    authority.root_path = bytes(asset_root_);
    authority.max_active_files = 2U;
    authority.max_file_bytes = 1U << 20U;
    configuration.file_authorities = &authority;
    configuration.file_authority_count = 1U;

    std::array<coakka_http_host_route_t, 3> routes{};
    const std::array methods{"POST", "GET", "GET"};
    const std::array paths{"/echo", "/download", "/version"};
    for (std::size_t index = 0U; index < routes.size(); ++index) {
      coakka_http_host_route_init(&routes[index]);
      routes[index].route_id = index + 1U;
      routes[index].handler_binding_id = index + 1U;
      routes[index].method = bytes(methods[index]);
      routes[index].encoded_path_pattern = bytes(paths[index]);
    }
    routes[0].body_policy.enabled = 1U;
    routes[0].body_policy.accept_other = 1U;
    routes[0].body_policy.max_body_bytes = 1U << 20U;

    std::uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
    require_ok(coakka_http_host_service_create(
                   &configuration, routes.data(),
                   static_cast<std::uint32_t>(routes.size()), &service_,
                   &failed_route),
               "create service");
    try {
      require_start(service_, "start service");
      require_ok(coakka_http_host_service_port(service_, &port_),
                 "read bound port");
      reader_ = std::thread([this] { read_events(); });
      activate_v2();

      coakka_http_host_service_info_t info{};
      coakka_http_host_service_info_init(&info);
      require_ok(coakka_http_host_service_info(service_, &info),
                 "read runtime info");
      if (info.io_uring_requested != configuration.use_io_uring ||
          (info.io_uring_requested != 0U && info.io_uring_effective == 0U &&
           info.io_fallback_reason == COAKKA_HTTP_HOST_IO_FALLBACK_NONE)) {
        throw std::runtime_error("native backend truth is inconsistent");
      }
      std::cout << "io_uring requested="
                << static_cast<unsigned>(info.io_uring_requested)
                << " effective="
                << static_cast<unsigned>(info.io_uring_effective)
                << " fallback=" << info.io_fallback_reason << '\n';
    } catch (...) {
      close_noexcept();
      throw;
    }
  }

  ~HttpService() { close_noexcept(); }
  HttpService(const HttpService &) = delete;
  HttpService &operator=(const HttpService &) = delete;

  [[nodiscard]] std::uint16_t port() const noexcept { return port_; }

  /** Pull fresh health and bounded monitoring truth. */
  void verify_observability() const {
    coakka_http_host_health_t health{};
    coakka_http_host_monitor_snapshot_t monitor{};
    coakka_http_host_health_init(&health);
    coakka_http_host_monitor_snapshot_init(&monitor);
    require_ok(coakka_http_host_probe_liveness(service_, 1000U, &health),
               "probe liveness");
    require_ok(coakka_http_host_monitor_snapshot(service_, &monitor),
               "read monitor snapshot");
    if (health.acknowledged_probe_sequence == 0U) {
      throw std::runtime_error("liveness probe was not acknowledged");
    }
  }

  /** Stop the reader, drain admission, and release native ownership. */
  void close() {
    if (service_ == nullptr) {
      return;
    }
    reader_stopping_.store(true, std::memory_order_release);
    (void)coakka_http_host_interrupt_requests(service_);
    if (reader_.joinable()) {
      reader_.join();
    }
    require_ok(coakka_http_host_service_begin_drain(service_), "begin drain");
    require_ok(coakka_http_host_service_stop(service_), "stop service");
    coakka_http_host_service_destroy(&service_);
    if (reader_failed_.load(std::memory_order_acquire)) {
      throw std::runtime_error("inbound reader failed");
    }
  }

private:
  void activate_v2() const {
    coakka_http_host_rebind_request_t request{};
    coakka_http_host_rebind_outcome_t outcome{};
    coakka_http_host_rebind_request_init(&request);
    coakka_http_host_rebind_outcome_init(&outcome);
    request.activation_id = 1U;
    request.expected_route_generation = 1U;
    request.route_id = 3U;
    request.expected_binding_revision = 1U;
    request.new_handler_binding_id = 4U;
    require_ok(coakka_http_host_rebind(service_, &request, &outcome),
               "rebind handler");
    if (outcome.code != COAKKA_HTTP_HOST_CONTROL_APPLIED) {
      throw std::runtime_error("handler rebind was rejected");
    }
  }

  void read_events() noexcept {
    while (!reader_stopping_.load(std::memory_order_acquire)) {
      coakka_http_host_request_event_t event{};
      coakka_http_host_request_event_init(&event);
      const auto status = coakka_http_host_take_request(service_, 100U, &event);
      if (status == COAKKA_HTTP_HOST_TIMEOUT) {
        continue;
      }
      if (status == COAKKA_HTTP_HOST_CLOSED) {
        break;
      }
      if (status != COAKKA_HTTP_HOST_OK) {
        reader_failed_.store(true, std::memory_order_release);
        break;
      }
      if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
        respond(event);
      }
      if (coakka_http_host_release_request(service_, &event) !=
          COAKKA_HTTP_HOST_OK) {
        reader_failed_.store(true, std::memory_order_release);
      }
    }
  }

  void respond(const coakka_http_host_request_event_t &event) noexcept {
    coakka_http_host_status_t status = COAKKA_HTTP_HOST_OK;
    if (event.handler_binding_id == 2U) {
      coakka_http_host_file_response_t file{};
      coakka_http_host_file_response_init(&file);
      file.authority_id = 82U;
      file.encoded_path = bytes("/sample.txt");
      status = coakka_http_host_respond_file(service_, event.exchange, &file);
    } else {
      coakka_http_host_response_t response{};
      coakka_http_host_response_init(&response);
      if (event.handler_binding_id == 1U) {
        response.status_code = 201U;
        response.body = coakka_http_host_request_body(&event);
      } else if (event.handler_binding_id == 4U) {
        response.body = bytes("v2");
      } else {
        response.status_code = 404U;
        response.body = bytes("not found");
      }
      status = coakka_http_host_respond(service_, event.exchange, &response);
    }
    if (status != COAKKA_HTTP_HOST_OK) {
      reader_failed_.store(true, std::memory_order_release);
    }
  }

  void close_noexcept() noexcept {
    try {
      close();
    } catch (const std::exception &error) {
      std::cerr << "close failed: " << error.what() << '\n';
    }
  }

  std::string asset_root_;
  coakka_http_host_service_t *service_{nullptr};
  std::uint16_t port_{0U};
  std::atomic<bool> reader_stopping_{false};
  std::atomic<bool> reader_failed_{false};
  std::thread reader_;
};

void stop_signal(int signal_number) noexcept {
  (void)signal_number;
  stopping = 1;
}

[[nodiscard]] bool has_argument(int argc, char **argv,
                                std::string_view expected) {
  for (int index = 1; index < argc; ++index) {
    if (argv[index] == expected) {
      return true;
    }
  }
  return false;
}

} // namespace

int main(int argc, char **argv) {
  try {
    SocketSystem sockets;
    const bool serve = has_argument(argc, argv, "--serve");
    const bool io_uring = has_argument(argc, argv, "--io-uring");
    const std::string asset_root = argc > 2 ? argv[2] : "../assets";
    HttpService service(asset_root, io_uring);
    if (serve) {
      (void)std::signal(SIGINT, stop_signal);
#if defined(SIGTERM)
      (void)std::signal(SIGTERM, stop_signal);
#endif
      std::cout << "C++ sample listening on http://127.0.0.1:" << service.port()
                << '\n';
      while (stopping == 0) {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
      }
    } else {
      const auto echo =
          exchange(service.port(),
                   "POST /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: "
                   "text/plain\r\n"
                   "Content-Length: 7\r\nConnection: close\r\n\r\npayload");
      const auto version =
          exchange(service.port(), "GET /version HTTP/1.1\r\nHost: "
                                   "127.0.0.1\r\nConnection: close\r\n\r\n");
      const auto file =
          exchange(service.port(), "GET /download HTTP/1.1\r\nHost: "
                                   "127.0.0.1\r\nConnection: close\r\n\r\n");
      const auto frontend =
          exchange(service.port(), "GET /app/client/route HTTP/1.1\r\nHost: "
                                   "127.0.0.1\r\nAccept: text/html\r\n"
                                   "Connection: close\r\n\r\n");
      if (echo.find("payload") == std::string::npos ||
          version.find("v2") == std::string::npos ||
          file.find("confined application file") == std::string::npos ||
          frontend.find("CoAkka HTTP Runtime") == std::string::npos) {
        throw std::runtime_error("C++ request smoke failed");
      }
    }
    service.verify_observability();
    service.close();
    std::cout << "coakka-http-cpp-sample=pass\n";
    return EXIT_SUCCESS;
  } catch (const std::exception &error) {
    std::cerr << error.what() << '\n';
    return EXIT_FAILURE;
  }
}
