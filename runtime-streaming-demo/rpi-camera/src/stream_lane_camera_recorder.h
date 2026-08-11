#ifndef COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_RECORDER_H
#define COAKKA_V2_EXAMPLES_STREAM_LANE_CAMERA_RECORDER_H

#include "coakka/v2/stream_lane.h"

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

struct coakka_v2_camera_recorder_snapshot_t {
  std::string state = "idle";
  std::string path;
  std::string error;
  uint64_t frames = 0u;
  uint64_t bytes = 0u;
  uint64_t queue_drops = 0u;
  uint64_t audio_frames = 0u;
  uint64_t audio_bytes = 0u;
  uint64_t audio_queue_drops = 0u;
  bool with_audio = false;
};

class coakka_v2_camera_recorder_t {
public:
  coakka_v2_camera_recorder_t(size_t max_frame_bytes, uint32_t fps,
                              std::string directory, std::string ffmpeg_binary);
  ~coakka_v2_camera_recorder_t();

  coakka_v2_camera_recorder_t(const coakka_v2_camera_recorder_t &) = delete;
  coakka_v2_camera_recorder_t &
  operator=(const coakka_v2_camera_recorder_t &) = delete;

  bool start_worker(std::string *error);
  void shutdown();
  bool request_start(bool with_audio, std::string *error);
  bool request_stop(std::string *error);
  void push(const uint8_t *data,
            const coakka_v2_stream_frame_t *frame) noexcept;
  void push_audio(const uint8_t *data,
                  const coakka_v2_stream_frame_t *frame) noexcept;
  coakka_v2_camera_recorder_snapshot_t snapshot() const;

private:
  struct impl_t;
  std::unique_ptr<impl_t> impl_;
};

#endif
