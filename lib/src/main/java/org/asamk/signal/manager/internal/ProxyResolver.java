package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.api.ProxyConfig;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves template placeholders ({@code {COUNTRY}}, {@code {SESSION}}) on a
 * {@link ProxyConfig} at call time into concrete host/username values.
 * <p>
 * Resolution order for a {@code {COUNTRY}} placeholder:
 * <ol>
 *   <li>call-time override (from {@code callTimeVars} map, key {@code COUNTRY})</li>
 *   <li>random pick from the comma-separated list in {@code ProxyConfig.templateVars.get("COUNTRY")}</li>
 *   <li>throw {@link IllegalStateException} — the placeholder is unresolvable</li>
 * </ol>
 * A {@code {SESSION}} placeholder is always replaced with a fresh random
 * 8-char alphanumeric string unless {@code callTimeVars} supplies one.
 */
public final class ProxyResolver {

    private static final String COUNTRY_KEY = "COUNTRY";
    private static final String SESSION_KEY = "SESSION";
    private static final String COUNTRY_PLACEHOLDER = "{COUNTRY}";
    private static final String SESSION_PLACEHOLDER = "{SESSION}";
    private static final String SESSION_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SESSION_LENGTH = 8;

    private final SecureRandom random;

    public ProxyResolver() {
        this(new SecureRandom());
    }

    ProxyResolver(final SecureRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    public ResolvedProxy resolve(final ProxyConfig config) {
        return resolve(config, Collections.emptyMap());
    }

    public ResolvedProxy resolve(final ProxyConfig config, final Map<String, String> callTimeVars) {
        Objects.requireNonNull(config, "config");
        final var vars = callTimeVars == null ? Collections.<String, String>emptyMap() : callTimeVars;

        final var resolvedHost = resolvePlaceholders(config.host(), config.templateVars(), vars);
        final var resolvedUsername = config.username() == null
                ? null
                : resolvePlaceholders(config.username(), config.templateVars(), vars);

        return new ResolvedProxy(config.type(), resolvedHost, config.port(), resolvedUsername, config.password());
    }

    public static Proxy toJavaProxy(final ResolvedProxy resolved) {
        Objects.requireNonNull(resolved, "resolved");
        final var type = switch (resolved.type()) {
            case HTTP -> Proxy.Type.HTTP;
            case SOCKS5 -> Proxy.Type.SOCKS;
        };
        return new Proxy(type, new InetSocketAddress(resolved.host(), resolved.port()));
    }

    private String resolvePlaceholders(
            final String template,
            final Map<String, String> configTemplateVars,
            final Map<String, String> callTimeVars
    ) {
        if (template == null) {
            return null;
        }

        var result = template;
        if (result.contains(COUNTRY_PLACEHOLDER)) {
            result = result.replace(COUNTRY_PLACEHOLDER, selectCountry(configTemplateVars, callTimeVars));
        }
        if (result.contains(SESSION_PLACEHOLDER)) {
            result = result.replace(SESSION_PLACEHOLDER, selectSession(callTimeVars));
        }
        return result;
    }

    private String selectCountry(final Map<String, String> configVars, final Map<String, String> callVars) {
        final var override = callVars.get(COUNTRY_KEY);
        if (override != null && !override.isBlank()) {
            return override;
        }

        if (configVars != null) {
            final var list = configVars.get(COUNTRY_KEY);
            if (list != null && !list.isBlank()) {
                final var countries = parseCountryList(list);
                return countries.get(random.nextInt(countries.size()));
            }
        }

        throw new IllegalStateException(
                "Cannot resolve {COUNTRY} placeholder: no override in callTimeVars and no COUNTRY list in templateVars");
    }

    private String selectSession(final Map<String, String> callVars) {
        final var override = callVars.get(SESSION_KEY);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return generateSession();
    }

    private String generateSession() {
        final var sb = new StringBuilder(SESSION_LENGTH);
        for (int i = 0; i < SESSION_LENGTH; i++) {
            sb.append(SESSION_ALPHABET.charAt(random.nextInt(SESSION_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static List<String> parseCountryList(final String list) {
        final var parts = Arrays.stream(list.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (parts.isEmpty()) {
            throw new IllegalStateException(
                    "templateVars COUNTRY list is empty after trimming: \"" + list + "\"");
        }
        return parts;
    }

    public record ResolvedProxy(
            ProxyConfig.Type type,
            String host,
            int port,
            String username,
            String password
    ) {
        public ResolvedProxy {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(host, "host");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be 1-65535, got " + port);
            }
        }

        @Override
        public String toString() {
            return "ResolvedProxy[type=" + type
                    + ", host=" + host
                    + ", port=" + port
                    + ", username=" + username
                    + ", password=" + (password == null ? "null" : "[redacted]")
                    + "]";
        }
    }
}
