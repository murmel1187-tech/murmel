package de.murmelsvisuals.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Schaltet die Hintergrund-Unschärfe von Menüs aus, solange unser Menü offen ist, damit man das Spiel
 * dahinter klar sieht. Der alte Wert wird beim Schließen wiederhergestellt.
 * Läuft per Reflection: Gibt es die Einstellung in einer Version nicht, passiert einfach nichts.
 */
final class MenuBlur {
    private MenuBlur() {}

    private static Object option;
    private static Object savedValue;

    static void disable(Object options) {
        if (option != null) return; // schon aus
        try {
            Object opt = find(options);
            if (opt == null) return;
            Object old = opt.getClass().getMethod("get").invoke(opt);
            opt.getClass().getMethod("set", Object.class).invoke(opt, Integer.valueOf(0));
            option = opt;
            savedValue = old;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Einstellung nicht vorhanden -> Hintergrund bleibt wie er ist
        }
    }

    static void restore() {
        if (option == null) return;
        try {
            option.getClass().getMethod("set", Object.class).invoke(option, savedValue);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // nichts zu tun
        }
        option = null;
        savedValue = null;
    }

    private static Object find(Object options) throws ReflectiveOperationException {
        try {
            Method m = options.getClass().getMethod("menuBackgroundBlurriness");
            return m.invoke(options);
        } catch (NoSuchMethodException e) {
            Field f = options.getClass().getDeclaredField("menuBackgroundBlurriness");
            f.setAccessible(true);
            return f.get(options);
        }
    }
}
