package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

import java.util.Collection;

public interface ModMetadata {
    String getType();
    String getId();
    Collection<String> getProvides();
    Version getVersion();
    String getName();
    String getDescription();
    Collection<String> getLicense();
    boolean containsCustomValue(String key);
}
