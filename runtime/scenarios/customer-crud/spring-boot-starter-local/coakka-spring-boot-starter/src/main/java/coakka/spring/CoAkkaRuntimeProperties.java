package coakka.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getSourceTarget() {
        return sourceTarget;
    }

    public void setSourceTarget(String sourceTarget) {
        this.sourceTarget = sourceTarget;
    }

    public long getGeneration() {
        return generation;
    }

    public void setGeneration(long generation) {
        this.generation = generation;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public boolean isStrictNoDrop() {
        return strictNoDrop;
    }

    public void setStrictNoDrop(boolean strictNoDrop) {
        this.strictNoDrop = strictNoDrop;
    }

    public boolean isSeparateDeliveredRequestLane() {
        return separateDeliveredRequestLane;
    }

    public void setSeparateDeliveredRequestLane(boolean separateDeliveredRequestLane) {
        this.separateDeliveredRequestLane = separateDeliveredRequestLane;
    }

    public String getLocalEndpointHost() {
        return localEndpointHost;
    }

    public void setLocalEndpointHost(String localEndpointHost) {
        this.localEndpointHost = localEndpointHost;
    }

    public int getLocalEndpointPort() {
        return localEndpointPort;
    }

    public void setLocalEndpointPort(int localEndpointPort) {
        this.localEndpointPort = localEndpointPort;
    }
}
