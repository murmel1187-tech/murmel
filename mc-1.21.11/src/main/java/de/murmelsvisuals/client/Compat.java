package de.murmelsvisuals.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Alles, was sich zwischen den Minecraft-Versionen unterscheidet, steht hier. */
final class Compat {
    private Compat() {}

    static void registerCommand(String name, Runnable action) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal(name).executes(ctx -> {
                action.run();
                return 1;
            })));
    }

    static void openScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    static Screen currentScreen(Minecraft mc) {
        return mc.screen;
    }
}
