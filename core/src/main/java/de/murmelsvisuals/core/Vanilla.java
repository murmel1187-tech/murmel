package de.murmelsvisuals.core;

import java.io.InputStream;

/** Liest unveränderte Original-Dateien aus dem Minecraft-Spiel selbst (nicht aus Resource Packs). */
final class Vanilla {
    private Vanilla() {}

    /** @param path z. B. "assets/minecraft/textures/block/fire_1.png"; null, wenn nicht gefunden */
    /** Only for tests outside the game: replaces the lookup. */
    static java.util.function.Function<String, InputStream> override = null;

    static InputStream open(String path) {
        if (override != null) return override.apply(path);
        InputStream in = Vanilla.class.getResourceAsStream("/" + path);
        if (in == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) in = cl.getResourceAsStream(path);
        }
        if (in == null) in = ClassLoader.getSystemResourceAsStream(path);
        return in;
    }
}
