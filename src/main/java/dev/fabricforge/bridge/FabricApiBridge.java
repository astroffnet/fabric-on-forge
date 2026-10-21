package dev.fabricforge.bridge;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Forwards Forge events to the Fabric API events implemented in this project. */
public final class FabricApiBridge {
    private static boolean done;

    private FabricApiBridge() {}

    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ServerStartingEvent e) ->
            ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(e.getServer()));
        bus.addListener((ServerStartedEvent e) ->
            ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(e.getServer()));
        bus.addListener((ServerStoppingEvent e) ->
            ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(e.getServer()));
        bus.addListener((ServerStoppedEvent e) ->
            ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(e.getServer()));
        bus.addListener((TickEvent.ServerTickEvent e) -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            if (e.phase == TickEvent.Phase.START) {
                ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(server);
            } else {
                ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
            }
        });
        bus.addListener((RegisterCommandsEvent e) ->
            CommandRegistrationCallback.EVENT.invoker().register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
    }
}
