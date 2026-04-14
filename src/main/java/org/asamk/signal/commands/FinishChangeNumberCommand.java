package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.commands.util.ProxyArgumentHelper;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.PinLockMissingException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class FinishChangeNumberCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "finishChangeNumber";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Verify the new number using the code received via SMS or voice.");
        subparser.addArgument("number").help("The new phone number in E164 format.").required(true);
        subparser.addArgument("-v", "--verification-code")
                .help("The verification code you received via sms or voice call.")
                .required(true);
        subparser.addArgument("-p", "--pin").help("The registration lock PIN, that was set by the user (Optional)");
        ProxyArgumentHelper.attachProxyArgs(subparser);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        final var newNumber = ns.getString("number");
        final var verificationCode = ns.getString("verification-code");
        final var pin = ns.getString("pin");
        final var proxyOverride = ProxyArgumentHelper.parseProxyUrl(ns.getString("proxy"));

        if (proxyOverride == null) {
            finishChangeNumber(m, newNumber, verificationCode, pin);
            return;
        }
        try {
            m.withProxyOverride(proxyOverride, () -> {
                finishChangeNumber(m, newNumber, verificationCode, pin);
                return null;
            });
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedErrorException("Failed to run finishChangeNumber with proxy override: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }
    }

    private void finishChangeNumber(
            final Manager m,
            final String newNumber,
            final String verificationCode,
            final String pin
    ) throws CommandException {
        try {
            m.finishChangeNumber(newNumber, verificationCode, pin);
        } catch (PinLockedException e) {
            throw new UserErrorException(
                    "Verification failed! This number is locked with a pin. Hours remaining until reset: "
                            + (e.getTimeRemaining() / 1000 / 60 / 60)
                            + "\nUse '--pin PIN_CODE' to specify the registration lock PIN");
        } catch (IncorrectPinException e) {
            throw new UserErrorException("Verification failed! Invalid pin, tries remaining: " + e.getTriesRemaining());
        } catch (PinLockMissingException e) {
            throw new UserErrorException("Account is pin locked, but pin data has been deleted on the server.");
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new IOErrorException("Failed to change number: %s (%s)".formatted(e.getMessage(),
                    e.getClass().getSimpleName()), e);
        }
    }
}
