package dev.fabricforge.bridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/** Called from the generated @Mod class of each wrapped Fabric mod. */
public final class Bridge {
    private static final Logger LOG = LogManager.getLogger("FabricOnForge");

    private Bridge() {}

    public static void onModConstruct(String forgeId, Class<?> entry) {
        ModContainerImpl mod = FabricModRegistry.byForgeId(forgeId);
        if (mod == null) {
            LOG.error("No Fabric mod registered for id {}", forgeId);
            return;
        }
        try {
            FabricApiBridge.init();
        } catch (Throwable t) {
            LOG.error("Fabric API event bridge failed to initialise", t);
        }
        mod.setLoader(entry.getClassLoader());
        LOG.info("Initializing Fabric mod {}", forgeId);

        run(mod, "main", ModInitializer.class, ModInitializer::onInitialize);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            run(mod, "client", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
        } else {
            run(mod, "server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
        }
    }

    private static <T> void run(ModContainerImpl mod, String key, Class<T> type, Consumer<T> call) {
        for (String className : mod.entrypointClasses(key)) {
            T entrypoint = FabricLoaderImpl.INSTANCE.instantiate(mod, className, type);
            if (entrypoint == null) {
                continue;
            }
            try {
                call.accept(entrypoint);
            } catch (Throwable t) {
                // Without remapping (stage 2) mods that touch Minecraft classes fail here.
                LOG.error("[{}] '{}' entrypoint {} failed: {}", mod.forgeId(), key, className, t.toString());
            }
        }
    }
}
