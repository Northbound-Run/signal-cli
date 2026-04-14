package org.asamk.signal.commands.util;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.api.ProxyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link ProxyArgumentHelper#parseProxyUrl} handles the documented
 * {@code http}/{@code https}/{@code socks5} scheme variants and rejects
 * malformed input with {@link UserErrorException}.
 */
class ProxyArgumentHelperTest {

    @Test
    void parsesPlainHttpProxy() throws Exception {
        final var config = ProxyArgumentHelper.parseProxyUrl("http://host:8080");

        assertEquals(ProxyConfig.Type.HTTP, config.type());
        assertEquals("host", config.host());
        assertEquals(8080, config.port());
        assertNull(config.username());
        assertNull(config.password());
        assertNull(config.templateVars());
    }

    @Test
    void parsesHttpProxyWithAuth() throws Exception {
        final var config = ProxyArgumentHelper.parseProxyUrl("http://u:p@host:8080");

        assertEquals(ProxyConfig.Type.HTTP, config.type());
        assertEquals("host", config.host());
        assertEquals(8080, config.port());
        assertEquals("u", config.username());
        assertEquals("p", config.password());
    }

    @Test
    void parsesHttpsProxyAsHttpType() throws Exception {
        // An HTTP proxy reachable over TLS still proxies HTTP traffic — map
        // to ProxyConfig.Type.HTTP just like plain http://.
        final var config = ProxyArgumentHelper.parseProxyUrl("https://u:p@secure.example.com:443");

        assertEquals(ProxyConfig.Type.HTTP, config.type());
        assertEquals("secure.example.com", config.host());
        assertEquals(443, config.port());
        assertEquals("u", config.username());
        assertEquals("p", config.password());
    }

    @Test
    void parsesPlainSocks5Proxy() throws Exception {
        final var config = ProxyArgumentHelper.parseProxyUrl("socks5://host:1080");

        assertEquals(ProxyConfig.Type.SOCKS5, config.type());
        assertEquals("host", config.host());
        assertEquals(1080, config.port());
        assertNull(config.username());
        assertNull(config.password());
    }

    @Test
    void parsesSocks5WithAuth() throws Exception {
        final var config = ProxyArgumentHelper.parseProxyUrl("socks5://alice:secret@host:1080");

        assertEquals(ProxyConfig.Type.SOCKS5, config.type());
        assertEquals("alice", config.username());
        assertEquals("secret", config.password());
    }

    @Test
    void nullInputReturnsNull() throws Exception {
        assertNull(ProxyArgumentHelper.parseProxyUrl(null));
    }

    @Test
    void emptyInputReturnsNull() throws Exception {
        assertNull(ProxyArgumentHelper.parseProxyUrl(""));
    }

    @Test
    void blankInputReturnsNull() throws Exception {
        assertNull(ProxyArgumentHelper.parseProxyUrl("   "));
    }

    @Test
    void unsupportedSchemeIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("ftp://host:21"));
    }

    @Test
    void schemelessUrlIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("host:8080"));
    }

    @Test
    void missingHostIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("http://:8080"));
    }

    @Test
    void missingPortIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("http://host"));
    }

    @Test
    void portOutOfRangeIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("http://host:99999"));
    }

    @Test
    void garbageInputIsRejected() {
        assertThrows(UserErrorException.class,
                () -> ProxyArgumentHelper.parseProxyUrl("not a url at all"));
    }

    @Test
    void usernameOnlyAuthIsAccepted() throws Exception {
        final var config = ProxyArgumentHelper.parseProxyUrl("http://onlyuser@host:8080");

        assertEquals("onlyuser", config.username());
        assertNull(config.password());
    }
}
