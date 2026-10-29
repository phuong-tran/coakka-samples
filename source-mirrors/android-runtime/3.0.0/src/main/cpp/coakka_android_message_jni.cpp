#include <jni.h>

#include "coakka/v2/client.h"
#include "coakka_android_jni_support.h"

#include <cstdint>
#include <cstring>
#include <limits>

namespace {
void fail(JNIEnv *env, const char *type, const char *message) {
  if (env->ExceptionCheck()) return;
  jclass klass = env->FindClass(type);
  if (klass != nullptr) {
    env->ThrowNew(klass, message);
    env->DeleteLocalRef(klass);
  }
}

bool injected_oom(JNIEnv *env, int point) {
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_message_at(point)) {
    fail(env, "java/lang/OutOfMemoryError", "runtime message publication failed");
    return true;
  }
#else
  (void)env;
  (void)point;
#endif
  return false;
}

// JNI array storage is borrowed synchronously; neither Core nor this wrapper
// retains it. Empty byte arrays need no pin and remain valid empty spans.
class Bytes final {
public:
  Bytes(JNIEnv *env, jbyteArray array) : env_(env), array_(array) {
    if (env_->ExceptionCheck()) return;
    if (array_ == nullptr) {
      fail(env_, "java/lang/IllegalArgumentException", "message byte array is required");
      return;
    }
    size_ = env_->GetArrayLength(array_);
    if (env_->ExceptionCheck() || injected_oom(env_, 1)) return;
    if (size_ != 0) {
      bytes_ = env_->GetByteArrayElements(array_, nullptr);
      if (bytes_ == nullptr) {
        fail(env_, "java/lang/OutOfMemoryError", "runtime message byte borrow failed");
        return;
      }
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
      coakka::android::jni::host_message_borrows.fetch_add(1, std::memory_order_relaxed);
#endif
    }
    valid_ = true;
  }
  ~Bytes() {
    if (bytes_ != nullptr) {
      env_->ReleaseByteArrayElements(array_, bytes_, JNI_ABORT);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
      coakka::android::jni::host_message_borrows.fetch_sub(1, std::memory_order_relaxed);
#endif
    }
  }
  Bytes(const Bytes &) = delete;
  Bytes &operator=(const Bytes &) = delete;
  bool valid() const { return valid_; }
  const uint8_t *data() const { return reinterpret_cast<const uint8_t *>(bytes_); }
  size_t size() const { return static_cast<size_t>(size_); }
  const char *text() const {
    if (!valid_ || size_ == 0 || bytes_[size_ - 1] != 0 ||
        std::memchr(bytes_, 0, static_cast<size_t>(size_ - 1)) != nullptr) {
      fail(env_, "java/lang/IllegalArgumentException", "message text requires one terminal NUL");
      return nullptr;
    }
    return reinterpret_cast<const char *>(bytes_);
  }
private:
  JNIEnv *env_;
  jbyteArray array_;
  jbyte *bytes_ = nullptr;
  jsize size_ = 0;
  bool valid_ = false;
};

struct Output final {
  Output() = default;
  Output(const Output &) = delete;
  Output &operator=(const Output &) = delete;
  uint8_t *data = nullptr;
  size_t size = 0;
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  bool tracked = false;
  void acquired() {
    if (data != nullptr) {
      tracked = true;
      coakka::android::jni::host_message_output_owners.fetch_add(1, std::memory_order_relaxed);
    }
  }
#else
  void acquired() {}
#endif
  ~Output() {
    if (data != nullptr) coakka_v2_client_bytes_release(data);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
    if (tracked) coakka::android::jni::host_message_output_owners.fetch_sub(1, std::memory_order_relaxed);
#endif
  }
};

bool facts_array(JNIEnv *env, jintArray array, jsize count) {
  if (env->ExceptionCheck()) return false;
  if (array == nullptr || env->GetArrayLength(array) != count) {
    fail(env, "java/lang/IllegalArgumentException", "invalid message result extent");
    return false;
  }
  return !env->ExceptionCheck();
}

jbyteArray publish(JNIEnv *env, const Output &value, int point = 2) {
  if (value.size > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
    fail(env, "java/lang/OutOfMemoryError", "runtime message exceeds host-array capacity");
    return nullptr;
  }
  if (value.size != 0 && value.data == nullptr) {
    fail(env, "java/lang/IllegalStateException", "runtime message has no backing storage");
    return nullptr;
  }
  if (injected_oom(env, point)) return nullptr;
  jbyteArray result = env->NewByteArray(static_cast<jsize>(value.size));
  if (result == nullptr) {
    fail(env, "java/lang/OutOfMemoryError", "runtime message array allocation failed");
    return nullptr;
  }
  if (result != nullptr && value.size != 0) {
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(value.size),
                           reinterpret_cast<const jbyte *>(value.data));
  }
  if (env->ExceptionCheck()) {
    if (result != nullptr) env->DeleteLocalRef(result);
    return nullptr;
  }
  return result;
}

jbyteArray complete(JNIEnv *env, jintArray status_array, coakka_v2_status_t status,
                    const Output &output) {
  const jint fact = static_cast<jint>(status);
  env->SetIntArrayRegion(status_array, 0, 1, &fact);
  if (env->ExceptionCheck() || status != COAKKA_V2_OK) return nullptr;
  return publish(env, output);
}
} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeBuildMessageRequest(
    JNIEnv *env, jobject, jbyteArray id, jbyteArray source, jbyteArray target,
    jbyteArray reply_to, jbyteArray payload, jint timeout_ms, jboolean one_way,
    jintArray status_array) {
  if (!facts_array(env, status_array, 1)) return nullptr;
  if (timeout_ms < 0) {
    fail(env, "java/lang/IllegalArgumentException", "message timeout must be non-negative");
    return nullptr;
  }
  Bytes id_bytes(env, id), source_bytes(env, source), target_bytes(env, target),
      reply_bytes(env, reply_to), payload_bytes(env, payload);
  if (!id_bytes.valid() || !source_bytes.valid() || !target_bytes.valid() ||
      !reply_bytes.valid() || !payload_bytes.valid()) return nullptr;
  coakka_v2_client_raw_request_spec_t spec{};
  spec.struct_size = sizeof(spec);
  spec.message_id = id_bytes.text();
  spec.source = source_bytes.text();
  spec.target = target_bytes.text();
  spec.reply_to = reply_bytes.text();
  if (env->ExceptionCheck()) return nullptr;
  spec.payload = payload_bytes.data();
  spec.payload_len = payload_bytes.size();
  spec.timeout_ms = static_cast<uint32_t>(timeout_ms);
  spec.delivery_hint = COAKKA_V2_CLIENT_DELIVERY_HINT_UNSPECIFIED;
  spec.one_way = static_cast<uint32_t>(one_way);
  Output result;
  const auto status = coakka_v2_client_build_raw_request(&spec, &result.data, &result.size);
  result.acquired();
  return complete(env, status_array, status, result);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeBuildMessageReply(
    JNIEnv *env, jobject, jbyteArray request, jbyteArray source, jbyteArray payload,
    jintArray status_array) {
  if (!facts_array(env, status_array, 1)) return nullptr;
  Bytes request_bytes(env, request), source_bytes(env, source), payload_bytes(env, payload);
  if (!request_bytes.valid() || !source_bytes.valid() || !payload_bytes.valid()) return nullptr;
  coakka_v2_client_raw_reply_spec_t spec{};
  spec.struct_size = sizeof(spec);
  spec.request_buf = request_bytes.data();
  spec.request_len = request_bytes.size();
  spec.source = source_bytes.text();
  if (env->ExceptionCheck()) return nullptr;
  spec.payload = payload_bytes.data();
  spec.payload_len = payload_bytes.size();
  Output result;
  const auto status = coakka_v2_client_build_raw_reply(&spec, &result.data, &result.size);
  result.acquired();
  return complete(env, status_array, status, result);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadMessageRequest(
    JNIEnv *env, jobject, jbyteArray envelope, jintArray facts) {
  if (!facts_array(env, facts, 2)) return nullptr;
  Bytes frame(env, envelope);
  if (!frame.valid()) return nullptr;
  Output target, payload;
  uint32_t one_way = 0;
  const auto status = coakka_v2_client_copy_request_dispatch(
      frame.data(), frame.size(), &target.data, &target.size, &payload.data,
      &payload.size, &one_way);
  target.acquired();
  payload.acquired();
  const jint values[] = {static_cast<jint>(status), static_cast<jint>(one_way)};
  env->SetIntArrayRegion(facts, 0, 2, values);
  if (env->ExceptionCheck() || status != COAKKA_V2_OK) return nullptr;
  jclass bytes_class = env->FindClass("[B");
  if (bytes_class == nullptr) return nullptr;
  jobjectArray result = env->NewObjectArray(2, bytes_class, nullptr);
  env->DeleteLocalRef(bytes_class);
  if (result == nullptr) {
    fail(env, "java/lang/OutOfMemoryError", "runtime message result allocation failed");
    return nullptr;
  }
  jbyteArray target_bytes = publish(env, target);
  jbyteArray payload_bytes = env->ExceptionCheck() ? nullptr : publish(env, payload, 3);
  if (!env->ExceptionCheck() && target_bytes != nullptr && payload_bytes != nullptr) {
    env->SetObjectArrayElement(result, 0, target_bytes);
    if (!env->ExceptionCheck() && !injected_oom(env, 4)) env->SetObjectArrayElement(result, 1, payload_bytes);
  }
  if (target_bytes != nullptr) env->DeleteLocalRef(target_bytes);
  if (payload_bytes != nullptr) env->DeleteLocalRef(payload_bytes);
  if (env->ExceptionCheck()) {
    env->DeleteLocalRef(result);
    return nullptr;
  }
  return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeMessageResponsePayload(
    JNIEnv *env, jobject, jbyteArray envelope, jintArray status_array) {
  if (!facts_array(env, status_array, 1)) return nullptr;
  Bytes frame(env, envelope);
  if (!frame.valid()) return nullptr;
  Output result;
  const auto status = coakka_v2_client_copy_envelope_payload(
      frame.data(), frame.size(), COAKKA_V2_CLIENT_ENVELOPE_RESPONSE,
      &result.data, &result.size);
  result.acquired();
  return complete(env, status_array, status, result);
}
