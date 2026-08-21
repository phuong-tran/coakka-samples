# The smoke app has no production entrypoint; its release instrumentation APK is
# the only consumer of these public APIs. Keep that fixture surface while the
# AAR's consumer-rules.pro remains solely responsible for name-based JNI types.
-keep class coakka.v2.android.AndroidRuntime** { *; }
-keep class coakka.v2.android.AndroidStreamConsumerDecision { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult { *; }
-keep class coakka.v2.android.AndroidStreamSourceResult$* { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime { *; }
-keep class coakka.v2.android.CoAkkaAndroidRuntime$* { *; }
-keep class coakka.v2.android.File** { *; }
-keep class coakka.v2.android.LaneOwner** { *; }
-keep class coakka.v2.android.Stream** { *; }

# AndroidX Test resolves its Kotlin runtime from the minified target APK. This
# fixture-only rule keeps that harness dependency and does not match CoAkka JNI.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }

# The independently optimized instrumentation APK constructs transport payloads
# through protobuf runtime APIs that the target shrinker cannot otherwise see.
-keep class com.google.protobuf.** { *; }

# JUnit reflects test method signatures before the instrumentation APK can
# instantiate the generated transport messages used by the smoke workflow.
-keep class coakka.v2.transport.** { *; }
