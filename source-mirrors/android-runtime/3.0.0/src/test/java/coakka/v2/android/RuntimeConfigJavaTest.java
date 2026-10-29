package coakka.v2.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Java callers use ordinary overloads and a nullable grouped startup choice. */
public final class RuntimeConfigJavaTest {
    @Test
    public void javaStartupOptionsPreserveAbsentFalseAndTrue() {
        AndroidRuntimeConfig defaults = new AndroidRuntimeConfig("system", "node");
        assertEquals(0, defaults.getQueueCapacity());
        assertNull(defaults.getMonitor());
        assertNull(defaults.getNetwork());

        AndroidRuntimeConfig off = new AndroidRuntimeConfig(
            "system", "off", RuntimeNetworkConfig.embedded(), 0, true,
            new AndroidRuntimeMonitorOptions(false)
        );
        AndroidRuntimeConfig on = new AndroidRuntimeConfig(
            "system", "on", RuntimeNetworkConfig.embedded(), 0, true,
            new AndroidRuntimeMonitorOptions(true)
        );
        assertFalse(off.getMonitor().getEnabled());
        assertTrue(on.getMonitor().getEnabled());
    }
    @Test
    public void javaNetworkIntentKeepsPresenceWithoutResolvingPolicy() {
        RuntimeNetworkConfig absentPorts = new RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED);
        assertNull(absentPorts.getBindPort());
        RuntimeNetworkConfig zero = new RuntimeNetworkConfig(
            RuntimeNetworkMode.OUTBOUND_ONLY, null, 0, null, 0
        );
        assertEquals(Integer.valueOf(0), zero.getBindPort());
        RuntimeNetworkConfig intent = RuntimeNetworkConfig.networkNode("invalid", 0, "::", 0);
        assertEquals(Integer.valueOf(0), intent.getAdvertisePort());
    }
}
