package com.murmelsvisuals.util;

import java.lang.reflect.Field;

/**
 * Key codes without GLFW. Minecraft 26.3 replaced GLFW by SDL3, so the old GLFW constants are gone and key
 * codes are SDL scancodes. Values are looked up reflectively in {@code InputConstants} (so they always match
 * what the game sends); the literal SDL scancode is used if a field name is not found.
 */
public final class Keys {
	private Keys() {}

	public static final int ESCAPE = k(41, "KEY_ESCAPE");
	public static final int ENTER = k(40, "KEY_RETURN", "KEY_ENTER");
	public static final int KP_ENTER = k(88, "KEY_NUMPADENTER", "KEY_KP_ENTER");
	public static final int BACKSPACE = k(42, "KEY_BACKSPACE");
	public static final int DELETE = k(76, "KEY_DELETE");
	public static final int LEFT = k(80, "KEY_LEFT");
	public static final int RIGHT = k(79, "KEY_RIGHT");
	public static final int HOME = k(74, "KEY_HOME");
	public static final int END = k(77, "KEY_END");
	public static final int V = k(25, "KEY_V");
	public static final int A = k(4, "KEY_A");
	public static final int F = k(9, "KEY_F");
	public static final int RSHIFT = k(229, "KEY_RSHIFT", "KEY_RIGHT_SHIFT");

	/** True if the modifier bit set contains Ctrl (or Cmd/Super). Understands both GLFW- and SDL-style masks. */
	public static boolean ctrl(int mods) {
		return (mods & 2) != 0 || (mods & 0x00C0) != 0 || (mods & 0x0300) != 0;
	}

	private static int k(int fallback, String... names) {
		try {
			Class<?> c = Class.forName("com.mojang.blaze3d.platform.InputConstants");
			for (String n : names) {
				try {
					Field f = c.getField(n);
					if (f.getType() == int.class) return f.getInt(null);
				} catch (NoSuchFieldException ignored) { /* next name */ }
			}
		} catch (ReflectiveOperationException | LinkageError ignored) { /* use fallback */ }
		return fallback;
	}
}
