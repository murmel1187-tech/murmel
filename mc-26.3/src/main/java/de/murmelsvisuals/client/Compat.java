package de.murmelsvisuals.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** Alles, was sich zwischen den Minecraft-Versionen unterscheidet, steht hier. */
final class Compat {
    private Compat() {}

    private static KeyMapping openKey;

    static void registerCommand(String name, Runnable action) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal(name).executes(ctx -> {
                action.run();
                return 1;
            })));
    }

    /** Taste zum Öffnen des Menüs (Standard: M, in den Steuerungs-Optionen änderbar). */
    static void registerOpenKey() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("murmelsvisuals", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.murmelsvisuals.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, category));
    }

    static boolean openKeyPressed() {
        boolean pressed = false;
        while (openKey.consumeClick()) pressed = true;
        return pressed;
    }

    /** Makes a widget invisible but keeps it clickable (we draw the look ourselves). */
    static void hide(AbstractWidget w) {
        try {
            AbstractWidget.class.getMethod("setAlpha", float.class).invoke(w, 0f);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // keep the vanilla look if this version has no setAlpha
        }
    }

    static void openScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    static Screen currentScreen(Minecraft mc) {
        return mc.gui.screen();
    }
}
