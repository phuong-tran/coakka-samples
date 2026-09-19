#include <App.h>

#include <atomic>
#include <csignal>
#include <cstdint>
#include <iostream>
#include <pthread.h>
#include <string_view>
#include <thread>

namespace {

constexpr std::string_view kBody = "0123456789abcdef0123456789abcdef";

bool block_termination_signals(sigset_t *signals) {
  return sigemptyset(signals) == 0 && sigaddset(signals, SIGINT) == 0 &&
         sigaddset(signals, SIGTERM) == 0 &&
         pthread_sigmask(SIG_BLOCK, signals, nullptr) == 0;
}

} // namespace

int main() {
  sigset_t signals{};
  if (!block_termination_signals(&signals)) {
    std::cerr << "failed to establish synchronous signal ownership\n";
    return 2;
  }

  uWS::App app;
  if (app.constructorFailed()) {
    std::cerr << "failed to construct the pinned uWebSockets app\n";
    return 3;
  }

  std::uint64_t requests = 0U;
  app.get("/fixed", [&requests](auto *response, auto *) {
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
    return 4;
  }

  auto *loop = uWS::Loop::get();
  std::atomic<int> signal_status{0};
  std::atomic<int> received_signal{0};
  std::thread signal_owner([&] {
    int signal_number = 0;
    const int status = sigwait(&signals, &signal_number);
    signal_status.store(status, std::memory_order_release);
    received_signal.store(signal_number, std::memory_order_release);
    // Loop::defer owns the cross-thread wakeup; listener close stays on the
    // uWebSockets event-loop thread and app.run() then converges naturally.
    loop->defer([listener] { us_listen_socket_close(0, listener); });
  });

  std::cout << "{\"ready\":true,\"bound_port\":" << port
            << ",\"application_path\":\"direct\""
            << ",\"benchmark_layer\":\"raw-uws-cpp\"}\n";
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
  return stopped_status == 0 ? 0 : 5;
}
