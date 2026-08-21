# AndroidX Test references this compile-time annotation from tracing metadata.
# It is not needed by the release instrumentation runtime.
-dontwarn com.google.errorprone.annotations.MustBeClosed

# The target and instrumentation APKs are optimized independently. Keep the
# fixture's cross-APK API references aligned with the names preserved in the
# minified target; the AAR consumer rules still exclusively own the four
# name-based JNI bridge types.
-keep class coakka.v2.android.AndroidRuntime** { *; }
-keep class coakka.v2.android.AndroidStreamConsumerDecision { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult$* { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime$* { *; }
-keep class coakka.v2.android.File** { *; }
-keep class coakka.v2.android.LaneOwnerConfig { *; }
-keep class coakka.v2.android.Stream** { *; }
-keep class coakka.v2.transport.** { *; }
