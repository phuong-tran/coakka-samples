package coakka.samples.runtime.jvm.javadeadletter;

import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.protocol.ConnectorBusinessStatus;
import coakka.v2.connector.protocol.ConnectorDeadletter;
import coakka.v2.connector.protocol.ConnectorDeadletterReason;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorMessageKind;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import java.util.Collections;

public final class SampleControls {
    private SampleControls() {}

    private static ConnectorDeadletter value(String target, ConnectorDeadletterReason reason) {
        ConnectorEnvelope original = target == null ? null : new ConnectorEnvelope(
            "control", "", "sample-test", target, "", ConnectorMessageKind.REQUEST, false, 0,
            new byte[0], Collections.emptyMap(), ConnectorBusinessStatus.OK, "", "",
            ConnectorDeliveryHint.ROUTER_DEFAULT, "", 0, ConnectorPayloadFormat.UNSPECIFIED
        );
        return new ConnectorDeadletter(original, reason, "ROUTE_MISS", 1, "", 0, 0, null, Collections.emptyMap());
    }

    public static void main(String[] args) throws Exception {
        Main.requireRouteMiss(value("missing", new ConnectorDeadletterReason(2)), "missing");
        if (!RuntimeTcpConnectionMode.MULTIPLEXING.equals(new RuntimeTcpConnectionMode(3))) {
            throw new AssertionError("mode is a value, not an enum identity");
        }
        ConnectorDeadletter[] invalid = {
            value(null, ConnectorDeadletterReason.ROUTE_MISS),
            value("other", ConnectorDeadletterReason.ROUTE_MISS),
            value("missing", new ConnectorDeadletterReason(123456)),
            value("missing", ConnectorDeadletterReason.NO_RESPONSIBLE_HOST)
        };
        for (ConnectorDeadletter item : invalid) {
            try {
                Main.requireRouteMiss(item, "missing");
                throw new AssertionError("invalid observation was accepted");
            } catch (IllegalStateException expected) {
                // Only the app's typed assertion refusal counts as success.
            }
        }
        // Both real sample invocations close subscription/host before the next starts.
        Main.main(new String[0]);
        Main.main(new String[0]);
        System.out.println("java_sample_controls cases=8");
    }
}
