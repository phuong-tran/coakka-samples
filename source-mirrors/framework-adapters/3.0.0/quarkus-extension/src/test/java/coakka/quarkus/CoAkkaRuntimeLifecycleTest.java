package coakka.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import coakka.v2.connector.CoakkaRuntimeStartupCpuPolicies;
import coakka.v2.connector.CoakkaRuntimeStartupDefaultGroups;
import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.DeadletterException;
import coakka.v2.connector.RuntimeAskTimeoutException;
import coakka.v2.connector.RuntimeMonitorOptions;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.protocol.ConnectorDeadletterReason;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadFormat;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Actual selected Runtime package controls for the thin CDI lifecycle boundary. */
class CoAkkaRuntimeLifecycleTest {
    private static final ConnectorPayloadIdentity IDENTITY =
        new ConnectorPayloadIdentity("quarkus.test.request", 1, ConnectorPayloadFormat.JSON);

    @Test
    void omittedSettingsRemainCoreDefaultsAndSnapshotSurvivesClose() {
        CoAkkaRuntime runtime = configured(List.of());
        runtime.start();
        var accepted = runtime.startupConfig();
        try {
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION));
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION));
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR));
            assertEquals(CoakkaRuntimeStartupCpuPolicies.AUTO, accepted.getExecution().getRequestedPolicy());
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, accepted.getConnection().getMode());
            assertFalse(accepted.getMonitorEnabled());
            assertNotNull(runtime.connector().getMonitor().snapshot(0L));
            assertThrows(IllegalStateException.class,
                () -> runtime.connector().getMonitor().awaitNextBlocking(0));
        } finally {
            runtime.close();
        }
        assertFalse(accepted.getMonitorEnabled());
        assertThrows(IllegalStateException.class, runtime::connector);
        runtime.close();
    }

    @Test
    void explicitMonitorIntentIsCopiedAndOnlyNewOwnersChangeIt() {
        CoAkkaRuntime enabled = configured(List.of());
        enabled.monitorEnabled = Optional.of(true);
        enabled.start();
        try {
            assertTrue(enabled.startupConfig().getMonitorEnabled());
            assertFalse(enabled.startupConfig().wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR));
            enabled.monitorEnabled = Optional.of(false);
            assertTrue(enabled.startupConfig().getMonitorEnabled());
            assertNotNull(enabled.connector().getMonitor().awaitNextBlocking(1_000));
        } finally {
            enabled.close();
        }
        CoAkkaRuntime disabled = configured(List.of());
        disabled.monitorEnabled = Optional.of(false);
        disabled.start();
        try {
            assertFalse(disabled.startupConfig().getMonitorEnabled());
            assertFalse(disabled.startupConfig().wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR));
        } finally {
            disabled.close();
        }
    }

    @Test
    void absentFalseAndTrueMonitorGroupsRemainDistinct() {
        CoAkkaRuntime runtime = configured(List.of());
        assertNull(runtime.monitorOptions());
        runtime.monitorEnabled = Optional.of(false);
        assertEquals(new RuntimeMonitorOptions(false), runtime.monitorOptions());
        runtime.monitorEnabled = Optional.of(true);
        assertEquals(new RuntimeMonitorOptions(true), runtime.monitorOptions());
    }

    @Test
    void disabledAdapterDoesNotCreateAnOwner() {
        CoAkkaRuntime runtime = configured(List.of());
        runtime.enabled = false;
        runtime.start();
        assertThrows(IllegalStateException.class, runtime::connector);
        runtime.close();
    }

    @Test
    void badFrameworkModeRefusesBeforeRuntimePublication() {
        CoAkkaRuntime runtime = configured(List.of());
        runtime.mode = "unsupported";
        assertThrows(IllegalArgumentException.class, runtime::start);
        assertThrows(IllegalStateException.class, runtime::connector);
        runtime.close();
    }

    @Test
    void duplicateApplicationHandlersRefuseBeforeRuntimePublication() {
        CoAkkaLocalHandler first = handler("quarkus.duplicate", (request, mapper) -> request);
        CoAkkaLocalHandler second = handler("quarkus.duplicate", (request, mapper) -> request);
        CoAkkaRuntime runtime = configured(List.of(first, second));
        assertThrows(IllegalArgumentException.class, runtime::start);
        assertThrows(IllegalStateException.class, runtime::connector);
        runtime.close();
    }

    @Test
    void handlerTargetIsCapturedOnceForRoutesAndRegistration() throws Exception {
        AtomicInteger targetReads = new AtomicInteger();
        CoAkkaLocalHandler handler = new CoAkkaLocalHandler() {
            @Override
            public String target() {
                targetReads.incrementAndGet();
                return "quarkus.once";
            }

            @Override
            public ConnectorEnvelope handle(ConnectorEnvelope request, ObjectMapper mapper) throws Exception {
                return CoAkkaReplies.json(request, "quarkus.once", new Reply("once"), IDENTITY, mapper);
            }
        };
        CoAkkaRuntime runtime = configured(List.of(handler));
        runtime.start();
        try {
            Reply result = client(runtime).askBlocking("quarkus.once", new Request("Ada"), IDENTITY,
                Reply.class, "quarkus_target_once", 1_000);
            assertEquals("once", result.message());
            assertEquals(1, targetReads.get());
        } finally {
            runtime.close();
        }
    }

    @Test
    void jsonHandlerMapsApplicationValuesAndPreservesRequestCorrelation() throws Exception {
        CoAkkaRuntime runtime = configured(List.of(new GreetingHandler()));
        runtime.start();
        try {
            CoAkkaRuntimeClient client = client(runtime);
            Reply result = client.askBlocking("quarkus.greet", new Request("Ada"), IDENTITY,
                Reply.class, "quarkus_json", 1_000);
            assertEquals("hello Ada", result.message());
            assertEquals(runtime.startupConfig(), client.startupConfig());
            assertTrue(client.clientStats().getMatchedResponses() >= 1);
        } finally {
            runtime.close();
        }
    }

    @Test
    void routeMissKeepsCoreReasonAndDoesNotBreakLaterCalls() throws Exception {
        CoAkkaRuntime runtime = configured(List.of(new GreetingHandler()));
        runtime.start();
        try {
            CoAkkaRuntimeClient client = client(runtime);
            DeadletterException failure = assertThrows(DeadletterException.class,
                () -> client.askBlocking("quarkus.missing", new Request("Ada"), IDENTITY,
                    Reply.class, "quarkus_missing", 1_000));
            assertEquals(ConnectorDeadletterReason.ROUTE_MISS, failure.getDeadletter().getReason());
            assertNotNull(failure.getDeadletter().getOriginalEnvelope());
            assertEquals("hello Grace", client.askBlocking("quarkus.greet", new Request("Grace"), IDENTITY,
                Reply.class, "quarkus_after_missing", 1_000).message());
        } finally {
            runtime.close();
        }
    }

    @Test
    void nullReplyUsesCoreTimeoutAndContextCanStillReply() throws Exception {
        CoAkkaRuntime runtime = configured(List.of(
            new GreetingHandler(), handler("quarkus.no-reply", (request, mapper) -> null)
        ));
        runtime.start();
        try {
            CoAkkaRuntimeClient client = client(runtime);
            assertThrows(RuntimeAskTimeoutException.class,
                () -> client.askBlocking("quarkus.no-reply", new Request("Ada"), IDENTITY,
                    Reply.class, "quarkus_no_reply", 100));
            assertEquals("hello Lin", client.askBlocking("quarkus.greet", new Request("Lin"), IDENTITY,
                Reply.class, "quarkus_after_timeout", 1_000).message());
        } finally {
            runtime.close();
        }
    }

    @Test
    void unrepresentableHostWaitRefusesBeforeSubmittingAnAsk() {
        CoAkkaRuntime runtime = configured(List.of(new GreetingHandler()));
        runtime.start();
        try {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client(runtime).askBlocking("quarkus.greet", new Request("Ada"), IDENTITY,
                    Reply.class, "quarkus_overflow", Long.MAX_VALUE));
            assertTrue(failure.getMessage().contains("wait budget"));
            assertEquals(0, runtime.clientStats().getPendingRequests());
            assertEquals(0, runtime.clientStats().getDeliveredRequests());
        } finally {
            runtime.close();
        }
    }

    @Test
    void blockingCallerInterruptionRemainsVisible() throws InterruptedException {
        CoAkkaRuntime runtime = configured(List.of(handler("quarkus.no-reply", (request, mapper) -> null)));
        runtime.start();
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class,
                () -> client(runtime).askBlocking("quarkus.no-reply", new Request("Ada"), IDENTITY,
                    Reply.class, "quarkus_interrupted", 1_000));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (runtime.clientStats().getPendingRequests() != 0 && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertEquals(0, runtime.clientStats().getPendingRequests());
            } finally {
                runtime.close();
            }
        }
    }

    @Test
    void closeCompletesPendingAskAndRejectsNewFacadeAccess() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CoAkkaRuntime runtime = configured(List.of(handler("quarkus.no-reply", (request, mapper) -> {
            received.countDown();
            return null;
        })));
        runtime.start();
        var pending = runtime.connector().getJava().ask("quarkus.test.source", "quarkus.no-reply",
            "{}", IDENTITY, 5_000, "quarkus_close_pending",
            coakka.v2.connector.protocol.ConnectorDeliveryHint.ROUTER_DEFAULT);
        try {
            assertTrue(received.await(1, TimeUnit.SECONDS));
            runtime.close();
            assertTrue(pending.isDone());
            try {
                pending.get(1, TimeUnit.SECONDS);
                throw new AssertionError("pending request unexpectedly replied after close");
            } catch (ExecutionException | CancellationException expected) {
                // The existing connector owns closed/cancelled terminal projection.
            }
            assertThrows(IllegalStateException.class, () -> client(runtime).runtimeInfo());
        } finally {
            runtime.close();
        }
    }

    @Test
    void registrationFailureClosesItsRealOwnerDespiteCallerInterruption() throws Exception {
        for (boolean interrupt : new boolean[] {false, true}) {
            CoAkkaRuntime runtime = configured(List.of(new GreetingHandler()));
            AtomicReference<ConnectorOrchestrator> real = new AtomicReference<>();
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> runtime.start(spec -> {
                    ConnectorOrchestrator owner = ConnectorOrchestrator.start(spec);
                    real.set(owner);
                    return (ConnectorOrchestrator) Proxy.newProxyInstance(
                        ConnectorOrchestrator.class.getClassLoader(), new Class<?>[] {ConnectorOrchestrator.class},
                        (proxy, method, arguments) -> {
                            if ("registerHandler".equals(method.getName())) {
                                if (interrupt) Thread.currentThread().interrupt();
                                throw new IllegalStateException("injected registration refusal");
                            }
                            try {
                                return method.invoke(owner, arguments);
                            } catch (InvocationTargetException wrapped) {
                                throw wrapped.getCause();
                            }
                        });
                }));
                assertEquals("injected registration refusal", failure.getMessage());
                assertEquals(interrupt, Thread.currentThread().isInterrupted());
                assertNotNull(real.get());
                assertThrows(RuntimeException.class, real.get()::startupConfig);
                assertThrows(IllegalStateException.class, runtime::connector);
            } finally {
                Thread.interrupted();
                runtime.close();
                if (real.get() != null) real.get().getJava().shutdown().get(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void interruptedCloseWaitsForCleanupAndRestoresTheFlag() {
        CoAkkaRuntime runtime = configured(List.of());
        runtime.start();
        ConnectorOrchestrator owner = runtime.connector();
        try {
            Thread.currentThread().interrupt();
            runtime.close();
            assertTrue(Thread.currentThread().isInterrupted());
            assertThrows(RuntimeException.class, owner::startupConfig);
        } finally {
            Thread.interrupted();
            runtime.close();
        }
    }

    @Test
    void holdingTheBeanMonitorCannotBlockOwnedShutdown() throws Exception {
        CoAkkaRuntime runtime = configured(List.of());
        runtime.start();
        ConnectorOrchestrator owner = runtime.connector();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            entered.countDown();
            try {
                runtime.close();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        }, "quarkus-private-close-monitor-control");
        try {
            synchronized (runtime) {
                closer.start();
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                assertTrue(completed.await(4, TimeUnit.SECONDS));
                assertNull(failure.get());
                assertThrows(RuntimeException.class, owner::startupConfig);
                assertThrows(IllegalStateException.class, runtime::connector);
            }
        } finally {
            closer.join(4_000);
            runtime.close();
            assertFalse(closer.isAlive());
        }
    }

    @Test
    void repeatedInterruptsCannotExtendTheShutdownBound() throws Exception {
        CoAkkaRuntime runtime = configured(List.of());
        AtomicReference<ConnectorOrchestrator> real = new AtomicReference<>();
        runtime.start(spec -> {
            ConnectorOrchestrator owner = ConnectorOrchestrator.start(spec);
            real.set(owner);
            ConnectorOrchestrator.Java java = (ConnectorOrchestrator.Java) Proxy.newProxyInstance(
                ConnectorOrchestrator.Java.class.getClassLoader(), new Class<?>[] {ConnectorOrchestrator.Java.class},
                (proxy, method, arguments) -> {
                    if ("shutdown".equals(method.getName())) return new CompletableFuture<Void>();
                    try {
                        return method.invoke(owner.getJava(), arguments);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                });
            return (ConnectorOrchestrator) Proxy.newProxyInstance(
                ConnectorOrchestrator.class.getClassLoader(), new Class<?>[] {ConnectorOrchestrator.class},
                (proxy, method, arguments) -> {
                    if ("getJava".equals(method.getName())) return java;
                    try {
                        return method.invoke(owner, arguments);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                });
        });
        AtomicBoolean stop = new AtomicBoolean();
        Thread caller = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            while (!stop.get()) {
                caller.interrupt();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException cancelled) {
                    return;
                }
            }
        }, "quarkus-close-interrupt-control");
        try {
            long begin = System.nanoTime();
            interrupter.start();
            IllegalStateException failure = assertThrows(IllegalStateException.class, runtime::close);
            assertTrue(failure.getCause() instanceof TimeoutException);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(System.nanoTime() - begin < TimeUnit.SECONDS.toNanos(4));
        } finally {
            stop.set(true);
            interrupter.interrupt();
            Thread.interrupted();
            long joinDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (interrupter.isAlive() && joinDeadline - System.nanoTime() > 0) {
                try {
                    interrupter.join(10);
                } catch (InterruptedException lastControlInterrupt) {
                    // Reap the fixture even if its final interrupt raced teardown.
                }
            }
            Thread.interrupted();
            assertFalse(interrupter.isAlive());
            real.get().getJava().shutdown().get(3, TimeUnit.SECONDS);
            runtime.close();
        }
    }

    @Test
    void malformedJsonFailsAtApplicationMappingBoundary() {
        GreetingHandler handler = new GreetingHandler();
        assertThrows(JsonProcessingException.class,
            () -> handler.handle(new ConnectorEnvelope("id", "", "source", "quarkus.greet", "",
                coakka.v2.connector.protocol.ConnectorMessageKind.REQUEST, false, 0,
                new byte[] {'['}, java.util.Map.of(),
                coakka.v2.connector.protocol.ConnectorBusinessStatus.OK, "", "",
                coakka.v2.connector.protocol.ConnectorDeliveryHint.ROUTER_DEFAULT,
                "quarkus.test.request", 1, ConnectorPayloadFormat.JSON), new ObjectMapper()));
    }

    private static CoAkkaRuntimeClient client(CoAkkaRuntime runtime) {
        return new CoAkkaRuntimeClient(runtime, runtime.objectMapper);
    }

    private static CoAkkaRuntime configured(List<CoAkkaLocalHandler> handlers) {
        CoAkkaRuntime runtime = new CoAkkaRuntime();
        runtime.objectMapper = new ObjectMapper();
        runtime.handlers = instances(handlers);
        runtime.enabled = true;
        runtime.mode = "local";
        runtime.systemName = "quarkus-unit";
        runtime.nodeId = "quarkus-unit-1";
        runtime.sourceTarget = "quarkus.test.source";
        runtime.generation = 1;
        runtime.queueCapacity = 0;
        runtime.strictNoDrop = true;
        runtime.monitorEnabled = Optional.empty();
        runtime.localEndpointHost = "127.0.0.1";
        runtime.localEndpointPort = 0;
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

    @SuppressWarnings("unchecked")
    private static Instance<CoAkkaLocalHandler> instances(List<CoAkkaLocalHandler> handlers) {
        return (Instance<CoAkkaLocalHandler>) Proxy.newProxyInstance(
            Instance.class.getClassLoader(), new Class<?>[] {Instance.class},
            (proxy, method, arguments) -> {
                if ("iterator".equals(method.getName())) return handlers.iterator();
                throw new UnsupportedOperationException("unused CDI fixture method: " + method.getName());
            });
    }

    private interface ReplyFunction {
        ConnectorEnvelope call(ConnectorEnvelope request, ObjectMapper mapper) throws Exception;
    }

    private static CoAkkaLocalHandler handler(String target, ReplyFunction function) {
        return new CoAkkaLocalHandler() {
            @Override
            public String target() {
                return target;
            }

            @Override
            public ConnectorEnvelope handle(ConnectorEnvelope request, ObjectMapper mapper) throws Exception {
                return function.call(request, mapper);
            }
        };
    }

    public record Request(String name) {
    }

    public record Reply(String message) {
    }

    @CoAkkaHandler("quarkus.greet")
    public static class GreetingHandler implements CoAkkaJsonHandler<Request, Reply> {
        @Override
        public Class<Request> requestType() {
            return Request.class;
        }

        @Override
        public ConnectorPayloadIdentity responseIdentity(Reply response) {
            return IDENTITY;
        }

        @Override
        public Reply handlePayload(Request request) {
            return new Reply("hello " + request.name());
        }
    }
}
