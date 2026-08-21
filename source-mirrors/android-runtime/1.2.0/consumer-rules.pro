# The JNI entrypoints use stable class and method names.
-keep class coakka.v2.android.NativeRuntimeBridge { *; }
-keep class coakka.v2.android.NativeStreamCallbacks { *; }
-keep interface coakka.v2.android.AndroidStreamSource { *; }
-keep interface coakka.v2.android.AndroidStreamConsumer { *; }

# GeneratedMessageLite resolves its compact schema against these field names.
# Keep only generated message fields; R8 may still optimize and rename classes
# and methods that do not cross the JNI boundary.
-keepclassmembers class coakka.v2.control.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class coakka.v2.transport.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
