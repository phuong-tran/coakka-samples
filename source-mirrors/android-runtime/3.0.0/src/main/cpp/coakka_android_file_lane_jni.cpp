#include <jni.h>

#include "coakka/v2/file_lane.h"

#include "coakka_android_jni_support.h"

#include <cstdint>
#include <cstring>
#include <limits>
#include <new>

namespace {

using coakka::android::jni::UtfChars;
using coakka::android::jni::UtfStringArray;
using coakka::android::jni::owner_grants_available;
using coakka::android::jni::read_digest;
using coakka::android::jni::read_longs;
using coakka::android::jni::write_digest;
using coakka::android::jni::write_int;
using coakka::android::jni::write_longs;
using coakka::android::jni::write_text;

constexpr jsize kFileConfigNumericCount = 12;
constexpr jsize kFileConfigTextCount = 7;
constexpr jsize kFileGrantNumericCount = 2;
constexpr jsize kFileGrantTextCount = 4;
constexpr jsize kFileSnapshotNumericCount = 13;
constexpr jsize kFileSnapshotTextCount = 1;
constexpr jsize kFileStatsNumericCount = 15;

struct AndroidFileLaneHandle {
  coakka_v2_file_lane_t *lane = nullptr;
};

AndroidFileLaneHandle *from_file_handle(jlong value) {
  return reinterpret_cast<AndroidFileLaneHandle *>(
      static_cast<uintptr_t>(value));
}

void stop_and_destroy_file_lane(coakka_v2_file_lane_t *lane) {
  if (lane == nullptr) {
    return;
  }
  (void)coakka_v2_file_lane_stop(lane);
  coakka_v2_file_lane_destroy(lane);
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

bool valid_file_config(const jlong values[kFileConfigNumericCount]) {
  return fits_u32(values[0]) && values[0] != 0 &&
         values[1] >= 0 && values[1] <= UINT16_MAX && fits_size(values[2]) &&
         values[3] >= 0 && fits_u32(values[4]) && values[5] >= 0 &&
         values[6] >= 0 && fits_u32(values[7]) && fits_u32(values[8]) &&
         fits_u32(values[9]) && values[10] >= -1 && values[10] <= 2 &&
         values[11] >= 0;
}

coakka_v2_status_t fill_file_snapshot(JNIEnv *env,
                                      const coakka_v2_file_transfer_snapshot_t &snapshot,
                                      jlongArray numeric, jobjectArray text) {
  const jlong values[kFileSnapshotNumericCount] = {
      static_cast<jlong>(snapshot.direction),
      static_cast<jlong>(snapshot.state),
      static_cast<jlong>(snapshot.result),
      static_cast<jlong>(snapshot.expected_size),
      static_cast<jlong>(snapshot.transferred_bytes),
      static_cast<jlong>(snapshot.committed_offset),
      static_cast<jlong>(snapshot.progress_milli),
      static_cast<jlong>(snapshot.cancel_requested),
      static_cast<jlong>(snapshot.update_sequence),
      static_cast<jlong>(snapshot.submitted_mono_ns),
      static_cast<jlong>(snapshot.started_mono_ns),
      static_cast<jlong>(snapshot.updated_mono_ns),
      static_cast<jlong>(snapshot.terminal_mono_ns),
  };
  if (!write_longs(env, numeric, kFileSnapshotNumericCount, values) ||
      text == nullptr || env->GetArrayLength(text) != kFileSnapshotTextCount ||
      !write_text(env, text, 0, snapshot.detail)) {
    return COAKKA_V2_ERR_NOMEM;
  }
  return COAKKA_V2_OK;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneCreate(
    JNIEnv *env, jobject, jlongArray numeric, jobjectArray text,
    jboolean owner_aware, jintArray out_status) {
  jlong values[kFileConfigNumericCount]{};
  UtfStringArray strings(env, text, kFileConfigTextCount);
  if (!read_longs(env, numeric, kFileConfigNumericCount, values) ||
      !strings.valid() || !valid_file_config(values)) {
    (void)write_int(env, out_status, COAKKA_V2_ERR_INVALID_ARG);
    return 0;
  }

  coakka_v2_file_lane_security_config_t security{};
  coakka_v2_file_lane_security_config_t *security_pointer = nullptr;
  if (values[10] >= 0) {
    security.struct_size = sizeof(security);
    security.mode = static_cast<uint32_t>(values[10]);
    security.credential_generation = static_cast<uint64_t>(values[11]);
    security.credential_id = strings.get(1);
    security.ca_certificate_file = strings.get(2);
    security.identity_certificate_file = strings.get(3);
    security.private_key_file = strings.get(4);
    security_pointer = &security;
  }

  coakka_v2_file_lane_config_t config{};
  config.struct_size = sizeof(config);
  config.flags = static_cast<uint32_t>(values[0]);
  config.bind_host = strings.get(0);
  config.bind_port = static_cast<uint16_t>(values[1]);
  config.queue_capacity = static_cast<size_t>(values[2]);
  config.max_file_size = static_cast<uint64_t>(values[3]);
  config.io_timeout_ms = static_cast<uint32_t>(values[4]);
  config.checkpoint_bytes = static_cast<uint64_t>(values[5]);
  config.progress_bytes = static_cast<uint64_t>(values[6]);
  config.progress_interval_ms = static_cast<uint32_t>(values[7]);
  config.sender_worker_count = static_cast<uint32_t>(values[8]);
  config.receiver_worker_count = static_cast<uint32_t>(values[9]);
  config.security = security_pointer;

  coakka_v2_file_lane_t *lane = nullptr;
  coakka_v2_status_t status = COAKKA_V2_OK;
  if (owner_aware == JNI_TRUE) {
    if (!owner_grants_available()) {
      status = COAKKA_V2_ERR_FEATURE_UNAVAILABLE;
    } else {
      coakka_v2_file_lane_owned_config_t owned{};
      owned.struct_size = sizeof(owned);
      owned.lane = config;
      owned.owner.struct_size = sizeof(owned.owner);
      owned.owner.owner_instance_id = strings.get(5);
      owned.owner.advertised_host = strings.get(6);
      status = coakka_v2_file_lane_create_owned_ex(&owned, &lane);
    }
  } else {
    status = coakka_v2_file_lane_create_ex(&config, &lane);
  }
  if (status == COAKKA_V2_OK) {
    status = coakka_v2_file_lane_start(lane);
  }
  if (status != COAKKA_V2_OK) {
    stop_and_destroy_file_lane(lane);
    (void)write_int(env, out_status, status);
    return 0;
  }

  auto *handle = new (std::nothrow) AndroidFileLaneHandle{lane};
  if (handle == nullptr) {
    stop_and_destroy_file_lane(lane);
    (void)write_int(env, out_status, COAKKA_V2_ERR_NOMEM);
    return 0;
  }
  if (!write_int(env, out_status, COAKKA_V2_OK)) {
    stop_and_destroy_file_lane(lane);
    delete handle;
    return 0;
  }
  return static_cast<jlong>(reinterpret_cast<uintptr_t>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneStop(
    JNIEnv *, jobject, jlong raw_handle) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  return handle == nullptr || handle->lane == nullptr
             ? COAKKA_V2_ERR_INVALID_ARG
             : coakka_v2_file_lane_stop(handle->lane);
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneDestroy(
    JNIEnv *, jobject, jlong raw_handle) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  if (handle == nullptr) {
    return;
  }
  coakka_v2_file_lane_destroy(handle->lane);
  handle->lane = nullptr;
  delete handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneBoundPort(
    JNIEnv *env, jobject, jlong raw_handle, jintArray out_port) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  if (handle == nullptr || handle->lane == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  uint16_t port = 0;
  const coakka_v2_status_t status =
      coakka_v2_file_lane_get_bound_port(handle->lane, &port);
  return status != COAKKA_V2_OK || write_int(env, out_port, port)
             ? status
             : COAKKA_V2_ERR_NOMEM;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLanePrepareReceive(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jstring authorization_token, jstring destination_path, jlong expected_size,
    jbyteArray expected_sha256) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  UtfChars token(env, authorization_token);
  UtfChars destination(env, destination_path);
  coakka_v2_file_receive_spec_t spec{};
  if (handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
      !token.valid() || !destination.valid() || transfer.get() == nullptr ||
      token.get() == nullptr || destination.get() == nullptr ||
      expected_size < 0 || !read_digest(env, expected_sha256,
                                        spec.expected_sha256)) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  spec.struct_size = sizeof(spec);
  spec.transfer_id = transfer.get();
  spec.authorization_token = token.get();
  spec.destination_path = destination.get();
  spec.expected_size = static_cast<uint64_t>(expected_size);
  return coakka_v2_file_lane_prepare_receive(handle->lane, &spec);
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLanePrepareReceiveGrant(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jstring authorization_token, jstring destination_path, jlong expected_size,
    jbyteArray expected_sha256, jlongArray out_numeric,
    jobjectArray out_text, jbyteArray out_sha256) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  UtfChars token(env, authorization_token);
  UtfChars destination(env, destination_path);
  coakka_v2_file_receive_spec_t spec{};
  if (handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
      !token.valid() || !destination.valid() || transfer.get() == nullptr ||
      token.get() == nullptr || destination.get() == nullptr ||
      expected_size < 0 || !read_digest(env, expected_sha256,
                                        spec.expected_sha256) ||
      out_numeric == nullptr ||
      env->GetArrayLength(out_numeric) != kFileGrantNumericCount ||
      out_text == nullptr ||
      env->GetArrayLength(out_text) != kFileGrantTextCount) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  spec.struct_size = sizeof(spec);
  spec.transfer_id = transfer.get();
  spec.authorization_token = token.get();
  spec.destination_path = destination.get();
  spec.expected_size = static_cast<uint64_t>(expected_size);

  coakka_v2_file_receive_grant_t grant{};
  grant.struct_size = sizeof(grant);
  const coakka_v2_status_t status =
      coakka_v2_file_lane_prepare_receive_grant(handle->lane, &spec, &grant);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong values[kFileGrantNumericCount] = {
      static_cast<jlong>(grant.owner.port),
      static_cast<jlong>(grant.expected_size),
  };
  if (!write_longs(env, out_numeric, kFileGrantNumericCount, values) ||
      !write_text(env, out_text, 0, grant.owner.owner_instance_id) ||
      !write_text(env, out_text, 1, grant.owner.advertised_host) ||
      !write_text(env, out_text, 2, grant.transfer_id) ||
      !write_text(env, out_text, 3, grant.authorization_token) ||
      !write_digest(env, out_sha256, grant.expected_sha256)) {
    (void)coakka_v2_file_lane_cancel_transfer(
        handle->lane, transfer.get(),
        COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE);
    (void)coakka_v2_file_lane_forget_transfer(
        handle->lane, transfer.get(),
        COAKKA_V2_FILE_TRANSFER_DIRECTION_RECEIVE);
    return COAKKA_V2_ERR_NOMEM;
  }
  return COAKKA_V2_OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneSubmitSend(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jstring authorization_token, jstring remote_host, jint remote_port,
    jstring source_path, jlong expected_size, jbyteArray expected_sha256,
    jint timeout_ms) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  UtfChars token(env, authorization_token);
  UtfChars remote(env, remote_host);
  UtfChars source(env, source_path);
  coakka_v2_file_send_spec_t spec{};
  if (handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
      !token.valid() || !remote.valid() || !source.valid() ||
      transfer.get() == nullptr || token.get() == nullptr ||
      remote.get() == nullptr || source.get() == nullptr || remote_port <= 0 ||
      remote_port > UINT16_MAX || expected_size < 0 || timeout_ms < 0 ||
      !read_digest(env, expected_sha256, spec.expected_sha256)) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  spec.struct_size = sizeof(spec);
  spec.transfer_id = transfer.get();
  spec.authorization_token = token.get();
  spec.remote_host = remote.get();
  spec.remote_port = static_cast<uint16_t>(remote_port);
  spec.source_path = source.get();
  spec.expected_size = static_cast<uint64_t>(expected_size);
  spec.timeout_ms = static_cast<uint32_t>(timeout_ms);
  return coakka_v2_file_lane_submit_send(handle->lane, &spec);
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneTransfer(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jint direction, jlong after_update_sequence, jint timeout_ms,
    jboolean wait, jlongArray out_numeric, jobjectArray out_text) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  if (handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
      transfer.get() == nullptr || after_update_sequence < 0 || timeout_ms < 0) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_file_transfer_snapshot_t snapshot{};
  snapshot.struct_size = sizeof(snapshot);
  const coakka_v2_status_t status = wait == JNI_TRUE
      ? coakka_v2_file_lane_wait_transfer(
            handle->lane, transfer.get(), static_cast<uint32_t>(direction),
            static_cast<uint64_t>(after_update_sequence),
            static_cast<uint32_t>(timeout_ms), &snapshot)
      : coakka_v2_file_lane_get_transfer(
            handle->lane, transfer.get(), static_cast<uint32_t>(direction),
            &snapshot);
  return status == COAKKA_V2_OK
             ? fill_file_snapshot(env, snapshot, out_numeric, out_text)
             : status;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneCancel(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jint direction) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  return handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
                 transfer.get() == nullptr
             ? COAKKA_V2_ERR_INVALID_ARG
             : coakka_v2_file_lane_cancel_transfer(
                   handle->lane, transfer.get(),
                   static_cast<uint32_t>(direction));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneForget(
    JNIEnv *env, jobject, jlong raw_handle, jstring transfer_id,
    jint direction) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  UtfChars transfer(env, transfer_id);
  return handle == nullptr || handle->lane == nullptr || !transfer.valid() ||
                 transfer.get() == nullptr
             ? COAKKA_V2_ERR_INVALID_ARG
             : coakka_v2_file_lane_forget_transfer(
                   handle->lane, transfer.get(),
                   static_cast<uint32_t>(direction));
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileLaneStats(
    JNIEnv *env, jobject, jlong raw_handle, jlongArray out_numeric) {
  AndroidFileLaneHandle *handle = from_file_handle(raw_handle);
  if (handle == nullptr || handle->lane == nullptr) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  coakka_v2_file_lane_stats_t stats{};
  stats.struct_size = sizeof(stats);
  const coakka_v2_status_t status =
      coakka_v2_file_lane_get_stats(handle->lane, &stats);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong values[kFileStatsNumericCount] = {
      static_cast<jlong>(stats.queue_capacity),
      static_cast<jlong>(stats.queued_sends),
      static_cast<jlong>(stats.prepared_receives),
      static_cast<jlong>(stats.active_sends),
      static_cast<jlong>(stats.active_receives),
      static_cast<jlong>(stats.retained_records),
      static_cast<jlong>(stats.submitted_sends),
      static_cast<jlong>(stats.prepared_receive_count),
      static_cast<jlong>(stats.completed_sends),
      static_cast<jlong>(stats.completed_receives),
      static_cast<jlong>(stats.failed_sends),
      static_cast<jlong>(stats.failed_receives),
      static_cast<jlong>(stats.canceled_transfers),
      static_cast<jlong>(stats.completed_send_bytes),
      static_cast<jlong>(stats.completed_receive_bytes),
  };
  return write_longs(env, out_numeric, kFileStatsNumericCount, values)
             ? COAKKA_V2_OK
             : COAKKA_V2_ERR_NOMEM;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_NativeRuntimeBridge_nativeFileSha256(
    JNIEnv *env, jobject, jstring path, jbyteArray out_sha256,
    jlongArray out_size) {
  UtfChars native_path(env, path);
  if (!native_path.valid() || native_path.get() == nullptr ||
      out_size == nullptr || env->GetArrayLength(out_size) != 1) {
    return COAKKA_V2_ERR_INVALID_ARG;
  }
  uint8_t digest[COAKKA_V2_FILE_LANE_SHA256_BYTES]{};
  uint64_t size = 0;
  const coakka_v2_status_t status =
      coakka_v2_file_sha256_path(native_path.get(), digest, &size);
  if (status != COAKKA_V2_OK) {
    return status;
  }
  const jlong size_value = static_cast<jlong>(size);
  return write_digest(env, out_sha256, digest) &&
                 write_longs(env, out_size, 1, &size_value)
             ? COAKKA_V2_OK
             : COAKKA_V2_ERR_NOMEM;
}
