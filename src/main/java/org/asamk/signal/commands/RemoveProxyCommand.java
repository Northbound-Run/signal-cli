package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

public class RemoveProxyCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "removeProxy";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Clear the per-account proxy and reconfigure the network stack to connect directly.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        m.removeProxy();
        if (outputWriter instanceof PlainTextWriter w) {
            w.println("Proxy cleared");
        }
    }
}
