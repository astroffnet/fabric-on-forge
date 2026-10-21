package dev.fabricforge.bridge;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();
    private static final Logger LOG = LogManager.getLogger("FabricOnForge");

    private final Map<String, Object> instances = new HashMap<>();

    private FabricLoaderImpl() {}

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
            out.add(c.getEntrypoint());
        }
        return out;
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> out = new ArrayList<>();
        for (ModContainerImpl mod : FabricModRegistry.all()) {
            for (String className : mod.entrypointClasses(key)) {
                T instance = instantiate(mod, className, type);
                if (instance != null) {
                    out.add(new EntrypointContainer<>() {
                        @Override public T getEntrypoint() { return instance; }
                        @Override public ModContainer getProvider() { return mod; }
                    });
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    synchronized <T> T instantiate(ModContainerImpl mod, String className, Class<T> type) {
        if (className.contains("::")) {
            LOG.warn("[{}] Entrypoint reference '{}' is not supported yet", mod.forgeId(), className);
            return null;
        }
        String cacheKey = mod.forgeId() + "/" + className;
        Object cached = instances.get(cacheKey);
        if (cached != null) {
            return type.isInstance(cached) ? (T) cached : null;
        }
        try {
            ClassLoader cl = mod.loader() != null ? mod.loader() : Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(className, true, cl);
            if (!type.isAssignableFrom(clazz)) {
                return null;
            }
            Object o = clazz.getDeclaredConstructor().newInstance();
            instances.put(cacheKey, o);
            return (T) o;
        } catch (Throwable t) {
            LOG.error("[{}] Could not create entrypoint {}: {}", mod.forgeId(), className, t.toString());
            return null;
        }
    }

    @Override public boolean isModLoaded(String id) { return FabricModRegistry.byFabricId(id) != null; }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(FabricModRegistry.byFabricId(id));
    }

    @Override public Collection<ModContainer> getAllMods() { return new ArrayList<>(FabricModRegistry.all()); }
    @Override public boolean isDevelopmentEnvironment() { return !FMLEnvironment.production; }
    @Override public EnvType getEnvironmentType() { return FMLEnvironment.dist == Dist.CLIENT ? EnvType.CLIENT : EnvType.SERVER; }
    @Override public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public Object getGameInstance() { return null; }
}
