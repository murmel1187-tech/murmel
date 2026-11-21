package de.murmelsvisuals.core;

import java.io.InputStream;

/** Liest unveränderte Original-Dateien aus dem Minecraft-Spiel selbst (nicht aus Resource Packs). */
final class Vanilla {
    private Vanilla() {}

    /** @param path z. B. "assets/minecraft/textures/block/fire_1.png"; null, wenn nicht gefunden */
    static InputStream open(String path) {
        InputStream in = Vanilla.class.getResourceAsStream("/" + path);
        if (in == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) in = cl.getResourceAsStream(path);
        }
        return in;
    }
}
