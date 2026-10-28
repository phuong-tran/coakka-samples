#include "stream_lane_camera_recorder.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <cstdio>
#include <ctime>
#include <filesystem>
#include <mutex>
#include <system_error>
#if defined(_WIN32)
#include <io.h>
#include <process.h>
#include <windows.h>
#else
#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>
#endif
#include <string>
#include <thread>
#include <utility>
#include <vector>

#if !defined(_WIN32)
extern char **environ;
#endif

namespace {

constexpr size_t kQueueFrames = 4u;
constexpr size_t kAudioQueueFrames = 16u;
constexpr size_t kAudioFrameBytes = 1920u;
constexpr uint32_t kAudioSampleRate = 48000u;
#if !defined(_WIN32)
constexpr auto kFinalizeTimeout = std::chrono::seconds(15);
constexpr auto kTerminateGrace = std::chrono::seconds(1);
#endif

struct FrameSlot {
  explicit FrameSlot(size_t capacity) : bytes(capacity) {}
  std::vector<uint8_t> bytes;
  size_t size = 0u;
  uint64_t sequence = 0u;
  uint64_t captured_mono_ns = 0u;
};

bool flush_and_close(FILE *file) {
  if (file == nullptr) {
    return true;
  }
  bool ok = std::fflush(file) == 0;
  if (ok) {
#if defined(_WIN32)
    ok = _commit(_fileno(file)) == 0;
#else
    ok = fsync(fileno(file)) == 0;
#endif
  }
  return std::fclose(file) == 0 && ok;
}

std::string timestamp_name() {
  const std::time_t now = std::time(nullptr);
  std::tm value{};
#if defined(_WIN32)
  gmtime_s(&value, &now);
#else
  gmtime_r(&now, &value);
#endif
  std::array<char, 32u> text{};
  (void)std::strftime(text.data(), text.size(), "camera-%Y%m%d-%H%M%S", &value);
  const auto suffix = std::chrono::steady_clock::now().time_since_epoch();
  const auto millis =
      std::chrono::duration_cast<std::chrono::milliseconds>(suffix).count();
  return std::string(text.data()) + "-" + std::to_string(millis % 1000000);
}

int run_process(const std::vector<std::string> &arguments) {
  std::vector<char *> argv;
  argv.reserve(arguments.size() + 1u);
  for (const std::string &argument : arguments) {
    argv.push_back(const_cast<char *>(argument.c_str()));
  }
  argv.push_back(nullptr);
#if defined(_WIN32)
  const intptr_t process = _spawnvp(_P_NOWAIT, argv[0], argv.data());
  if (process == -1) {
    return errno;
  }
  const HANDLE handle = reinterpret_cast<HANDLE>(process);
  const DWORD waited = WaitForSingleObject(handle, 15000u);
  if (waited == WAIT_OBJECT_0) {
    DWORD exit_code = 0u;
    const bool read_exit = GetExitCodeProcess(handle, &exit_code) != 0;
    (void)CloseHandle(handle);
    return read_exit ? static_cast<int>(exit_code) : EIO;
  }
  if (waited == WAIT_TIMEOUT) {
    (void)TerminateProcess(handle, static_cast<UINT>(ETIMEDOUT));
    (void)WaitForSingleObject(handle, 1000u);
    (void)CloseHandle(handle);
    return ETIMEDOUT;
  }
  (void)TerminateProcess(handle, static_cast<UINT>(EIO));
  (void)CloseHandle(handle);
  return EIO;
#else
  pid_t pid = 0;
  const int spawned =
      posix_spawnp(&pid, argv[0], nullptr, nullptr, argv.data(), environ);
  if (spawned != 0) {
    return spawned;
  }
  int status = 0;
  const auto deadline = std::chrono::steady_clock::now() + kFinalizeTimeout;
  for (;;) {
    const pid_t waited = waitpid(pid, &status, WNOHANG);
    if (waited == pid) {
      return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    }
    if (waited < 0 && errno != EINTR) {
      return errno;
    }
    if (std::chrono::steady_clock::now() >= deadline) {
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
  }
  (void)kill(pid, SIGTERM);
  const auto terminate_deadline =
      std::chrono::steady_clock::now() + kTerminateGrace;
  while (std::chrono::steady_clock::now() < terminate_deadline) {
    const pid_t waited = waitpid(pid, &status, WNOHANG);
    if (waited == pid) {
      return ETIMEDOUT;
    }
    if (waited < 0 && errno != EINTR) {
      return errno;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
  }
  (void)kill(pid, SIGKILL);
  while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
  }
  return ETIMEDOUT;
#endif
}

FILE *open_binary_file(const std::filesystem::path &path) {
#if defined(_WIN32)
  return _wfopen(path.c_str(), L"wb");
#else
  return std::fopen(path.c_str(), "wb");
#endif
}

} // namespace

struct coakka_v2_camera_recorder_t::impl_t {
  impl_t(size_t max_frame_bytes, uint32_t frame_rate, std::string directory,
         std::string ffmpeg_binary)
      : fps(frame_rate), recording_directory(std::move(directory)),
        ffmpeg(std::move(ffmpeg_binary)) {
    slots.reserve(kQueueFrames);
    for (size_t index = 0u; index < kQueueFrames; ++index) {
      slots.emplace_back(max_frame_bytes);
    }
    audio_slots.reserve(kAudioQueueFrames);
    for (size_t index = 0u; index < kAudioQueueFrames; ++index) {
      audio_slots.emplace_back(kAudioFrameBytes);
    }
  }

  bool open_recording(FILE **video_file, FILE **audio_file,
                      std::filesystem::path *video_path,
                      std::filesystem::path *audio_path,
                      std::filesystem::path *final_path, bool with_audio,
                      std::string *error) {
    std::error_code ec;
    std::filesystem::create_directories(recording_directory, ec);
    if (ec) {
      *error = "cannot create recording directory: " + ec.message();
      return false;
    }
    const std::string base = timestamp_name();
    *video_path =
        std::filesystem::path(recording_directory) / (base + ".part.mjpeg");
    *audio_path =
        std::filesystem::path(recording_directory) / (base + ".part.s16le");
    *final_path = std::filesystem::path(recording_directory) / (base + ".mkv");
    *video_file = open_binary_file(*video_path);
    if (*video_file == nullptr) {
      *error = "cannot open video recording file";
      return false;
    }
    if (with_audio) {
      *audio_file = open_binary_file(*audio_path);
      if (*audio_file == nullptr) {
        (void)std::fclose(*video_file);
        *video_file = nullptr;
        std::filesystem::remove(*video_path, ec);
        *error = "cannot open audio recording file";
        return false;
      }
    }
    return true;
  }

  bool take_oldest(FrameSlot *destination) {
    std::lock_guard<std::mutex> lock(mutex);
    if (count == 0u) {
      return false;
    }
    FrameSlot &slot = slots[head];
    std::copy_n(slot.bytes.data(), slot.size, destination->bytes.data());
    destination->size = slot.size;
    destination->sequence = slot.sequence;
    destination->captured_mono_ns = slot.captured_mono_ns;
    head = (head + 1u) % slots.size();
    --count;
    return true;
  }

  bool take_oldest_audio(FrameSlot *destination) {
    std::lock_guard<std::mutex> lock(mutex);
    if (audio_count == 0u) {
      return false;
    }
    FrameSlot &slot = audio_slots[audio_head];
    std::copy_n(slot.bytes.data(), slot.size, destination->bytes.data());
    destination->size = slot.size;
    destination->sequence = slot.sequence;
    destination->captured_mono_ns = slot.captured_mono_ns;
    audio_head = (audio_head + 1u) % audio_slots.size();
    --audio_count;
    return true;
  }

  bool should_finish() const {
    std::lock_guard<std::mutex> lock(mutex);
    return (stop_requested || shutdown_requested) && count == 0u &&
           audio_count == 0u;
  }

  void wait_for_frame_or_stop() {
    std::unique_lock<std::mutex> lock(mutex);
    condition.wait(lock, [this] {
      return count != 0u || audio_count != 0u || stop_requested ||
             shutdown_requested;
    });
  }

  bool finalize(const std::filesystem::path &video_path,
                const std::filesystem::path &audio_path,
                const std::filesystem::path &final_path, bool with_audio,
                double frame_rate, std::string *error) const {
    const std::filesystem::path partial = final_path.string() + ".part";
    std::vector<std::string> command{ffmpeg,       "-hide_banner",
                                     "-loglevel",  "error",
                                     "-nostdin",   "-y",
                                     "-f",         "mjpeg",
                                     "-framerate", std::to_string(frame_rate),
                                     "-i",         video_path.string()};
    if (with_audio) {
      command.insert(command.end(),
                     {"-f", "s16le", "-ar", std::to_string(kAudioSampleRate),
                      "-ac", "1", "-i", audio_path.string(), "-c:a", "aac",
                      "-shortest"});
    } else {
      command.push_back("-an");
    }
    command.insert(command.end(),
                   {"-c:v", "copy", "-f", "matroska", partial.string()});
    const int result = run_process(command);
    if (result != 0) {
      *error = result == ETIMEDOUT
                   ? "ffmpeg finalization timed out"
                   : "ffmpeg failed with exit code " + std::to_string(result);
      return false;
    }
    std::error_code ec;
    std::filesystem::rename(partial, final_path, ec);
    if (ec) {
      *error = "cannot publish finalized recording: " + ec.message();
      return false;
    }
    std::filesystem::remove(video_path, ec);
    if (with_audio) {
      std::filesystem::remove(audio_path, ec);
    }
    return true;
  }

  void record_once() {
    FILE *video_file = nullptr;
    FILE *audio_file = nullptr;
    std::filesystem::path video_path;
    std::filesystem::path audio_path;
    std::filesystem::path final_path;
    std::string error;
    bool with_audio = false;
    {
      std::lock_guard<std::mutex> lock(mutex);
      with_audio = snapshot.with_audio;
    }
    if (!open_recording(&video_file, &audio_file, &video_path, &audio_path,
                        &final_path, with_audio, &error)) {
      std::lock_guard<std::mutex> lock(mutex);
      snapshot.state = "failed";
      snapshot.error = std::move(error);
      start_requested = false;
      return;
    }
    {
      std::lock_guard<std::mutex> lock(mutex);
      snapshot.state = "recording";
      snapshot.path = video_path.string();
      accepting = true;
      start_requested = false;
    }

    FrameSlot frame(slots[0].bytes.size());
    FrameSlot audio_frame(audio_slots[0].bytes.size());
    bool write_ok = true;
    while (!should_finish()) {
      bool wrote = false;
      if (take_oldest(&frame)) {
        wrote = true;
        if (std::fwrite(frame.bytes.data(), 1u, frame.size, video_file) !=
            frame.size) {
          write_ok = false;
          break;
        }
        std::lock_guard<std::mutex> lock(mutex);
        if (first_video_mono_ns == 0u) {
          first_video_mono_ns = frame.captured_mono_ns;
        }
        last_video_mono_ns = frame.captured_mono_ns;
        ++snapshot.frames;
        snapshot.bytes += frame.size;
      }
      if (with_audio && take_oldest_audio(&audio_frame)) {
        wrote = true;
        if (std::fwrite(audio_frame.bytes.data(), 1u, audio_frame.size,
                        audio_file) != audio_frame.size) {
          write_ok = false;
          break;
        }
        std::lock_guard<std::mutex> lock(mutex);
        ++snapshot.audio_frames;
        snapshot.audio_bytes += audio_frame.size;
      }
      if (!wrote) {
        wait_for_frame_or_stop();
      }
    }
    {
      std::lock_guard<std::mutex> lock(mutex);
      accepting = false;
      count = 0u;
      head = 0u;
      audio_count = 0u;
      audio_head = 0u;
    }
    write_ok = flush_and_close(video_file) && write_ok;
    write_ok = flush_and_close(audio_file) && write_ok;

    double frame_rate = static_cast<double>(fps);
    {
      std::lock_guard<std::mutex> lock(mutex);
      snapshot.state = write_ok ? "finalizing" : "failed";
      if (!write_ok) {
        snapshot.error = "recording flush or sync failed";
      }
      if (snapshot.frames > 1u && last_video_mono_ns > first_video_mono_ns) {
        frame_rate =
            static_cast<double>(snapshot.frames - 1u) * 1.0e9 /
            static_cast<double>(last_video_mono_ns - first_video_mono_ns);
        if (frame_rate < 1.0 || frame_rate > 120.0) {
          frame_rate = static_cast<double>(fps);
        }
      }
    }
    if (write_ok && finalize(video_path, audio_path, final_path, with_audio,
                             frame_rate, &error)) {
      std::lock_guard<std::mutex> lock(mutex);
      snapshot.state = "completed";
      snapshot.path = final_path.string();
    } else if (write_ok) {
      std::lock_guard<std::mutex> lock(mutex);
      snapshot.state = "failed";
      snapshot.path = video_path.string();
      snapshot.error = std::move(error);
    }
    std::lock_guard<std::mutex> lock(mutex);
    stop_requested = false;
  }

  void run() {
    for (;;) {
      std::unique_lock<std::mutex> lock(mutex);
      condition.wait(lock,
                     [this] { return start_requested || shutdown_requested; });
      if (shutdown_requested && !start_requested) {
        return;
      }
      lock.unlock();
      record_once();
      lock.lock();
      if (shutdown_requested) {
        return;
      }
    }
  }

  uint32_t fps;
  std::string recording_directory;
  std::string ffmpeg;
  mutable std::mutex mutex;
  std::condition_variable condition;
  std::vector<FrameSlot> slots;
  std::vector<FrameSlot> audio_slots;
  size_t head = 0u;
  size_t count = 0u;
  size_t audio_head = 0u;
  size_t audio_count = 0u;
  uint64_t first_video_mono_ns = 0u;
  uint64_t last_video_mono_ns = 0u;
  coakka_v2_camera_recorder_snapshot_t snapshot;
  bool accepting = false;
  bool start_requested = false;
  bool stop_requested = false;
  bool shutdown_requested = false;
  std::thread worker;
};

coakka_v2_camera_recorder_t::coakka_v2_camera_recorder_t(
    size_t max_frame_bytes, uint32_t fps, std::string directory,
    std::string ffmpeg_binary)
    : impl_(std::make_unique<impl_t>(max_frame_bytes, fps, std::move(directory),
                                     std::move(ffmpeg_binary))) {}

coakka_v2_camera_recorder_t::~coakka_v2_camera_recorder_t() { shutdown(); }

bool coakka_v2_camera_recorder_t::start_worker(std::string *error) {
  if (impl_ == nullptr || error == nullptr) {
    return false;
  }
  try {
    impl_->worker = std::thread([impl = impl_.get()] { impl->run(); });
    return true;
  } catch (const std::system_error &failure) {
    *error = "recorder worker start failed: " + std::string(failure.what());
    return false;
  }
}

void coakka_v2_camera_recorder_t::shutdown() {
  if (impl_ == nullptr) {
    return;
  }
  {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    if (!impl_->worker.joinable()) {
      return;
    }
    impl_->accepting = false;
    impl_->stop_requested = true;
    impl_->shutdown_requested = true;
    impl_->condition.notify_one();
  }
  impl_->worker.join();
}

bool coakka_v2_camera_recorder_t::request_start(bool with_audio,
                                                std::string *error) {
  std::lock_guard<std::mutex> lock(impl_->mutex);
  if (impl_->snapshot.state == "starting" ||
      impl_->snapshot.state == "recording" ||
      impl_->snapshot.state == "finalizing") {
    *error = "recording is already active";
    return false;
  }
  impl_->snapshot = {};
  impl_->snapshot.state = "starting";
  impl_->snapshot.with_audio = with_audio;
  impl_->head = 0u;
  impl_->count = 0u;
  impl_->audio_head = 0u;
  impl_->audio_count = 0u;
  impl_->first_video_mono_ns = 0u;
  impl_->last_video_mono_ns = 0u;
  impl_->start_requested = true;
  impl_->stop_requested = false;
  impl_->condition.notify_one();
  return true;
}

bool coakka_v2_camera_recorder_t::request_stop(std::string *error) {
  std::lock_guard<std::mutex> lock(impl_->mutex);
  if (impl_->snapshot.state != "starting" &&
      impl_->snapshot.state != "recording") {
    *error = "recording is not active";
    return false;
  }
  impl_->accepting = false;
  impl_->stop_requested = true;
  impl_->condition.notify_one();
  return true;
}

void coakka_v2_camera_recorder_t::push(
    const uint8_t *data, const coakka_v2_stream_frame_t *frame) noexcept {
  if (data == nullptr || frame == nullptr) {
    return;
  }
  std::lock_guard<std::mutex> lock(impl_->mutex);
  if (!impl_->accepting || frame->size > impl_->slots[0].bytes.size()) {
    return;
  }
  if (impl_->count == impl_->slots.size()) {
    impl_->head = (impl_->head + 1u) % impl_->slots.size();
    --impl_->count;
    ++impl_->snapshot.queue_drops;
  }
  FrameSlot &slot =
      impl_->slots[(impl_->head + impl_->count) % impl_->slots.size()];
  std::copy_n(data, frame->size, slot.bytes.data());
  slot.size = frame->size;
  slot.sequence = frame->sequence;
  slot.captured_mono_ns = frame->captured_mono_ns;
  ++impl_->count;
  impl_->condition.notify_one();
}

void coakka_v2_camera_recorder_t::push_audio(
    const uint8_t *data, const coakka_v2_stream_frame_t *frame) noexcept {
  if (data == nullptr || frame == nullptr) {
    return;
  }
  std::lock_guard<std::mutex> lock(impl_->mutex);
  if (!impl_->accepting || !impl_->snapshot.with_audio || frame->size == 0u ||
      frame->size > impl_->audio_slots[0].bytes.size()) {
    return;
  }
  if (impl_->audio_count == impl_->audio_slots.size()) {
    impl_->audio_head = (impl_->audio_head + 1u) % impl_->audio_slots.size();
    --impl_->audio_count;
    ++impl_->snapshot.audio_queue_drops;
  }
  FrameSlot &slot =
      impl_->audio_slots[(impl_->audio_head + impl_->audio_count) %
                         impl_->audio_slots.size()];
  std::copy_n(data, frame->size, slot.bytes.data());
  slot.size = frame->size;
  slot.sequence = frame->sequence;
  slot.captured_mono_ns = frame->captured_mono_ns;
  ++impl_->audio_count;
  impl_->condition.notify_one();
}

coakka_v2_camera_recorder_snapshot_t
coakka_v2_camera_recorder_t::snapshot() const {
  std::lock_guard<std::mutex> lock(impl_->mutex);
  return impl_->snapshot;
}
