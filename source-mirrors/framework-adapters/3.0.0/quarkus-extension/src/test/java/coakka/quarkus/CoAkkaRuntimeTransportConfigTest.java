package coakka.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeTcpConnectionStrategySpec;
import coakka.v2.connector.RuntimeTcpSecurityMode;
import coakka.v2.connector.RuntimeTcpSecuritySpec;
import coakka.v2.connector.RuntimeTlsReloadMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoAkkaRuntimeTransportConfigTest {
    @Test
    void runtimeDefaultsDoNotCreateExplicitTransportSpecs() {
        CoAkkaRuntime runtime = runtimeDefaults();

        assertNull(runtime.connectionStrategy());
        assertNull(runtime.securityPolicy());
    }

    @Test
    void mapsTypedConnectionAndSecuritySpecs() {
        CoAkkaRuntime runtime = runtimeDefaults();
        runtime.tcpConnectionStrategy = "persistent_single_flight";
        runtime.tcpSecurityMode = "tls";
        runtime.tlsReloadMode = "graceful";
        runtime.tlsCredentialGeneration = Optional.of(9L);
        runtime.tlsCredentialId = Optional.of("quarkus-generation-9");
        runtime.tlsCaCertificateFile = Optional.of("/run/secrets/coakka/ca.pem");
        runtime.tlsIdentityCertificateFile = Optional.of("/run/secrets/coakka/node.pem");
        runtime.tlsPrivateKeyFile = Optional.of("/run/secrets/coakka/node.key");

        RuntimeTcpConnectionStrategySpec connection = runtime.connectionStrategy();
        RuntimeTcpSecuritySpec security = runtime.securityPolicy();

        assertEquals(RuntimeTcpConnectionMode.PERSISTENT_SINGLE_FLIGHT, connection.getMode());
        assertEquals(RuntimeTcpSecurityMode.TLS, security.getMode());
        assertEquals(RuntimeTlsReloadMode.GRACEFUL, security.getReloadMode());
        assertEquals(9L, security.getCredentialGeneration());
        assertEquals("quarkus-generation-9", security.getCredentialId());
    }

    @Test
    void tuningAndCredentialsRequireAnExplicitMode() {
        CoAkkaRuntime connection = runtimeDefaults();
        connection.tcpMaxConnections = Optional.of(4);
        assertThrows(IllegalArgumentException.class, connection::connectionStrategy);

        CoAkkaRuntime security = runtimeDefaults();
        security.tlsCredentialGeneration = Optional.of(1L);
        assertThrows(IllegalArgumentException.class, security::securityPolicy);

        CoAkkaRuntime reload = runtimeDefaults();
        reload.tlsReloadMode = "drain-existing-connections";
        assertThrows(IllegalArgumentException.class, reload::securityPolicy);
    }

    private static CoAkkaRuntime runtimeDefaults() {
        CoAkkaRuntime runtime = new CoAkkaRuntime();
        runtime.tcpConnectionStrategy = "runtime-default";
        runtime.tcpMaxConnections = Optional.empty();
        runtime.tcpMaxRequestsPerConnection = Optional.empty();
        runtime.tcpIdleTimeoutMs = Optional.empty();
        runtime.tcpSecurityMode = "runtime-default";
        runtime.tlsReloadMode = "graceful";
        runtime.tlsCredentialGeneration = Optional.empty();
        runtime.tlsCredentialId = Optional.empty();
        runtime.tlsCaCertificateFile = Optional.empty();
        runtime.tlsIdentityCertificateFile = Optional.empty();
        runtime.tlsPrivateKeyFile = Optional.empty();
        return runtime;
    }
}
