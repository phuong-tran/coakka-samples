#include "stream_lane_camera_web_gateway.h"

#include <atomic>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <iostream>
#include <string>
#include <thread>

int main() {
  const std::filesystem::path recordings =
      std::filesystem::temp_directory_path() /
      "coakka-camera-gateway-lifecycle-test";
  std::error_code filesystem_error;
  std::filesystem::remove_all(recordings, filesystem_error);

  coakka_v2_camera_web_gateway_config_t config;
  config.bind_host = "127.0.0.1";
  config.bind_port = 0u;
  config.max_frame_bytes = 4096u;
  config.initial_width = 1u;
  config.initial_height = 1u;
  config.fps = 30u;
  config.recording_directory = recordings.string();
  config.ffmpeg_binary = "ffmpeg-not-invoked-by-lifecycle-test";

  auto gateway = coakka_v2_camera_web_gateway_t::create(config);
  if (gateway == nullptr) {
    std::cerr << "gateway create failed\n";
    return 1;
  }
  std::string error;
  if (!gateway->start(&error)) {
    std::cerr << "gateway start failed: " << error << '\n';
    return 1;
  }

  constexpr uint8_t jpeg[] = {0xffu, 0xd8u, 0xffu, 0xd9u};
  std::atomic<bool> run{true};
  std::atomic<uint64_t> accepted{0u};
  std::thread producer([&] {
    coakka_v2_stream_frame_t frame{};
    frame.size = sizeof(jpeg);
    frame.flags = COAKKA_V2_STREAM_LANE_FRAME_FLAG_KEYFRAME;
    while (run.load(std::memory_order_acquire)) {
      frame.sequence = accepted.load(std::memory_order_relaxed) + 1u;
      frame.captured_mono_ns = frame.sequence;
      if (gateway->consume(jpeg, &frame) != COAKKA_V2_OK) {
        std::cerr << "frame admission failed\n";
        run.store(false, std::memory_order_release);
        return;
      }
      accepted.fetch_add(1u, std::memory_order_relaxed);
    }
  });

  std::this_thread::sleep_for(std::chrono::milliseconds(25));
  gateway->stop();
  run.store(false, std::memory_order_release);
  producer.join();
  gateway->stop();

  const auto snapshot = gateway->snapshot();
  std::filesystem::remove_all(recordings, filesystem_error);
  if (accepted.load(std::memory_order_relaxed) == 0u ||
      snapshot.received_frames == 0u) {
    std::cerr << "producer did not cross the gateway lifecycle\n";
    return 1;
  }
  return 0;
}
