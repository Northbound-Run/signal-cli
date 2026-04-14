package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.api.ProxyConfig;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyResolverTest {

    private static final Pattern SESSION_FORMAT = Pattern.compile("[a-zA-Z0-9]{8}");

    private static ProxyConfig withTemplateCountry(final String countryList) {
        final Map<String, String> vars = new LinkedHashMap<>();
        vars.put("COUNTRY", countryList);
        return new ProxyConfig(ProxyConfig.Type.HTTP,
                "proxy-{COUNTRY}.example.com",
                8080,
                "user-{SESSION}",
                "pw",
                vars);
    }

    @Test
    void resolvesCountryFromConfigList() {
        final var config = withTemplateCountry("US,CA,GB");
        final var resolver = new ProxyResolver();

        final var resolved = resolver.resolve(config);

        assertTrue(resolved.host().matches("proxy-(US|CA|GB)\\.example\\.com"),
                "unexpected host: " + resolved.host());
    }

    @Test
    void callTimeCountryOverrideBeatsConfigList() {
        final var config = withTemplateCountry("US,CA");
        final var resolver = new ProxyResolver();
        final Map<String, String> callVars = Map.of("COUNTRY", "NL");

        final var resolved = resolver.resolve(config, callVars);

        assertEquals("proxy-NL.example.com", resolved.host());
    }

    @Test
    void generatesUniqueSessionPerCall() {
        final var config = withTemplateCountry("US");
        final var resolver = new ProxyResolver();

        final var first = resolver.resolve(config);
        final var second = resolver.resolve(config);

        final var firstSession = first.username().substring("user-".length());
        final var secondSession = second.username().substring("user-".length());
        assertTrue(SESSION_FORMAT.matcher(firstSession).matches(),
                "session did not match expected format: " + firstSession);
        assertNotEquals(firstSession, secondSession,
                "two consecutive resolves should produce different sessions");
    }

    @Test
    void callTimeSessionOverrideIsUsedVerbatim() {
        final var config = withTemplateCountry("US");
        final var resolver = new ProxyResolver();
        final var callVars = Map.of("SESSION", "fixed-session-xyz");

        final var resolved = resolver.resolve(config, callVars);

        assertEquals("user-fixed-session-xyz", resolved.username());
    }

    @Test
    void throwsWhenCountryPlaceholderCannotBeResolved() {
        final var config = new ProxyConfig(ProxyConfig.Type.HTTP,
                "proxy-{COUNTRY}.example.com",
                8080,
                null,
                null,
                null);
        final var resolver = new ProxyResolver();

        assertThrows(IllegalStateException.class, () -> resolver.resolve(config));
    }

    @Test
    void noPlaceholdersMeansNoExpansion() {
        final var config = new ProxyConfig(ProxyConfig.Type.HTTP,
                "proxy.example.com",
                8080,
                "bob",
                "pw",
                null);
        final var resolver = new ProxyResolver();

        final var resolved = resolver.resolve(config);

        assertEquals("proxy.example.com", resolved.host());
        assertEquals("bob", resolved.username());
    }

    @Test
    void nullUsernameIsPreserved() {
        final var config = new ProxyConfig(ProxyConfig.Type.SOCKS5,
                "10.0.0.1",
                1080,
                null,
                null,
                null);
        final var resolver = new ProxyResolver();

        final var resolved = resolver.resolve(config);

        assertNull(resolved.username());
        assertNull(resolved.password());
    }

    @Test
    void toJavaProxyMapsHttpType() {
        final var resolved = new ProxyResolver.ResolvedProxy(
                ProxyConfig.Type.HTTP, "proxy.example.com", 8080, null, null);

        final var proxy = ProxyResolver.toJavaProxy(resolved);

        assertEquals(Proxy.Type.HTTP, proxy.type());
    }

    @Test
    void toJavaProxyMapsSocks5Type() {
        final var resolved = new ProxyResolver.ResolvedProxy(
                ProxyConfig.Type.SOCKS5, "socks.example.com", 1080, null, null);

        final var proxy = ProxyResolver.toJavaProxy(resolved);

        assertEquals(Proxy.Type.SOCKS, proxy.type());
    }

    @Test
    void countrySelectionIsDeterministicWithSeededRandom() {
        final var config = withTemplateCountry("US,CA,GB,NL");
        final var resolver = new ProxyResolver(new SecureRandom(new byte[]{1, 2, 3, 4}));

        final var first = resolver.resolve(config);
        final var second = resolver.resolve(config);

        assertTrue(first.host().matches("proxy-(US|CA|GB|NL)\\.example\\.com"));
        assertTrue(second.host().matches("proxy-(US|CA|GB|NL)\\.example\\.com"));
    }

    @Test
    void resolvedProxyToStringRedactsPassword() {
        final var resolved = new ProxyResolver.ResolvedProxy(
                ProxyConfig.Type.HTTP, "proxy.example.com", 8080, "user", "sensitive");
        final var rendered = resolved.toString();
        assertFalse(rendered.contains("sensitive"),
                "ResolvedProxy.toString() must not leak the password: " + rendered);
        assertTrue(rendered.contains("[redacted]"));
    }
}
