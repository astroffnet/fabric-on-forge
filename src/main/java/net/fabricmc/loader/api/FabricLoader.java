package net.fabricmc.loader.api;

import dev.fabricforge.bridge.FabricLoaderImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FabricLoader {
    static FabricLoader getInstance() {
        return FabricLoaderImpl.INSTANCE;
    }

    <T> List<T> getEntrypoints(String key, Class<T> type);
    <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);
    boolean isModLoaded(String id);
    Optional<ModContainer> getModContainer(String id);
    Collection<ModContainer> getAllMods();
    boolean isDevelopmentEnvironment();
    EnvType getEnvironmentType();
    Path getGameDir();
    Path getConfigDir();
    Object getGameInstance();
}
