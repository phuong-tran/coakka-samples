package coakka.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import coakka.v2.connector.CoakkaRuntimeStartupCpuPolicies;
import coakka.v2.connector.CoakkaRuntimeStartupDefaultGroups;
import coakka.v2.connector.ConnectorOrchestrator;
import coakka.v2.connector.DeadletterException;
import coakka.v2.connector.RuntimeAskTimeoutException;
import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.RuntimeStartupConfigSnapshot;
import coakka.v2.connector.RuntimeTcpConnectionMode;
import coakka.v2.connector.RuntimeTcpSecurityApplyException;
import coakka.v2.connector.protocol.ConnectorDeadletterReason;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class CoAkkaRuntimeContextTest {
    @Test
    void contextProjectsCoreDefaultsAndClosesItsOwner() {
        ConnectorOrchestrator owned;
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            CoAkkaRuntime runtime = context.getBean(CoAkkaRuntime.class);
            owned = runtime.getOrchestrator();
            RuntimeStartupConfigSnapshot accepted = runtime.startupConfig();
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION));
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION));
            assertTrue(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR));
            assertEquals(CoakkaRuntimeStartupCpuPolicies.AUTO, accepted.getExecution().getRequestedPolicy());
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, accepted.getConnection().getMode());
            assertFalse(accepted.getMonitorEnabled());
            assertEquals(runtime.runtimeConfig().getQueueCapacity(), accepted.getQueueCapacity());
            assertEquals("started", runtime.runtimeConfig().getRuntimeStateName());
        }
        assertThrows(RuntimeException.class, owned::runtimeConfig);
    }

    @Test
    void boundMonitorOnAndOffAreCoreAcceptedChoices() {
        for (boolean enabled : new boolean[] {false, true}) {
            try (AnnotationConfigApplicationContext context = context(
                Map.of("coakka.runtime.monitor-enabled", Boolean.toString(enabled))
            )) {
                CoAkkaRuntimeClient client = context.getBean(CoAkkaRuntimeClient.class);
                RuntimeStartupConfigSnapshot accepted = client.startupConfig();
                assertEquals(enabled, accepted.getMonitorEnabled());
                assertFalse(accepted.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR));
                assertNotNull(context.getBean(CoAkkaRuntime.class).getOrchestrator().getMonitor().snapshot(0L));
            }
        }
    }

    @Test
    void disabledAutoConfigurationCreatesNoRuntimeOrClient() {
        try (AnnotationConfigApplicationContext context = context(Map.of("coakka.runtime.enabled", "false"))) {
            assertTrue(context.getBeansOfType(CoAkkaRuntime.class).isEmpty());
            assertTrue(context.getBeansOfType(CoAkkaRuntimeClient.class).isEmpty());
        }
    }

    @Test
    void jsonRepliesIncludeUnicodeAndNullWithoutLosingCorrelation() throws Exception {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            CoAkkaRuntimeClient client = context.getBean(CoAkkaRuntimeClient.class);
            Reply reply = client.askBlocking("test.echo", new Request("Xin chào 空"), Reply.class, "unicode", 1_000);
            assertEquals("Xin chào 空", reply.text());
            assertNull(client.askBlocking("test.null", new Request("null"), Reply.class, "null", 1_000));
            assertEquals(0, client.clientStats().getPendingRequests());
            assertEquals(2, client.clientStats().getMatchedResponses());
        }
    }

    @Test
    void routeMissRetainsCoreDeadletterAndJsonDecodeFailureDoesNotStopTheOwner() throws Exception {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            CoAkkaRuntimeClient client = context.getBean(CoAkkaRuntimeClient.class);
            DeadletterException missing = assertThrows(DeadletterException.class, () ->
                client.askBlocking("test.missing", new Request("missing"), Reply.class, "missing", 1_000)
            );
            assertEquals(ConnectorDeadletterReason.ROUTE_MISS, missing.getDeadletter().getReason());
            assertThrows(JsonMappingException.class, () ->
                client.askBlocking("test.echo", new Request("wrong reply type"), Integer.class, "decode", 1_000)
            );
            assertEquals("still live", client.askBlocking(
                "test.echo", new Request("still live"), Reply.class, "after-decode", 1_000
            ).text());
            assertEquals(0, client.clientStats().getPendingRequests());
        }
    }

    @Test
    void duplicateTargetsAndInvalidSignaturesFailBeforeRuntimeCreation() {
        for (Class<?> handler : new Class<?>[] {DuplicateHandler.class, InvalidHandler.class, VoidHandler.class}) {
            AtomicInteger starts = new AtomicInteger();
            assertThrows(IllegalArgumentException.class, () -> CoAkkaRuntime.start(
                new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(handler), spec -> {
                    starts.incrementAndGet();
                    throw new AssertionError("invalid handler configuration must not create a runtime");
                }
            ));
            assertEquals(0, starts.get());
        }
    }

    @Test
    void genericHandlerBridgeIsNotAnotherTargetRegistration() throws Exception {
        try (CoAkkaRuntime runtime = CoAkkaRuntime.start(
            new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(GenericHandler.class)
        )) {
            CoAkkaRuntimeClient client = new CoAkkaRuntimeClient(runtime, new ObjectMapper());
            assertEquals("generic", client.askBlocking(
                "test.generic", new Request("generic"), Reply.class, "generic", 1_000
            ).text());
        }
    }

    @Test
    void overriddenInheritedHandlerIsDiscoveredOnce() throws Exception {
        try (CoAkkaRuntime runtime = CoAkkaRuntime.start(
            new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(InheritedHandler.class)
        )) {
            CoAkkaRuntimeClient client = new CoAkkaRuntimeClient(runtime, new ObjectMapper());
            assertEquals("child", client.askBlocking(
                "test.inherited", new Request("child"), Reply.class, "inherited", 1_000
            ).text());
        }
    }

    @Test
    void rejectedSecurityIsNotReplacedWithAnUnsecuredRuntime() {
        CoAkkaRuntimeProperties properties = new CoAkkaRuntimeProperties();
        properties.setTcpSecurityMode("tls");
        RuntimeTcpSecurityApplyException refused = assertThrows(RuntimeTcpSecurityApplyException.class, () ->
            CoAkkaRuntime.start(properties, new ObjectMapper(), beans(EchoHandler.class))
        );
        assertFalse(refused.getResult().applied());
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            assertFalse(context.getBean(CoAkkaRuntimeClient.class).startupConfig().getMonitorEnabled());
        }
    }

    @Test
    void coreTimeoutRemainsDistinctAndTheNextRequestCanComplete() throws Exception {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            CoAkkaRuntime runtime = context.getBean(CoAkkaRuntime.class);
            runtime.getOrchestrator().applySnapshot(2, List.of(
                RuntimeClient.localRoute("test.echo"), RuntimeClient.localRoute("test.unhandled")
            ), "spring-timeout-control");
            CoAkkaRuntimeClient client = context.getBean(CoAkkaRuntimeClient.class);
            assertThrows(RuntimeAskTimeoutException.class, () ->
                client.askBlocking("test.unhandled", new Request("timeout"), Reply.class, "timeout", 100)
            );
            assertEquals("after timeout", client.askBlocking(
                "test.echo", new Request("after timeout"), Reply.class, "after-timeout", 1_000
            ).text());
            assertEquals(0, client.clientStats().getPendingRequests());
        }
    }

    @Test
    void simultaneousContextOwnerCloseCallsConvergeWithoutLeakingThreads() throws Exception {
        CoAkkaRuntime runtime = CoAkkaRuntime.start(
            new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(EchoHandler.class)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable close = () -> {
            ready.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("close control was not released");
                }
                runtime.close();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Thread first = new Thread(close, "coakka-spring-close-first");
        Thread second = new Thread(close, "coakka-spring-close-second");
        first.start();
        second.start();
        try {
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            release.countDown();
            first.join(3_000);
            second.join(3_000);
            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertNull(failure.get());
            assertThrows(RuntimeException.class, runtime::runtimeConfig);
        } finally {
            release.countDown();
            first.join(3_000);
            second.join(3_000);
            runtime.close();
        }
    }

    @Test
    void startupPropertiesAndRequestSourceAreIndependentOfLaterBeanMutation() throws Exception {
        CoAkkaRuntimeProperties requested = new CoAkkaRuntimeProperties();
        requested.setSourceTarget("test.startup-source");
        requested.setQueueCapacity(128);
        try (CoAkkaRuntime runtime = CoAkkaRuntime.start(
            requested, new ObjectMapper(), beans(EchoHandler.class), spec -> {
                requested.setSourceTarget("test.mutated-during-start");
                requested.setQueueCapacity(256);
                requested.setMonitorEnabled(true);
                return ConnectorOrchestrator.start(spec);
            }
        )) {
            requested.setSourceTarget("test.mutated-after-start");
            CoAkkaRuntimeProperties inspected = runtime.getProperties();
            assertNotSame(requested, inspected);
            assertEquals("test.startup-source", inspected.getSourceTarget());
            assertEquals(128, inspected.getQueueCapacity());
            assertNull(inspected.getMonitorEnabled());
            inspected.setSourceTarget("test.mutated-inspection");
            inspected.setQueueCapacity(512);
            inspected.setMonitorEnabled(true);
            assertNotSame(inspected, runtime.getProperties());
            assertEquals("test.startup-source", runtime.getProperties().getSourceTarget());
            assertEquals(128, runtime.getProperties().getQueueCapacity());
            assertNull(runtime.getProperties().getMonitorEnabled());
            assertEquals(128, runtime.startupConfig().getQueueCapacity());
            assertFalse(runtime.startupConfig().getMonitorEnabled());

            AtomicReference<String> observedSource = new AtomicReference<>();
            runtime.getOrchestrator().applySnapshot(
                2, List.of(RuntimeClient.localRoute("test.origin")), "spring-source-control"
            );
            runtime.getOrchestrator().registerHandler("test.origin", (request, continuation) -> {
                observedSource.set(request.getSource());
                return RuntimeClient.Companion.replyTo(
                    request, "test.origin", "{\"text\":\"origin\"}",
                    CoAkkaRuntime.payloadIdentity(new Reply("origin"))
                );
            });
            CoAkkaRuntimeClient client = new CoAkkaRuntimeClient(runtime, new ObjectMapper());
            assertEquals("origin", client.askBlocking(
                "test.origin", new Request("source"), Reply.class, "source", 1_000
            ).text());
            assertEquals("test.startup-source", observedSource.get());
        }
    }

    @Test
    void holdingThePublicObjectMonitorDoesNotDelayOwnedClose() throws Exception {
        CoAkkaRuntime runtime = CoAkkaRuntime.start(
            new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(EchoHandler.class)
        );
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                runtime.close();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                closed.countDown();
            }
        }, "coakka-spring-public-monitor-control");
        try {
            synchronized (runtime) {
                caller.start();
                assertTrue(closed.await(2, TimeUnit.SECONDS));
            }
            assertNull(failure.get());
            assertThrows(RuntimeException.class, runtime::runtimeConfig);
        } finally {
            caller.join(3_000);
            assertFalse(caller.isAlive());
            runtime.close();
        }
    }

    @Test
    void registrationFailureClosesTheAlreadyCreatedOwner() {
        AtomicReference<ConnectorOrchestrator> underlying = new AtomicReference<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> CoAkkaRuntime.start(
            new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(EchoHandler.class), spec -> {
                ConnectorOrchestrator real = ConnectorOrchestrator.start(spec);
                underlying.set(real);
                return (ConnectorOrchestrator) Proxy.newProxyInstance(
                    ConnectorOrchestrator.class.getClassLoader(), new Class<?>[] {ConnectorOrchestrator.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("registerHandler")) {
                            throw new IllegalStateException("injected registration failure");
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    }
                );
            }
        ));
        assertEquals("injected registration failure", failure.getMessage());
        assertNotNull(underlying.get());
        assertThrows(RuntimeException.class, underlying.get()::runtimeConfig);
    }

    @Test
    void registrationFailureStillClosesTheOwnerWhenCallerWasInterrupted() {
        AtomicReference<ConnectorOrchestrator> underlying = new AtomicReference<>();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> CoAkkaRuntime.start(
                new CoAkkaRuntimeProperties(), new ObjectMapper(), beans(EchoHandler.class), spec -> {
                    ConnectorOrchestrator real = ConnectorOrchestrator.start(spec);
                    underlying.set(real);
                    return (ConnectorOrchestrator) Proxy.newProxyInstance(
                        ConnectorOrchestrator.class.getClassLoader(), new Class<?>[] {ConnectorOrchestrator.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("registerHandler")) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("interrupted registration failure");
                            }
                            try {
                                return method.invoke(real, args);
                            } catch (InvocationTargetException error) {
                                throw error.getCause();
                            }
                        }
                    );
                }
            ));
            assertEquals("interrupted registration failure", failure.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
            assertThrows(RuntimeException.class, underlying.get()::runtimeConfig);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void callerWaitOverflowRefusesBeforeSubmittingAnyRequest() {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            CoAkkaRuntimeClient client = context.getBean(CoAkkaRuntimeClient.class);
            assertThrows(IllegalArgumentException.class, () -> client.askBlocking(
                "test.echo", new Request("overflow"), Reply.class, "overflow", Long.MAX_VALUE
            ));
            assertEquals(0, client.clientStats().getPendingRequests());
            assertEquals(0, client.clientStats().getDeliveredRequests());
        }
    }

    @Test
    void expiredShutdownCannotRetryForeverWhenEveryWaitIsInterrupted() {
        AtomicInteger waits = new AtomicInteger();
        CompletableFuture<Void> completion = new CompletableFuture<>() {
            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException {
                if (waits.incrementAndGet() != 1) {
                    throw new AssertionError("expired shutdown retried after interruption");
                }
                throw new InterruptedException("injected repeated interrupt");
            }
        };
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                CoAkkaRuntime.awaitShutdown(completion, System.nanoTime() - 1)
            );
            assertTrue(failure.getCause() instanceof TimeoutException);
            assertEquals(1, waits.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptedCallerKeepsItsInterruptFlag() throws Exception {
        BlockingHandler handler = new BlockingHandler();
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(BlockingHandler.class);
        definition.setInstanceSupplier(() -> handler);
        factory.registerBeanDefinition("handler", definition);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        try (CoAkkaRuntime runtime = CoAkkaRuntime.start(new CoAkkaRuntimeProperties(), new ObjectMapper(), factory)) {
            CoAkkaRuntimeClient client = new CoAkkaRuntimeClient(runtime, new ObjectMapper());
            Thread caller = new Thread(() -> {
                try {
                    client.askBlocking("test.block", new Request("wait"), Reply.class, "interrupt", 1_000);
                } catch (Throwable failure) {
                    outcome.set(failure);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            }, "coakka-spring-interrupt-control");
            caller.start();
            try {
                assertTrue(handler.entered.await(2, TimeUnit.SECONDS));
                caller.interrupt();
                caller.join(2_000);
                assertFalse(caller.isAlive());
                assertTrue(outcome.get() instanceof InterruptedException);
                assertEquals(Boolean.TRUE, interrupted.get());
            } finally {
                handler.release.countDown();
                caller.join(2_000);
                assertFalse(caller.isAlive());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (client.clientStats().getPendingRequests() != 0 && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertEquals(0, client.clientStats().getPendingRequests());
            }
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean(ObjectMapper.class);
        context.registerBean(EchoHandler.class);
        context.register(CoAkkaRuntimeAutoConfiguration.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    private static DefaultListableBeanFactory beans(Class<?> handler) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("handler", new RootBeanDefinition(handler));
        return factory;
    }

    record Request(String text) { }
    record Reply(String text) { }

    static final class EchoHandler {
        @CoAkkaHandler("test.echo")
        public Reply echo(Request request) { return new Reply(request.text()); }

        @CoAkkaHandler("test.null")
        public Reply nullReply(Request request) { return null; }
    }

    static final class DuplicateHandler {
        @CoAkkaHandler("same") public Reply first(Request request) { return new Reply(request.text()); }
        @CoAkkaHandler("same") public Reply second(Request request) { return new Reply(request.text()); }
    }

    static final class InvalidHandler {
        @CoAkkaHandler("invalid") public Reply invalid() { return new Reply("bad"); }
    }

    static final class VoidHandler {
        @CoAkkaHandler("void") public void invalid(Request request) { }
    }

    interface GenericContract<P, R> {
        R handle(P request);
    }

    static final class GenericHandler implements GenericContract<Request, Reply> {
        @Override
        @CoAkkaHandler("test.generic")
        public Reply handle(Request request) { return new Reply(request.text()); }
    }

    static class ParentHandler {
        @CoAkkaHandler("test.inherited")
        public Reply handle(Request request) { return new Reply("parent"); }
    }

    static final class InheritedHandler extends ParentHandler {
        @Override
        public Reply handle(Request request) { return new Reply(request.text()); }
    }

    static final class BlockingHandler {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @CoAkkaHandler("test.block")
        public Reply block(Request request) throws InterruptedException {
            entered.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release the handler");
            }
            return new Reply(request.text());
        }
    }
}
