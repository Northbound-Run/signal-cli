package org.asamk.signal.commands;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link SetProxyCommand} attaches argparse arguments correctly
 * for both HTTP and SOCKS5 proxy types and surfaces required-argument errors.
 */
class SetProxyCommandTest {

    private SetProxyCommand command;
    private Subparser subparser;

    @BeforeEach
    void setUp() {
        command = new SetProxyCommand();
        final ArgumentParser parser = ArgumentParsers.newFor("test").build();
        subparser = parser.addSubparsers().addParser(command.getName());
        command.attachToSubparser(subparser);
    }

    @Test
    void parsesHttpProxyArgsHappyPath() throws ArgumentParserException {
        final Namespace ns = subparser.parseArgs(new String[]{
                "--type", "http",
                "--host", "proxy.example.com",
                "--port", "8080",
                "--username", "user",
                "--password", "secret",
        });

        assertEquals("http", ns.getString("type"));
        assertEquals("proxy.example.com", ns.getString("host"));
        assertEquals(Integer.valueOf(8080), ns.getInt("port"));
        assertEquals("user", ns.getString("username"));
        assertEquals("secret", ns.getString("password"));
        assertNull(ns.getString("countries"));
    }

    @Test
    void parsesSocks5ProxyArgsHappyPath() throws ArgumentParserException {
        final Namespace ns = subparser.parseArgs(new String[]{
                "--type", "socks5",
                "--host", "{COUNTRY}.proxy.example.com",
                "--port", "1080",
                "--countries", "us,ca,gb,nl",
        });

        assertEquals("socks5", ns.getString("type"));
        assertEquals("{COUNTRY}.proxy.example.com", ns.getString("host"));
        assertEquals(Integer.valueOf(1080), ns.getInt("port"));
        assertEquals("us,ca,gb,nl", ns.getString("countries"));
        assertNull(ns.getString("username"));
        assertNull(ns.getString("password"));
    }

    @Test
    void requiresType() {
        assertThrows(ArgumentParserException.class, () -> subparser.parseArgs(new String[]{
                "--host", "proxy.example.com",
                "--port", "8080",
        }));
    }

    @Test
    void requiresHostAndPort() {
        assertThrows(ArgumentParserException.class, () -> subparser.parseArgs(new String[]{
                "--type", "http",
        }));
    }

    @Test
    void rejectsInvalidProxyType() {
        assertThrows(ArgumentParserException.class, () -> subparser.parseArgs(new String[]{
                "--type", "ftp",
                "--host", "proxy.example.com",
                "--port", "8080",
        }));
    }

    @Test
    void commandNameIsSetProxy() {
        assertEquals("setProxy", command.getName());
    }

    @Test
    void removeProxyCommandHasMatchingName() {
        assertNotNull(new RemoveProxyCommand());
        assertEquals("removeProxy", new RemoveProxyCommand().getName());
    }
}
