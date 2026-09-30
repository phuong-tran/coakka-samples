/** Fixed response through the host-inlined public C host API. */
#include <coakka/http/host.h>

#include <csignal>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <string>
#include <string_view>

namespace {
volatile std::sig_atomic_t stopping = 0;
constexpr std::string_view body = "0123456789abcdef0123456789abcdef";

coakka_http_host_bytes_t bytes(std::string_view value) noexcept {
  return {reinterpret_cast<const std::uint8_t *>(value.data()), value.size()};
}

void stop_signal(int value) noexcept {
  (void)value;
  stopping = 1;
}

class Service final {
public:
  explicit Service(std::uint16_t port) {
    coakka_http_host_configuration_t configuration{};
    coakka_http_host_configuration_init(&configuration);
    configuration.port = port;
    configuration.bind_address = bytes("127.0.0.1");
    configuration.limits.event_loop_threads = 1U;
    configuration.limits.max_connections = 512U;
    configuration.limits.max_active_requests = 256U;
    configuration.limits.request_queue_capacity = 256U;
    configuration.limits.completion_queue_capacity = 256U;
    coakka_http_host_route_t route{};
    coakka_http_host_route_init(&route);
    route.route_id = 1U;
    route.handler_binding_id = 1U;
    route.method = bytes("GET");
    route.encoded_path_pattern = bytes("/fixed");
    std::uint32_t failed_route = COAKKA_HTTP_HOST_FAILED_INDEX_NONE;
    coakka_http_host_issue_t issue{};
    coakka_http_host_issue_init(&issue);
    if (coakka_http_host_service_create(&configuration, &route, 1U, &service_,
                                        &failed_route) != COAKKA_HTTP_HOST_OK ||
        coakka_http_host_service_start(service_, &issue) !=
            COAKKA_HTTP_HOST_OK) {
      coakka_http_host_service_destroy(&service_);
      throw std::runtime_error("CoAkka C++ benchmark service failed to start");
    }
  }

  Service(const Service &) = delete;
  Service &operator=(const Service &) = delete;

  ~Service() {
    if (service_ != nullptr) {
      (void)coakka_http_host_service_stop(service_);
      coakka_http_host_service_destroy(&service_);
    }
  }

  void run() {
    static constexpr std::string_view content_type_name = "content-type";
    static constexpr std::string_view content_type_value =
        "application/octet-stream";
    const coakka_http_host_header_t content_type{bytes(content_type_name),
                                                 bytes(content_type_value)};
    while (stopping == 0) {
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
        throw std::runtime_error("CoAkka C++ event read failed");
      }
      if (event.kind == COAKKA_HTTP_HOST_REQUEST) {
        coakka_http_host_response_t response{};
        coakka_http_host_response_init(&response);
        response.headers = &content_type;
        response.header_count = 1U;
        response.body = bytes(body);
        if (coakka_http_host_respond(service_, event.exchange, &response) !=
            COAKKA_HTTP_HOST_OK) {
          (void)coakka_http_host_release_request(service_, &event);
          throw std::runtime_error("CoAkka C++ response failed");
        }
      }
      if (coakka_http_host_release_request(service_, &event) !=
          COAKKA_HTTP_HOST_OK) {
        throw std::runtime_error("CoAkka C++ event release failed");
      }
    }
  }

  void close() {
    if (service_ == nullptr) {
      return;
    }
    (void)coakka_http_host_service_begin_drain(service_);
    if (coakka_http_host_service_stop(service_) != COAKKA_HTTP_HOST_OK) {
      throw std::runtime_error("CoAkka C++ benchmark close failed");
    }
    coakka_http_host_service_destroy(&service_);
  }

private:
  coakka_http_host_service_t *service_{nullptr};
};
} // namespace

int main(int argc, char **argv) {
  if (argc != 2) {
    return EXIT_FAILURE;
  }
  const auto parsed = std::stoul(argv[1]);
  if (parsed == 0U || parsed > 65535U) {
    return EXIT_FAILURE;
  }
  Service service(static_cast<std::uint16_t>(parsed));
  (void)std::signal(SIGINT, stop_signal);
  (void)std::signal(SIGTERM, stop_signal);
  service.run();
  service.close();
  return EXIT_SUCCESS;
}
