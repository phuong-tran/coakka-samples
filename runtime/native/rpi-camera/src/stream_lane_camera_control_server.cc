#include "stream_lane_camera_control_server.h"

#include "runtime_addon_web_wire.h"

#include <google/protobuf/struct.pb.h>
#include <google/protobuf/util/json_util.h>

#include <uv.h>

#include <arpa/inet.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <charconv>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <system_error>
#include <thread>
#include <utility>
#include <vector>

namespace {

using coakka::runtime_addon_web::http_request_t;
using coakka::runtime_addon_web::make_http_response_text;
using coakka::runtime_addon_web::parse_request_from_text;
using coakka::runtime_addon_web::request_path_without_query;
using coakka::runtime_addon_web::try_extract_http_request_bytes;

constexpr size_t kMaxRequestBytes = 8u * 1024u;
constexpr size_t kMaxClients = 4u;

bool constant_time_equal(std::string_view left, std::string_view right) {
  const size_t compared = std::max(left.size(), right.size());
  size_t difference = left.size() ^ right.size();
  for (size_t index = 0u; index < compared; ++index) {
    const unsigned char left_byte =
        index < left.size() ? static_cast<unsigned char>(left[index]) : 0u;
    const unsigned char right_byte =
        index < right.size() ? static_cast<unsigned char>(right[index]) : 0u;
    difference |= left_byte ^ right_byte;
  }
  return difference == 0u;
}

struct Server;

struct Client {
  uv_tcp_t handle{};
  Server *server = nullptr;
  std::array<char, 4096u> read_buffer{};
  std::string input;
  bool closing = false;
  bool responded = false;
};

struct Write {
  uv_write_t request{};
  Client *client = nullptr;
  std::string payload;
};

bool read_uint32(const google::protobuf::Struct &body, const char *name,
                 uint32_t *out) {
  const auto found = body.fields().find(name);
  if (found == body.fields().end() ||
      found->second.kind_case() != google::protobuf::Value::kNumberValue) {
    return false;
  }
  const double value = found->second.number_value();
  if (value < 1.0 || value > static_cast<double>(UINT32_MAX) ||
      value != static_cast<double>(static_cast<uint32_t>(value))) {
    return false;
  }
  *out = static_cast<uint32_t>(value);
  return true;
}

struct Server {
  explicit Server(coakka_v2_camera_control_server_config_t value)
      : config(std::move(value)) {}

  bool start(std::string *error) {
    try {
      worker = std::thread([this] { run(); });
    } catch (const std::system_error &failure) {
      *error = "control worker start failed: " + std::string(failure.what());
      return false;
    }
    std::unique_lock<std::mutex> lock(ready_mutex);
    ready_condition.wait(lock, [this] {
      return ready.load(std::memory_order_acquire);
    });
    if (start_result != 0) {
      *error = uv_strerror(start_result);
      return false;
    }
    return true;
  }

  void stop() {
    if (stopped.exchange(true, std::memory_order_acq_rel)) {
      return;
    }
    stopping.store(true, std::memory_order_release);
    if (ready.load(std::memory_order_acquire) && start_result == 0) {
      (void)uv_async_send(&async);
    }
    if (worker.joinable()) {
      worker.join();
    }
  }

  static void alloc_read(uv_handle_t *handle, size_t, uv_buf_t *buffer) {
    auto *client = static_cast<Client *>(handle->data);
    *buffer =
        uv_buf_init(client->read_buffer.data(),
                    static_cast<unsigned int>(client->read_buffer.size()));
  }

  static void delete_client(uv_handle_t *handle) {
    auto *client = static_cast<Client *>(handle->data);
    auto &clients = client->server->clients;
    clients.erase(std::remove(clients.begin(), clients.end(), client),
                  clients.end());
    delete client;
  }

  static void close_client(Client *client) {
    if (client == nullptr || client->closing) {
      return;
    }
    client->closing = true;
    (void)uv_read_stop(reinterpret_cast<uv_stream_t *>(&client->handle));
    uv_close(reinterpret_cast<uv_handle_t *>(&client->handle), delete_client);
  }

  static void write_complete(uv_write_t *request, int) {
    std::unique_ptr<Write> write(static_cast<Write *>(request->data));
    close_client(write->client);
  }

  static void respond(Client *client, int status, std::string body) {
    auto write = std::make_unique<Write>();
    write->client = client;
    write->payload =
        make_http_response_text(status, "application/json", std::move(body));
    write->request.data = write.get();
    uv_buf_t buffer =
        uv_buf_init(write->payload.data(),
                    static_cast<unsigned int>(write->payload.size()));
    if (uv_write(&write->request,
                 reinterpret_cast<uv_stream_t *>(&client->handle), &buffer, 1u,
                 write_complete) != 0) {
      close_client(client);
      return;
    }
    (void)write.release();
  }

  void handle_request(Client *client) {
    size_t request_size = 0u;
    std::string error;
    if (!try_extract_http_request_bytes(client->input, &request_size, &error)) {
      return;
    }
    client->responded = true;
    if (!error.empty() || request_size > kMaxRequestBytes) {
      respond(client, 400, "{\"ok\":false,\"error\":\"bad request\"}");
      return;
    }
    http_request_t request;
    if (!parse_request_from_text(client->input.substr(0u, request_size),
                                 &request, &error)) {
      respond(client, 400, "{\"ok\":false,\"error\":\"bad request\"}");
      return;
    }
    const auto authorization = request.headers.find("authorization");
    const std::string expected_authorization =
        "Bearer " + config.authorization_token;
    if (authorization == request.headers.end() ||
        !constant_time_equal(authorization->second, expected_authorization)) {
      respond(client, 403, "{\"ok\":false,\"error\":\"forbidden\"}");
      return;
    }
    if (request.method != "POST" ||
        request_path_without_query(request.path) != "/v1/profile") {
      respond(client, 404, "{\"ok\":false,\"error\":\"not found\"}");
      return;
    }
    google::protobuf::Struct body;
    google::protobuf::util::JsonParseOptions options;
    options.ignore_unknown_fields = false;
    const auto parsed = google::protobuf::util::JsonStringToMessage(
        request.body, &body, options);
    uint32_t width = 0u;
    uint32_t height = 0u;
    uint32_t fps = 0u;
    if (!parsed.ok() || !read_uint32(body, "width", &width) ||
        !read_uint32(body, "height", &height) ||
        !read_uint32(body, "fps", &fps)) {
      respond(client, 400, "{\"ok\":false,\"error\":\"invalid profile\"}");
      return;
    }
    std::array<char, 192u> handler_error{};
    if (!config.profile_handler(config.handler_context, width, height, fps,
                                handler_error.data(), handler_error.size())) {
      google::protobuf::Struct response;
      (*response.mutable_fields())["ok"].set_bool_value(false);
      (*response.mutable_fields())["error"].set_string_value(
          handler_error.data());
      std::string json;
      (void)google::protobuf::util::MessageToJsonString(response, &json);
      respond(client, 409, std::move(json));
      return;
    }
    respond(client, 202, "{\"ok\":true}");
  }

  static void read_client(uv_stream_t *stream, ssize_t read,
                          const uv_buf_t *buffer) {
    auto *client = static_cast<Client *>(stream->data);
    if (read < 0) {
      close_client(client);
      return;
    }
    if (read == 0 || client->responded) {
      return;
    }
    client->input.append(buffer->base, static_cast<size_t>(read));
    if (client->input.size() > kMaxRequestBytes) {
      close_client(client);
      return;
    }
    client->server->handle_request(client);
  }

  static void accept_client(uv_stream_t *stream, int status) {
    auto *server = static_cast<Server *>(stream->data);
    if (status < 0 || server->stopping.load(std::memory_order_acquire)) {
      return;
    }
    auto client = std::make_unique<Client>();
    client->server = server;
    client->handle.data = client.get();
    if (uv_tcp_init(&server->loop, &client->handle) != 0) {
      return;
    }
    Client *owned = client.release();
    if (uv_accept(stream, reinterpret_cast<uv_stream_t *>(&owned->handle)) !=
        0) {
      close_client(owned);
      return;
    }
    if (server->clients.size() >= kMaxClients) {
      close_client(owned);
      return;
    }
    server->clients.push_back(owned);
    if (uv_read_start(reinterpret_cast<uv_stream_t *>(&owned->handle),
                      alloc_read, read_client) != 0) {
      close_client(owned);
    }
  }

  static void async_ready(uv_async_t *handle) {
    auto *server = static_cast<Server *>(handle->data);
    if (!server->stopping.load(std::memory_order_acquire)) {
      return;
    }
    const std::vector<Client *> clients = server->clients;
    for (Client *client : clients) {
      close_client(client);
    }
    uv_close(reinterpret_cast<uv_handle_t *>(&server->listener), nullptr);
    uv_close(reinterpret_cast<uv_handle_t *>(&server->async), nullptr);
  }

  void publish_ready(int result) {
    {
      std::lock_guard<std::mutex> lock(ready_mutex);
      start_result = result;
      ready.store(true, std::memory_order_release);
    }
    ready_condition.notify_one();
  }

  void run() {
    int result = uv_loop_init(&loop);
    if (result != 0) {
      publish_ready(result);
      return;
    }
    listener.data = this;
    async.data = this;
    result = uv_tcp_init(&loop, &listener);
    if (result == 0) {
      result = uv_async_init(&loop, &async, async_ready);
    }
    sockaddr_in address{};
    if (result == 0) {
      result =
          uv_ip4_addr(config.bind_host.c_str(), config.bind_port, &address);
    }
    if (result == 0) {
      result = uv_tcp_bind(&listener,
                           reinterpret_cast<const sockaddr *>(&address), 0u);
    }
    if (result == 0) {
      result = uv_listen(reinterpret_cast<uv_stream_t *>(&listener), 4,
                         accept_client);
    }
    if (result == 0) {
      int length = sizeof(address);
      result = uv_tcp_getsockname(
          &listener, reinterpret_cast<sockaddr *>(&address), &length);
      port = ntohs(address.sin_port);
    }
    if (result != 0) {
      uv_walk(
          &loop,
          [](uv_handle_t *handle, void *) {
            if (!uv_is_closing(handle)) {
              uv_close(handle, nullptr);
            }
          },
          nullptr);
      (void)uv_run(&loop, UV_RUN_DEFAULT);
      (void)uv_loop_close(&loop);
      publish_ready(result);
      return;
    }
    publish_ready(0);
    (void)uv_run(&loop, UV_RUN_DEFAULT);
    (void)uv_loop_close(&loop);
  }

  coakka_v2_camera_control_server_config_t config;
  uv_loop_t loop{};
  uv_tcp_t listener{};
  uv_async_t async{};
  std::vector<Client *> clients;
  std::thread worker;
  std::mutex ready_mutex;
  std::condition_variable ready_condition;
  std::atomic<bool> stopping{false};
  std::atomic<bool> stopped{false};
  std::atomic<bool> ready{false};
  int start_result = UV_EINVAL;
  uint16_t port = 0u;
};

} // namespace

struct coakka_v2_camera_control_server_t::impl_t {
  explicit impl_t(const coakka_v2_camera_control_server_config_t &config)
      : server(config) {}
  Server server;
};

std::unique_ptr<coakka_v2_camera_control_server_t>
coakka_v2_camera_control_server_t::create(
    const coakka_v2_camera_control_server_config_t &config) {
  if (config.bind_host.empty() || config.bind_port == 0u ||
      config.authorization_token.empty() || config.profile_handler == nullptr) {
    return nullptr;
  }
  return std::unique_ptr<coakka_v2_camera_control_server_t>(
      new coakka_v2_camera_control_server_t(std::make_unique<impl_t>(config)));
}

coakka_v2_camera_control_server_t::coakka_v2_camera_control_server_t(
    std::unique_ptr<impl_t> impl)
    : impl_(std::move(impl)) {}

coakka_v2_camera_control_server_t::~coakka_v2_camera_control_server_t() {
  stop();
}

bool coakka_v2_camera_control_server_t::start(std::string *error) {
  return impl_ != nullptr && error != nullptr && impl_->server.start(error);
}

void coakka_v2_camera_control_server_t::stop() {
  if (impl_ != nullptr) {
    impl_->server.stop();
  }
}

uint16_t coakka_v2_camera_control_server_t::bound_port() const noexcept {
  return impl_ != nullptr ? impl_->server.port : 0u;
}
