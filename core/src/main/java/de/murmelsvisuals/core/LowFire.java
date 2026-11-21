package de.murmelsvisuals.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Low Fire: Die Feuer-Textur (fire_1), die du im Ego-Blick siehst, wird oben abgeschnitten,
 * sodass das Feuer nur noch unten im Bild sichtbar ist.
 */
final class LowFire {
    private LowFire() {}

    private static final String PNG = "assets/minecraft/textures/block/fire_1.png";

    /** @param level 1 = mittel (untere 50 %), 2 = niedrig (untere 28 %). Rückgabe: Warnung oder null. */
    static String apply(Path out, int level) throws IOException {
        Path png = out.resolve(PNG);
        BufferedImage img;
        if (Files.isRegularFile(png)) {
            img = ImageIO.read(png.toFile()); // Textur aus dem gewählten Feuer-Pack
        } else {
            try (InputStream in = Vanilla.open(PNG)) {
                if (in == null) return "Low Fire: Original-Feuertextur nicht gefunden.";
                img = ImageIO.read(in);
            }
            Files.createDirectories(png.getParent());
            try (InputStream meta = Vanilla.open(PNG + ".mcmeta")) {
                Path metaOut = out.resolve(PNG + ".mcmeta");
                if (meta != null) Files.copy(meta, metaOut);
                else Files.writeString(metaOut, "{\"animation\":{\"frametime\":2}}", StandardCharsets.UTF_8);
            }
        }
        if (img == null) return "Low Fire: Feuertextur konnte nicht gelesen werden.";
        ImageIO.write(crop(img, level == 2 ? 0.28 : 0.5), "png", png.toFile());
        return null;
    }

    /** Löscht in jedem Einzelbild den oberen Teil und behält nur den unteren Anteil keep. */
    static BufferedImage crop(BufferedImage src, double keep) {
        int w = src.getWidth();
        int h = src.getHeight();
        int frameH = (h > w && h % w == 0) ? w : h;
        int clearRows = frameH - Math.max(1, (int) Math.round(frameH * keep));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            boolean cleared = (y % frameH) < clearRows;
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, cleared ? 0 : src.getRGB(x, y));
            }
        }
        return out;
    }
}
