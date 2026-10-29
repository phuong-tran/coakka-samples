# The smoke app has no production entrypoint; its release instrumentation APK is
# the only consumer of these public APIs. Keep that fixture surface while the
# AAR's consumer-rules.pro remains solely responsible for name-based JNI types.
-keep class coakka.v2.android.AndroidRuntimeAskResult { *; }
-keep class coakka.v2.android.AndroidRuntimeAskResultKind { *; }
# Cross-APK access needs only this getter. The JNI constructor is retained
# exclusively by the AAR's production consumer rule.
-keepclassmembers class coakka.v2.android.CoAkkaNativeException { public int getStatus(); }
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

# AndroidX Test resolves its Kotlin runtime from the minified target APK. This
# fixture-only rule keeps that harness dependency and does not match CoAkka JNI.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
