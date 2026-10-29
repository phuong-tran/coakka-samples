package coakka.v2.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

/** Java consumers use the same product surface, without a generated codec. */
public final class HostJniMessagesJavaIntegrationTest {
    @Test
    public void javaOverloadProjectsMessagesThroughRealCore() {
        assumeTrue("true".equals(System.getProperty("coakka.android.hostJni")));
        byte[] request = AndroidRuntimeMessages.request(
            "java-😀", "client", "svc.😀", "client/replies", new byte[]{0, 7, -1}, 5000);
        AndroidRuntimeRequest values = AndroidRuntimeMessages.decodeRequest(request);
        assertEquals("svc.😀", values.getTarget());
        assertFalse(values.getOneWay());
        assertArrayEquals(new byte[]{0, 7, -1}, values.getPayload());
        byte[] reply = AndroidRuntimeMessages.reply(request, "svc.😀", new byte[]{8});
        assertArrayEquals(new byte[]{8}, AndroidRuntimeMessages.responsePayload(reply));
    }
}
