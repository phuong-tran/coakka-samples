/** Fixed response through cpp-httplib's normal routing API. */
#include <httplib.h>

#include <chrono>
#include <cstddef>
#include <csignal>
#include <cstdlib>
#include <memory>
#include <string>
#include <thread>

namespace {
volatile std::sig_atomic_t stopping = 0;
const std::string body = "0123456789abcdef0123456789abcdef";
constexpr std::size_t connection_workers = 64;

void stop_signal(int value) noexcept {
  (void)value;
  stopping = 1;
}
} // namespace

int main(int argc, char **argv) {
  if (argc != 2)
    return EXIT_FAILURE;
  const auto port = std::stoi(argv[1]);
  if (port < 1 || port > 65535)
    return EXIT_FAILURE;
  httplib::Server server;
  // cpp-httplib assigns a persistent connection to a worker. Match the
  // declared client concurrency so idle keep-alive sockets cannot starve the
  // accept queue; taskset still confines execution to the server CPU set.
  server.new_task_queue = [] {
    return new httplib::ThreadPool(connection_workers);
  };
  // Avoid delayed-ACK stalls for the benchmark's small persistent responses.
  server.set_tcp_nodelay(true);
  server.Get("/fixed",
             [](const httplib::Request &, httplib::Response &response) {
               response.set_content(body, "application/octet-stream");
             });
  std::thread listener([&] {
    if (!server.listen("127.0.0.1", port))
      stopping = 1;
  });
  (void)std::signal(SIGINT, stop_signal);
  (void)std::signal(SIGTERM, stop_signal);
  while (stopping == 0)
    std::this_thread::sleep_for(std::chrono::milliseconds(50));
  server.stop();
  listener.join();
  return EXIT_SUCCESS;
}
