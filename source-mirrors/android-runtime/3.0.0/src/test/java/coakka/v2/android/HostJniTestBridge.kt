package coakka.v2.android

internal object HostJniTestBridge {
    init {
        NativeRuntimeBridge.nativeAbiVersion()
    }

    external fun nativeFailTextWriteAfter(successfulWrites: Int)

    external fun nativeFailRuntimeAt(point: Int)

    external fun nativeRuntimeOwnerCount(): Int

    external fun nativeFailMessageAt(point: Int)

    external fun nativeMessageOwnerCounts(): LongArray

    external fun nativeRecoverableLeakCheck(): Int

    external fun nativeRetainedStreamCallbacks(): Long
}
