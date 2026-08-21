# The JNI entrypoints use stable class and method names.
-keep class coakka.v2.android.NativeRuntimeBridge { *; }
-keep class coakka.v2.android.NativeStreamCallbacks { *; }
-keep interface coakka.v2.android.AndroidStreamSource { *; }
-keep interface coakka.v2.android.AndroidStreamConsumer { *; }
