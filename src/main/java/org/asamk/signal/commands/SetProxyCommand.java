package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.ProxyConfig;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

import java.util.HashMap;
import java.util.Locale;

public class SetProxyCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "setProxy";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Persist a per-account proxy and reconfigure the network stack to use it.");
        subparser.addArgument("--type")
                .required(true)
                .choices("http", "socks5")
                .help("Proxy type: 'http' or 'socks5'.");
        subparser.addArgument("--host").required(true).help("Proxy host (may contain {COUNTRY}/{SESSION} placeholders).");
        subparser.addArgument("--port").required(true).type(Integer.class).help("Proxy port (1-65535).");
        subparser.addArgument("--username").help("Optional proxy username (may contain {COUNTRY}/{SESSION} placeholders).");
        subparser.addArgument("--password").help("Optional proxy password.");
        subparser.addArgument("--countries")
                .help("Optional comma-separated list of country codes to expand the {COUNTRY} placeholder.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var typeStr = ns.getString("type");
        final var host = ns.getString("host");
        final var port = ns.getInt("port");
        final var username = ns.getString("username");
        final var password = ns.getString("password");
        final var countries = ns.getString("countries");

        if (typeStr == null) {
            throw new UserErrorException("--type is required");
        }
        if (host == null || host.isBlank()) {
            throw new UserErrorException("--host is required");
        }
        if (port == null) {
            throw new UserErrorException("--port is required");
        }

        final ProxyConfig.Type type;
        try {
            type = ProxyConfig.Type.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserErrorException("Invalid --type: " + typeStr + " (expected http or socks5)");
        }

        final var templateVars = new HashMap<String, String>();
        if (countries != null && !countries.isBlank()) {
            templateVars.put("COUNTRY", countries);
        }

        final ProxyConfig proxy;
        try {
            proxy = new ProxyConfig(type, host, port, username, password, templateVars.isEmpty() ? null : templateVars);
        } catch (IllegalArgumentException e) {
            throw new UserErrorException("Invalid proxy configuration: " + e.getMessage());
        }

        m.setProxy(proxy);

        if (outputWriter instanceof PlainTextWriter w) {
            w.println("Proxy set: type={}, host={}, port={}", type, host, port);
        }
    }
}
