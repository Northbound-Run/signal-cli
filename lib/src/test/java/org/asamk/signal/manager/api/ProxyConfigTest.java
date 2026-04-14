package org.asamk.signal.manager.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.storage.Utils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyConfigTest {

    private static ObjectMapper newMapper() {
        return Utils.createStorageObjectMapper();
    }

    @Test
    void roundTripHttpWithAuth() throws Exception {
        final var mapper = newMapper();
        final Map<String, String> vars = new LinkedHashMap<>();
        vars.put("COUNTRY", "US,CA");
        vars.put("SESSION", "abc123");
        final var original = new ProxyConfig(ProxyConfig.Type.HTTP,
                "proxy.example.com",
                8080,
                "alice",
                "s3cret",
                vars);

        final var json = mapper.writeValueAsString(original);
        final var parsed = mapper.readValue(json, ProxyConfig.class);

        assertEquals(ProxyConfig.Type.HTTP, parsed.type());
        assertEquals("proxy.example.com", parsed.host());
        assertEquals(8080, parsed.port());
        assertEquals("alice", parsed.username());
        assertEquals("s3cret", parsed.password());
        assertNotNull(parsed.templateVars());
        assertEquals("US,CA", parsed.templateVars().get("COUNTRY"));
        assertEquals("abc123", parsed.templateVars().get("SESSION"));
        assertEquals(original, parsed);
    }

    @Test
    void roundTripSocks5WithoutAuth() throws Exception {
        final var mapper = newMapper();
        final var original = new ProxyConfig(ProxyConfig.Type.SOCKS5,
                "10.0.0.1",
                1080,
                null,
                null,
                null);

        final var json = mapper.writeValueAsString(original);
        final var parsed = mapper.readValue(json, ProxyConfig.class);

        assertEquals(ProxyConfig.Type.SOCKS5, parsed.type());
        assertEquals("10.0.0.1", parsed.host());
        assertEquals(1080, parsed.port());
        assertNull(parsed.username());
        assertNull(parsed.password());
        assertNull(parsed.templateVars());
        assertEquals(original, parsed);
    }

    @Test
    void toStringRedactsPassword() {
        final var password = "super-secret-password-value";
        final var config = new ProxyConfig(ProxyConfig.Type.HTTP,
                "proxy.example.com",
                3128,
                "user",
                password,
                null);

        final var rendered = config.toString();
        assertNotNull(rendered);
        assertFalse(rendered.contains(password),
                "toString() must not expose the raw password, got: " + rendered);
        assertTrue(rendered.contains("[redacted]"),
                "toString() must contain the [redacted] marker when a password is set, got: " + rendered);
    }

    @Test
    void toStringNullPasswordIsPlainNull() {
        final var config = new ProxyConfig(ProxyConfig.Type.SOCKS5,
                "host.example.com",
                1080,
                null,
                null,
                null);
        final var rendered = config.toString();
        assertTrue(rendered.contains("password=null"),
                "toString() should render a null password as 'null', got: " + rendered);
        assertFalse(rendered.contains("[redacted]"),
                "toString() should not claim redaction when there is no password, got: " + rendered);
    }

    @Test
    void deserializationIgnoresUnknownFields() {
        final var mapper = newMapper();
        final var json = """
                {
                  "type": "HTTP",
                  "host": "proxy.example.com",
                  "port": 8080,
                  "username": "alice",
                  "password": "s3cret",
                  "templateVars": {"COUNTRY": "US"},
                  "legacyField": "ignore-me",
                  "futureNested": {"a": 1}
                }
                """;

        final var parsed = assertDoesNotThrow(() -> mapper.readValue(json, ProxyConfig.class));
        assertEquals(ProxyConfig.Type.HTTP, parsed.type());
        assertEquals("proxy.example.com", parsed.host());
        assertEquals(8080, parsed.port());
        assertEquals("alice", parsed.username());
    }

    @Test
    void nullFieldsRoundTripLosslessly() throws Exception {
        final var mapper = newMapper();
        final var original = new ProxyConfig(ProxyConfig.Type.HTTP,
                "host.example.com",
                8080,
                null,
                null,
                null);

        final var json = mapper.writeValueAsString(original);
        final var parsed = mapper.readValue(json, ProxyConfig.class);

        assertEquals(original, parsed);
        assertNull(parsed.username());
        assertNull(parsed.password());
        assertNull(parsed.templateVars());
    }

    @Test
    void nullInclusionOmitsUnsetFieldsFromJson() throws Exception {
        final var mapper = newMapper();
        final var config = new ProxyConfig(ProxyConfig.Type.HTTP,
                "host.example.com",
                8080,
                null,
                null,
                null);
        final var json = mapper.writeValueAsString(config);
        assertFalse(json.contains("\"username\""),
                "username key should be omitted when null, got: " + json);
        assertFalse(json.contains("\"password\""),
                "password key should be omitted when null, got: " + json);
        assertFalse(json.contains("\"templateVars\""),
                "templateVars key should be omitted when null, got: " + json);
    }

    @Test
    void validatesTypeNonNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(null, "host", 8080, null, null, null));
    }

    @Test
    void validatesHostNonBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(ProxyConfig.Type.HTTP, "", 8080, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(ProxyConfig.Type.HTTP, "  ", 8080, null, null, null));
    }

    @Test
    void validatesPortRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(ProxyConfig.Type.HTTP, "host", 0, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(ProxyConfig.Type.HTTP, "host", 65536, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProxyConfig(ProxyConfig.Type.HTTP, "host", -1, null, null, null));
    }
}
