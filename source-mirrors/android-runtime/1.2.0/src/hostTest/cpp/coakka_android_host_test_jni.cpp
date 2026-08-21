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

extern "C" JNIEXPORT jint JNICALL
Java_coakka_v2_android_HostJniTestBridge_nativeRecoverableLeakCheck(
    JNIEnv *, jobject) {
#if defined(COAKKA_ANDROID_HOST_HAS_LSAN)
  return static_cast<jint>(__lsan_do_recoverable_leak_check());
#else
  return static_cast<jint>(-1);
#endif
}
