#include <jni.h>

#include "coakka/v2/stream_lane.h"

#include "coakka_android_jni_support.h"

#include <algorithm>
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
#include <atomic>
#endif
#include <cstdint>
#include <limits>
#include <mutex>
#include <new>
#include <string>
#include <string_view>
#include <unordered_map>

#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
namespace {
std::atomic<size_t> g_host_retained_stream_callbacks{0};
}
#endif

namespace {

using coakka::android::jni::owner_grants_available;
using coakka::android::jni::read_longs;
using coakka::android::jni::UtfChars;
using coakka::android::jni::UtfStringArray;
using coakka::android::jni::write_int;
using coakka::android::jni::write_longs;
using coakka::android::jni::write_text;

constexpr jsize kStreamConfigNumericCount = 17;
constexpr jsize kStreamConfigTextCount = 7;
constexpr jsize kStreamGrantNumericCount = 3;
constexpr jsize kStreamGrantTextCount = 4;
constexpr jsize kStreamSessionNumericCount = 16;
constexpr jsize kStreamSessionTextCount = 1;
constexpr jsize kStreamPressureNumericCount = 16;
constexpr jsize kStreamStatsNumericCount = 18;
constexpr jsize kSourceMetadataCount = 4;
constexpr jsize kConsumerMetadataCount = 4;
constexpr size_t kMaxRetainedCallbacks = 128;

constexpr const char *kCallbackBridgeClass =
    "coakka/v2/android/NativeStreamCallbacks";
constexpr const char *kSourceMethodName = "sourceNext";
constexpr const char *kSourceMethodSignature =
    "(Lcoakka/v2/android/AndroidStreamSource;Ljava/nio/ByteBuffer;[JJ)I";
constexpr const char *kConsumerMethodName = "consume";
constexpr const char *kConsumerMethodSignature =
    "(Lcoakka/v2/android/AndroidStreamConsumer;Ljava/nio/ByteBuffer;[JJ)I";

struct CallbackContext {
  JavaVM *vm = nullptr;
  jobject callback = nullptr;
  jclass bridge_class = nullptr;
  jmethodID method = nullptr;
  jlong lane_handle = 0;
};

struct AndroidStreamLaneHandle {
  coakka_v2_stream_lane_t *lane = nullptr;
  std::mutex callback_mutex;
  std::unordered_map<std::string, CallbackContext> callbacks;
};

class AttachedEnv final {
public:
  explicit AttachedEnv(JavaVM *vm) : vm_(vm) {
    if (vm_ == nullptr) {
      return;
    }
    void *raw = nullptr;
    const jint status = vm_->GetEnv(&raw, JNI_VERSION_1_6);
    if (status == JNI_OK) {
      env_ = static_cast<JNIEnv *>(raw);
      return;
    }
    if (status == JNI_EDETACHED) {
#if defined(__ANDROID__)
      JNIEnv *attached_env = nullptr;
      if (vm_->AttachCurrentThread(&attached_env, nullptr) == JNI_OK) {
        env_ = attached_env;
#else
      void *attached_env = nullptr;
      if (vm_->AttachCurrentThread(&attached_env, nullptr) == JNI_OK) {
        env_ = static_cast<JNIEnv *>(attached_env);
#endif
        attached_ = true;
      }
    }
  }

  ~AttachedEnv() {
    if (attached_) {
      (void)vm_->DetachCurrentThread();
    }
  }

  AttachedEnv(const AttachedEnv &) = delete;
  AttachedEnv &operator=(const AttachedEnv &) = delete;

  JNIEnv *get() const { return env_; }

private:
  JavaVM *vm_ = nullptr;
  JNIEnv *env_ = nullptr;
  bool attached_ = false;
};

AndroidStreamLaneHandle *from_stream_handle(jlong value) {
  return reinterpret_cast<AndroidStreamLaneHandle *>(
      static_cast<uintptr_t>(value));
}

void stop_and_destroy_stream_lane(coakka_v2_stream_lane_t *lane) {
  if (lane == nullptr) {
    return;
  }
  (void)coakka_v2_stream_lane_stop(lane);
  coakka_v2_stream_lane_destroy(lane);
}

bool fits_u32(jlong value) {
  return value >= 0 &&
         static_cast<uint64_t>(value) <=
             static_cast<uint64_t>(std::numeric_limits<uint32_t>::max());
}

bool fits_size(jlong value) {
  return value >= 0 &&
         static_cast<uint64_t>(value) <=
             static_cast<uint64_t>(std::numeric_limits<size_t>::max());
}

bool valid_stream_config(const jlong values[kStreamConfigNumericCount]) {
  if (!fits_u32(values[0]) || values[0] == 0 || values[1] < 0 ||
      values[1] > UINT16_MAX || !fits_size(values[2]) || values[2] > 64 ||
      values[11] < -1 || values[11] > 2 || values[12] < 0) {
    return false;
  }
  for (jsize index = 3; index < kStreamConfigNumericCount; ++index) {
    if (index == 11 || index == 12) {
      continue;
    }
    if (!fits_u32(values[index])) {
      return false;
    }
  }
  return true;
}

std::string callback_key(const char *session_id, uint32_t direction) {
  return std::to_string(direction) + ":" +
         (session_id == nullptr ? "" : session_id);
}

bool make_callback(JNIEnv *env, jobject callback, const char *method_name,
                   const char *method_signature, jlong lane_handle,
                   CallbackContext *out_context) {
  if (env == nullptr || callback == nullptr || lane_handle == 0 ||
      out_context == nullptr) {
    return false;
  }
  JavaVM *vm = nullptr;
  if (env->GetJavaVM(&vm) != JNI_OK) {
    return false;
  }
  jclass local_bridge = env->FindClass(kCallbackBridgeClass);
  if (local_bridge == nullptr) {
    return false;
  }
  jmethodID method =
      env->GetStaticMethodID(local_bridge, method_name, method_signature);
  if (method == nullptr) {
    env->DeleteLocalRef(local_bridge);
    return false;
  }
  jobject callback_global = env->NewGlobalRef(callback);
  jclass bridge_global = static_cast<jclass>(env->NewGlobalRef(local_bridge));
  env->DeleteLocalRef(local_bridge);
  if (callback_global == nullptr || bridge_global == nullptr) {
    if (callback_global != nullptr) {
      env->DeleteGlobalRef(callback_global);
    }
    if (bridge_global != nullptr) {
      env->DeleteGlobalRef(bridge_global);
    }
    return false;
  }
  *out_context =
      CallbackContext{vm, callback_global, bridge_global, method, lane_handle};
  return true;
}

void release_callback(JNIEnv *env, CallbackContext *context) {
  if (env == nullptr || context == nullptr) {
    return;
  }
  if (context->callback != nullptr) {
    env->DeleteGlobalRef(context->callback);
    context->callback = nullptr;
  }
  if (context->bridge_class != nullptr) {
    env->DeleteGlobalRef(context->bridge_class);
    context->bridge_class = nullptr;
  }
}

void release_all_callbacks(JNIEnv *env, AndroidStreamLaneHandle *handle) {
  std::lock_guard<std::mutex> lock(handle->callback_mutex);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  g_host_retained_stream_callbacks.fetch_sub(handle->callbacks.size(),
                                             std::memory_order_relaxed);
#endif
  for (auto &[key, callback] : handle->callbacks) {
    (void)key;
    release_callback(env, &callback);
  }
  handle->callbacks.clear();
}

coakka_v2_status_t insert_callback(AndroidStreamLaneHandle *handle,
                                   const std::string &key,
                                   CallbackContext *context,
                                   CallbackContext **out_registered) {
  if (handle == nullptr || context == nullptr || out_registered == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  *out_registered = nullptr;
  std::lock_guard<std::mutex> lock(handle->callback_mutex);
  if (handle->callbacks.size() >= kMaxRetainedCallbacks ||
      handle->callbacks.find(key) != handle->callbacks.end()) {
    return COAKKA_V2_ERR_BAD_STATE;
  }
  try {
    const auto [entry, inserted] = handle->callbacks.emplace(key, *context);
    if (!inserted) {
      return COAKKA_V2_ERR_BAD_STATE;
    }
    // Rehash keeps element addresses stable. Core borrows this address until
    // terminal forget or lane stop/destroy releases the bounded map.
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
    g_host_retained_stream_callbacks.fetch_add(1, std::memory_order_relaxed);
#endif
    *out_registered = &entry->second;
    *context = CallbackContext{};
    return COAKKA_V2_OK;
  } catch (const std::bad_alloc &) {
    return COAKKA_V2_ERR_NOMEM;
  }
}

void erase_callback(JNIEnv *env, AndroidStreamLaneHandle *handle,
                    const std::string &key) {
  std::lock_guard<std::mutex> lock(handle->callback_mutex);
  const auto found = handle->callbacks.find(key);
  if (found == handle->callbacks.end()) {
    return;
  }
  release_callback(env, &found->second);
  handle->callbacks.erase(found);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  g_host_retained_stream_callbacks.fetch_sub(1, std::memory_order_relaxed);
#endif
}

void erase_callback(JNIEnv *env, AndroidStreamLaneHandle *handle,
                    const char *session_id, uint32_t direction) {
  if (session_id == nullptr || direction > 9) {
    return;
  }
  const std::string_view session(session_id);
  const char direction_digit = static_cast<char>('0' + direction);
  std::lock_guard<std::mutex> lock(handle->callback_mutex);
  const auto found = std::find_if(
      handle->callbacks.begin(), handle->callbacks.end(),
      [session, direction_digit](const auto &entry) {
        return entry.first.size() == session.size() + 2 &&
               entry.first[0] == direction_digit && entry.first[1] == ':' &&
               entry.first.compare(2, session.size(), session) == 0;
      });
  if (found == handle->callbacks.end()) {
    return;
  }
  release_callback(env, &found->second);
  handle->callbacks.erase(found);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  g_host_retained_stream_callbacks.fetch_sub(1, std::memory_order_relaxed);
#endif
}

coakka_v2_status_t source_next(void *raw_context, uint8_t *destination,
                               size_t capacity,
                               coakka_v2_stream_frame_t *out_frame) {
  auto *context = static_cast<CallbackContext *>(raw_context);
  if (context == nullptr || destination == nullptr || out_frame == nullptr ||
      capacity == 0 ||
      capacity > static_cast<size_t>(std::numeric_limits<jlong>::max())) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  AttachedEnv attached(context->vm);
  JNIEnv *env = attached.get();
  if (env == nullptr) {
    return COAKKA_V2_ERR_IO;
  }
  jobject buffer =
      env->NewDirectByteBuffer(destination, static_cast<jlong>(capacity));
  jlongArray metadata = env->NewLongArray(kSourceMetadataCount);
  if (buffer == nullptr || metadata == nullptr) {
    if (buffer != nullptr)
      env->DeleteLocalRef(buffer);
    if (metadata != nullptr)
      env->DeleteLocalRef(metadata);
    return COAKKA_V2_ERR_NOMEM;
  }
  const jint status = env->CallStaticIntMethod(
      context->bridge_class, context->method, context->callback, buffer,
      metadata, context->lane_handle);
  jlong values[kSourceMetadataCount]{};
  if (!env->ExceptionCheck() && status == COAKKA_V2_OK) {
    env->GetLongArrayRegion(metadata, 0, kSourceMetadataCount, values);
  }
  const bool failed = env->ExceptionCheck();
  if (failed) {
    env->ExceptionClear();
  }
  env->DeleteLocalRef(metadata);
  env->DeleteLocalRef(buffer);
  if (failed) {
    return COAKKA_V2_ERR_IO;
  }
  if (status != COAKKA_V2_OK) {
    return static_cast<coakka_v2_status_t>(status);
  }
  if (values[0] < 0 || values[1] < 0 || !fits_u32(values[2]) ||
      values[3] <= 0 || static_cast<uint64_t>(values[3]) > capacity) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  out_frame->captured_mono_ns = static_cast<uint64_t>(values[0]);
  out_frame->dropped_before = static_cast<uint64_t>(values[1]);
  out_frame->flags = static_cast<uint32_t>(values[2]);
  out_frame->size = static_cast<size_t>(values[3]);
  return COAKKA_V2_OK;
}

coakka_v2_status_t consume_frame(void *raw_context, const uint8_t *data,
                                 const coakka_v2_stream_frame_t *frame) {
  auto *context = static_cast<CallbackContext *>(raw_context);
  if (context == nullptr || data == nullptr || frame == nullptr ||
      frame->size == 0 ||
      frame->size > static_cast<size_t>(std::numeric_limits<jlong>::max())) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  AttachedEnv attached(context->vm);
  JNIEnv *env = attached.get();
  if (env == nullptr) {
    return COAKKA_V2_ERR_IO;
  }
  jobject buffer = env->NewDirectByteBuffer(const_cast<uint8_t *>(data),
                                            static_cast<jlong>(frame->size));
  const jlong metadata_values[kConsumerMetadataCount] = {
      static_cast<jlong>(frame->sequence),
      static_cast<jlong>(frame->captured_mono_ns),
      static_cast<jlong>(frame->dropped_before),
      static_cast<jlong>(frame->flags),
  };
  jlongArray metadata = env->NewLongArray(kConsumerMetadataCount);
  if (buffer == nullptr || metadata == nullptr ||
      !write_longs(env, metadata, kConsumerMetadataCount, metadata_values)) {
    if (buffer != nullptr)
      env->DeleteLocalRef(buffer);
    if (metadata != nullptr)
      env->DeleteLocalRef(metadata);
    return COAKKA_V2_ERR_NOMEM;
  }
  const jint status = env->CallStaticIntMethod(
      context->bridge_class, context->method, context->callback, buffer,
      metadata, context->lane_handle);
  const bool failed = env->ExceptionCheck();
  if (failed) {
    env->ExceptionClear();
  }
  env->DeleteLocalRef(metadata);
  env->DeleteLocalRef(buffer);
  return failed ? COAKKA_V2_ERR_IO : static_cast<coakka_v2_status_t>(status);
}

coakka_v2_status_t
fill_session(JNIEnv *env, const coakka_v2_stream_session_snapshot_t &snapshot,
             jlongArray numeric, jobjectArray text) {
  const jlong values[kStreamSessionNumericCount] = {
      static_cast<jlong>(snapshot.direction),
      static_cast<jlong>(snapshot.state),
      static_cast<jlong>(snapshot.result),
      static_cast<jlong>(snapshot.format_id),
      static_cast<jlong>(snapshot.frames),
      static_cast<jlong>(snapshot.bytes),
      static_cast<jlong>(snapshot.dropped_frames),
      static_cast<jlong>(snapshot.last_sequence),
      static_cast<jlong>(snapshot.negotiated_max_frame_bytes),
      static_cast<jlong>(snapshot.window_bytes),
      static_cast<jlong>(snapshot.cancel_requested),
      static_cast<jlong>(snapshot.update_sequence),
      static_cast<jlong>(snapshot.submitted_mono_ns),
      static_cast<jlong>(snapshot.started_mono_ns),
      static_cast<jlong>(snapshot.updated_mono_ns),
      static_cast<jlong>(snapshot.terminal_mono_ns),
  };
  if (!write_longs(env, numeric, kStreamSessionNumericCount, values) ||
      text == nullptr || env->GetArrayLength(text) != kStreamSessionTextCount ||
      !write_text(env, text, 0, snapshot.detail)) {
    return COAKKA_V2_ERR_NOMEM;
  }
  return COAKKA_V2_OK;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneCreate(
    JNIEnv *env, jobject, jlongArray numeric, jobjectArray text,
    jboolean owner_aware, jintArray out_status) {
  jlong values[kStreamConfigNumericCount]{};
  UtfStringArray strings(env, text, kStreamConfigTextCount);
  if (!read_longs(env, numeric, kStreamConfigNumericCount, values) ||
      !strings.valid() || !valid_stream_config(values)) {
    (void)write_int(env, out_status, COAKKA_V2_ERR_INVALID_ARG);
    return 0;
  }

  coakka_v2_stream_lane_security_config_t security{};
  coakka_v2_stream_lane_security_config_t *security_pointer = nullptr;
  if (values[11] >= 0) {
    security.struct_size = sizeof(security);
    security.mode = static_cast<uint32_t>(values[11]);
    security.credential_generation = static_cast<uint64_t>(values[12]);
    security.credential_id = strings.get(1);
    security.ca_certificate_file = strings.get(2);
    security.identity_certificate_file = strings.get(3);
    security.private_key_file = strings.get(4);
    security_pointer = &security;
  }

  coakka_v2_stream_lane_config_t config{};
  config.struct_size = sizeof(config);
  config.flags = static_cast<uint32_t>(values[0]);
  config.bind_host = strings.get(0);
  config.bind_port = static_cast<uint16_t>(values[1]);
  config.capacity = static_cast<size_t>(values[2]);
  config.max_frame_bytes = static_cast<uint32_t>(values[3]);
  config.max_window_bytes = static_cast<uint32_t>(values[4]);
  config.io_timeout_ms = static_cast<uint32_t>(values[5]);
  config.source_retry_ms = static_cast<uint32_t>(values[6]);
  config.progress_frames = static_cast<uint32_t>(values[7]);
  config.progress_interval_ms = static_cast<uint32_t>(values[8]);
  config.publisher_worker_count = static_cast<uint32_t>(values[9]);
  config.subscriber_worker_count = static_cast<uint32_t>(values[10]);
  config.security = security_pointer;
  config.pressure_after_ms = static_cast<uint32_t>(values[13]);
  config.stalled_after_ms = static_cast<uint32_t>(values[14]);
  config.recovery_after_ms = static_cast<uint32_t>(values[15]);
  config.pressure_observation_ms = static_cast<uint32_t>(values[16]);

  coakka_v2_stream_lane_t *lane = nullptr;
  coakka_v2_status_t status = COAKKA_V2_OK;
  if (owner_aware == JNI_TRUE) {
    if (!owner_grants_available()) {
      status = COAKKA_V2_ERR_FEATURE_UNAVAILABLE;
    } else {
      coakka_v2_stream_lane_owned_config_t owned{};
      owned.struct_size = sizeof(owned);
      owned.lane = config;
      owned.owner.struct_size = sizeof(owned.owner);
      owned.owner.owner_instance_id = strings.get(5);
      owned.owner.advertised_host = strings.get(6);
      status = coakka_v2_stream_lane_create_owned_ex(&owned, &lane);
    }
  } else {
    status = coakka_v2_stream_lane_create_ex(&config, &lane);
  }
  if (status == COAKKA_V2_OK) {
    status = coakka_v2_stream_lane_start(lane);
  }
  if (status != COAKKA_V2_OK) {
    stop_and_destroy_stream_lane(lane);
    (void)write_int(env, out_status, status);
    return 0;
  }

  auto *handle = new (std::nothrow) AndroidStreamLaneHandle();
  if (handle == nullptr) {
    stop_and_destroy_stream_lane(lane);
    (void)write_int(env, out_status, COAKKA_V2_ERR_NOMEM);
    return 0;
  }
  handle->lane = lane;
  if (!write_int(env, out_status, COAKKA_V2_OK)) {
    stop_and_destroy_stream_lane(lane);
    delete handle;
    return 0;
  }
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneStop(
    JNIEnv *, jobject, jlong raw_handle) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  return handle == nullptr || handle->lane == nullptr
             ? COAKKA_V2_ERR_INVALID_ARG
             : coakka_v2_stream_lane_stop(handle->lane);
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneDestroy(
    JNIEnv *env, jobject, jlong raw_handle) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  if (handle == nullptr) {
    return;
  }
  coakka_v2_stream_lane_destroy(handle->lane);
  handle->lane = nullptr;
  release_all_callbacks(env, handle);
  delete handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneBoundPort(
    JNIEnv *env, jobject, jlong raw_handle, jintArray out_port) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  if (handle == nullptr || handle->lane == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  uint16_t port = 0;
  const coakka_v2_status_t status =
      coakka_v2_stream_lane_get_bound_port(handle->lane, &port);
  return status != COAKKA_V2_OK || write_int(env, out_port, port)
             ? status
             : COAKKA_V2_ERR_NOMEM;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLanePreparePublish(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id,
    jstring authorization_token, jlong format_id, jint max_frame_bytes,
    jobject source, jlongArray out_numeric, jobjectArray out_text) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  UtfChars token(env, authorization_token);
  const bool with_grant = out_numeric != nullptr || out_text != nullptr;
  if (handle == nullptr || handle->lane == nullptr || !session.valid() ||
      !token.valid() || session.get() == nullptr || token.get() == nullptr ||
      format_id <= 0 || max_frame_bytes <= 0 || source == nullptr ||
      (with_grant &&
       (out_numeric == nullptr || out_text == nullptr ||
        env->GetArrayLength(out_numeric) != kStreamGrantNumericCount ||
        env->GetArrayLength(out_text) != kStreamGrantTextCount))) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  std::string key;
  try {
    key = callback_key(session.get(), COAKKA_V2_STREAM_DIRECTION_PUBLISH);
  } catch (const std::bad_alloc &) {
    return COAKKA_V2_ERR_NOMEM;
  }
  CallbackContext context{};
  if (!make_callback(env, source, kSourceMethodName, kSourceMethodSignature,
                     raw_handle, &context)) {
    if (env->ExceptionCheck())
      env->ExceptionClear();
    return COAKKA_V2_ERR_NOMEM;
  }
  CallbackContext *registered = nullptr;
  const coakka_v2_status_t insert_status =
      insert_callback(handle, key, &context, &registered);
  if (insert_status != COAKKA_V2_OK) {
    release_callback(env, &context);
    return insert_status;
  }

  coakka_v2_stream_publish_spec_t spec{};
  spec.struct_size = sizeof(spec);
  spec.session_id = session.get();
  spec.authorization_token = token.get();
  spec.format_id = static_cast<uint64_t>(format_id);
  spec.max_frame_bytes = static_cast<uint32_t>(max_frame_bytes);
  spec.source_next = source_next;
  spec.source_context = registered;

  coakka_v2_status_t status = COAKKA_V2_OK;
  coakka_v2_stream_publish_grant_t grant{};
  if (with_grant) {
    grant.struct_size = sizeof(grant);
    status = coakka_v2_stream_lane_prepare_publish_grant(handle->lane, &spec,
                                                         &grant);
  } else {
    status = coakka_v2_stream_lane_prepare_publish(handle->lane, &spec);
  }
  if (status != COAKKA_V2_OK) {
    erase_callback(env, handle, key);
    return status;
  }
  if (!with_grant) {
    return COAKKA_V2_OK;
  }
  const jlong values[kStreamGrantNumericCount] = {
      static_cast<jlong>(grant.owner.port),
      static_cast<jlong>(grant.format_id),
      static_cast<jlong>(grant.max_frame_bytes),
  };
  if (write_longs(env, out_numeric, kStreamGrantNumericCount, values) &&
      write_text(env, out_text, 0, grant.owner.owner_instance_id) &&
      write_text(env, out_text, 1, grant.owner.advertised_host) &&
      write_text(env, out_text, 2, grant.session_id) &&
      write_text(env, out_text, 3, grant.authorization_token)) {
    return COAKKA_V2_OK;
  }
  (void)coakka_v2_stream_lane_cancel_session(
      handle->lane, session.get(), COAKKA_V2_STREAM_DIRECTION_PUBLISH);
  // The pinned Core transitions PREPARED cancellation synchronously, so this
  // forget must succeed after a local grant-projection failure. If a future
  // Core changes that contract, retain the callback until lane stop/destroy;
  // Core may still borrow it while the session record remains live.
  if (coakka_v2_stream_lane_forget_session(
          handle->lane, session.get(), COAKKA_V2_STREAM_DIRECTION_PUBLISH) ==
      COAKKA_V2_OK) {
    erase_callback(env, handle, key);
  }
  return COAKKA_V2_ERR_NOMEM;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneSubscribe(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id,
    jstring authorization_token, jstring remote_host, jint remote_port,
    jlong format_id, jint max_frame_bytes, jint initial_window_bytes,
    jint timeout_ms, jobject consumer) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  UtfChars token(env, authorization_token);
  UtfChars remote(env, remote_host);
  if (handle == nullptr || handle->lane == nullptr || !session.valid() ||
      !token.valid() || !remote.valid() || session.get() == nullptr ||
      token.get() == nullptr || remote.get() == nullptr || remote_port <= 0 ||
      remote_port > UINT16_MAX || format_id <= 0 || max_frame_bytes <= 0 ||
      initial_window_bytes <= 0 || timeout_ms < 0 || consumer == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  std::string key;
  try {
    key = callback_key(session.get(), COAKKA_V2_STREAM_DIRECTION_SUBSCRIBE);
  } catch (const std::bad_alloc &) {
    return COAKKA_V2_ERR_NOMEM;
  }
  CallbackContext context{};
  if (!make_callback(env, consumer, kConsumerMethodName,
                     kConsumerMethodSignature, raw_handle, &context)) {
    if (env->ExceptionCheck())
      env->ExceptionClear();
    return COAKKA_V2_ERR_NOMEM;
  }
  CallbackContext *registered = nullptr;
  const coakka_v2_status_t insert_status =
      insert_callback(handle, key, &context, &registered);
  if (insert_status != COAKKA_V2_OK) {
    release_callback(env, &context);
    return insert_status;
  }

  coakka_v2_stream_subscribe_spec_t spec{};
  spec.struct_size = sizeof(spec);
  spec.session_id = session.get();
  spec.authorization_token = token.get();
  spec.remote_host = remote.get();
  spec.remote_port = static_cast<uint16_t>(remote_port);
  spec.format_id = static_cast<uint64_t>(format_id);
  spec.max_frame_bytes = static_cast<uint32_t>(max_frame_bytes);
  spec.initial_window_bytes = static_cast<uint32_t>(initial_window_bytes);
  spec.timeout_ms = static_cast<uint32_t>(timeout_ms);
  spec.consume = consume_frame;
  spec.consumer_context = registered;
  const coakka_v2_status_t status =
      coakka_v2_stream_lane_subscribe(handle->lane, &spec);
  if (status != COAKKA_V2_OK) {
    erase_callback(env, handle, key);
  }
  return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneSession(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id, jint direction,
    jlong after_update_sequence, jint timeout_ms, jboolean wait,
    jlongArray out_numeric, jobjectArray out_text) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  if (handle == nullptr || handle->lane == nullptr || !session.valid() ||
      session.get() == nullptr || after_update_sequence < 0 || timeout_ms < 0) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_stream_session_snapshot_t snapshot{};
  snapshot.struct_size = sizeof(snapshot);
  const coakka_v2_status_t status =
      wait == JNI_TRUE
          ? coakka_v2_stream_lane_wait_session(
                handle->lane, session.get(), static_cast<uint32_t>(direction),
                static_cast<uint64_t>(after_update_sequence),
                static_cast<uint32_t>(timeout_ms), &snapshot)
          : coakka_v2_stream_lane_get_session(handle->lane, session.get(),
                                              static_cast<uint32_t>(direction),
                                              &snapshot);
  return status == COAKKA_V2_OK
             ? fill_session(env, snapshot, out_numeric, out_text)
             : status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLanePressure(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id, jint direction,
    jlong after_update_sequence, jint timeout_ms, jboolean wait,
    jlongArray out_numeric) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  if (handle == nullptr || handle->lane == nullptr || !session.valid() ||
      session.get() == nullptr || after_update_sequence < 0 || timeout_ms < 0) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_stream_pressure_snapshot_t snapshot{};
  snapshot.struct_size = sizeof(snapshot);
  const coakka_v2_status_t status =
      wait == JNI_TRUE
          ? coakka_v2_stream_lane_wait_pressure(
                handle->lane, session.get(), static_cast<uint32_t>(direction),
                static_cast<uint64_t>(after_update_sequence),
                static_cast<uint32_t>(timeout_ms), &snapshot)
          : coakka_v2_stream_lane_get_pressure(handle->lane, session.get(),
                                               static_cast<uint32_t>(direction),
                                               &snapshot);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong values[kStreamPressureNumericCount] = {
      static_cast<jlong>(snapshot.direction),
      static_cast<jlong>(snapshot.state),
      static_cast<jlong>(snapshot.reason_bits),
      static_cast<jlong>(snapshot.available_credit_bytes),
      static_cast<jlong>(snapshot.window_capacity_bytes),
      static_cast<jlong>(snapshot.update_sequence),
      static_cast<jlong>(snapshot.transition_count),
      static_cast<jlong>(snapshot.observed_mono_ns),
      static_cast<jlong>(snapshot.state_started_mono_ns),
      static_cast<jlong>(snapshot.pressure_started_mono_ns),
      static_cast<jlong>(snapshot.last_progress_mono_ns),
      static_cast<jlong>(snapshot.observed_delivery_bps),
      static_cast<jlong>(snapshot.current_operation_ns),
      static_cast<jlong>(snapshot.last_operation_ns),
      static_cast<jlong>(snapshot.total_pressured_ns),
      static_cast<jlong>(snapshot.max_pressured_ns),
  };
  return write_longs(env, out_numeric, kStreamPressureNumericCount, values)
             ? COAKKA_V2_OK
             : COAKKA_V2_ERR_NOMEM;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneCancel(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id,
    jint direction) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  return handle == nullptr || handle->lane == nullptr || !session.valid() ||
                 session.get() == nullptr
             ? COAKKA_V2_ERR_INVALID_ARG
             : coakka_v2_stream_lane_cancel_session(
                   handle->lane, session.get(),
                   static_cast<uint32_t>(direction));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneForget(
    JNIEnv *env, jobject, jlong raw_handle, jstring session_id,
    jint direction) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  UtfChars session(env, session_id);
  if (handle == nullptr || handle->lane == nullptr || !session.valid() ||
      session.get() == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  const coakka_v2_status_t status = coakka_v2_stream_lane_forget_session(
      handle->lane, session.get(), static_cast<uint32_t>(direction));
  if (status == COAKKA_V2_OK) {
    erase_callback(env, handle, session.get(),
                   static_cast<uint32_t>(direction));
  }
  return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStreamLaneStats(
    JNIEnv *env, jobject, jlong raw_handle, jlongArray out_numeric) {
  AndroidStreamLaneHandle *handle = from_stream_handle(raw_handle);
  if (handle == nullptr || handle->lane == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_stream_lane_stats_t stats{};
  stats.struct_size = sizeof(stats);
  const coakka_v2_status_t status =
      coakka_v2_stream_lane_get_stats(handle->lane, &stats);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong values[kStreamStatsNumericCount] = {
      static_cast<jlong>(stats.capacity),
      static_cast<jlong>(stats.queued_subscribers),
      static_cast<jlong>(stats.prepared_publishers),
      static_cast<jlong>(stats.active_publishers),
      static_cast<jlong>(stats.active_subscribers),
      static_cast<jlong>(stats.retained_records),
      static_cast<jlong>(stats.submitted_subscribers),
      static_cast<jlong>(stats.prepared_publisher_count),
      static_cast<jlong>(stats.ended_publishers),
      static_cast<jlong>(stats.ended_subscribers),
      static_cast<jlong>(stats.failed_publishers),
      static_cast<jlong>(stats.failed_subscribers),
      static_cast<jlong>(stats.canceled_sessions),
      static_cast<jlong>(stats.published_frames),
      static_cast<jlong>(stats.published_bytes),
      static_cast<jlong>(stats.consumed_frames),
      static_cast<jlong>(stats.consumed_bytes),
      static_cast<jlong>(stats.source_reported_drops),
  };
  return write_longs(env, out_numeric, kStreamStatsNumericCount, values)
             ? COAKKA_V2_OK
             : COAKKA_V2_ERR_NOMEM;
}

#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
extern "C" JNIEXPORT jlong JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeRetainedStreamCallbacks(
    JNIEnv *, jobject) {
  return static_cast<jlong>(
      g_host_retained_stream_callbacks.load(std::memory_order_relaxed));
}
#endif
