/** @file security.cpp Host-inlined C++20 TLS and mutual-TLS sample. */
#include <coakka/http/host.h>

#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>

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

[[nodiscard]] std::string option(int argc, char **argv, std::string_view name) {
  for (int index = 1; index + 1 < argc; ++index) {
    if (argv[index] == name) {
      return argv[index + 1];
    }
  }
  throw std::invalid_argument(std::string("missing option ") +
                              std::string(name));
}

/** Own one secure native runtime and its sole inbound reader. */
class SecureService final {
public:
  SecureService(std::string mode, std::string protocol,
                const std::filesystem::path &fixtures, std::uint16_t port)
      : body_(mode == "mtls" ? "mtls-ready" : "tls-ready"),
        certificate_((fixtures / "server.pem").string()),
        private_key_((fixtures / "server.key").string()),
        trust_roots_((fixtures / "ca.pem").string()) {
    coakka_http_host_configuration_t configuration{};
    coakka_http_host_configuration_init(&configuration);
    configuration.bind_address = bytes("127.0.0.1");
    configuration.port = port;
    if (protocol == "http1") {
      configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_1_1;
    } else if (protocol == "http2") {
      configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_2;
    } else if (protocol == "http3") {
      configuration.protocol = COAKKA_HTTP_HOST_PROTOCOL_HTTP_3;
    } else {
      throw std::invalid_argument("protocol must be http1, http2, or http3");
    }
    if (mode == "tls") {
      configuration.security = COAKKA_HTTP_HOST_SECURITY_TLS;
    } else if (mode == "mtls") {
      configuration.security = COAKKA_HTTP_HOST_SECURITY_MUTUAL_TLS;
      configuration.trust_roots_file = bytes(trust_roots_);
    } else {
      throw std::invalid_argument("security must be tls or mtls");
    }
    configuration.credential_generation = 1U;
    configuration.credential_id = bytes("cpp-sample-server");
    configuration.certificate_chain_file = bytes(certificate_);
    configuration.private_key_file = bytes(private_key_);

    coakka_http_host_route_t route{};
    coakka_http_host_route_init(&route);
    route.route_id = 1U;
    route.handler_binding_id = 1U;
    route.method = bytes("GET");
    route.encoded_path_pattern = bytes("/secure");
    std::uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
    require_ok(coakka_http_host_service_create(&configuration, &route, 1U,
                                               &service_, &failed_route),
               "create secure service");
    try {
      require_start(service_, "start secure service");
      reader_ = std::thread([this] { read_events(); });
    } catch (...) {
      coakka_http_host_service_destroy(&service_);
      throw;
    }
  }

  ~SecureService() { close_noexcept(); }
  SecureService(const SecureService &) = delete;
  SecureService &operator=(const SecureService &) = delete;

  /** Stop the reader and release native ownership. */
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
    require_ok(coakka_http_host_service_stop(service_), "stop secure service");
    coakka_http_host_service_destroy(&service_);
    if (reader_failed_.load(std::memory_order_acquire)) {
      throw std::runtime_error("secure inbound reader failed");
    }
  }

private:
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
        coakka_http_host_response_t response{};
        coakka_http_host_response_init(&response);
        response.body = bytes(body_);
        if (coakka_http_host_respond(service_, event.exchange, &response) !=
            COAKKA_HTTP_HOST_OK) {
          reader_failed_.store(true, std::memory_order_release);
        }
      }
      if (coakka_http_host_release_request(service_, &event) !=
          COAKKA_HTTP_HOST_OK) {
        reader_failed_.store(true, std::memory_order_release);
      }
    }
  }

  void close_noexcept() noexcept {
    try {
      close();
    } catch (const std::exception &error) {
      std::cerr << "secure close failed: " << error.what() << '\n';
    }
  }

  std::string body_;
  std::string certificate_;
  std::string private_key_;
  std::string trust_roots_;
  coakka_http_host_service_t *service_{nullptr};
  std::atomic<bool> reader_stopping_{false};
  std::atomic<bool> reader_failed_{false};
  std::thread reader_;
};

void stop_signal(int signal_number) noexcept {
  (void)signal_number;
  stopping = 1;
}

} // namespace

int main(int argc, char **argv) {
  try {
    const auto mode = option(argc, argv, "--security");
    const auto protocol = option(argc, argv, "--protocol");
    const auto fixtures =
        std::filesystem::path(option(argc, argv, "--fixtures"));
    const auto port_number = std::stoul(option(argc, argv, "--port"));
    if (port_number == 0U || port_number > 65535U) {
      throw std::invalid_argument("port must be between 1 and 65535");
    }
    SecureService service(mode, protocol, fixtures,
                          static_cast<std::uint16_t>(port_number));
    (void)std::signal(SIGINT, stop_signal);
#if defined(SIGTERM)
    (void)std::signal(SIGTERM, stop_signal);
#endif
    while (stopping == 0) {
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    service.close();
    return EXIT_SUCCESS;
  } catch (const std::exception &error) {
    std::cerr << error.what() << '\n';
    return EXIT_FAILURE;
  }
}
