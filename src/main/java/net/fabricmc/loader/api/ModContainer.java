package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModContainer {
    ModMetadata getMetadata();
    Path getRootPath();
    List<Path> getRootPaths();
    Optional<Path> findPath(String file);
    Optional<ModContainer> getContainingMod();
    Collection<ModContainer> getContainedMods();
}
