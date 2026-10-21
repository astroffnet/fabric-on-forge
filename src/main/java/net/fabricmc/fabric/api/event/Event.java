package net.fabricmc.fabric.api.event;

import net.minecraft.resources.ResourceLocation;

public abstract class Event<T> {
    public static final ResourceLocation DEFAULT_PHASE = new ResourceLocation("fabric", "default");

    protected volatile T invoker;

    public final T invoker() {
        return invoker;
    }

    public abstract void register(T listener);

    public void register(ResourceLocation phaseIdentifier, T listener) {
        register(listener);
    }

    /** Phase ordering is not implemented: listeners run in registration order. */
    public void addPhaseOrdering(ResourceLocation firstPhase, ResourceLocation secondPhase) {
    }
}
