package org.asamk.signal.manager.config;

import org.asamk.signal.manager.api.ServiceEnvironment;
import org.junit.jupiter.api.Test;
import org.whispersystems.signalservice.internal.configuration.HttpProxy;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import okhttp3.Interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior-parity tests for the per-account proxy refactor (US-003).
 * <p>
 * When no account-level proxy is configured, the per-account
 * {@link ServiceEnvironmentConfig} builder must produce the same
 * {@link SignalServiceConfiguration} as the original no-arg code path, so that
 * accounts without a stored proxy keep identical network behavior.
 */
class LiveConfigProxyParityTest {

    private static final String USER_AGENT = "signal-cli-test";

    private static List<Interceptor> interceptors() {
        return List.of(chain -> chain.proceed(chain.request()));
    }

    @Test
    void emptyProxyOptionalsMatchLegacyLiveConfig() {
        final var interceptors = interceptors();

        final var legacy = LiveConfig.createDefaultServiceConfiguration(interceptors);
        final var withExplicitEmpty = LiveConfig.createDefaultServiceConfiguration(interceptors,
                Optional.empty(),
                Optional.empty());

        assertConfigsEquivalent(legacy, withExplicitEmpty);
    }

    @Test
    void emptyProxyOptionalsMatchLegacyStagingConfig() {
        final var interceptors = interceptors();

        final var legacy = StagingConfig.createDefaultServiceConfiguration(interceptors);
        final var withExplicitEmpty = StagingConfig.createDefaultServiceConfiguration(interceptors,
                Optional.empty(),
                Optional.empty());

        assertConfigsEquivalent(legacy, withExplicitEmpty);
    }

    @Test
    void serviceConfigRoutesNoProxyToLegacyShape() {
        final var legacyLive = LiveConfig.createDefaultServiceConfiguration(interceptors());
        final var viaServiceConfig = ServiceConfig.getServiceEnvironmentConfig(ServiceEnvironment.LIVE, USER_AGENT)
                .signalServiceConfiguration();

        // Both must end up with empty proxy/system-proxy optionals.
        assertTrue(viaServiceConfig.getSignalProxy().isEmpty(),
                "signalProxy must be empty when no account proxy is configured");
        assertTrue(viaServiceConfig.getSystemHttpProxy().isEmpty(),
                "systemHttpProxy must be empty when no account proxy is configured");
        assertEquals(legacyLive.getSignalProxy(), viaServiceConfig.getSignalProxy());
        assertEquals(legacyLive.getSystemHttpProxy(), viaServiceConfig.getSystemHttpProxy());
    }

    @Test
    void serviceConfigWithHttpProxyPopulatesSystemHttpProxy() {
        final var systemProxy = Optional.of(new HttpProxy("p.example.com", 8080));
        final var cfg = ServiceConfig.getServiceEnvironmentConfig(ServiceEnvironment.LIVE,
                USER_AGENT,
                Optional.empty(),
                systemProxy).signalServiceConfiguration();

        assertTrue(cfg.getSignalProxy().isEmpty());
        assertTrue(cfg.getSystemHttpProxy().isPresent());
        assertEquals("p.example.com", cfg.getSystemHttpProxy().get().getHost());
        assertEquals(8080, cfg.getSystemHttpProxy().get().getPort());
    }

    @Test
    void serviceConfigWithSignalProxyPopulatesSignalProxyField() {
        final var signalProxy = Optional.of(new SignalProxy("relay.example.com", 443));
        final var cfg = ServiceConfig.getServiceEnvironmentConfig(ServiceEnvironment.LIVE,
                USER_AGENT,
                signalProxy,
                Optional.empty()).signalServiceConfiguration();

        assertTrue(cfg.getSystemHttpProxy().isEmpty());
        assertTrue(cfg.getSignalProxy().isPresent());
        assertEquals("relay.example.com", cfg.getSignalProxy().get().getHost());
        assertEquals(443, cfg.getSignalProxy().get().getPort());
    }

    /**
     * {@link SignalServiceConfiguration} is a Kotlin data class and uses
     * reference equality on arrays in its generated {@code equals}. To keep
     * this test independent of that, compare each field explicitly.
     */
    private static void assertConfigsEquivalent(
            final SignalServiceConfiguration expected,
            final SignalServiceConfiguration actual
    ) {
        assertUrlArraysEquivalent("signalServiceUrls",
                expected.getSignalServiceUrls(),
                actual.getSignalServiceUrls());
        assertEquals(expected.getSignalCdnUrlMap().keySet(), actual.getSignalCdnUrlMap().keySet());
        for (final var key : expected.getSignalCdnUrlMap().keySet()) {
            assertUrlArraysEquivalent("signalCdnUrlMap[" + key + "]",
                    expected.getSignalCdnUrlMap().get(key),
                    actual.getSignalCdnUrlMap().get(key));
        }
        assertUrlArraysEquivalent("signalStorageUrls",
                expected.getSignalStorageUrls(),
                actual.getSignalStorageUrls());
        assertUrlArraysEquivalent("signalCdsiUrls", expected.getSignalCdsiUrls(), actual.getSignalCdsiUrls());
        assertUrlArraysEquivalent("signalSvr2Urls", expected.getSignalSvr2Urls(), actual.getSignalSvr2Urls());
        assertEquals(expected.getNetworkInterceptors(), actual.getNetworkInterceptors());
        assertEquals(expected.getDns(), actual.getDns());
        assertEquals(expected.getSignalProxy(), actual.getSignalProxy());
        assertEquals(expected.getSystemHttpProxy(), actual.getSystemHttpProxy());
        assertEquals(expected.getCensored(), actual.getCensored());
        assertTrue(Arrays.equals(expected.getZkGroupServerPublicParams(), actual.getZkGroupServerPublicParams()),
                "zkGroupServerPublicParams bytes must match");
        assertTrue(Arrays.equals(expected.getGenericServerPublicParams(), actual.getGenericServerPublicParams()),
                "genericServerPublicParams bytes must match");
        assertTrue(Arrays.equals(expected.getBackupServerPublicParams(), actual.getBackupServerPublicParams()),
                "backupServerPublicParams bytes must match");
    }

    private static <T extends SignalUrl> void assertUrlArraysEquivalent(
            final String label,
            final T[] a,
            final T[] b
    ) {
        assertEquals(a.length, b.length, label + ": length");
        assertSame(a.getClass(), b.getClass(), label + ": component type");
        final var aUrls = Stream.of(a).map(SignalUrl::getUrl).toList();
        final var bUrls = Stream.of(b).map(SignalUrl::getUrl).toList();
        assertEquals(aUrls, bUrls, label + ": urls");
    }
}
