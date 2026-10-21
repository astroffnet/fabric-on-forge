package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

public final class ServerTickEvents {
    private ServerTickEvents() {}

    public static final Event<StartTick> START_SERVER_TICK = EventFactory.createArrayBacked(StartTick.class,
        callbacks -> server -> {
            for (StartTick c : callbacks) {
                c.onStartTick(server);
            }
        });

    public static final Event<EndTick> END_SERVER_TICK = EventFactory.createArrayBacked(EndTick.class,
        callbacks -> server -> {
            for (EndTick c : callbacks) {
                c.onEndTick(server);
            }
        });

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
}
