# Library build only: preserve the explicit public surface, not internal implementation.
# Consumer rules below separately preserve JNI/reflection during app optimization.
-include consumer-rules.pro

# GeneratedMessageLite resolves its compact schema against these field names.
# Keep only generated message fields; R8 may still optimize and rename classes
# and methods that do not cross the JNI boundary.
-keepclassmembers class coakka.v2.control.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class coakka.v2.transport.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

-repackageclasses coakka.v2.android.internal
-applymapping library-internal-names.map
-keep,allowoptimization,allowobfuscation class com.google.protobuf.GeneratedMessageLite
# The pinned runtime explicitly provides this whole-program optimization switch.
# Every generated message is a private lite input; no host type enters this set.
-assumevalues class com.google.protobuf.Protobuf {
    static boolean assumeLiteRuntime return true;
}
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,*Annotation*
-keep class kotlin.Metadata { *; }

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeMessages {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}
-keep,allowoptimization class coakka.v2.android.AndroidRuntimeRequest {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeAskResult {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeAskResultKind {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeAskResultKind$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeEndpoint {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeFeatures {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeHealth {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeInfo {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeMonitorOptions {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeRoute {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeRoute$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeStartupConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeStartupCpuPolicies {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeStartupDefaultGroups {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeStartupExecution {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeStartupIo {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidRuntimeTcpConnectionConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamConsumer {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamConsumerDecision {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamSource {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamSourceResult {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamSourceResult$End {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamSourceResult$Frame {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.AndroidStreamSourceResult$WouldBlock {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.CoAkkaAndroidRuntime {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.CoAkkaAndroidRuntime$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.CoAkkaNativeException {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileDigest {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLane {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLane$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneException {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneFlags {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneSecurityConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneSecurityMode {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileLaneStats {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileReceiveGrant {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileReceiveSpec {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileSendSpec {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferDirection {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferDirection$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferResult {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferResult$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferSnapshot {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferState {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.FileTransferState$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.LaneOwnerConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.LaneOwnerEndpoint {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeControlEncoder {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeEndpointFlags {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeNetworkConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeNetworkConfig$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeNetworkMode {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeNetworkMode$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeNetworkSnapshot {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeRouteFlags {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeState {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeState$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeTcpConnectionMode {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.RuntimeTcpConnectionMode$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamDirection {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamDirection$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamFrameFlags {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamFrameMetadata {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLane {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLane$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneException {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneFlags {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneSecurityConfig {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneSecurityMode {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamLaneStats {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPressureReasons {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPressureSnapshot {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPressureState {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPressureState$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPublishGrant {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamPublishSpec {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamResult {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamResult$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamSessionSnapshot {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamState {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamState$Companion {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}

-keep,allowoptimization class coakka.v2.android.StreamSubscribeSpec {
    public protected <fields>;
    public <init>(...);
    public protected *** !*$*(...);
    public static synthetic *** *$default(...);
}
