package de.murmelsvisuals.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MurmelsVisualsClient implements ClientModInitializer {
    /** Wird vom Befehl gesetzt; der Tick öffnet dann das Menü (sonst schließt der Chat es sofort wieder). */
    private static volatile boolean openRequested = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("murmels").executes(ctx -> {
                openRequested = true;
                return 1;
            })));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openRequested) {
                openRequested = false;
                mc.setScreen(new MixScreen(mc.screen));
            }
        });
    }
}
