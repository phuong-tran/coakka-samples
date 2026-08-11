#ifndef COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_WEB_GATEWAY_H
#define COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_WEB_GATEWAY_H

#include "coakka/v2/stream_lane.h"

#include <cstdint>
#include <memory>
#include <string>

struct coakka_v2_camera_web_gateway_config_t {
  std::string bind_host;
  uint16_t bind_port = 0u;
  uint32_t max_frame_bytes = 0u;
  uint32_t initial_width = 1280u;
  uint32_t initial_height = 720u;
  uint32_t fps = 0u;
  std::string recording_directory;
  std::string ffmpeg_binary;
};

struct coakka_v2_camera_web_gateway_snapshot_t {
  uint16_t bound_port = 0u;
  uint64_t received_frames = 0u;
  uint64_t received_bytes = 0u;
  uint64_t display_queue_drops = 0u;
  uint64_t browser_frames = 0u;
  uint64_t browser_bytes = 0u;
  uint64_t recording_frames = 0u;
  uint64_t recording_bytes = 0u;
  uint64_t recording_queue_drops = 0u;
  uint64_t recording_audio_frames = 0u;
  uint64_t recording_audio_bytes = 0u;
  uint64_t recording_audio_queue_drops = 0u;
  std::string recording_path;
};

struct coakka_v2_camera_profile_request_t {
  uint32_t width = 0u;
  uint32_t height = 0u;
  uint32_t fps = 0u;
  uint64_t sequence = 0u;
};

class coakka_v2_camera_web_gateway_t {
public:
  static std::unique_ptr<coakka_v2_camera_web_gateway_t>
  create(const coakka_v2_camera_web_gateway_config_t &config);

  ~coakka_v2_camera_web_gateway_t();
  coakka_v2_camera_web_gateway_t(const coakka_v2_camera_web_gateway_t &) =
      delete;
  coakka_v2_camera_web_gateway_t &
  operator=(const coakka_v2_camera_web_gateway_t &) = delete;

  bool start(std::string *error);
  void stop();

  coakka_v2_status_t consume(const uint8_t *data,
                             const coakka_v2_stream_frame_t *frame) noexcept;
  coakka_v2_status_t
  consume_audio(const uint8_t *data,
                const coakka_v2_stream_frame_t *frame) noexcept;
  void set_transport_state(uint32_t state) noexcept;
  void set_audio_transport_state(uint32_t state) noexcept;
  bool take_profile_request(coakka_v2_camera_profile_request_t *request);
  void complete_profile_request(uint64_t sequence, bool accepted,
                                std::string error);
  bool disconnect_requested() const noexcept;
  coakka_v2_camera_web_gateway_snapshot_t snapshot() const;

private:
  struct impl_t;
  explicit coakka_v2_camera_web_gateway_t(std::unique_ptr<impl_t> impl);
  std::unique_ptr<impl_t> impl_;
};

#endif
