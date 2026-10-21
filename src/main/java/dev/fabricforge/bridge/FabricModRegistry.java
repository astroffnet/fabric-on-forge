package dev.fabricforge.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared state between the locator (early) and the bridge (mod construction). */
public final class FabricModRegistry {
    private static final Map<String, ModContainerImpl> BY_FABRIC_ID = new LinkedHashMap<>();
    private static final Map<String, ModContainerImpl> BY_FORGE_ID = new LinkedHashMap<>();

    private FabricModRegistry() {}

    public static synchronized void register(ModContainerImpl c) {
        BY_FABRIC_ID.put(c.getMetadata().getId(), c);
        BY_FORGE_ID.put(c.forgeId(), c);
    }

    public static synchronized ModContainerImpl byFabricId(String id) { return BY_FABRIC_ID.get(id); }
    public static synchronized ModContainerImpl byForgeId(String id) { return BY_FORGE_ID.get(id); }
    public static synchronized Collection<ModContainerImpl> all() { return new ArrayList<>(BY_FABRIC_ID.values()); }
}
