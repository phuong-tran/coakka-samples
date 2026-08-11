#ifndef COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_CONTROL_SERVER_H
#define COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_CONTROL_SERVER_H

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

using coakka_v2_camera_profile_handler_fn = bool (*)(void *context,
                                                     uint32_t width,
                                                     uint32_t height,
                                                     uint32_t fps, char *error,
                                                     size_t error_capacity);

struct coakka_v2_camera_control_server_config_t {
  std::string bind_host;
  uint16_t bind_port = 0u;
  std::string authorization_token;
  void *handler_context = nullptr;
  coakka_v2_camera_profile_handler_fn profile_handler = nullptr;
};

class coakka_v2_camera_control_server_t {
public:
  static std::unique_ptr<coakka_v2_camera_control_server_t>
  create(const coakka_v2_camera_control_server_config_t &config);

  ~coakka_v2_camera_control_server_t();
  coakka_v2_camera_control_server_t(const coakka_v2_camera_control_server_t &) =
      delete;
  coakka_v2_camera_control_server_t &
  operator=(const coakka_v2_camera_control_server_t &) = delete;

  bool start(std::string *error);
  // Idempotent; wakes the loop and joins its single owning thread.
  void stop();
  uint16_t bound_port() const noexcept;

private:
  struct impl_t;
  explicit coakka_v2_camera_control_server_t(std::unique_ptr<impl_t> impl);
  std::unique_ptr<impl_t> impl_;
};

#endif
