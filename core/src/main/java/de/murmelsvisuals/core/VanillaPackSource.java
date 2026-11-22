package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** The original Minecraft assets (read from the game itself), used when a pack has nothing for a category. */
public final class VanillaPackSource implements PackSource {
    public static final String ID = "<vanilla>";

    @Override public String id() { return ID; }

    @Override public List<String> files() { return List.of(); }

    @Override
    public InputStream open(String path) throws IOException {
        InputStream in = Vanilla.open(path);
        if (in == null) throw new IOException("Not in vanilla assets: " + path);
        return in;
    }

    @Override public void close() {}
}
