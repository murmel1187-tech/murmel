package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Ein installiertes Resource Pack (Ordner oder ZIP), nur lesend. */
public interface PackSource extends AutoCloseable {
    /** Eindeutige Kennung, z. B. der Datei-/Ordnername. */
    String id();

    /** Alle Dateien unter "assets/", relativ zur Pack-Wurzel, mit "/" als Trenner. */
    List<String> files() throws IOException;

    InputStream open(String path) throws IOException;

    @Override
    void close() throws IOException;
}
