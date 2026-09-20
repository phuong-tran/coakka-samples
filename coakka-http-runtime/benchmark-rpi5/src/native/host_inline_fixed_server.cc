#include <App.h>
#include <coakka/http/host_inline.h>

#include <atomic>
#include <csignal>
#include <cstdint>
#include <iostream>
#include <pthread.h>
#include <string>
#include <string_view>
#include <thread>

namespace {

constexpr std::uint64_t kRouteId = 1U;
constexpr std::string_view kMethod = "GET";
constexpr std::string_view kPattern = "/fixed";
constexpr std::string_view kBody = "0123456789abcdef0123456789abcdef";

bool block_termination_signals(sigset_t *signals) {
  return sigemptyset(signals) == 0 && sigaddset(signals, SIGINT) == 0 &&
         sigaddset(signals, SIGTERM) == 0 &&
         pthread_sigmask(SIG_BLOCK, signals, nullptr) == 0;
}

bool validate_route_table() {
  if (coakka_http_host_inline_abi_version() !=
      COAKKA_HTTP_HOST_INLINE_ABI_VERSION) {
    std::cerr << "host-inline ABI version mismatch\n";
    return false;
  }

  const coakka_http_host_inline_route_t route{
      kRouteId,        reinterpret_cast<const std::uint8_t *>(kMethod.data()),
      kMethod.size(),  reinterpret_cast<const std::uint8_t *>(kPattern.data()),
      kPattern.size(),
  };
  std::uint32_t failed_index = COAKKA_HTTP_HOST_INLINE_FAILED_INDEX_NONE;

  // Core borrows these string views only for this synchronous startup call.
  // No Core state or native request envelope survives into the host hot path.
  const auto status =
      coakka_http_host_inline_validate(&route, 1U, &failed_index);
  if (status == COAKKA_HTTP_HOST_INLINE_OK &&
      failed_index == COAKKA_HTTP_HOST_INLINE_FAILED_INDEX_NONE) {
    return true;
  }

  std::cerr << "host-inline route validation failed: status="
            << coakka_http_host_inline_status_name(status)
            << " failed_index=" << failed_index << '\n';
  return false;
}

} // namespace

int main() {
  sigset_t signals{};
  if (!block_termination_signals(&signals)) {
    std::cerr << "failed to establish synchronous signal ownership\n";
    return 2;
  }
  if (!validate_route_table()) {
    return 3;
  }

  uWS::App app;
  if (app.constructorFailed()) {
    std::cerr << "failed to construct the pinned uWebSockets app\n";
    return 4;
  }

  // uWebSockets compiles the Core-admitted route into its host-native table.
  // The handler remains on this event-loop thread with no dispatch handoff.
  std::uint64_t requests = 0U;
  app.get(std::string{kPattern}, [&requests](auto *response, auto *) {
    ++requests;
    response->writeStatus("200 OK")
        ->writeHeader("content-type", "text/plain")
        ->end(kBody);
  });

  us_listen_socket_t *listener = nullptr;
  std::uint16_t port = 0U;
  app.listen("127.0.0.1", 0, [&](auto *socket) {
    listener = socket;
    if (socket == nullptr) {
      return;
    }
    const int bound =
        us_socket_local_port(0, reinterpret_cast<us_socket_t *>(socket));
    if (bound > 0 && bound <= 65535) {
      port = static_cast<std::uint16_t>(bound);
    }
  });
  if (listener == nullptr || port == 0U) {
    std::cerr << "pinned uWebSockets did not bind loopback\n";
    return 5;
  }

  auto *loop = uWS::Loop::get();
  std::atomic<int> signal_status{0};
  std::atomic<int> received_signal{0};
  std::thread signal_owner([&] {
    int signal_number = 0;
    const int status = sigwait(&signals, &signal_number);
    signal_status.store(status, std::memory_order_release);
    received_signal.store(signal_number, std::memory_order_release);
    // The signal thread owns only the stop request. Listener mutation is
    // deferred to the host loop that owns the socket and connection state.
    loop->defer([listener] { us_listen_socket_close(0, listener); });
  });

  std::cout << "{\"ready\":true,\"bound_port\":" << port
            << ",\"application_path\":\"host-inline\""
            << ",\"connector_surface\":\"native-cpp-host-inline\""
            << ",\"route_count\":1}" << '\n';
  std::cout.flush();
  app.run();
  signal_owner.join();

  const auto stopped_signal = received_signal.load(std::memory_order_acquire);
  const auto stopped_status = signal_status.load(std::memory_order_acquire);
  std::cout << "{\"stopped\":true,\"handler_errors\":"
            << (stopped_status == 0 ? 0 : 1)
            << ",\"handler_requests\":" << requests
            << ",\"received_signal\":" << stopped_signal << "}\n";
  std::cout.flush();
  return stopped_status == 0 ? 0 : 6;
}
