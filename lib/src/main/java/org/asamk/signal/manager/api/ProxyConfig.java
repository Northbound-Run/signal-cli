package org.asamk.signal.manager.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Per-account proxy configuration.
 * <p>
 * Supports HTTP and SOCKS5 proxies with optional authentication and template
 * variables such as {@code {COUNTRY}} and {@code {SESSION}} that downstream
 * proxy selectors can expand when building the effective host/user values.
 * <p>
 * Nullable fields ({@code username}, {@code password}, {@code templateVars})
 * match the existing record style in this package so the storage
 * {@link com.fasterxml.jackson.databind.ObjectMapper} does not need the
 * {@code Jdk8Module} registered.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProxyConfig(
        Type type,
        String host,
        int port,
        String username,
        String password,
        Map<String, String> templateVars
) {

    private static final Logger logger = LoggerFactory.getLogger(ProxyConfig.class);

    public ProxyConfig {
        if (type == null) {
            throw new IllegalArgumentException("ProxyConfig.type must not be null");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("ProxyConfig.host must not be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ProxyConfig.port must be between 1 and 65535, got " + port);
        }

        if (templateVars != null) {
            final var country = templateVars.get("COUNTRY");
            if (country != null && country.isBlank()) {
                throw new IllegalArgumentException(
                        "ProxyConfig.templateVars COUNTRY must be a non-blank comma-separated list");
            }
        }

        final var hasPlaceholders = containsPlaceholder(host) || containsPlaceholder(username);
        if (hasPlaceholders && templateVars == null) {
            logger.warn("ProxyConfig host/username contains {COUNTRY} or {SESSION} placeholders "
                    + "but no templateVars were provided; placeholders will not be expanded");
        }
    }

    @Override
    public String toString() {
        return "ProxyConfig[type="
                + type
                + ", host="
                + host
                + ", port="
                + port
                + ", username="
                + username
                + ", password="
                + (password == null ? "null" : "[redacted]")
                + ", templateVars="
                + templateVars
                + "]";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProxyConfig that)) {
            return false;
        }
        return port == that.port
                && type == that.type
                && Objects.equals(host, that.host)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(templateVars, that.templateVars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, host, port, username, password, templateVars);
    }

    private static boolean containsPlaceholder(final String value) {
        return value != null && (value.contains("{COUNTRY}") || value.contains("{SESSION}"));
    }

    public enum Type {
        HTTP,
        SOCKS5
    }
}
