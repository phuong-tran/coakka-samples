#ifndef COAKKA_ANDROID_JNI_SUPPORT_H
#define COAKKA_ANDROID_JNI_SUPPORT_H

#include <jni.h>

#include "coakka/v2/runtime.h"

#include <array>
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
#include <atomic>
#endif
#include <cstddef>
#include <cstdint>

namespace coakka::android::jni {

#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
inline std::atomic<int> host_text_writes_before_failure{-1};

inline void host_fail_text_write_after(int successful_writes) {
  host_text_writes_before_failure.store(successful_writes,
                                        std::memory_order_release);
}

inline bool host_should_fail_text_write() {
  int remaining =
      host_text_writes_before_failure.load(std::memory_order_acquire);
  while (remaining >= 0) {
    const int next = remaining == 0 ? -1 : remaining - 1;
    if (host_text_writes_before_failure.compare_exchange_weak(
            remaining, next, std::memory_order_acq_rel,
            std::memory_order_acquire)) {
      return remaining == 0;
    }
  }
  return false;
}
#endif

class UtfChars final {
public:
  UtfChars(JNIEnv *env, jstring value) : env_(env), value_(value) {
    if (value_ != nullptr) {
      chars_ = env_->GetStringUTFChars(value_, nullptr);
    }
  }

  ~UtfChars() {
    if (chars_ != nullptr) {
      env_->ReleaseStringUTFChars(value_, chars_);
    }
  }

  UtfChars(const UtfChars &) = delete;
  UtfChars &operator=(const UtfChars &) = delete;

  const char *get() const { return chars_; }
  bool valid() const { return value_ == nullptr || chars_ != nullptr; }

private:
  JNIEnv *env_;
  jstring value_;
  const char *chars_ = nullptr;
};

class UtfStringArray final {
public:
  static constexpr jsize kCapacity = 8;

  UtfStringArray(JNIEnv *env, jobjectArray values, jsize expected_count)
      : env_(env), values_(values) {
    if (values_ == nullptr || expected_count < 0 || expected_count > kCapacity ||
        env_->GetArrayLength(values_) != expected_count) {
      return;
    }
    for (jsize index = 0; index < expected_count; ++index) {
      const auto string = static_cast<jstring>(
          env_->GetObjectArrayElement(values_, index));
      if (env_->ExceptionCheck()) {
        return;
      }
      strings_[static_cast<std::size_t>(index)] = string;
      acquired_count_ = index + 1;
      if (string != nullptr) {
        chars_[static_cast<std::size_t>(index)] =
            env_->GetStringUTFChars(string, nullptr);
        if (chars_[static_cast<std::size_t>(index)] == nullptr) {
          return;
        }
      }
    }
    valid_ = true;
  }

  ~UtfStringArray() {
    for (jsize raw_index = 0; raw_index < acquired_count_; ++raw_index) {
      const auto index = static_cast<std::size_t>(raw_index);
      if (chars_[index] != nullptr) {
        env_->ReleaseStringUTFChars(strings_[index], chars_[index]);
      }
      if (strings_[index] != nullptr) {
        env_->DeleteLocalRef(strings_[index]);
      }
    }
  }

  UtfStringArray(const UtfStringArray &) = delete;
  UtfStringArray &operator=(const UtfStringArray &) = delete;

  bool valid() const { return valid_; }
  const char *get(std::size_t index) const {
    return valid_ && index < static_cast<std::size_t>(acquired_count_)
               ? chars_[index]
               : nullptr;
  }

private:
  JNIEnv *env_;
  jobjectArray values_;
  std::array<jstring, static_cast<std::size_t>(kCapacity)> strings_{};
  std::array<const char *, static_cast<std::size_t>(kCapacity)> chars_{};
  jsize acquired_count_ = 0;
  bool valid_ = false;
};

inline bool read_longs(JNIEnv *env, jlongArray source, jsize count,
                       jlong *destination) {
  if (source == nullptr || destination == nullptr ||
      env->GetArrayLength(source) != count) {
    return false;
  }
  env->GetLongArrayRegion(source, 0, count, destination);
  return !env->ExceptionCheck();
}

inline bool write_longs(JNIEnv *env, jlongArray destination, jsize count,
                        const jlong *source) {
  if (destination == nullptr || source == nullptr ||
      env->GetArrayLength(destination) != count) {
    return false;
  }
  env->SetLongArrayRegion(destination, 0, count, source);
  return !env->ExceptionCheck();
}

inline bool write_int(JNIEnv *env, jintArray destination, jint value) {
  if (destination == nullptr || env->GetArrayLength(destination) != 1) {
    return false;
  }
  env->SetIntArrayRegion(destination, 0, 1, &value);
  return !env->ExceptionCheck();
}

inline bool read_digest(JNIEnv *env, jbyteArray source,
                        uint8_t destination[32]) {
  if (source == nullptr || env->GetArrayLength(source) != 32) {
    return false;
  }
  env->GetByteArrayRegion(source, 0, 32,
                          reinterpret_cast<jbyte *>(destination));
  return !env->ExceptionCheck();
}

inline bool write_digest(JNIEnv *env, jbyteArray destination,
                         const uint8_t source[32]) {
  if (destination == nullptr || env->GetArrayLength(destination) != 32) {
    return false;
  }
  env->SetByteArrayRegion(destination, 0, 32,
                          reinterpret_cast<const jbyte *>(source));
  return !env->ExceptionCheck();
}

inline bool write_text(JNIEnv *env, jobjectArray destination, jsize index,
                       const char *source) {
  if (destination == nullptr || index < 0 ||
      index >= env->GetArrayLength(destination)) {
    return false;
  }
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (host_should_fail_text_write()) {
    return false;
  }
#endif
  jstring value = env->NewStringUTF(source == nullptr ? "" : source);
  if (value == nullptr) {
    return false;
  }
  env->SetObjectArrayElement(destination, index, value);
  env->DeleteLocalRef(value);
  return !env->ExceptionCheck();
}

inline bool owner_grants_available() {
  coakka_v2_runtime_info_t info{};
  info.struct_size = sizeof(info);
  return coakka_v2_runtime_get_info(&info) == COAKKA_V2_OK &&
         (info.feature_flags & COAKKA_V2_RUNTIME_FEATURE_LANE_OWNER_GRANTS) !=
             0u;
}

} // namespace coakka::android::jni

#endif
