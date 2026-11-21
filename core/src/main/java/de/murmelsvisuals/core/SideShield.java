package de.murmelsvisuals.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Side Shield (so wie in den gängigen PvP-Packs):
 *  - Ruhezustand: Der Schild liegt in der Ego-Sicht seitlich, fast außerhalb des Bildes.
 *  - Blocken: Der Schild ist kleiner und sitzt tiefer, damit er weniger Sicht verdeckt.
 * Alle Anzeige-Positionen (3rd Person, Inventar, Boden) bleiben wie im Original.
 */
final class SideShield {
    private SideShield() {}

    /** @param blockScale Größe des Schilds beim Blocken in der Ego-Sicht (z. B. 0.9 klein, 1.25 normal) */
    static void write(Path out, double blockScale) throws IOException {
        Path dir = out.resolve("assets/minecraft/models/item");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("shield.json"), idle(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("shield_blocking.json"), blocking(blockScale), StandardCharsets.UTF_8);
    }

    private static String idle() {
        return "{\n"
            + "  \"parent\": \"builtin/entity\",\n"
            + "  \"gui_light\": \"front\",\n"
            + "  \"textures\": {\"particle\": \"block/dark_oak_planks\"},\n"
            + "  \"display\": {\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [0, 90, 0], \"translation\": [10, 6, -4], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [0, 90, 0], \"translation\": [10, 6, 12], \"scale\": [1, 1, 1]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 90, 90], \"translation\": [15, 0, 10], \"scale\": [1.25, 1.25, 1.25]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 90, 90], \"translation\": [15, -20, 10], \"scale\": [1.25, 1.25, 1.25]},\n"
            + "    \"gui\": {\"rotation\": [15, -25, -5], \"translation\": [2, 3, 0], \"scale\": [0.65, 0.65, 0.65]},\n"
            + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [-2, 4, -5], \"scale\": [0.5, 0.5, 0.5]},\n"
            + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [4, 4, 2], \"scale\": [0.25, 0.25, 0.25]}\n"
            + "  }\n"
            + "}\n";
    }

    private static String blocking(double s) {
        String sc = String.format(Locale.ROOT, "%s, %s, %s", s, s, s);
        return "{\n"
            + "  \"parent\": \"builtin/entity\",\n"
            + "  \"gui_light\": \"front\",\n"
            + "  \"textures\": {\"particle\": \"block/dark_oak_planks\"},\n"
            + "  \"display\": {\n"
            + "    \"thirdperson_righthand\": {\"rotation\": [45, 135, 0], \"translation\": [3.51, 11, -2], \"scale\": [1, 1, 1]},\n"
            + "    \"thirdperson_lefthand\": {\"rotation\": [45, 135, 0], \"translation\": [13.51, 3, 5], \"scale\": [1, 1, 1]},\n"
            + "    \"firstperson_righthand\": {\"rotation\": [0, 180, -5], \"translation\": [-15, 0, -11], \"scale\": [" + sc + "]},\n"
            + "    \"firstperson_lefthand\": {\"rotation\": [0, 180, -5], \"translation\": [5, 0, -11], \"scale\": [" + sc + "]},\n"
            + "    \"gui\": {\"rotation\": [15, -25, -5], \"translation\": [2, 3, 0], \"scale\": [0.65, 0.65, 0.65]}\n"
            + "  }\n"
            + "}\n";
    }
}
