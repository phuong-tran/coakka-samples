package coakka.spring;

import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeInfoSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class CoAkkaRuntimeClient {
    private final CoAkkaRuntime runtime;
    private final ObjectMapper objectMapper;

    public CoAkkaRuntimeClient(CoAkkaRuntime runtime, ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    public <T> T askBlocking(
        String target,
        Object payload,
        Class<T> responseType,
        String operation,
        long timeoutMs
    ) throws Exception {
        ConnectorEnvelope response;
        try {
            response = runtime.getOrchestrator().getJava().ask(
                runtime.getProperties().getSourceTarget(),
                target,
                objectMapper.writeValueAsString(payload),
                CoAkkaRuntime.payloadIdentity(payload),
                timeoutMs,
                operation,
                ConnectorDeliveryHint.ROUTER_DEFAULT
            ).get(timeoutMs + 1_000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw error;
        }
        return objectMapper.readValue(response.payloadUtf8(), responseType);
    }

    public RuntimeInfoSnapshot runtimeInfo() {
        return runtime.runtimeInfo();
    }

    public RuntimeConfigSnapshot runtimeConfig() {
        return runtime.runtimeConfig();
    }

    public RuntimeClientStats clientStats() {
        return runtime.clientStats();
    }
}
