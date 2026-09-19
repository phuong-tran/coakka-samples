#include <coakka/http/native_connector/native_connector.h>
#include <coakka/http/v1/http_runtime_startup.pb.h>

#include <array>
#include <atomic>
#include <csignal>
#include <cstdint>
#include <iostream>
#include <pthread.h>
#include <span>
#include <string>
#include <string_view>
#include <utility>

namespace {

using namespace coakka::http::native_connector;

constexpr std::uint64_t kBindingId = 1U;
constexpr std::string_view kBody = "0123456789abcdef0123456789abcdef";

struct Counters final {
  std::atomic<std::uint64_t> handler_requests{0U};
  std::atomic<std::uint64_t> handler_errors{0U};
  std::atomic<std::uint64_t> contexts_destroyed{0U};
};

struct HandlerContext final {
  Counters *counters{nullptr};
  ResponseLimits response_limits{};
};

std::span<const std::uint8_t> as_bytes(const std::string &value) {
  return {reinterpret_cast<const std::uint8_t *>(value.data()), value.size()};
}

void destroy_handler(void *opaque) noexcept {
  auto *context = static_cast<HandlerContext *>(opaque);
  context->counters->contexts_destroyed.fetch_add(1U,
                                                  std::memory_order_relaxed);
  delete context;
}

void handle_fixed(void *opaque, HandlerInvocation &invocation) {
  auto *context = static_cast<HandlerContext *>(opaque);
  context->counters->handler_requests.fetch_add(1U, std::memory_order_relaxed);
  constexpr std::array<BufferedResponseHeaderInput, 1U> headers{{
      {"content-type", "text/plain"},
  }};
  const BufferedResponseInput response{
      200U, std::span<const BufferedResponseHeaderInput>{headers}, kBody, true};
  auto built =
      build_buffered_response_frame(context->response_limits, response);
  if (!built.ok() || !invocation.respond(std::move(*built.frame)).ok()) {
    context->counters->handler_errors.fetch_add(1U, std::memory_order_relaxed);
  }
}

std::string startup_bytes() {
  coakka::http::v1::CoAkkaHttpRuntimeStartupSpec startup;
  startup.set_contract_version(1U);
  auto *listener = startup.add_listeners();
  listener->set_listener_id(1U);
  listener->set_bind_host("127.0.0.1");
  listener->set_port(0U);
  auto *route = startup.add_routes();
  route->set_route_id(1U);
  route->set_method("GET");
  route->set_pattern("/fixed");
  route->set_handler_binding_id(kBindingId);

  std::string bytes;
  if (!startup.SerializeToString(&bytes)) {
    return {};
  }
  return bytes;
}

bool block_termination_signals(sigset_t *signals) {
  return sigemptyset(signals) == 0 && sigaddset(signals, SIGINT) == 0 &&
         sigaddset(signals, SIGTERM) == 0 &&
         pthread_sigmask(SIG_BLOCK, signals, nullptr) == 0;
}

void print_issue(std::string_view operation,
                 const coakka_http_runtime_issue_t &issue) {
  std::cerr << operation << " failed: status=" << issue.status
            << " stage=" << issue.stage << " reason=" << issue.reason
            << " detail=" << issue.detail << '\n';
}

} // namespace

int main() {
  sigset_t signals{};
  if (!block_termination_signals(&signals)) {
    std::cerr << "failed to establish synchronous signal ownership\n";
    return 2;
  }

  Counters counters;
  auto *context = new HandlerContext{&counters};
  HandlerBinding binding;
  const auto adopted =
      HandlerBinding::adopt(context, handle_fixed, destroy_handler, &binding);
  if (!adopted.ok()) {
    delete context;
    std::cerr << "failed to adopt the fixed handler\n";
    return 3;
  }

  HandlerRegistryBuilder registry_builder;
  if (!HandlerRegistryBuilder::create(1U, &registry_builder).ok() ||
      !registry_builder.add(kBindingId, std::move(binding)).ok()) {
    std::cerr << "failed to build the bounded handler registry\n";
    return 4;
  }
  HandlerRegistry registry;
  if (!registry_builder.freeze(&registry).ok()) {
    std::cerr << "failed to freeze the handler registry\n";
    return 5;
  }

  const auto startup = startup_bytes();
  if (startup.empty()) {
    std::cerr << "failed to serialize startup configuration\n";
    return 6;
  }

  NativeConnector connector;
  coakka_http_runtime_issue_t issue{};
  const auto created = NativeConnector::create(
      as_bytes(startup), std::move(registry), {}, &issue, &connector);
  if (!created.ok()) {
    print_issue("native connector create", issue);
    return 7;
  }
  if (!connector.response_limits(&context->response_limits).ok()) {
    std::cerr << "failed to read connector response limits\n";
    return 8;
  }
  if (!connector.start(&issue).ok()) {
    print_issue("native connector start", issue);
    return 9;
  }

  std::uint16_t port = 0U;
  NativeConnectorSnapshot started_snapshot;
  if (!connector.bound_port(&port).ok() || port == 0U ||
      !connector.snapshot(&started_snapshot).ok()) {
    std::cerr << "native connector did not publish startup state\n";
    return 10;
  }
  std::cout << "{\"ready\":true,\"bound_port\":" << port
            << ",\"application_path\":\"normal\""
            << ",\"connector_surface\":\"native-cpp\""
            << ",\"worker_count\":" << started_snapshot.worker_count << "}\n";
  std::cout.flush();

  int received_signal = 0;
  const int signal_status = sigwait(&signals, &received_signal);
  NativeConnectorSnapshot stopped_snapshot;
  const bool snapshot_ok = connector.snapshot(&stopped_snapshot).ok();
  const auto closed = connector.close();
  const auto handler_errors =
      counters.handler_errors.load(std::memory_order_relaxed);
  const auto contexts_destroyed =
      counters.contexts_destroyed.load(std::memory_order_relaxed);
  std::cout << "{\"stopped\":true"
            << ",\"handler_requests\":"
            << counters.handler_requests.load(std::memory_order_relaxed)
            << ",\"handler_errors\":" << handler_errors
            << ",\"contexts_destroyed\":" << contexts_destroyed
            << ",\"requests_dispatched\":"
            << (snapshot_ok ? stopped_snapshot.requests_dispatched : 0U)
            << ",\"requests_rejected\":"
            << (snapshot_ok ? stopped_snapshot.requests_rejected : 0U)
            << ",\"submission_rejected\":"
            << (snapshot_ok ? stopped_snapshot.submission_rejected : 0U)
            << ",\"cleanup_failures\":"
            << (snapshot_ok ? stopped_snapshot.cleanup_failures : 0U)
            << ",\"fatal_code\":"
            << static_cast<unsigned int>(snapshot_ok
                                             ? stopped_snapshot.fatal_code
                                             : NativeConnectorFatalCode::kNone)
            << ",\"received_signal\":" << received_signal << "}\n";
  std::cout.flush();

  return signal_status == 0 && snapshot_ok && closed.ok() &&
                 handler_errors == 0U && contexts_destroyed == 1U &&
                 stopped_snapshot.fatal_code == NativeConnectorFatalCode::kNone
             ? 0
             : 11;
}
