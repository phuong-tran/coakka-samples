import coakka.v2.android.AndroidRuntimeConfig;
import coakka.v2.android.AndroidRuntimeMonitorOptions;
import coakka.v2.android.AndroidRuntimeRoute;
import coakka.v2.android.RuntimeControlEncoder;
import coakka.v2.android.RuntimeEndpointFlags;
import coakka.v2.android.RuntimeNetworkConfig;
import coakka.v2.android.RuntimeNetworkMode;
import coakka.v2.control.ControlEnvelope;
import coakka.v2.control.RouteSnapshotPayload;
import java.util.List;

/** Executes public value APIs and independently decodes the processed encoder. */
public final class ManagedJavaConsumer {
    private ManagedJavaConsumer() {}

    public static void main(String[] args) throws Exception {
        AndroidRuntimeConfig absent = new AndroidRuntimeConfig("system", "node");
        require(absent.getNetwork() == null && absent.getMonitor() == null);
        RuntimeNetworkConfig zero = new RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY, null, 0, null, 0);
        require(Integer.valueOf(0).equals(zero.getBindPort()));
        AndroidRuntimeConfig off = new AndroidRuntimeConfig(
            "system", "off", RuntimeNetworkConfig.embedded(), 0, true,
            new AndroidRuntimeMonitorOptions(false));
        require(!off.getMonitor().getEnabled());
        byte[] bytes = RuntimeControlEncoder.encodeSnapshot(7L,
            List.of(AndroidRuntimeRoute.local("svc.echo", "android-node")), 11L, "java-consumer");
        ControlEnvelope envelope = ControlEnvelope.parseFrom(bytes);
        RouteSnapshotPayload snapshot = RouteSnapshotPayload.parseFrom(envelope.getPayload());
        require(envelope.getSeq() == 11L && envelope.getGeneration() == 7L);
        require(envelope.getMetadataOrThrow("source_connector").equals("java-consumer"));
        require(snapshot.getGeneration() == 7L && snapshot.getRoutesCount() == 1);
        require(snapshot.getRoutes(0).getTarget().equals("svc.echo"));
        require(snapshot.getRoutes(0).getEndpoints(0).getFlags() == RuntimeEndpointFlags.LOCAL);
        System.out.println("PASS_PACKAGED_JAVA_API_AND_WIRE");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("public Java consumer invariant");
    }
}
