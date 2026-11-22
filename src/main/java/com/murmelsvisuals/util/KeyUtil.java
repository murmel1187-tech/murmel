package com.murmelsvisuals.util;

import com.murmelsvisuals.MurmelsVisuals;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;

/**
 * Creates a {@link KeyMapping} without depending on one constructor shape. 26.3 uses
 * {@code (String, int, Category)}; older versions used {@code (String, InputConstants.Type, int, Category)}.
 */
public final class KeyUtil {
	private KeyUtil() {}

	/** @return the mapping, or null if no known constructor works. */
	public static KeyMapping create(String name, int code, KeyMapping.Category category) {
		for (Constructor<?> c : KeyMapping.class.getConstructors()) {
			Class<?>[] p = c.getParameterTypes();
			try {
				if (p.length == 3 && p[0] == String.class && p[1] == int.class) {
					return (KeyMapping) c.newInstance(name, code, category);
				}
				if (p.length == 4 && p[0] == String.class && p[2] == int.class && p[1].isEnum()) {
					Object type = null;
					for (Object o : p[1].getEnumConstants()) {
						String n = ((Enum<?>) o).name();
						if (n.equals("KEYBOARD") || n.equals("KEYSYM")) { type = o; break; }
					}
					if (type != null) return (KeyMapping) c.newInstance(name, type, code, category);
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				MurmelsVisuals.LOGGER.warn("KeyMapping constructor {} failed", c, e);
			}
		}
		MurmelsVisuals.LOGGER.error("No usable KeyMapping constructor found - key bind '{}' not registered", name);
		return null;
	}
}
