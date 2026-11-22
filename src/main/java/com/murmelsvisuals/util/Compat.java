package com.murmelsvisuals.util;

import com.murmelsvisuals.MurmelsVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Small reflection shims for Minecraft APIs that were renamed or moved between 26.1, 26.2 and 26.3
 * (for example {@code Minecraft.setScreen} -> {@code setScreenAndShow} and the {@code screen} field
 * moving to {@code gui.screen()}). Everything is resolved lazily and every failure is caught, so a
 * future rename degrades one convenience instead of crashing the game.
 */
public final class Compat {
	private Compat() {}

	private static Method setScreenMethod;
	private static Object setScreenOwner; // Minecraft or its Gui object
	private static boolean setScreenResolved;

	/** Opens a screen (or returns to the game when {@code screen} is null). */
	public static void setScreen(Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		if (!setScreenResolved) {
			setScreenResolved = true;
			Object gui = fieldValue(mc, "gui");
			// preferred order: Minecraft.setScreenAndShow, Minecraft.setScreen, Gui.setScreen
			Object[][] owners = {{mc, "setScreenAndShow"}, {mc, "setScreen"}, {gui, "setScreen"}, {gui, "setScreenAndShow"}};
			for (Object[] o : owners) {
				if (o[0] == null) continue;
				Method m = findMethod(o[0].getClass(), (String) o[1], Screen.class);
				if (m != null) { setScreenMethod = m; setScreenOwner = o[0]; break; }
			}
			if (setScreenMethod == null) MurmelsVisuals.LOGGER.error("No setScreen method found - GUI cannot open");
		}
		if (setScreenMethod == null) return;
		try {
			setScreenMethod.invoke(setScreenOwner, screen);
		} catch (ReflectiveOperationException e) {
			MurmelsVisuals.LOGGER.error("setScreen failed", e);
		}
	}

	/** The screen currently shown, or null. */
	public static Screen currentScreen() {
		Minecraft mc = Minecraft.getInstance();
		try {
			Object gui = fieldValue(mc, "gui");
			if (gui != null) {
				Method m = findMethod(gui.getClass(), "screen");
				if (m != null && m.invoke(gui) instanceof Screen s) return s;
			}
			Object direct = fieldValue(mc, "screen");
			if (direct instanceof Screen s) return s;
		} catch (ReflectiveOperationException ignored) { /* fall through */ }
		return null;
	}

	/** Opens the vanilla Controls > Key Binds screen on top of {@code parent}. */
	public static boolean openKeyBinds(Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		String[] names = {
				"net.minecraft.client.gui.screens.options.controls.KeyBindsScreen",
				"net.minecraft.client.gui.screens.controls.KeyBindsScreen"
		};
		for (String n : names) {
			try {
				Class<?> c = Class.forName(n);
				for (Constructor<?> k : c.getConstructors()) {
					if (k.getParameterCount() == 2) {
						setScreen((Screen) k.newInstance(parent, mc.options));
						return true;
					}
				}
			} catch (ReflectiveOperationException | ClassCastException ignored) { /* try next */ }
		}
		return false;
	}

	/**
	 * Adds a widget to an already initialised screen (used for the main-menu button). Tries the screen's own
	 * {@code addRenderableWidget}, then Fabric's {@code Screens} helper, because both changed names in 26.x.
	 */
	public static boolean addWidget(Screen screen, Object widget) {
		for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1
						&& m.getParameterTypes()[0].isInstance(widget)) {
					try {
						m.setAccessible(true);
						m.invoke(screen, widget);
						return true;
					} catch (ReflectiveOperationException | RuntimeException ignored) { /* try next */ }
				}
			}
		}
		try {
			Class<?> screens = Class.forName("net.fabricmc.fabric.api.client.screen.v1.Screens");
			for (String n : new String[] {"getButtons", "getWidgets", "getChildren"}) {
				for (Method m : screens.getMethods()) {
					if (m.getName().equals(n) && m.getParameterCount() == 1 && m.invoke(null, screen) instanceof java.util.List<?> l) {
						@SuppressWarnings("unchecked") java.util.List<Object> list = (java.util.List<Object>) l;
						list.add(widget);
						return true;
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) { /* give up */ }
		MurmelsVisuals.LOGGER.warn("Could not add the main menu button");
		return false;
	}

	/** Clipboard text via the game's keyboard handler; empty string if unavailable. */
	public static String clipboard() {
		Minecraft mc = Minecraft.getInstance();
		try {
			Object kh = fieldValue(mc, "keyboardHandler");
			Method m = kh == null ? null : findMethod(kh.getClass(), "getClipboard");
			if (m != null && m.invoke(kh) instanceof String s) return s;
		} catch (ReflectiveOperationException ignored) { /* none */ }
		return "";
	}

	/** Opens a folder in the OS file manager. Uses plain OS commands, independent of Minecraft APIs. */
	public static void openFolder(Path dir) {
		try {
			String os = System.getProperty("os.name", "").toLowerCase();
			String p = dir.toAbsolutePath().toString();
			ProcessBuilder pb;
			if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", p);
			else if (os.contains("mac")) pb = new ProcessBuilder("open", p);
			else pb = new ProcessBuilder("xdg-open", p);
			pb.start();
		} catch (Exception e) {
			MurmelsVisuals.LOGGER.warn("Could not open {}", dir, e);
		}
	}

	// ---- reflection helpers -------------------------------------------------------------------------

	private static Object fieldValue(Object target, String name) {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				// try superclass
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	private static Method findMethod(Class<?> type, String name, Class<?>... params) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name, params);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) { /* keep looking */ }
		}
		return null;
	}
}
