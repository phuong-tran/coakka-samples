package coakka.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound startup and transport properties for the context-owned CoAkka runtime. */
@ConfigurationProperties("coakka.runtime")
public class CoAkkaRuntimeProperties {
    private boolean enabled = true;
    private String mode = "local";
    private String systemName = "coakka-spring-app";
    private String nodeId = "coakka-spring-node";
    private String sourceTarget = "coakka.spring.source";
    private long generation = 1;
    private int queueCapacity = 128;
    private boolean strictNoDrop = true;
    private boolean separateDeliveredRequestLane = true;
    private String localEndpointHost = "127.0.0.1";
    private int localEndpointPort = 19172;
    private String tcpConnectionStrategy = "runtime-default";
    private Integer tcpMaxConnections;
    private Long tcpMaxRequestsPerConnection;
    private Long tcpIdleTimeoutMs;
    private String tcpSecurityMode = "runtime-default";
    private String tlsReloadMode = "graceful";
    private Long tlsCredentialGeneration;
    private String tlsCredentialId;
    private String tlsCaCertificateFile;
    private String tlsIdentityCertificateFile;
    private String tlsPrivateKeyFile;

    /** @return whether runtime startup is requested */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled whether runtime startup is requested */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return adapter mode; this release supports only {@code local} */
    public String getMode() {
        return mode;
    }

    /** @param mode adapter mode; this release supports only {@code local} */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /** @return runtime system name */
    public String getSystemName() {
        return systemName;
    }

    /** @param systemName runtime system name */
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    /** @return runtime node identity */
    public String getNodeId() {
        return nodeId;
    }

    /** @param nodeId runtime node identity */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /** @return source target used for application requests */
    public String getSourceTarget() {
        return sourceTarget;
    }

    /** @param sourceTarget source target used for application requests */
    public void setSourceTarget(String sourceTarget) {
        this.sourceTarget = sourceTarget;
    }

    /** @return startup route generation */
    public long getGeneration() {
        return generation;
    }

    /** @param generation positive startup route generation */
    public void setGeneration(long generation) {
        this.generation = generation;
    }

    /** @return bounded runtime queue capacity */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** @param queueCapacity bounded runtime queue capacity */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /** @return whether runtime overload must fail instead of dropping work */
    public boolean isStrictNoDrop() {
        return strictNoDrop;
    }

    /** @param strictNoDrop whether runtime overload must fail instead of dropping work */
    public void setStrictNoDrop(boolean strictNoDrop) {
        this.strictNoDrop = strictNoDrop;
    }

    /** @return whether delivered requests use their dedicated runtime lane */
    public boolean isSeparateDeliveredRequestLane() {
        return separateDeliveredRequestLane;
    }

    /** @param separateDeliveredRequestLane whether delivered requests use a dedicated runtime lane */
    public void setSeparateDeliveredRequestLane(boolean separateDeliveredRequestLane) {
        this.separateDeliveredRequestLane = separateDeliveredRequestLane;
    }

    /** @return local route endpoint host */
    public String getLocalEndpointHost() {
        return localEndpointHost;
    }

    /** @param localEndpointHost local route endpoint host */
    public void setLocalEndpointHost(String localEndpointHost) {
        this.localEndpointHost = localEndpointHost;
    }

    /** @return local route endpoint port; {@code 0} avoids opening a listener */
    public int getLocalEndpointPort() {
        return localEndpointPort;
    }

    /** @param localEndpointPort local route endpoint port; use {@code 0} for embedded mode */
    public void setLocalEndpointPort(int localEndpointPort) {
        this.localEndpointPort = localEndpointPort;
    }

    /** @return runtime-default or an explicit TCP connection strategy */
    public String getTcpConnectionStrategy() {
        return tcpConnectionStrategy;
    }

    /** @param tcpConnectionStrategy runtime-default or an explicit TCP connection strategy */
    public void setTcpConnectionStrategy(String tcpConnectionStrategy) {
        this.tcpConnectionStrategy = tcpConnectionStrategy;
    }

    /** @return optional maximum connection count */
    public Integer getTcpMaxConnections() {
        return tcpMaxConnections;
    }

    /** @param tcpMaxConnections optional maximum connection count */
    public void setTcpMaxConnections(Integer tcpMaxConnections) {
        this.tcpMaxConnections = tcpMaxConnections;
    }

    /** @return optional request limit per connection */
    public Long getTcpMaxRequestsPerConnection() {
        return tcpMaxRequestsPerConnection;
    }

    /** @param tcpMaxRequestsPerConnection optional request limit per connection */
    public void setTcpMaxRequestsPerConnection(Long tcpMaxRequestsPerConnection) {
        this.tcpMaxRequestsPerConnection = tcpMaxRequestsPerConnection;
    }

    /** @return optional idle connection timeout in milliseconds */
    public Long getTcpIdleTimeoutMs() {
        return tcpIdleTimeoutMs;
    }

    /** @param tcpIdleTimeoutMs optional idle connection timeout in milliseconds */
    public void setTcpIdleTimeoutMs(Long tcpIdleTimeoutMs) {
        this.tcpIdleTimeoutMs = tcpIdleTimeoutMs;
    }

    /** @return runtime-default or an explicit TCP security mode */
    public String getTcpSecurityMode() {
        return tcpSecurityMode;
    }

    /** @param tcpSecurityMode runtime-default or an explicit TCP security mode */
    public void setTcpSecurityMode(String tcpSecurityMode) {
        this.tcpSecurityMode = tcpSecurityMode;
    }

    /** @return TLS credential reload mode */
    public String getTlsReloadMode() {
        return tlsReloadMode;
    }

    /** @param tlsReloadMode TLS credential reload mode */
    public void setTlsReloadMode(String tlsReloadMode) {
        this.tlsReloadMode = tlsReloadMode;
    }

    /** @return optional monotonically increasing TLS credential generation */
    public Long getTlsCredentialGeneration() {
        return tlsCredentialGeneration;
    }

    /** @param tlsCredentialGeneration optional monotonically increasing TLS credential generation */
    public void setTlsCredentialGeneration(Long tlsCredentialGeneration) {
        this.tlsCredentialGeneration = tlsCredentialGeneration;
    }

    /** @return optional non-secret TLS credential identity */
    public String getTlsCredentialId() {
        return tlsCredentialId;
    }

    /** @param tlsCredentialId optional non-secret TLS credential identity */
    public void setTlsCredentialId(String tlsCredentialId) {
        this.tlsCredentialId = tlsCredentialId;
    }

    /** @return optional CA certificate file path borrowed during startup */
    public String getTlsCaCertificateFile() {
        return tlsCaCertificateFile;
    }

    /** @param tlsCaCertificateFile optional CA certificate file path borrowed during startup */
    public void setTlsCaCertificateFile(String tlsCaCertificateFile) {
        this.tlsCaCertificateFile = tlsCaCertificateFile;
    }

    /** @return optional identity certificate-chain file path borrowed during startup */
    public String getTlsIdentityCertificateFile() {
        return tlsIdentityCertificateFile;
    }

    /** @param tlsIdentityCertificateFile optional certificate-chain path borrowed during startup */
    public void setTlsIdentityCertificateFile(String tlsIdentityCertificateFile) {
        this.tlsIdentityCertificateFile = tlsIdentityCertificateFile;
    }

    /** @return optional private-key file path borrowed during startup */
    public String getTlsPrivateKeyFile() {
        return tlsPrivateKeyFile;
    }

    /** @param tlsPrivateKeyFile optional private-key file path borrowed during startup */
    public void setTlsPrivateKeyFile(String tlsPrivateKeyFile) {
        this.tlsPrivateKeyFile = tlsPrivateKeyFile;
    }
}
