/** C++20 application: RAII owns the server; callbacks borrow request views.
 * No exception may cross the C callback boundary. Explicit close reports errors;
 * the destructor is the final safety net, not a silent graceful-close claim.
 */
#include "../native/sample_options.h"
#include "../native/monitor_example.h"
#include <array>
#include <atomic>
#include <csignal>
#include <cstdint>
#include <iostream>
#include <limits>
#include <mutex>
#include <stdexcept>
#include <string>

namespace {
volatile std::sig_atomic_t stopping = 0;
std::atomic<bool> failed{false};
// Diagnostics are a sample-owned shared resource. One lock covers every line;
// no runtime call or body read runs while it is held. This uses portable basic
// library facilities rather than requiring newer standard-library stream APIs.
std::mutex diagnostic_mutex;
template <typename... Values>
void diagnostic(std::ostream &output, const Values &...values) {
  const std::lock_guard<std::mutex> guard(diagnostic_mutex);
  (output << ... << values);
  output << '\n'; output.flush();
}
void check(coakka_http_result_t result) {
  if (result.code != COAKKA_HTTP_RESULT_OK)
    throw std::runtime_error(std::string(coakka_http_result_code_name(result.code)) + ": " + result.detail);
}

// Only scalar application state survives a body read. Core owns the bounded
// input and its deadline; no chunk or trailer view escapes the next read.
void handle_upload(coakka_http_request_t *request) {
  std::uint64_t bytes = 0;
  std::uint32_t trailers = 0;
  bool announced = false;
  for (;;) {
    coakka_http_request_body_event_t event{};
    coakka_http_request_body_event_init(&event);
    auto result = coakka_http_request_body_read(request, 5000, &event);
    if (result.code == COAKKA_HTTP_RESULT_CANCELLED ||
        result.code == COAKKA_HTTP_RESULT_CLOSED ||
        (result.code == COAKKA_HTTP_RESULT_OK && event.kind == COAKKA_HTTP_REQUEST_BODY_CANCELLED)) {
      diagnostic(std::cout, "native-upload-cancelled");
      return;
    }
    if (result.code == COAKKA_HTTP_RESULT_TIMEOUT) {
      coakka_http_response_t response{};
      coakka_http_response_init(&response); response.status_code = 408;
      result = coakka_http_request_respond(request, &response);
      if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED) check(result);
      return;
    }
    check(result);
    if (event.kind == COAKKA_HTTP_REQUEST_BODY_DATA) {
      if (event.data.size > std::numeric_limits<std::uint64_t>::max() - bytes)
        throw std::overflow_error("upload size overflow");
      bytes += event.data.size;
      if (!announced) {
        // Lifecycle-only smoke marker: no body, header or credential content.
        diagnostic(std::cout, "native-upload-data"); announced = true;
      }
    } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_TRAILERS) {
      trailers = coakka_http_request_body_event_trailer_count(&event);
    } else if (event.kind == COAKKA_HTTP_REQUEST_BODY_END) {
      const auto text = "bytes=" + std::to_string(bytes) + " trailers=" + std::to_string(trailers);
      coakka_http_response_t response{}; coakka_http_response_init(&response);
      response.body = sample_bytes(text.c_str());
      // Successful respond copies the short summary before text is destroyed.
      result = coakka_http_request_respond(request, &response);
      if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED) check(result);
      return;
    }
  }
}
void handle(void *context, coakka_http_request_t *request) noexcept {
  try {
    const auto route = coakka_http_request_route_id(request);
    if (route == 5) { handle_upload(request); return; }
    if (route == 6) {
      check(coakka_http_request_accept_websocket(request, sample_bytes("")));
      return;
    }
    if (route == 2) {
      coakka_http_file_response_t file{};
      coakka_http_file_response_init(&file);
      file.authority_id = 82; file.encoded_path = sample_bytes("/sample.txt");
      check(coakka_http_request_respond_file(request, &file));
      return;
    }
    coakka_http_response_t response{};
    coakka_http_response_init(&response);
    if (route == 7) {
      coakka_http_path_parameter_t path{};
      if (!coakka_http_request_path_parameter(request, 0, &path))
        throw std::runtime_error("missing route capture");
      const auto count = coakka_http_request_query_parameter_count(request);
      std::uint32_t values = 0;
      // Enumerate Core's borrowed, ordered entries; no URL reparse or map
      // allocation, and duplicate keys/absent values remain distinguishable.
      for (std::uint32_t index = 0; index < count; ++index) {
        coakka_http_query_parameter_t query{};
        if (!coakka_http_request_query_parameter(request, index, &query))
          throw std::runtime_error("missing query entry");
        values += query.has_value != 0;
      }
      const auto count_text = std::to_string(count);
      const auto values_text = std::to_string(values);
      const std::array<coakka_http_header_t, 3> headers{{
          {sample_bytes("content-type"), sample_bytes("text/plain")},
          {sample_bytes("x-query-count"), sample_bytes(count_text.c_str())},
          {sample_bytes("x-query-with-value"), sample_bytes(values_text.c_str())}}};
      response.headers = headers.data(); response.header_count = static_cast<std::uint32_t>(headers.size());
      response.body = path.encoded_value; // respond copies before locals expire
      check(coakka_http_request_respond(request, &response));
      return;
    }
    response.status_code = route == 1 ? 201 : 200;
    response.body = route == 1 ? coakka_http_request_body(request) : sample_bytes(static_cast<const char *>(context));
    check(coakka_http_request_respond(request, &response));
  } catch (const std::exception &error) {
    failed.store(true); diagnostic(std::cerr, "handler: ", error.what());
  } catch (...) { failed.store(true); }
}

class Server final {
 public:
  Server(const coakka_http_server_options_t &options, const char *identity) {
    std::array<coakka_http_route_t, 7> routes{};
    const std::array<const char *, 7> paths{"/echo", "/download", "/version", "/secure", "/upload", "/socket", "/items/{id}"};
    for (std::size_t i = 0; i < routes.size(); ++i) {
      auto &route = routes[i]; coakka_http_route_init(&route);
      route.route_id = i + 1; route.method = sample_bytes(i == 0 || i == 4 ? "POST" : "GET");
      if (i == 4) route.body_delivery = COAKKA_HTTP_BODY_STREAM;
      route.path = sample_bytes(paths[i]); route.handler = handle;
      route.context = const_cast<char *>(i == 2 ? "v1" : identity);
    }
    const auto created = coakka_http_server_create(&options, routes.data(), static_cast<uint32_t>(routes.size()), &value_);
    if (created.code != COAKKA_HTTP_RESULT_OK) {
      // A constructor that throws never runs this class's destructor. Failed
      // affinity restoration can retain a destroy-only owner even on create.
      if (value_ && coakka_http_server_destroy(&value_).code != COAKKA_HTTP_RESULT_OK) std::terminate();
      check(created);
    }
  }
  Server(const Server &) = delete;
  Server &operator=(const Server &) = delete;
  ~Server() noexcept {
    if (value_ != nullptr && coakka_http_server_destroy(&value_).code != COAKKA_HTTP_RESULT_OK) {
      // A retained runtime may still call application code. Do not continue
      // unwinding its borrowed contexts as if destruction had succeeded.
      std::terminate();
    }
  }
  void start() { check(coakka_http_server_start(value_)); }
  void activate() {
    check(coakka_http_server_prepare_handler(value_, 10, const_cast<char *>("v2"), handle));
    coakka_http_route_rebind_t change{}; coakka_http_route_rebind_init(&change);
    change.activation_id = 1; change.expected_route_generation = 1;
    change.route_id = 3; change.expected_binding_revision = 1; change.new_handler_binding_id = 10;
    coakka_http_route_rebind_outcome_t outcome{}; coakka_http_route_rebind_outcome_init(&outcome);
    check(coakka_http_server_rebind_handler(value_, &change, 2000, &outcome));
    if (outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED) throw std::runtime_error("handler activation rejected");
    // A prepared binding is not active until Core accepts its expected revision.
    check(coakka_http_server_prepare_handler(value_, 11, const_cast<char *>("unreachable"), handle));
    change.activation_id = 2; change.new_handler_binding_id = 11;
    coakka_http_route_rebind_outcome_init(&outcome);
    check(coakka_http_server_rebind_handler(value_, &change, 2000, &outcome));
    if (outcome.code != COAKKA_HTTP_ROUTE_REBIND_BINDING_REVISION_MISMATCH || outcome.changed ||
        outcome.effective_handler_binding_id != 10) throw std::runtime_error("stale handler activation was not preserved");
  }
  uint16_t observe() const {
    if (!sample_monitor_reload(value_)) throw std::runtime_error("monitor reload/refusal contract failed");
    coakka_http_runtime_info_t info{}; coakka_http_runtime_info_init(&info);
    coakka_http_health_t health{}; coakka_http_health_init(&health);
    uint16_t port = 0;
    check(coakka_http_server_get_runtime_info(value_, &info));
    // Core supplies the actual execution state. Worker count and construction
    // intent must never stand in for an absent instance observation.
    if (!info.execution.observed || !info.core_started ||
        info.execution.active_event_loops == 0 ||
        info.execution.active_event_loops != info.execution.configured_event_loops)
      throw std::runtime_error("Core execution observation is not ready");
    diagnostic(std::cout, "execution-observed=", info.execution.observed,
               " configured-loops=", info.execution.configured_event_loops,
               " active-loops=", info.execution.active_event_loops);
    diagnostic(std::cout, "cpu-policy=", info.cpu.requested_policy,
               " placement=", info.cpu.placement, " selected=", info.cpu.selected_cpu_count,
               " verified=", static_cast<unsigned>(info.cpu.startup_verified),
               " request-batch=", info.request_notification_batch_size,
               " terminal-batch=", info.terminal_notification_batch_size);
    check(coakka_http_server_probe_liveness(value_, 2000, &health));
    std::array<coakka_http_monitor_event_view_t, 8> events{};
    for (auto &event : events) coakka_http_monitor_event_view_init(&event);
    coakka_http_monitor_event_page_view_t page{}; coakka_http_monitor_event_page_init(&page);
    check(coakka_http_server_monitor_read(value_, 0, 8, events.data(), 8, &page));
    diagnostic(std::cout, "io-backend-requested=", info.requested_io_backend,
               " effective=", info.effective_io_backend, " fallback=", info.fallback_reason);
    check(coakka_http_server_port(value_, &port));
    return port;
  }
  void close() {
    check(coakka_http_server_stop(value_));
    check(coakka_http_server_destroy(&value_));
  }
  // One reader owns each borrowed event until exact release. Core bounds the
  // session/frame storage; this sample does not add a second message queue.
  void poll_socket() {
    coakka_http_socket_event_t event{}; coakka_http_socket_event_init(&event);
    const auto taken = coakka_http_server_take_websocket(value_, 50, &event);
    if (taken.code == COAKKA_HTTP_RESULT_TIMEOUT) return;
    check(taken);
    coakka_http_result_t sent{}; coakka_http_result_init(&sent);
    if (event.kind == COAKKA_HTTP_WEBSOCKET_TEXT || event.kind == COAKKA_HTTP_WEBSOCKET_BINARY) {
      sent = coakka_http_server_send_websocket(value_, event.session, event.kind,
                                              {event.data, event.data_size});
      if (sent.code == COAKKA_HTTP_RESULT_QUEUE_FULL)
        sent = coakka_http_server_close_websocket(value_, event.session, 1013, sample_bytes("echo capacity"));
    }
    // Release before throwing on send failure: exception unwinding must not
    // retain a lease that would prevent server destruction.
    check(coakka_http_server_release_websocket(value_, &event));
    if (sent.code != COAKKA_HTTP_RESULT_CANCELLED && sent.code != COAKKA_HTTP_RESULT_CLOSED) check(sent);
  }
 private:
  coakka_http_server_t *value_ = nullptr;
};
void stop_signal(int) { stopping = 1; }
}

int main(int argc, char **argv) {
  try {
    sample_options_t config{};
    if (!sample_options_parse(&config, argc, argv)) throw std::invalid_argument("invalid sample arguments; see README");
    config.server.reserve_websocket = 1;
    Server server(config.server, config.response);
    server.start(); server.activate();
    const auto port = server.observe();
    std::signal(SIGINT, stop_signal); std::signal(SIGTERM, stop_signal);
    diagnostic(std::cout, "native-sample-port=", port);
    while (!stopping) server.poll_socket();
    server.close();
    return failed.load() ? EXIT_FAILURE : EXIT_SUCCESS;
  } catch (const std::exception &error) {
    diagnostic(std::cerr, error.what()); return EXIT_FAILURE;
  }
}
