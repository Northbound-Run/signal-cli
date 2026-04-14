package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.util.ProxyArgumentHelper;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.UpdateProfile;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;
import java.util.Base64;

public class UpdateProfileCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "updateProfile";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Set a name, about and avatar image for the user profile");
        subparser.addArgument("--given-name", "--name").help("New profile (given) name");
        subparser.addArgument("--family-name").help("New profile family name (optional)");
        subparser.addArgument("--about").help("New profile about text");
        subparser.addArgument("--about-emoji").help("New profile about emoji");
        subparser.addArgument("--mobile-coin-address", "--mobilecoin-address")
                .help("New MobileCoin address (Base64 encoded public address)");

        final var avatarOptions = subparser.addMutuallyExclusiveGroup();
        avatarOptions.addArgument("--avatar").help("Path to new profile avatar");
        avatarOptions.addArgument("--remove-avatar").action(Arguments.storeTrue());
        ProxyArgumentHelper.attachProxyArgs(subparser);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        var givenName = ns.getString("given-name");
        var familyName = ns.getString("family-name");
        var about = ns.getString("about");
        var aboutEmoji = ns.getString("about-emoji");
        var mobileCoinAddressString = ns.getString("mobile-coin-address");
        var mobileCoinAddress = mobileCoinAddressString == null
                ? null
                : Base64.getDecoder().decode(mobileCoinAddressString);

        var avatarPath = ns.getString("avatar");
        boolean removeAvatar = Boolean.TRUE.equals(ns.getBoolean("remove-avatar"));
        String avatarFile = removeAvatar || avatarPath == null ? null : avatarPath;
        final var proxyOverride = ProxyArgumentHelper.parseProxyUrl(ns.getString("proxy"));

        final var profile = UpdateProfile.newBuilder()
                .withGivenName(givenName)
                .withFamilyName(familyName)
                .withAbout(about)
                .withAboutEmoji(aboutEmoji)
                .withMobileCoinAddress(mobileCoinAddress)
                .withAvatar(avatarFile)
                .withDeleteAvatar(removeAvatar)
                .build();

        if (proxyOverride == null) {
            updateProfileSafe(m, profile);
            return;
        }
        try {
            m.withProxyOverride(proxyOverride, () -> {
                updateProfileSafe(m, profile);
                return null;
            });
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedErrorException("Failed to run updateProfile with proxy override: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        }
    }

    private void updateProfileSafe(final Manager m, final UpdateProfile profile) throws CommandException {
        try {
            m.updateProfile(profile);
        } catch (IOException e) {
            throw new IOErrorException("Update profile error: " + e.getMessage(), e);
        }
    }
}
