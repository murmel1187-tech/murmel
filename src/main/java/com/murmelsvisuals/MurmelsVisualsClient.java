package com.murmelsvisuals;

import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.gui.MurmelsScreen;
import com.murmelsvisuals.pack.PackController;
import com.murmelsvisuals.util.Compat;
import com.murmelsvisuals.util.GlintFeature;
import com.murmelsvisuals.util.HookStatus;
import com.murmelsvisuals.util.KeyUtil;
import com.murmelsvisuals.util.Keys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/** Client entrypoint: config, key binds, main-menu button, per-tick housekeeping. */
public final class MurmelsVisualsClient implements ClientModInitializer {
	private static KeyMapping openKey;
	private static KeyMapping quickSwitchKey;
	private int saveTimer;

	@Override
	public void onInitializeClient() {
		ConfigManager.init(FabricLoader.getInstance().getConfigDir().resolve("murmelsvisuals.json"));

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MurmelsVisuals.MOD_ID, "main"));
		KeyMapping open = KeyUtil.create("key.murmelsvisuals.open_gui", Keys.RSHIFT, category);
		KeyMapping quick = KeyUtil.create("key.murmelsvisuals.quick_switch", 0, category); // 0 = unbound
		if (open != null) openKey = KeyMappingHelper.registerKeyMapping(open);
		if (quick != null) quickSwitchKey = KeyMappingHelper.registerKeyMapping(quick);

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof TitleScreen) addMenuButton(screen, w, h);
		});
		MurmelsVisuals.LOGGER.info("{} initialised", MurmelsVisuals.NAME);
	}

	private void onTick(Minecraft mc) {
		while (openKey != null && openKey.consumeClick()) {
			if (!(Compat.currentScreen() instanceof MurmelsScreen)) Compat.setScreen(new MurmelsScreen(Compat.currentScreen()));
		}
		while (quickSwitchKey != null && quickSwitchKey.consumeClick()) {
			PackController pc = new PackController();
			boolean ok = pc.swapToPrevious();
			if (!ok && mc.player != null) mc.player.sendOverlayMessage(Component.literal("MurmelsVisuals: " + pc.message()));
			pc.close();
		}

		GlintFeature.tick();

		ModConfig c = ConfigManager.get();
		LocalPlayer p = mc.player;
		boolean first = p != null && mc.options.getCameraType().isFirstPerson();
		HookStatus.LOW_FIRE.expect(c.lowFireEnabled && first && p.isOnFire());
		HookStatus.SIDE_SHIELD.expect(c.sideShieldEnabled && first
				&& (p.getMainHandItem().is(Items.SHIELD) || p.getOffhandItem().is(Items.SHIELD)));

		if (++saveTimer >= 40) { saveTimer = 0; ConfigManager.saveIfDirty(); }
	}

	private static void addMenuButton(net.minecraft.client.gui.screens.Screen screen, int w, int h) {
		ModConfig c = ConfigManager.get();
		if (!c.mainMenuButton) return;
		int bw = 112, bh = 20, m = 6;
		int x = switch (c.mainMenuCorner) { case TOP_RIGHT -> w - bw - m; default -> m; };
		int y = switch (c.mainMenuCorner) { case BOTTOM_LEFT -> h - bh - m; default -> m; };
		Button b = Button.builder(Component.translatable("murmelsvisuals.menu.button"),
				btn -> Compat.setScreen(new MurmelsScreen(screen))).bounds(x, y, bw, bh).build();
		Compat.addWidget(screen, b);
	}

	/** Current key shown in the settings page. */
	public static String openKeyLabel() {
		try {
			return openKey == null ? "unbound" : openKey.getTranslatedKeyMessage().getString();
		} catch (RuntimeException e) {
			return "?";
		}
	}
}
