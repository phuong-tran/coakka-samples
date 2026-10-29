#include <jni.h>

#include "coakka_android_jni_support.h"

#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#define COAKKA_ANDROID_HOST_HAS_LSAN 1
#endif
#endif
#if defined(__SANITIZE_ADDRESS__)
#define COAKKA_ANDROID_HOST_HAS_LSAN 1
#endif

#if defined(COAKKA_ANDROID_HOST_HAS_LSAN)
extern "C" int __lsan_do_recoverable_leak_check(void);
#endif

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeFailTextWriteAfter(
    JNIEnv *, jobject, jint successful_writes) {
  coakka::android::jni::host_fail_text_write_after(successful_writes);
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeFailRuntimeAt(
    JNIEnv *, jobject, jint point) {
  coakka::android::jni::host_runtime_failure_point.store(
      point, std::memory_order_release);
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeRuntimeOwnerCount(
    JNIEnv *, jobject) {
  return coakka::android::jni::host_runtime_owner_count.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeFailMessageAt(JNIEnv *, jobject, jint point) {
  coakka::android::jni::host_message_failure_point.store(point, std::memory_order_release);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeMessageOwnerCounts(JNIEnv *env, jobject) {
  const jlong values[] = {
      coakka::android::jni::host_message_output_owners.load(std::memory_order_relaxed),
      coakka::android::jni::host_message_borrows.load(std::memory_order_relaxed)};
  jlongArray result = env->NewLongArray(2);
  if (result != nullptr) env->SetLongArrayRegion(result, 0, 2, values);
  return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeRecoverableLeakCheck(
    JNIEnv *, jobject) {
#if defined(COAKKA_ANDROID_HOST_HAS_LSAN)
  return static_cast<jint>(__lsan_do_recoverable_leak_check());
#else
  return static_cast<jint>(-1);
#endif
}
