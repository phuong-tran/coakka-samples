# The JNI entrypoints, including owned request read, ask, and close signaling,
# use stable class and method names. Public ask result models are ordinary
# managed values and remain shrinkable in one consumer APK; the independently
# minified device fixture keeps those cross-APK types in its own exact rules.
-keep,allowoptimization class coakka.v2.android.NativeRuntimeBridge { native <methods>; }
-keep,allowoptimization class coakka.v2.android.NativeStreamCallbacks {
    public static int sourceNext(coakka.v2.android.AndroidStreamSource, java.nio.ByteBuffer, long[], long);
    public static int consume(coakka.v2.android.AndroidStreamConsumer, java.nio.ByteBuffer, long[], long);
}
-keep interface coakka.v2.android.AndroidStreamSource { *; }
-keep interface coakka.v2.android.AndroidStreamConsumer { *; }

# Native construction/startup refusal projects the exact Core status through
# this constructor. Other exception members remain managed and shrinkable.
-keep class coakka.v2.android.CoAkkaNativeException {
    public <init>(java.lang.String, int);
}

# Library-level optimization moves generated implementation classes here. The
# second app-level optimizer must retain the field names used by their schema.
-keepclassmembers class coakka.v2.android.internal.** extends coakka.v2.android.internal.Record {
    <fields>;
}
