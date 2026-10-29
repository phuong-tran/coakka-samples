# AndroidX Test references this compile-time annotation from tracing metadata.
# It is not needed by the release instrumentation runtime.
-dontwarn com.google.errorprone.annotations.MustBeClosed

# The target and instrumentation APKs are optimized independently. Keep the
# fixture's cross-APK API references aligned with the names preserved in the
# minified target; the AAR consumer rules still exclusively own the
# name-based JNI bridge and exception-constructor identities.
-keep class coakka.v2.android.AndroidRuntimeAskResult { *; }
-keep class coakka.v2.android.AndroidRuntimeAskResultKind { *; }
-keep class coakka.v2.android.CoAkkaNativeException { *; }
-keep class coakka.v2.android.AndroidRuntime** { *; }
-keep class coakka.v2.android.AndroidStreamConsumerDecision { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult$* { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime$* { *; }
-keep class coakka.v2.android.Runtime** { *; }
-keep class coakka.v2.android.File** { *; }
-keep class coakka.v2.android.LaneOwner** { *; }
-keep class coakka.v2.android.Stream** { *; }
