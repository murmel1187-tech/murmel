package com.murmelsvisuals.util;

import com.murmelsvisuals.MurmelsVisuals;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/**
 * Enchantment Glint: drives the game's own "Glint Strength" and "Glint Speed" options, so the effect
 * applies to every enchanted item, armor piece and the GUI previews without any rendering hook.
 *
 * <p>The player's original values are saved in the config while the feature is on and restored when it
 * is switched off. Range is the vanilla range (0-100 %); the glint cannot be made stronger than vanilla.
 * Options are accessed reflectively ({@code Options#glintStrength()} / {@code glintSpeed()}), so a rename
 * disables only this feature and {@link #available()} reports it.</p>
 */
public final class GlintFeature {
	private GlintFeature() {}

	private static boolean checked, ok;

	public static boolean available() {
		if (!checked) {
			checked = true;
			Double s = read("glintStrength"), v = read("glintSpeed");
			ok = s != null && v != null;
			if (!ok) MurmelsVisuals.LOGGER.warn("Options.glintStrength()/glintSpeed() not found - Enchantment Glint feature disabled");
		}
		return ok;
	}

	/** Called every client tick. Cheap: only writes when a value actually differs. */
	public static void tick() {
		if (!available()) return;
		ModConfig c = ConfigManager.get();
		if (c.glintEnabled) {
			if (c.glintBackupStrength < 0) {
				Double s = read("glintStrength"), v = read("glintSpeed");
				if (s != null) c.glintBackupStrength = s.floatValue();
				if (v != null) c.glintBackupSpeed = v.floatValue();
				ConfigManager.markDirty();
			}
			writeIfDifferent("glintStrength", c.glintStrength);
			writeIfDifferent("glintSpeed", c.glintSpeed);
		} else if (c.glintBackupStrength >= 0 || c.glintBackupSpeed >= 0) {
			if (c.glintBackupStrength >= 0) writeIfDifferent("glintStrength", c.glintBackupStrength);
			if (c.glintBackupSpeed >= 0) writeIfDifferent("glintSpeed", c.glintBackupSpeed);
			c.glintBackupStrength = -1f;
			c.glintBackupSpeed = -1f;
			ConfigManager.markDirty();
		}
	}

	private static void writeIfDifferent(String method, float value) {
		Double cur = read(method);
		if (cur != null && Math.abs(cur - value) < 0.0005) return;
		try {
			Object opt = option(method);
			opt.getClass().getMethod("set", Object.class).invoke(opt, (double) value);
		} catch (ReflectiveOperationException | RuntimeException e) {
			MurmelsVisuals.LOGGER.debug("Could not set {}", method, e);
		}
	}

	private static Double read(String method) {
		try {
			Object opt = option(method);
			Object v = opt.getClass().getMethod("get").invoke(opt);
			return v instanceof Number n ? n.doubleValue() : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Object option(String method) throws ReflectiveOperationException {
		Object options = Minecraft.getInstance().options;
		Method m = options.getClass().getMethod(method);
		return m.invoke(options);
	}
}
