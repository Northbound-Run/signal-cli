package org.asamk.signal.commands.util;

import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.api.ProxyConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Reusable helpers for the per-call {@code --proxy} argparse fragment that
 * network-touching commands ({@code send}, {@code register}, {@code verify},
 * {@code link}, {@code updateProfile}, ...) share.
 * <p>
 * The argument accepts a single proxy URL of the form
 * {@code <scheme>://[user:pass@]host:port}, where {@code <scheme>} is one of
 * {@code http}, {@code https}, or {@code socks5}. Schemes {@code http} and
 * {@code https} both map to {@link ProxyConfig.Type#HTTP} because an HTTP
 * proxy itself being reachable over TLS is transport-only and does not change
 * the downstream proxy semantics; {@code socks5} maps to
 * {@link ProxyConfig.Type#SOCKS5}.
 */
public final class ProxyArgumentHelper {

    private ProxyArgumentHelper() {
    }

    /**
     * Attach the shared {@code --proxy} argument to the given subparser.
     */
    public static void attachProxyArgs(final Subparser subparser) {
        subparser.addArgument("--proxy")
                .help("Override the per-account proxy for this call only. "
                        + "Accepts a URL of the form "
                        + "'http://[user:pass@]host:port', "
                        + "'https://[user:pass@]host:port', or "
                        + "'socks5://[user:pass@]host:port'.");
    }

    /**
     * Parse a proxy URL into a {@link ProxyConfig}.
     * <p>
     * Returns {@code null} if the input is {@code null} or blank, so callers
     * can feed the raw argparse value through unconditionally.
     *
     * @throws UserErrorException if the URL is malformed or uses an
     *                            unsupported scheme.
     */
    public static ProxyConfig parseProxyUrl(final String url) throws UserErrorException {
        if (url == null || url.isBlank()) {
            return null;
        }
        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new UserErrorException("Invalid --proxy URL: " + url + " (" + e.getMessage() + ")");
        }

        final var scheme = uri.getScheme();
        if (scheme == null) {
            throw new UserErrorException("Invalid --proxy URL: missing scheme in '" + url + "'");
        }

        final ProxyConfig.Type type = switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http", "https" -> ProxyConfig.Type.HTTP;
            case "socks5" -> ProxyConfig.Type.SOCKS5;
            default -> throw new UserErrorException("Invalid --proxy scheme: '"
                    + scheme
                    + "' (expected http, https, or socks5)");
        };

        final var host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UserErrorException("Invalid --proxy URL: missing host in '" + url + "'");
        }
        final var port = uri.getPort();
        if (port < 1 || port > 65535) {
            throw new UserErrorException("Invalid --proxy URL: missing or out-of-range port in '"
                    + url
                    + "' (expected 1-65535)");
        }

        String username = null;
        String password = null;
        final var userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            final var colon = userInfo.indexOf(':');
            if (colon < 0) {
                username = userInfo;
            } else {
                username = colon == 0 ? null : userInfo.substring(0, colon);
                password = colon == userInfo.length() - 1 ? null : userInfo.substring(colon + 1);
            }
        }

        try {
            return new ProxyConfig(type, host, port, username, password, null);
        } catch (IllegalArgumentException e) {
            throw new UserErrorException("Invalid --proxy URL '" + url + "': " + e.getMessage(), e);
        }
    }
}
