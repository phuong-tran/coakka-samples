package benchmark.jvm;

import coakka.http.BodyDelivery;
import coakka.http.BodyPolicy;
import coakka.http.CoreConfiguration;
import coakka.http.CoreEvent;
import coakka.http.CoreLimits;
import coakka.http.CoreRoute;
import coakka.http.HttpCore;
import coakka.http.HttpRuntime;
import coakka.http.IoBackend;
import coakka.http.IoBackendFallbackReason;
import coakka.http.Listener;
import coakka.http.ListenerProtocol;
import coakka.http.Response;
import coakka.http.Responses;
import coakka.http.RuntimeInfo;
import coakka.http.TransportSecurity;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class CoreFixedServer {
    private static final byte[] BODY =
        "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private CoreFixedServer() {}

    static void run(String value) throws InterruptedException {
        System.setProperty(
            "coakka.http.core.path",
            requiredEnvironment("COAKKA_BENCHMARK_CORE_LIBRARY")
        );
        System.setProperty(
            "coakka.http.bridge.path",
            requiredEnvironment("COAKKA_BENCHMARK_JVM_BRIDGE_LIBRARY")
        );
        String backendName = value.toLowerCase(Locale.ROOT);
        IoBackend backend = switch (backendName) {
            case "platform-default" -> IoBackend.PLATFORM_DEFAULT;
            case "io-uring" -> IoBackend.IO_URING;
            default -> throw new IllegalArgumentException("unsupported I/O backend: " + value);
        };
        String certificate = requiredEnvironment("COAKKA_BENCHMARK_TLS_CERT");
        String privateKey = requiredEnvironment("COAKKA_BENCHMARK_TLS_KEY");
        RuntimeInfo hostRuntime = HttpRuntime.runtimeInfo();
        if (backend == IoBackend.IO_URING && !hostRuntime.getIoUringSupported()) {
            throw new IllegalStateException(
                "io_uring benchmark stopped: Core reports unsupported (compiled="
                    + hostRuntime.getIoUringCompiled() + " probe_error="
                    + hostRuntime.getIoUringProbeError() + ")"
            );
        }
        HttpCore core;
        try (CoreConfiguration configuration = new CoreConfiguration()) {
            core = configuration
                .limits(coreLimits())
                .listener(new Listener(
                    1L,
                    "127.0.0.1",
                    0,
                    ListenerProtocol.HTTP_2,
                    TransportSecurity.TLS,
                    1L,
                    "benchmark-server",
                    certificate,
                    privateKey,
                    ""
                ))
                .route(new CoreRoute(
                    1L,
                    "GET",
                    "/fixed",
                    1L,
                    0L,
                    BodyDelivery.INLINE,
                    new BodyPolicy()
                ))
                .ioBackend(backend)
                .createCore();
        }
        core.start();
        RuntimeInfo effectiveRuntime = core.runtimeInfo();
        boolean ioUringActive = effectiveRuntime.getEffectiveBackend() == IoBackend.IO_URING;
        if (effectiveRuntime.getEffectiveBackend() != backend) {
            core.close();
            throw new IllegalStateException(
                "backend benchmark stopped: requested=" + backendName
                    + " effective=" + effectiveRuntime.getEffectiveBackend()
                    + " fallback_reason=" + effectiveRuntime.getFallbackReason()
            );
        }

        Response response = Responses.bytes(BODY);
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicInteger errors = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!stopping.get()) {
                try {
                    CoreEvent event = core.takeEvent(100L);
                    if (event instanceof CoreEvent.RequestReady) {
                        core.respond(event.getExchange(), response);
                    }
                } catch (Throwable error) {
                    if (!stopping.get()) {
                        error.printStackTrace(System.err);
                        errors.incrementAndGet();
                        System.exit(1);
                    }
                    return;
                }
            }
        }, "coakka-benchmark-events");
        reader.start();

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                core.drain();
            } catch (Throwable error) {
                errors.incrementAndGet();
                error.printStackTrace(System.err);
            }
            try {
                stopping.set(true);
                try {
                    core.interruptEventReader();
                } catch (Throwable ignored) {
                    // drain() may have already closed the reader; join below is the proof.
                }
                reader.join(5_000L);
                if (reader.isAlive()) {
                    throw new IllegalStateException("JVM event reader did not stop");
                }
                core.stop();
                core.close();
            } catch (Throwable error) {
                errors.incrementAndGet();
                error.printStackTrace(System.err);
            } finally {
                System.out.println(
                    "{\"stopped\":true,\"handler_errors\":" + errors.get() + "}"
                );
                stopped.countDown();
            }
        }, "coakka-benchmark-shutdown"));

        System.out.println(
            "{\"ready\":true,\"bound_port\":" + core.getBoundPort()
                + ",\"application_path\":\"normal\""
                + ",\"application_protocol\":2,\"transport_security_mode\":2"
                + ",\"io_backend\":\"" + backendName + "\""
                + ",\"io_uring_active\":" + ioUringActive
                + ",\"io_uring_supported\":" + hostRuntime.getIoUringSupported()
                + ",\"requested_io_backend\":" + backendCode(effectiveRuntime.getRequestedBackend())
                + ",\"effective_io_backend\":" + backendCode(effectiveRuntime.getEffectiveBackend())
                + ",\"io_backend_fallback_reason\":"
                + fallbackCode(effectiveRuntime.getFallbackReason()) + "}"
        );
        stopped.await();
    }

    private static CoreLimits coreLimits() {
        return new CoreLimits();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing " + name);
        }
        return value;
    }

    private static int backendCode(IoBackend backend) {
        return switch (backend) {
            case PLATFORM_DEFAULT -> 1;
            case IO_URING -> 2;
        };
    }

    private static int fallbackCode(IoBackendFallbackReason reason) {
        return switch (reason) {
            case NONE -> 0;
            case CONFIGURATION -> 1;
            case UNAVAILABLE -> 2;
        };
    }

}
