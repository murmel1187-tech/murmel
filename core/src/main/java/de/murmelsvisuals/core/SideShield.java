package de.murmelsvisuals.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Side Shield: Beim Blocken wird der Schild im Ego-Blick an den Rand geschoben,
 * damit er dir nicht die Sicht versperrt. Die Werte sind Schätzungen und über die Config anpassbar.
 */
final class SideShield {
    private SideShield() {}

    static void write(Path out, String left, String right) throws IOException {
        Path dir = out.resolve("assets/minecraft/models/item");
        Files.createDirectories(dir);
        String json = "{\n"
            + "  \"parent\": \"minecraft:item/shield\",\n"
            + "  \"display\": {\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 180, -5], \"translation\": [" + right + "], \"scale\": [1.25, 1.25, 1.25]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 180, -5], \"translation\": [" + left + "], \"scale\": [1.25, 1.25, 1.25]}\n"
            + "  }\n"
            + "}\n";
        Files.writeString(dir.resolve("shield_blocking.json"), json, StandardCharsets.UTF_8);
    }

    /** Nimmt nur "x,y,z" mit Zahlen an, sonst den Standardwert (verhindert kaputtes JSON). */
    static String clean(String value, String fallback) {
        if (value != null && value.matches("\\s*-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?\\s*")) {
            return value.trim();
        }
        return fallback;
    }
}
