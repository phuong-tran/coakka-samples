package coakka.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeTcpConnectionStrategySpec;
import coakka.v2.connector.RuntimeTcpSecurityMode;
import coakka.v2.connector.RuntimeTcpSecuritySpec;
import coakka.v2.connector.RuntimeTlsReloadMode;
import org.junit.jupiter.api.Test;

class CoAkkaRuntimeTransportConfigTest {
    @Test
    void runtimeDefaultsDoNotCreateExplicitTransportSpecs() {
        CoAkkaRuntimeProperties properties = new CoAkkaRuntimeProperties();

        assertNull(CoAkkaRuntime.connectionStrategy(properties));
        assertNull(CoAkkaRuntime.securityPolicy(properties));
    }

    @Test
    void mapsConnectionStrategyAndOptionalTuningWithoutChangingValues() {
        CoAkkaRuntimeProperties properties = new CoAkkaRuntimeProperties();
        properties.setTcpConnectionStrategy("bounded_pool");
        properties.setTcpMaxConnections(4);
        properties.setTcpMaxRequestsPerConnection(200L);
        properties.setTcpIdleTimeoutMs(5_000L);

        RuntimeTcpConnectionStrategySpec spec = CoAkkaRuntime.connectionStrategy(properties);

        assertEquals(RuntimeTcpConnectionMode.BOUNDED_POOL, spec.getMode());
        assertEquals(4, spec.getMaxConnections());
        assertEquals(200L, spec.getMaxRequestsPerConnection());
        assertEquals(5_000L, spec.getIdleTimeoutMs());
    }

    @Test
    void mapsFileBackedMutualTlsPolicyWithoutReadingCredentials() {
        CoAkkaRuntimeProperties properties = new CoAkkaRuntimeProperties();
        properties.setTcpSecurityMode("mtls");
        properties.setTlsReloadMode("drain_existing_connections");
        properties.setTlsCredentialGeneration(7L);
        properties.setTlsCredentialId("spring-generation-7");
        properties.setTlsCaCertificateFile("/run/secrets/coakka/ca.pem");
        properties.setTlsIdentityCertificateFile("/run/secrets/coakka/node.pem");
        properties.setTlsPrivateKeyFile("/run/secrets/coakka/node.key");

        RuntimeTcpSecuritySpec spec = CoAkkaRuntime.securityPolicy(properties);

        assertEquals(RuntimeTcpSecurityMode.MUTUAL_TLS, spec.getMode());
        assertEquals(RuntimeTlsReloadMode.DRAIN_EXISTING_CONNECTIONS, spec.getReloadMode());
        assertEquals(7L, spec.getCredentialGeneration());
        assertEquals("spring-generation-7", spec.getCredentialId());
        assertEquals("/run/secrets/coakka/node.key", spec.getPrivateKeyFile());
    }

    @Test
    void tuningAndCredentialsRequireAnExplicitMode() {
        CoAkkaRuntimeProperties connection = new CoAkkaRuntimeProperties();
        connection.setTcpMaxConnections(4);
        assertThrows(
            IllegalArgumentException.class,
            () -> CoAkkaRuntime.connectionStrategy(connection)
        );

        CoAkkaRuntimeProperties security = new CoAkkaRuntimeProperties();
        security.setTlsCredentialId("generation-1");
        assertThrows(
            IllegalArgumentException.class,
            () -> CoAkkaRuntime.securityPolicy(security)
        );

        CoAkkaRuntimeProperties reload = new CoAkkaRuntimeProperties();
        reload.setTlsReloadMode("drain-existing-connections");
        assertThrows(
            IllegalArgumentException.class,
            () -> CoAkkaRuntime.securityPolicy(reload)
        );
    }
}
