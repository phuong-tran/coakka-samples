package coakka.v2.android

internal object HostJniTestBridge {
    external fun nativeFailTextWriteAfter(successfulWrites: Int)

    external fun nativeRecoverableLeakCheck(): Int

    external fun nativeRetainedStreamCallbacks(): Long
}
