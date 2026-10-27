# JNI_OnLoad registers this private class and its native methods by their exact
# binary names. Ordinary connector implementation classes remain eligible for
# shrinking/obfuscation.
-keep,includedescriptorclasses class coakka.v2.connector.internal.NativeBridge {
    native <methods>;
}

# JNI materializes one copied record using this fixed constructor descriptor.
-keep class coakka.v2.connector.internal.NativeRecord {
    public <init>(long[], byte[][]);
}

# The payload fast path avoids generic record arrays with this fixed result.
-keep class coakka.v2.connector.internal.NativePayloadResult {
    public <init>(int, int, byte[]);
}

# Native stream owners call these two bounded callback methods by name. The
# adapter implementation class names are irrelevant and remain obfuscatable.
-keep,includedescriptorclasses interface coakka.v2.connector.internal.NativeStreamSourceAdapter {
    public int next(byte[], long[]);
}
-keepclassmembers,allowoptimization,allowobfuscation class * implements coakka.v2.connector.internal.NativeStreamSourceAdapter {
    public int next(byte[], long[]);
}
-keep,includedescriptorclasses interface coakka.v2.connector.internal.NativeStreamConsumerAdapter {
    public int consume(byte[], long[]);
}
-keepclassmembers,allowoptimization,allowobfuscation class * implements coakka.v2.connector.internal.NativeStreamConsumerAdapter {
    public int consume(byte[], long[]);
}
