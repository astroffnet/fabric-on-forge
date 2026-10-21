package net.fabricmc.loader.api;

import dev.fabricforge.bridge.VersionImpl;

public interface Version extends Comparable<Version> {
    String getFriendlyString();

    static Version parse(String string) throws VersionParsingException {
        if (string == null || string.isBlank()) {
            throw new VersionParsingException("Version string is empty");
        }
        return new VersionImpl(string.trim());
    }
}
