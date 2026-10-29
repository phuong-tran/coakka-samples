#include <jni.h>

#include "coakka/v2/client.h"
#include "coakka/v2/control.h"
#include "coakka/v2/runtime.h"
#include "coakka/v2/runtime_startup.h"
#include "coakka/v2/transport.h"
#include "coakka_android_jni_support.h"

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <limits>
#include <mutex>
#include <new>
#include <system_error>

namespace {

constexpr jint kHostHandleLayoutVersion = 2;
constexpr jsize kHostHandleValueCount = 4;
constexpr jsize kHealthValueCount = 3;
constexpr jsize kRuntimeInfoNumericCount = 3;
constexpr jsize kRuntimeInfoTextCount = 4;
constexpr jsize kStartupConfigValueCount = 29;
constexpr uint32_t kNativeReadSliceMs = 50u;

void throw_java(JNIEnv *env, const char *type, const char *message) {
  if (env->ExceptionCheck()) {
    return;
  }
  jclass klass = env->FindClass(type);
  if (klass != nullptr) {
    env->ThrowNew(klass, message);
    env->DeleteLocalRef(klass);
  }
}

void throw_native_status(JNIEnv *env, const char *operation,
                         coakka_v2_status_t status) {
  // Never replace an allocation or class-lookup exception already issued by
  // the VM. Callers retire any unpublished native owner before arriving here.
  if (env->ExceptionCheck()) {
    return;
  }
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(5)) {
    throw_java(env, "java/lang/OutOfMemoryError", "runtime status publication failed");
    return;
  }
#endif
  jclass klass = env->FindClass("coakka/v2/android/CoAkkaNativeException");
  if (klass == nullptr) {
    return;
  }
  jmethodID constructor = env->GetMethodID(klass, "<init>", "(Ljava/lang/String;I)V");
  if (constructor != nullptr) {
    jstring text = env->NewStringUTF(operation);
    if (text != nullptr) {
      jobject failure = env->NewObject(klass, constructor, text, static_cast<jint>(status));
      if (failure != nullptr) {
        env->Throw(static_cast<jthrowable>(failure));
        env->DeleteLocalRef(failure);
      }
      env->DeleteLocalRef(text);
    }
  }
  env->DeleteLocalRef(klass);
}

void initialize_host_handles(coakka_v2_host_handles_t *handles) {
  *handles = {};
  handles->struct_size = sizeof(*handles);
  handles->request_write_fd = -1;
  handles->response_read_fd = -1;
  handles->deadletter_read_fd = -1;
  handles->control_write_fd = -1;
  handles->monitor_read_fd = -1;
  handles->delivered_request_read_fd = -1;
}

struct AndroidRuntimeHandle {
  std::mutex lifecycle_mutex;
  std::condition_variable lifecycle_cv;
  coakka_v2_runtime_t *runtime = nullptr;
  coakka_v2_ask_client_t *ask_client = nullptr;
  coakka_v2_request_reader_t *request_reader = nullptr;
  coakka_v2_host_handles_t borrowed_handles{};
  coakka_v2_host_handles_t owned_handles{};
  size_t active_calls = 0u;
  bool started = false;
  bool closing = false;
  bool delivered_read_active = false;

  AndroidRuntimeHandle() {
    initialize_host_handles(&borrowed_handles);
    initialize_host_handles(&owned_handles);
  }
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
  coakka_v2_host_handles_close(handles);
}

void set_status(JNIEnv *env, jintArray out_status, coakka_v2_status_t status) {
  const jint value = static_cast<jint>(status);
  env->SetIntArrayRegion(out_status, 0, 1, &value);
}

void finish_active_call(AndroidRuntimeHandle *handle, bool delivered_read) {
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  if (delivered_read) {
    handle->delivered_read_active = false;
  }
  if (handle->active_calls > 0u) {
    --handle->active_calls;
  }
  handle->lifecycle_cv.notify_all();
}

jbyteArray copy_bytes(JNIEnv *env, const uint8_t *bytes, size_t size) {
  if (size > static_cast<size_t>(std::numeric_limits<jsize>::max()) ||
      (size != 0u && bytes == nullptr)) {
    return nullptr;
  }
  jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
  if (result == nullptr) {
    return nullptr;
  }
  if (size != 0u) {
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(size),
                            reinterpret_cast<const jbyte *>(bytes));
    if (env->ExceptionCheck()) {
      return nullptr;
    }
  }
  return result;
}

coakka_v2_status_t stop_runtime_locked(AndroidRuntimeHandle *handle) {
  coakka_v2_request_reader_destroy(handle->request_reader);
  handle->request_reader = nullptr;
  coakka_v2_ask_client_destroy(handle->ask_client);
  handle->ask_client = nullptr;
  if (!handle->started) {
    return COAKKA_V2_OK;
  }
  const coakka_v2_status_t status = coakka_v2_runtime_stop(handle->runtime);
  if (status == COAKKA_V2_OK) {
    handle->started = false;
  }
  return status;
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
  env->SetLongArrayRegion(numeric, 0, kRuntimeInfoNumericCount, numeric_values);
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
    jstring value = env->NewStringUTF(
        text_values[index] == nullptr ? "" : text_values[index]);
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
    jint queue_capacity, jboolean strict_no_drop, jint monitor_intent) {
  if (env->ExceptionCheck()) {
    return 0;
  }
  UtfChars system_chars(env, system_name);
  if (!system_chars.valid()) {
    return 0;
  }
  UtfChars node_chars(env, node_id);
  if (!node_chars.valid()) {
    return 0;
  }
  if (system_chars.get() == nullptr || node_chars.get() == nullptr ||
      queue_capacity < 0 || monitor_intent < -1 || monitor_intent > 1) {
    throw_java(env, "java/lang/IllegalArgumentException", "invalid runtime boundary input");
    return 0;
  }

  AndroidRuntimeHandle *handle = nullptr;
  try {
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
    if (!coakka::android::jni::host_fail_runtime_at(1))
#endif
    {
      handle = new (std::nothrow) AndroidRuntimeHandle();
    }
  } catch (const std::bad_alloc &) {
    throw_java(env, "java/lang/OutOfMemoryError", "runtime bridge owner allocation failed");
    return 0;
  } catch (const std::system_error &) {
    throw_java(env, "java/lang/IllegalStateException", "runtime bridge synchronization creation failed");
    return 0;
  }
  if (handle == nullptr) {
    throw_java(env, "java/lang/OutOfMemoryError", "runtime bridge owner allocation failed");
    return 0;
  }
  coakka_v2_runtime_config_t config{};
  config.system_name = system_chars.get();
  config.node_id = node_chars.get();
  config.queue_capacity = queue_capacity;
  config.strict_no_drop = strict_no_drop == JNI_TRUE ? 1 : 0;

  // Absence is distinct from explicit OFF. Core resolves every absent group.
  coakka_v2_startup_monitor_options_t monitor{};
  monitor.struct_size = sizeof(monitor);
  monitor.enabled = monitor_intent == 1 ? 1u : 0u;
  coakka_v2_runtime_startup_options_t startup{};
  startup.struct_size = sizeof(startup);
  startup.monitor = monitor_intent < 0 ? nullptr : &monitor;
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(3)) {
    startup.struct_size = 0u;
  }
#endif
  coakka_v2_runtime_t *runtime = nullptr;
  const auto status = coakka_v2_runtime_create_native(&config, &startup, &runtime);
  if (status != COAKKA_V2_OK || runtime == nullptr) {
    coakka_v2_runtime_destroy(runtime);
    delete handle;
    if (status != COAKKA_V2_OK) {
      throw_native_status(env, "runtime_create_native", status);
    } else {
      throw_java(env, "java/lang/IllegalStateException", "runtime creation returned no owner");
    }
    return 0;
  }
  handle->runtime = runtime;
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  coakka::android::jni::host_runtime_owner_count.fetch_add(1, std::memory_order_relaxed);
#endif
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(handle));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadStartupConfig(
    JNIEnv *env, jobject, jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    throw_java(env, "java/lang/IllegalArgumentException", "runtime owner is required");
    return nullptr;
  }

  coakka_v2_runtime_startup_config_t config{};
  config.struct_size = sizeof(config);
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(4) ||
      coakka::android::jni::host_runtime_failure_point.load(std::memory_order_acquire) == 5) {
    config.struct_size = 0u;
  }
#endif
  coakka_v2_status_t status;
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    status = coakka_v2_runtime_get_startup_config(handle->runtime, &config);
  }
  if (status != COAKKA_V2_OK) {
    throw_native_status(env, "runtime_get_startup_config", status);
    return nullptr;
  }

  const jlong values[kStartupConfigValueCount] = {
      static_cast<jlong>(config.defaults_revision),
      static_cast<jlong>(config.defaulted_groups),
      static_cast<jlong>(config.strict_no_drop),
      static_cast<jlong>(config.queue_capacity),
      static_cast<jlong>(config.request_max_frame_size),
      static_cast<jlong>(config.runtime_state),
      static_cast<jlong>(config.execution.requested_policy),
      static_cast<jlong>(config.execution.supported),
      static_cast<jlong>(config.execution.available_cpu_count),
      static_cast<jlong>(config.execution.selected_cpu_count),
      static_cast<jlong>(config.execution.selected_cpu_ids[0]),
      static_cast<jlong>(config.execution.selected_cpu_ids[1]),
      static_cast<jlong>(config.execution.startup_applied),
      static_cast<jlong>(config.connection.defaults_revision),
      static_cast<jlong>(config.connection.mode),
      static_cast<jlong>(config.connection.applicable_fields),
      static_cast<jlong>(config.connection.explicitly_configured_fields),
      static_cast<jlong>(config.connection.defaulted_fields),
      static_cast<jlong>(config.connection.configurable_fields),
      static_cast<jlong>(config.connection.max_connections),
      static_cast<jlong>(config.connection.max_requests_per_connection),
      static_cast<jlong>(config.connection.idle_timeout_ms),
      static_cast<jlong>(config.io.requested),
      static_cast<jlong>(config.io.compiled),
      static_cast<jlong>(config.io.probed),
      static_cast<jlong>(config.io.supported),
      static_cast<jlong>(config.io.effective),
      static_cast<jlong>(config.io.probe_error),
      static_cast<jlong>(config.monitor.enabled),
  };
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(2)) {
    throw_java(env, "java/lang/OutOfMemoryError", "startup snapshot publication failed");
    return nullptr;
  }
#endif
  jlongArray result = env->NewLongArray(kStartupConfigValueCount);
  if (result == nullptr) {
    return nullptr;
  }
  env->SetLongArrayRegion(result, 0, kStartupConfigValueCount, values);
  return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeApplyNetwork(
    JNIEnv *env, jobject, jlong raw_handle, jint mode, jstring bind_host,
    jint bind_port, jboolean bind_port_present, jstring advertise_host,
    jint advertise_port, jboolean advertise_port_present) {
  if (env->ExceptionCheck()) return COAKKA_V2_ERR_NOMEM;
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr || bind_port < 0 ||
      bind_port > UINT16_MAX || advertise_port < 0 || advertise_port > UINT16_MAX) {
    throw_java(env, "java/lang/IllegalArgumentException", "invalid network boundary input");
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  UtfChars bind_chars(env, bind_host);
  if (!bind_chars.valid()) {
    return COAKKA_V2_ERR_NOMEM;
  }
  UtfChars advertise_chars(env, advertise_host);
  if (!advertise_chars.valid()) {
    return COAKKA_V2_ERR_NOMEM;
  }
  coakka_v2_network_options_t options{};
  options.struct_size = sizeof(options);
  options.fields = COAKKA_V2_NETWORK_FIELD_MODE;
  options.mode = static_cast<uint32_t>(mode);
  if (bind_host != nullptr) options.fields |= COAKKA_V2_NETWORK_FIELD_BIND_HOST;
  if (bind_port_present == JNI_TRUE) options.fields |= COAKKA_V2_NETWORK_FIELD_BIND_PORT;
  if (advertise_host != nullptr) options.fields |= COAKKA_V2_NETWORK_FIELD_ADVERTISE_HOST;
  if (advertise_port_present == JNI_TRUE) options.fields |= COAKKA_V2_NETWORK_FIELD_ADVERTISE_PORT;
  // Presence is mechanical projection, independent of network mode. Even a
  // value without a field bit reaches Core so it can issue the causal refusal.
  options.bind_host = bind_chars.get();
  options.bind_port = static_cast<uint16_t>(bind_port);
  options.advertise_host = advertise_chars.get();
  options.advertise_port = static_cast<uint16_t>(advertise_port);
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  return coakka_v2_runtime_apply_network_options(handle->runtime, &options);
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadNetworkSnapshot(
    JNIEnv *env, jobject, jlong raw_handle, jlongArray numeric, jobjectArray text) {
  if (env->ExceptionCheck()) return COAKKA_V2_ERR_NOMEM;
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr || numeric == nullptr ||
      text == nullptr || env->GetArrayLength(numeric) != 4 || env->GetArrayLength(text) != 2) {
    throw_java(env, "java/lang/IllegalArgumentException", "invalid network snapshot output");
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_network_config_snapshot_t snapshot{};
  snapshot.struct_size = sizeof(snapshot);
  auto *output = &snapshot;
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(7)) output = nullptr;
#endif
  // Core strings borrow this owner until next apply/destroy. Hold the same
  // lifecycle lock through their bounded synchronous Java copies; retain no
  // native view, JNI reference or output owner after this call.
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  const auto status = coakka_v2_runtime_get_network_config(handle->runtime, output);
  if (status != COAKKA_V2_OK) return status;
  const jlong values[] = {static_cast<jlong>(snapshot.mode),
                         static_cast<jlong>(snapshot.explicit_policy),
                         static_cast<jlong>(snapshot.bind_port),
                         static_cast<jlong>(snapshot.advertise_port)};
  env->SetLongArrayRegion(numeric, 0, 4, values);
  if (env->ExceptionCheck()) return COAKKA_V2_ERR_NOMEM;
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  if (coakka::android::jni::host_fail_runtime_at(6)) {
    throw_java(env, "java/lang/OutOfMemoryError", "network snapshot publication failed");
    return COAKKA_V2_ERR_NOMEM;
  }
#endif
  const char *hosts[] = {snapshot.bind_host, snapshot.advertise_host};
  for (jsize index = 0; index < 2; ++index) {
    jstring value = hosts[index] == nullptr ? nullptr : env->NewStringUTF(hosts[index]);
    if (env->ExceptionCheck()) return COAKKA_V2_ERR_NOMEM;
    env->SetObjectArrayElement(text, index, value);
    if (value != nullptr) env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) return COAKKA_V2_ERR_NOMEM;
  }
  return COAKKA_V2_OK;
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
    if (handle->closing || coakka_v2_runtime_get_host_handles(
                               handle->runtime, &handles) != COAKKA_V2_OK) {
      return nullptr;
    }
    handle->borrowed_handles = handles;
    handle->owned_handles = handles;
  }

  const jint values[kHostHandleValueCount] = {
      kHostHandleLayoutVersion,
      handles.request_write_fd,
      handles.control_write_fd,
      handles.monitor_read_fd,
  };
  jintArray result = env->NewIntArray(kHostHandleValueCount);
  if (result == nullptr) {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    close_exported_handles(&handle->owned_handles);
    initialize_host_handles(&handle->borrowed_handles);
    return nullptr;
  }
  env->SetIntArrayRegion(result, 0, kHostHandleValueCount, values);
  if (env->ExceptionCheck()) {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    close_exported_handles(&handle->owned_handles);
    initialize_host_handles(&handle->borrowed_handles);
    return nullptr;
  }
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    /* Java adopts only its write/doorbell descriptors. Core's response,
     * deadletter and delivered-request readers remain native-owned so the
     * native delivery backend may be a pipe or an owned queue without a
     * connector-side mode table. */
    handle->owned_handles.request_write_fd = -1;
    handle->owned_handles.control_write_fd = -1;
    handle->owned_handles.monitor_read_fd = -1;
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
  if (handle->closing || handle->started) {
    return COAKKA_V2_ERR_BAD_STATE;
  }
  coakka_v2_status_t status = coakka_v2_runtime_start(handle->runtime);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  status = coakka_v2_runtime_request_reader_create(
      handle->runtime, &handle->borrowed_handles, &handle->request_reader);
  if (status == COAKKA_V2_OK) {
    status = coakka_v2_ask_client_create_ex(
        handle->runtime, &handle->borrowed_handles, &handle->ask_client);
  }
  if (status != COAKKA_V2_OK) {
    coakka_v2_request_reader_destroy(handle->request_reader);
    handle->request_reader = nullptr;
    coakka_v2_ask_client_destroy(handle->ask_client);
    handle->ask_client = nullptr;
    (void)coakka_v2_runtime_stop(handle->runtime);
    return status;
  }
  handle->started = true;
  return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeStop(JNIEnv *, jobject,
                                                      jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || handle->runtime == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  std::unique_lock<std::mutex> lock(handle->lifecycle_mutex);
  handle->closing = true;
  handle->lifecycle_cv.wait(lock,
                            [handle] { return handle->active_calls == 0u; });
  return stop_runtime_locked(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeSignalClose(JNIEnv *, jobject,
                                                             jlong raw_handle) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr) {
    return;
  }
  std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
  handle->closing = true;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeReadDeliveredRequest(
    JNIEnv *env, jobject, jlong raw_handle, jintArray out_status) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || out_status == nullptr ||
      env->GetArrayLength(out_status) != 1) {
    return nullptr;
  }

  coakka_v2_request_reader_t *reader = nullptr;
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (handle->closing) {
      set_status(env, out_status, COAKKA_V2_ERR_CLOSED);
      return nullptr;
    }
    if (handle->runtime == nullptr || !handle->started ||
        handle->request_reader == nullptr || handle->delivered_read_active) {
      set_status(env, out_status, COAKKA_V2_ERR_BAD_STATE);
      return nullptr;
    }
    reader = handle->request_reader;
    handle->delivered_read_active = true;
    ++handle->active_calls;
  }

  coakka_v2_status_t status = COAKKA_V2_ERR_WOULD_BLOCK;
  jbyteArray result = nullptr;
  for (;;) {
    coakka_v2_owned_frame_t *frame = nullptr;
    status = coakka_v2_request_reader_read(reader, kNativeReadSliceMs, &frame);
    if (status == COAKKA_V2_OK) {
      result = copy_bytes(env, coakka_v2_owned_frame_data(frame),
                          coakka_v2_owned_frame_size(frame));
      coakka_v2_owned_frame_release(frame);
      if (result == nullptr && !env->ExceptionCheck()) {
        status = COAKKA_V2_ERR_NOMEM;
      }
      break;
    }
    if (status != COAKKA_V2_ERR_WOULD_BLOCK) {
      break;
    }
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (handle->closing) {
      status = COAKKA_V2_ERR_CLOSED;
      break;
    }
  }
  finish_active_call(handle, true);
  if (!env->ExceptionCheck()) {
    set_status(env, out_status, status);
  }
  return status == COAKKA_V2_OK ? result : nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeAsk(
    JNIEnv *env, jobject, jlong raw_handle, jbyteArray envelope,
    jint timeout_ms, jintArray out_status_and_kind) {
  AndroidRuntimeHandle *handle = from_handle(raw_handle);
  if (handle == nullptr || envelope == nullptr || timeout_ms < 0 ||
      out_status_and_kind == nullptr ||
      env->GetArrayLength(out_status_and_kind) != 2) {
    return nullptr;
  }
  const jsize length = env->GetArrayLength(envelope);
  if (length <= 0) {
    const jint values[2] = {COAKKA_V2_ERR_INVALID_ARG,
                            COAKKA_V2_CLIENT_RESULT_NONE};
    env->SetIntArrayRegion(out_status_and_kind, 0, 2, values);
    return nullptr;
  }

  coakka_v2_ask_client_t *client = nullptr;
  {
    std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
    if (handle->closing) {
      const jint values[2] = {COAKKA_V2_ERR_CLOSED,
                              COAKKA_V2_CLIENT_RESULT_NONE};
      env->SetIntArrayRegion(out_status_and_kind, 0, 2, values);
      return nullptr;
    }
    if (handle->runtime == nullptr || !handle->started ||
        handle->ask_client == nullptr) {
      const jint values[2] = {COAKKA_V2_ERR_BAD_STATE,
                              COAKKA_V2_CLIENT_RESULT_NONE};
      env->SetIntArrayRegion(out_status_and_kind, 0, 2, values);
      return nullptr;
    }
    client = handle->ask_client;
    ++handle->active_calls;
  }

  coakka_v2_status_t status = COAKKA_V2_ERR_NOMEM;
  uint32_t result_kind = COAKKA_V2_CLIENT_RESULT_NONE;
  uint8_t *result_bytes = nullptr;
  size_t result_size = 0u;
  coakka_v2_ask_ticket_t *ticket = nullptr;
  jbyteArray result = nullptr;
  jbyte *bytes = env->GetByteArrayElements(envelope, nullptr);
  if (bytes != nullptr) {
    status = coakka_v2_ask_client_begin(
        client, reinterpret_cast<const uint8_t *>(bytes),
        static_cast<size_t>(length), &ticket);
    env->ReleaseByteArrayElements(envelope, bytes, JNI_ABORT);
  }

  if (status == COAKKA_V2_OK) {
    const auto deadline = std::chrono::steady_clock::now() +
                          std::chrono::milliseconds(timeout_ms);
    for (;;) {
      bool closing;
      {
        std::lock_guard<std::mutex> lock(handle->lifecycle_mutex);
        closing = handle->closing;
      }
      if (closing) {
        status = COAKKA_V2_ERR_CLOSED;
        break;
      }
      const auto now = std::chrono::steady_clock::now();
      uint32_t wait_ms = 0u;
      if (now < deadline) {
        const auto remaining =
            std::chrono::duration_cast<std::chrono::milliseconds>(deadline -
                                                                  now);
        wait_ms = static_cast<uint32_t>(std::min<int64_t>(
            kNativeReadSliceMs, std::max<int64_t>(1, remaining.count())));
      }
      status = coakka_v2_ask_ticket_await(ticket, wait_ms, &result_kind,
                                          &result_bytes, &result_size);
      if (status != COAKKA_V2_ERR_WOULD_BLOCK ||
          std::chrono::steady_clock::now() >= deadline) {
        break;
      }
    }
  }
  if (status == COAKKA_V2_OK) {
    result = copy_bytes(env, result_bytes, result_size);
    if (result == nullptr && !env->ExceptionCheck()) {
      status = COAKKA_V2_ERR_NOMEM;
    }
  }
  coakka_v2_client_bytes_release(result_bytes);
  if (ticket != nullptr) {
    if (status != COAKKA_V2_OK) {
      coakka_v2_ask_ticket_cancel(ticket);
    }
    coakka_v2_ask_ticket_destroy(ticket);
  }
  finish_active_call(handle, false);
  if (!env->ExceptionCheck()) {
    const jint values[2] = {static_cast<jint>(status),
                            static_cast<jint>(result_kind)};
    env->SetIntArrayRegion(out_status_and_kind, 0, 2, values);
  }
  return status == COAKKA_V2_OK ? result : nullptr;
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
    std::unique_lock<std::mutex> lock(handle->lifecycle_mutex);
    handle->closing = true;
    handle->lifecycle_cv.wait(lock,
                              [handle] { return handle->active_calls == 0u; });
    (void)stop_runtime_locked(handle);
    coakka_v2_runtime_destroy(handle->runtime);
    handle->runtime = nullptr;
    close_exported_handles(&handle->owned_handles);
    initialize_host_handles(&handle->borrowed_handles);
  }
#if defined(COAKKA_ANDROID_JNI_HOST_TESTING)
  coakka::android::jni::host_runtime_owner_count.fetch_sub(1, std::memory_order_relaxed);
#endif
  delete handle;
}
