package de.murmelsvisuals.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MurmelsVisualsClient implements ClientModInitializer {
    /** Wird vom Befehl gesetzt; der Tick öffnet dann das Menü (sonst schließt der Chat es sofort wieder). */
    private static volatile boolean openRequested = false;

    @Override
    public void onInitializeClient() {
        Compat.registerCommand("murmels", () -> openRequested = true);
        Compat.registerOpenKey();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean keyPressed = Compat.openKeyPressed();
            if (openRequested || keyPressed) {
                openRequested = false;
                Compat.openScreen(mc, new MixScreen(Compat.currentScreen(mc)));
            }
        });
    }
}
