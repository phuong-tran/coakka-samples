#include <jni.h>

#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/transport.h"

#include <cstdint>
#include <mutex>
#include <new>
#include <unistd.h>

namespace {

constexpr jint kHostHandleLayoutVersion = 1;
constexpr jsize kHostHandleValueCount = 7;
constexpr jsize kHealthValueCount = 3;
constexpr jsize kRuntimeInfoNumericCount = 3;
constexpr jsize kRuntimeInfoTextCount = 4;

struct AndroidRuntimeHandle {
  std::mutex lifecycle_mutex;
  coakka_v2_runtime_t *runtime = nullptr;
  bool started = false;
};

AndroidRuntimeHandle *from_handle(jlong value) {
  return reinterpret_cast<AndroidRuntimeHandle *>(
      static_cast<uintptr_t>(value));
}

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

void close_exported_handles(coakka_v2_host_handles_t *handles) {
  if (handles == nullptr) {
    return;
  }
  int *const fds[] = {
      &handles->request_write_fd,   &handles->response_read_fd,
      &handles->deadletter_read_fd, &handles->control_write_fd,
      &handles->monitor_read_fd,    &handles->delivered_request_read_fd,
  };
  for (int *fd : fds) {
    if (*fd >= 0) {
      close(*fd);
      *fd = -1;
    }
  }
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeAbiVersion(JNIEnv *, jobject) {
  return static_cast<jint>(coakka_v2_runtime_get_abi_version());
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadRuntimeInfo(
    JNIEnv *env, jobject, jlongArray numeric, jobjectArray text) {
  if (numeric == nullptr || text == nullptr ||
      env->GetArrayLength(numeric) != kRuntimeInfoNumericCount ||
      env->GetArrayLength(text) != kRuntimeInfoTextCount) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }

  coakka_v2_runtime_info_t info{};
  info.struct_size = sizeof(info);
  const coakka_v2_status_t status = coakka_v2_runtime_get_info(&info);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong numeric_values[kRuntimeInfoNumericCount] = {
      static_cast<jlong>(info.abi_version),
      static_cast<jlong>(info.feature_flags),
      static_cast<jlong>(info.remote_wire_profile_version),
  };
  env->SetLongArrayRegion(numeric, 0, kRuntimeInfoNumericCount,
                          numeric_values);
  if (env->ExceptionCheck()) {
    return COAKKA_V2_ERR_NOMEM;
  }

  const char *const text_values[kRuntimeInfoTextCount] = {
      info.runtime_version,
      info.git_commit,
      info.southbound_backend,
      info.build_id,
  };
  for (jsize index = 0; index < kRuntimeInfoTextCount; ++index) {
    jstring value = env->NewStringUTF(text_values[index] == nullptr
                                          ? ""
                                          : text_values[index]);
    if (value == nullptr) {
      return COAKKA_V2_ERR_NOMEM;
    }
    env->SetObjectArrayElement(text, index, value);
    env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) {
      return COAKKA_V2_ERR_NOMEM;
    }
  }
  return COAKKA_V2_OK;
}

extern "C" JNIEXPORT jlong JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeCreate(
    JNIEnv *env, jobject, jstring system_name, jstring node_id,
    jint queue_capacity, jboolean strict_no_drop) {
  UtfChars system_chars(env, system_name);
  UtfChars node_chars(env, node_id);
  if (!system_chars.valid() || !node_chars.valid() ||
      system_chars.get() == nullptr || node_chars.get() == nullptr ||
      queue_capacity <= 0) {
    return 0;
  }

  auto *handle = new (std::nothrow) AndroidRuntimeHandle();
  if (handle == nullptr) {
    return 0;
  }
  coakka_v2_runtime_config_t config{};
  config.system_name = system_chars.get();
  config.node_id = node_chars.get();
  config.queue_capacity = queue_capacity;
  config.strict_no_drop = strict_no_drop == JNI_TRUE ? 1 : 0;
  handle->runtime = coakka_v2_runtime_create(&config);
  if (handle->runtime == nullptr) {
    delete handle;
    return 0;
  }
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeApplyNetwork(
    JNIEnv *env, jobject, jlong raw_handle, jint mode, jstring bind_host,
    jint bind_port, jstring advertise_host, jint advertise_port) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr || bind_port < 0 ||
      bind_port > UINT16_MAX || advertise_port < 0 ||
      advertise_port > UINT16_MAX) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  UtfChars bind_chars(env, bind_host);
  UtfChars advertise_chars(env, advertise_host);
  if (!bind_chars.valid() || !advertise_chars.valid()) {
    return COAKKA_V2_ERR_NOMEM;
  }

  coakka_v2_network_options_t options{};
  options.struct_size = sizeof(options);
  options.fields = COAKKA_V2_NETWORK_FIELD_MODE;
  options.mode = static_cast<uint32_t>(mode);
  if (mode == COAKKA_V2_NETWORK_NODE) {
    options.fields = COAKKA_V2_NETWORK_ALL_FIELDS;
    options.bind_host = bind_chars.get();
    options.bind_port = static_cast<uint16_t>(bind_port);
    options.advertise_host = advertise_chars.get();
    options.advertise_port = static_cast<uint16_t>(advertise_port);
  }

  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  return coakka_v2_runtime_apply_network_options(handle->runtime, &options);
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeApplyInitialControl(
    JNIEnv *env, jobject, jlong raw_handle, jbyteArray envelope) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr || envelope == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  const jsize length = env->GetArrayLength(envelope);
  if (length <= 0) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  jbyte *bytes = env->GetByteArrayElements(envelope, nullptr);
  if (bytes == nullptr) {
    return COAKKA_V2_ERR_NOMEM;
  }
  coakka_v2_status_t status;
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    status = coakka_v2_runtime_apply_control_envelope(
        handle->runtime, reinterpret_cast<const uint8_t *>(bytes),
        static_cast<size_t>(length));
  }
  env->ReleaseByteArrayElements(envelope, bytes, JNI_ABORT);
  return status;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeOpenHostHandles(
    JNIEnv *env, jobject, jlong raw_handle, jint flags) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    return nullptr;
  }

  coakka_v2_host_handles_t handles{};
  handles.struct_size = sizeof(handles);
  handles.flags = static_cast<uint32_t>(flags);
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (coakka_v2_runtime_get_host_handles(handle->runtime, &handles) !=
        COAKKA_V2_OK) {
      return nullptr;
    }
  }

  const jint values[kHostHandleValueCount] = {
      kHostHandleLayoutVersion,          handles.request_write_fd,
      handles.response_read_fd,          handles.deadletter_read_fd,
      handles.control_write_fd,          handles.monitor_read_fd,
      handles.delivered_request_read_fd,
  };
  jintArray result = env->NewIntArray(kHostHandleValueCount);
  if (result == nullptr) {
    close_exported_handles(&handles);
    return nullptr;
  }
  env->SetIntArrayRegion(result, 0, kHostHandleValueCount, values);
  if (env->ExceptionCheck()) {
    close_exported_handles(&handles);
    return nullptr;
  }
  return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStart(JNIEnv *, jobject,
                                                       jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  const coakka_v2_status_t status = coakka_v2_runtime_start(handle->runtime);
  if (status == COAKKA_V2_OK) {
    handle->started = true;
  }
  return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStop(JNIEnv *, jobject,
                                                      jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  if (!handle->started) {
    return COAKKA_V2_OK;
  }
  const coakka_v2_status_t status = coakka_v2_runtime_stop(handle->runtime);
  if (status == COAKKA_V2_OK) {
    handle->started = false;
  }
  return status;
}

extern "C" JNIEXPORT jlong JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeConsumeMonitor(JNIEnv *,
                                                                jobject,
                                                                jint fd) {
  uint64_t signal_count = 0u;
  const coakka_v2_status_t status =
      coakka_v2_monitor_consume(fd, &signal_count);
  return status == COAKKA_V2_OK ? static_cast<jlong>(signal_count)
                                : static_cast<jlong>(status);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadHealth(JNIEnv *env,
                                                            jobject,
                                                            jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    return nullptr;
  }

  coakka_v2_runtime_health_t health{};
  health.struct_size = sizeof(health);
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (coakka_v2_runtime_get_health(handle->runtime, &health) !=
        COAKKA_V2_OK) {
      return nullptr;
    }
  }

  const jlong values[kHealthValueCount] = {
      static_cast<jlong>(health.runtime_state),
      static_cast<jlong>(health.flags),
      static_cast<jlong>(health.applied_generation),
  };
  jlongArray result = env->NewLongArray(kHealthValueCount);
  if (result == nullptr) {
    return nullptr;
  }
  env->SetLongArrayRegion(result, 0, kHealthValueCount, values);
  return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeDestroy(JNIEnv *, jobject,
                                                         jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr) {
    return;
  }
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (handle->started) {
      (void)coakka_v2_runtime_stop(handle->runtime);
      handle->started = false;
    }
    coakka_v2_runtime_destroy(handle->runtime);
    handle->runtime = nullptr;
  }
  delete handle;
}
