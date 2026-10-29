import coakka.v2.android.AndroidRuntimeRoute;
import coakka.v2.android.RuntimeControlEncoder;
import java.security.MessageDigest;
import java.util.List;

/** Runs with only classes.jar and Kotlin; no private runtime may be supplied. */
public final class ManagedClosedConsumer {
    private ManagedClosedConsumer() {}

    public static void main(String[] args) throws Exception {
        byte[] encoded = RuntimeControlEncoder.encodeSnapshot(7L,
            List.of(AndroidRuntimeRoute.local("svc.echo", "android-node")), 7L, "coakka-runtime-android");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        if (!hex.toString().equals("fe14cb5cba65a4f0309426b41862bdd76a0722589ec86434af84aa071e8a5498")) {
            throw new AssertionError("frozen independent wire fixture mismatch: " + hex);
        }
        System.out.println("PASS_PACKAGED_PRIVATE_DEPENDENCY_CLOSURE");
    }
}
