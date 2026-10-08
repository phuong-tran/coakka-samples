/** Finite outbound example using the installed public C API.
 * Service and terminal leases have distinct non-copyable RAII owners. Main is
 * the only completion reader; no application transport thread is introduced.
 */
#include "../native/sample_options.h"
#include <atomic>
#include <cstdio>
#include <cstring>
#include <exception>
#include <stdexcept>
#include <string>

namespace {
std::atomic<bool> handler_failed{false};

void check(coakka_http_result_t result) {
  if (result.code != COAKKA_HTTP_RESULT_OK)
    throw std::runtime_error(std::string(coakka_http_result_code_name(result.code)) + ": " + result.detail);
}

void source(void *, coakka_http_request_t *request) noexcept {
  coakka_http_response_t response{};
  coakka_http_response_init(&response);
  response.body = sample_bytes("outbound-ready");
  // No exception crosses the callback; main observes failure after Core joins.
  if (coakka_http_request_respond(request, &response).code != COAKKA_HTTP_RESULT_OK)
    handler_failed.store(true);
}

class Service final {
 public:
  Service(const coakka_http_server_options_t &options, const coakka_http_route_t &route) {
    const auto created = coakka_http_server_create(&options, &route, 1, &value_);
    if (created.code != COAKKA_HTTP_RESULT_OK) {
      // Constructor failure must release a retained destroy-only owner.
      if (value_ && coakka_http_server_destroy(&value_).code != COAKKA_HTTP_RESULT_OK) std::terminate();
      check(created);
    }
  }
  Service(const Service &) = delete;
  Service &operator=(const Service &) = delete;
  ~Service() noexcept {
    try { close(); }
    catch (...) {
      // A retained native owner cannot be abandoned during stack unwinding.
      std::fputs("outbound service cleanup refused\n", stderr);
      std::terminate();
    }
  }
  void start() { check(coakka_http_server_start(value_)); }
  void close() {
    if (value_ != nullptr) {
      check(coakka_http_server_stop(value_));
      check(coakka_http_server_destroy(&value_));
    }
  }
  coakka_http_server_t *get() const noexcept { return value_; }
  uint16_t port() const {
    uint16_t result = 0;
    check(coakka_http_server_port(value_, &result));
    return result;
  }
 private:
  coakka_http_server_t *value_ = nullptr;
};

class Terminal final {
 public:
  explicit Terminal(Service &service) : server_(service.get()) {
    coakka_http_outbound_terminal_init(&value_);
    check(coakka_http_server_take_outbound(server_, 5000, &value_));
  }
  Terminal(const Terminal &) = delete;
  Terminal &operator=(const Terminal &) = delete;
  ~Terminal() noexcept {
    if (coakka_http_server_release_outbound(server_, &value_).code != COAKKA_HTTP_RESULT_OK) {
      std::fputs("outbound terminal release refused\n", stderr);
      std::terminate();
    }
  }
  const coakka_http_client_terminal_t &view() const noexcept { return value_; }
 private:
  coakka_http_server_t *server_;
  coakka_http_client_terminal_t value_{};
};

void exchange(Service &client, const char *path, uint16_t status) {
  coakka_http_client_request_t request{};
  coakka_http_outbound_id_t call{};
  coakka_http_outbound_request_init(&request);
  request.logical_target = sample_bytes("sample.upstream");
  request.method = sample_bytes("GET"); request.target = sample_bytes(path);
  request.timeout_ms = 3000;
  check(coakka_http_server_outbound_submit(client.get(), &request, &call));
  const Terminal terminal(client);
  const auto &view = terminal.view();
  // Both identity components are authoritative. HTTP 404 remains a response;
  // neither its status nor diagnostic text substitutes for the typed cause.
  if (view.call.slot != call.slot || view.call.generation != call.generation ||
      view.reason != COAKKA_HTTP_OUTBOUND_TERMINAL_RESPONSE || view.response_status != status)
    throw std::runtime_error("outbound identity, reason or status mismatch");
  constexpr char expected[] = "outbound-ready";
  if (status == 200 && (view.response_body.size != sizeof(expected) - 1 ||
      std::memcmp(view.response_body.data, expected, sizeof(expected) - 1) != 0))
    throw std::runtime_error("outbound body mismatch");
  // The borrowed body expires when terminal releases, before the next call.
}
/** Publish a complete route table on an isolated owner. Expected generation 1
 * is a compare-and-apply precondition for this fresh service, not an observation
 * reconstructed from local state. A mismatch is a failure, never a fallback.
 */
void publish_routes(Service &server) {
  check(coakka_http_server_prepare_handler(server.get(), 2, nullptr, source));
  coakka_http_core_route_t route{};
  coakka_http_core_route_init(&route);
  route.route_id = 2; route.handler_binding_id = 2;
  route.method = sample_bytes("GET"); route.path = sample_bytes("/published");
  coakka_http_route_publication_t change{};
  coakka_http_route_publication_init(&change);
  change.activation_id = 1; change.expected_route_generation = 1;
  change.expected_metadata_generation = 1; change.expected_binding_change_sequence = 1;
  change.routes = &route; change.route_count = 1;
  coakka_http_route_publication_outcome_t applied{}, replay{}, rejected{};
  coakka_http_route_publication_outcome_init(&applied);
  check(coakka_http_server_publish_routes(server.get(), &change, 2000, &applied));
  if (applied.code != COAKKA_HTTP_ROUTE_MANIFEST_APPLIED || !applied.changed || applied.replayed)
    throw std::runtime_error("route publication was not applied");
  // Exact replay reuses both identity and payload within Core's bounded history.
  // This is not an injected delivery-ambiguous timeout test.
  coakka_http_route_publication_outcome_init(&replay);
  check(coakka_http_server_publish_routes(server.get(), &change, 2000, &replay));
  if (replay.code != COAKKA_HTTP_ROUTE_MANIFEST_APPLIED || !replay.replayed ||
      replay.operation_digest != applied.operation_digest ||
      replay.effective_route_generation != applied.effective_route_generation)
    throw std::runtime_error("route replay did not preserve the original outcome");
  change.activation_id = 2;
  coakka_http_route_publication_outcome_init(&rejected);
  check(coakka_http_server_publish_routes(server.get(), &change, 2000, &rejected));
  if (rejected.code != COAKKA_HTTP_ROUTE_MANIFEST_GENERATION_MISMATCH || rejected.changed ||
      rejected.effective_route_generation != applied.effective_route_generation ||
      rejected.effective_metadata_generation != applied.effective_metadata_generation ||
      rejected.effective_binding_change_sequence != applied.effective_binding_change_sequence)
    throw std::runtime_error("stale publication did not preserve Core's effective state");
  std::puts("native-route-publication=pass");
}
}  // namespace

int main() {
  try {
    coakka_http_server_options_t options{};
    coakka_http_server_options_init(&options);
    options.bind_address = sample_bytes("127.0.0.1");
    coakka_http_route_t route{};
    coakka_http_route_init(&route);
    route.route_id = 1; route.method = sample_bytes("GET");
    route.path = sample_bytes("/source"); route.handler = source;
    Service upstream(options, route);
    upstream.start();

    coakka_http_outbound_endpoint_t endpoint{};
    coakka_http_outbound_endpoint_init(&endpoint);
    endpoint.node_id = sample_bytes("local-source");
    endpoint.connect_host = sample_bytes("127.0.0.1");
    endpoint.connect_port = upstream.port();
    endpoint.http_authority = sample_bytes("localhost");
    coakka_http_outbound_target_t target{};
    coakka_http_outbound_target_init(&target);
    target.name = sample_bytes("sample.upstream"); target.generation = 1;
    target.endpoints = &endpoint; target.endpoint_count = 1;
    options.outbound_targets = &target; options.outbound_target_count = 1;
    // Core copies topology at create and owns all transport state/defaults.
    Service client(options, route);
    client.start();
    exchange(client, "/source", 200);
    exchange(client, "/missing", 404);
    publish_routes(upstream);
    exchange(client, "/published", 200);
    exchange(client, "/source", 404);
    client.close();
    upstream.close();
    if (handler_failed.load()) throw std::runtime_error("upstream response failed");
    std::puts("native-outbound-smoke=pass");
    return EXIT_SUCCESS;
  } catch (const std::exception &error) {
    std::fprintf(stderr, "outbound: %s\n", error.what());
    return EXIT_FAILURE;
  }
}
