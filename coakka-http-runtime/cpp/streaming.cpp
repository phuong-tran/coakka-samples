/** C++20 single-producer stream/SSE example. Core owns HTTP; the application
 * owns one bounded writer slot. RAII keeps a writer alive across callback return
 * and releases it before service destruction, including exceptions/refusals.
 */
#include "../native/sample_options.h"
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <cstdio>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <utility>

namespace {
volatile std::sig_atomic_t stopping = 0;
std::atomic<bool> failed{false};
void stop_signal(int) { stopping = 1; }
void check(coakka_http_result_t value) {
  if (value.code != COAKKA_HTTP_RESULT_OK)
    throw std::runtime_error(std::string(coakka_http_result_code_name(value.code)) + ": " + value.detail);
}
struct WriterDeleter {
  void operator()(coakka_http_response_stream_t *value) const noexcept {
    coakka_http_response_stream_release(&value);
  }
};
using Writer = std::unique_ptr<coakka_http_response_stream_t, WriterDeleter>;
struct Job { Writer stream; bool sse; };

class Producer final {
 public:
  // Reservation covers preparation, publication and the active producer. Core
  // operations stay outside the mutex; no callback waits for response credit.
  void select(coakka_http_request_t *request) {
    bool admitted;
    {
      const std::lock_guard<std::mutex> guard(mutex_);
      admitted = !busy_ && !closing_;
      if (admitted) busy_ = true;
    }
    coakka_http_response_t head{}; coakka_http_response_init(&head);
    if (!admitted) {
      head.status_code = 503; head.body = sample_bytes("producer busy");
      const auto result = coakka_http_request_respond(request, &head);
      if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED) check(result);
      return;
    }
    const bool sse = coakka_http_request_route_id(request) == 2;
    coakka_http_response_stream_t *raw = nullptr;
    coakka_http_header_t header{sample_bytes("content-type"), sample_bytes("text/plain")};
    head.headers = &header; head.header_count = 1;
    // Do not supply transfer-framing headers: Core owns those and final trailers.
    const auto result = sse ? coakka_http_request_start_sse(request, nullptr, 0, &raw)
                           : coakka_http_request_start_response_stream(request, &head, &raw);
    Writer stream(raw);
    {
      const std::lock_guard<std::mutex> guard(mutex_);
      if (result.code == COAKKA_HTTP_RESULT_OK && !closing_) {
        pending_.emplace(Job{std::move(stream), sse});
        ready_.notify_one();
        return;
      }
      busy_ = false;
    }
    // A closing producer disposes its local writer; it cannot enqueue late work.
    if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED) check(result);
  }

  void run_turn() {
    std::optional<Job> job;
    {
      std::unique_lock<std::mutex> guard(mutex_);
      // Relative wait uses a steady deadline and observes termination signals.
      ready_.wait_for(guard, std::chrono::milliseconds(50), [&] { return pending_.has_value(); });
      if (!pending_) return;
      job = std::move(pending_); pending_.reset();
    }
    produce(*job);
    job.reset();
    const std::lock_guard<std::mutex> guard(mutex_);
    busy_ = false;
  }

  void close_admission() {
    std::optional<Job> abandoned;
    {
      const std::lock_guard<std::mutex> guard(mutex_);
      closing_ = true;
      abandoned = std::move(pending_); pending_.reset();
    }
    // Writer destruction occurs outside the mutex, before server stop.
  }

 private:
  static void produce(Job &job) {
    auto result = coakka_http_response_stream_wait_writable(job.stream.get(), 5000);
    if (result.code == COAKKA_HTTP_RESULT_OK) {
      if (job.sse) {
        coakka_http_sse_event_t event{}; coakka_http_sse_event_init(&event);
        event.data = sample_bytes("ready\nsecond line");
        event.event_type = sample_bytes("state"); event.has_event_type = 1;
        event.id = sample_bytes("1"); event.has_id = 1;
        event.retry_ms = 1000; event.has_retry = 1;
        result = coakka_http_response_stream_write_sse(job.stream.get(), &event);
      } else result = coakka_http_response_stream_write(job.stream.get(), sample_bytes("stream-ready"));
    }
    if (result.code == COAKKA_HTTP_RESULT_OK) {
      const coakka_http_header_t trailer{sample_bytes("x-stream-end"), sample_bytes("done")};
      auto *raw = job.stream.release();
      result = coakka_http_response_stream_finish(&raw, job.sse ? nullptr : &trailer, job.sse ? 0 : 1);
      job.stream.reset(raw); // Finish consumes on success; failure retains exact ownership.
    }
    if (result.code != COAKKA_HTTP_RESULT_OK) {
      std::fprintf(stderr, "stream stopped: %s\n", coakka_http_result_code_name(result.code));
      if (result.code != COAKKA_HTTP_RESULT_CANCELLED && result.code != COAKKA_HTTP_RESULT_CLOSED &&
          result.code != COAKKA_HTTP_RESULT_TIMEOUT && result.code != COAKKA_HTTP_RESULT_QUEUE_FULL) check(result);
    }
  }
  std::mutex mutex_;
  std::condition_variable ready_;
  bool busy_ = false, closing_ = false;
  std::optional<Job> pending_;
};

void select_writer(void *context, coakka_http_request_t *request) noexcept {
  try { static_cast<Producer *>(context)->select(request); }
  catch (const std::exception &error) { failed.store(true); std::fprintf(stderr, "stream callback: %s\n", error.what()); }
  catch (...) { failed.store(true); }
}

class Server final {
 public:
  explicit Server(Producer &producer) : producer_(producer) {
    coakka_http_server_options_t options{}; coakka_http_server_options_init(&options);
    options.bind_address = sample_bytes("127.0.0.1");
    coakka_http_route_t routes[2]{};
    for (unsigned i = 0; i < 2; ++i) {
      coakka_http_route_init(&routes[i]); routes[i].route_id = i + 1;
      routes[i].method = sample_bytes("GET"); routes[i].path = sample_bytes(i == 0 ? "/stream" : "/events");
      routes[i].handler = select_writer; routes[i].context = &producer_;
    }
    const auto created = coakka_http_server_create(&options, routes, 2, &value_);
    if (created.code != COAKKA_HTTP_RESULT_OK) {
      // Constructor failure must release a retained destroy-only owner.
      if (value_ && coakka_http_server_destroy(&value_).code != COAKKA_HTTP_RESULT_OK) std::terminate();
      check(created);
    }
  }
  Server(const Server &) = delete;
  Server &operator=(const Server &) = delete;
  ~Server() noexcept {
    try { close(); } catch (...) { std::terminate(); }
  }
  void start() {
    check(coakka_http_server_start(value_));
    uint16_t port = 0; check(coakka_http_server_port(value_, &port));
    std::printf("native-stream-port=%u\n", static_cast<unsigned>(port)); std::fflush(stdout);
  }
  void close() {
    if (value_ == nullptr) return;
    producer_.close_admission();
    check(coakka_http_server_stop(value_));
    check(coakka_http_server_destroy(&value_));
  }
 private:
  Producer &producer_; // Must outlive server, including failed close/retry.
  coakka_http_server_t *value_ = nullptr;
};
}

int main() {
  try {
    Producer producer;
    Server server(producer); // Reverse-order destruction joins callbacks first.
    std::signal(SIGINT, stop_signal); std::signal(SIGTERM, stop_signal);
    server.start();
    while (!stopping) producer.run_turn();
    server.close();
    return failed.load() ? EXIT_FAILURE : EXIT_SUCCESS;
  } catch (const std::exception &error) {
    std::fprintf(stderr, "%s\n", error.what()); return EXIT_FAILURE;
  }
}
