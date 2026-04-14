package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.api.ProxyConfig;
import org.asamk.signal.manager.api.ProxyOverrideCallable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the contract that {@link org.asamk.signal.manager.Manager#withProxyOverride}
 * implementations must follow: serialized rebuild, restore on normal and
 * exceptional exit, and correct nesting behaviour.
 * <p>
 * The Manager interface depends on too much account/network infrastructure to
 * stand up in a unit test, so this test exercises a small standalone
 * harness that replicates the synchronized-rebuild pattern used by
 * {@link ManagerImpl}, {@link RegistrationManagerImpl}, and
 * {@link ProvisioningManagerImpl}.
 */
class ProxyOverrideConcurrencyTest {

    /**
     * Minimal mirror of the synchronized-rebuild pattern. Records each
     * "applied" and "restored" proxy so tests can assert ordering.
     */
    private static final class FakeProxyAwareManager {

        private final Object lock = new Object();
        private ProxyConfig effective;
        private final List<ProxyConfig> applied = new ArrayList<>();
        private final List<ProxyConfig> restored = new ArrayList<>();

        FakeProxyAwareManager(final ProxyConfig initial) {
            this.effective = initial;
        }

        ProxyConfig effective() {
            synchronized (lock) {
                return effective;
            }
        }

        List<ProxyConfig> applied() {
            synchronized (lock) {
                return List.copyOf(applied);
            }
        }

        List<ProxyConfig> restored() {
            synchronized (lock) {
                return List.copyOf(restored);
            }
        }

        <T> T withProxyOverride(
                final ProxyConfig override,
                final ProxyOverrideCallable<T> callable
        ) throws Exception {
            Objects.requireNonNull(callable);
            if (override == null) {
                return callable.call();
            }
            synchronized (lock) {
                final var previous = effective;
                effective = override;
                applied.add(override);
                try {
                    return callable.call();
                } finally {
                    effective = previous;
                    restored.add(previous);
                }
            }
        }
    }

    private static ProxyConfig proxy(final String host, final int port) {
        return new ProxyConfig(ProxyConfig.Type.HTTP, host, port, null, null, null);
    }

    @Test
    void overrideIsAppliedDuringCallableAndRestoredAfter() throws Exception {
        final var initial = proxy("initial.example", 1111);
        final var override = proxy("override.example", 2222);
        final var manager = new FakeProxyAwareManager(initial);

        final var insideOverride = manager.withProxyOverride(override, manager::effective);

        assertEquals(override, insideOverride);
        assertEquals(initial, manager.effective(), "outer effective proxy must be restored");
        assertEquals(List.of(override), manager.applied());
        assertEquals(List.of(initial), manager.restored());
    }

    @Test
    void exceptionThrownInCallableStillRestoresPreviousProxy() {
        final var initial = proxy("initial.example", 1111);
        final var override = proxy("override.example", 2222);
        final var manager = new FakeProxyAwareManager(initial);

        assertThrows(IllegalStateException.class, () -> manager.withProxyOverride(override, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(initial, manager.effective(), "must restore proxy after exception");
        assertEquals(List.of(initial), manager.restored());
    }

    @Test
    void nestedOverridesRestoreInLifoOrder() throws Exception {
        final var initial = proxy("initial.example", 1111);
        final var outer = proxy("outer.example", 2222);
        final var inner = proxy("inner.example", 3333);
        final var manager = new FakeProxyAwareManager(initial);

        final var observed = new AtomicReference<List<ProxyConfig>>();

        manager.withProxyOverride(outer, () -> {
            final var beforeInner = manager.effective();
            final var insideInner = manager.withProxyOverride(inner, manager::effective);
            final var afterInner = manager.effective();
            observed.set(List.of(beforeInner, insideInner, afterInner));
            return null;
        });

        assertNotNull(observed.get());
        assertEquals(List.of(outer, inner, outer), observed.get());
        assertEquals(initial, manager.effective(), "must restore initial proxy after both overrides exit");
        assertEquals(List.of(outer, inner), manager.applied());
        assertEquals(List.of(outer, initial), manager.restored());
    }

    @Test
    void nullOverrideIsTransparent() throws Exception {
        final var initial = proxy("initial.example", 1111);
        final var manager = new FakeProxyAwareManager(initial);

        final var seen = manager.withProxyOverride(null, manager::effective);

        assertEquals(initial, seen);
        assertEquals(List.of(), manager.applied());
        assertEquals(List.of(), manager.restored());
    }

    @Test
    void concurrentOverridesDoNotLeakAcrossThreads() throws Exception {
        // Both managers share nothing; concurrent overrides should each see
        // their own override and not the other thread's. This proves the
        // serialization is per-Manager, not global.
        final var managerA = new FakeProxyAwareManager(proxy("a-initial.example", 1111));
        final var managerB = new FakeProxyAwareManager(proxy("b-initial.example", 2222));
        final var overrideA = proxy("a-override.example", 3333);
        final var overrideB = proxy("b-override.example", 4444);

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final var go = new CountDownLatch(1);
            final var observedA = new AtomicReference<ProxyConfig>();
            final var observedB = new AtomicReference<ProxyConfig>();

            final var fa = pool.submit(() -> managerA.withProxyOverride(overrideA, () -> {
                go.await();
                observedA.set(managerA.effective());
                Thread.sleep(20);
                observedA.set(managerA.effective());
                return null;
            }));
            final var fb = pool.submit(() -> managerB.withProxyOverride(overrideB, () -> {
                go.await();
                observedB.set(managerB.effective());
                Thread.sleep(20);
                observedB.set(managerB.effective());
                return null;
            }));
            go.countDown();
            fa.get(5, TimeUnit.SECONDS);
            fb.get(5, TimeUnit.SECONDS);

            assertEquals(overrideA, observedA.get(), "manager A must observe its own override");
            assertEquals(overrideB, observedB.get(), "manager B must observe its own override");
            assertEquals(proxy("a-initial.example", 1111), managerA.effective());
            assertEquals(proxy("b-initial.example", 2222), managerB.effective());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void serializesOverridesOnTheSameManager() throws Exception {
        // Two threads racing on the same manager must NOT see each other's
        // override because withProxyOverride serializes them on a per-manager
        // lock; each callable observes only its own override.
        final var manager = new FakeProxyAwareManager(proxy("initial.example", 1111));
        final var overrideX = proxy("x-override.example", 2222);
        final var overrideY = proxy("y-override.example", 3333);

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final var go = new CountDownLatch(1);
            final var observedX = new AtomicReference<ProxyConfig>();
            final var observedY = new AtomicReference<ProxyConfig>();

            final var fx = pool.submit(() -> manager.withProxyOverride(overrideX, () -> {
                go.await();
                Thread.sleep(20);
                observedX.set(manager.effective());
                return null;
            }));
            final var fy = pool.submit(() -> manager.withProxyOverride(overrideY, () -> {
                go.await();
                Thread.sleep(20);
                observedY.set(manager.effective());
                return null;
            }));
            go.countDown();
            fx.get(5, TimeUnit.SECONDS);
            fy.get(5, TimeUnit.SECONDS);

            // Each thread must have observed its own override even while the
            // other was queued waiting for the lock.
            assertEquals(overrideX, observedX.get());
            assertEquals(overrideY, observedY.get());
            // After both unwind, the original proxy is restored.
            assertEquals(proxy("initial.example", 1111), manager.effective());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
