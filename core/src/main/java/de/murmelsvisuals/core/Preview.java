package de.murmelsvisuals.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Lädt ein typisches Bild einer Kategorie aus einem Pack und verkleinert es auf res x res Pixel. */
public final class Preview {
    private Preview() {}

    private static final Map<Category, String[]> FILES = new EnumMap<>(Category.class);

    static {
        FILES.put(Category.CRYSTAL, new String[] {"item/end_crystal.png"});
        FILES.put(Category.TOTEM, new String[] {"item/totem_of_undying.png"});
        FILES.put(Category.SWORDS, new String[] {"item/netherite_sword.png", "item/diamond_sword.png"});
        FILES.put(Category.AXES, new String[] {"item/netherite_axe.png", "item/diamond_axe.png"});
        FILES.put(Category.TOOLS, new String[] {"item/netherite_pickaxe.png", "item/diamond_pickaxe.png"});
        FILES.put(Category.BOWS, new String[] {"item/bow.png", "item/crossbow_standby.png"});
        FILES.put(Category.ARMOR, new String[] {"item/netherite_chestplate.png", "item/diamond_chestplate.png"});
        FILES.put(Category.SHIELD, new String[] {"item/shield.png"});
        FILES.put(Category.POTIONS, new String[] {"item/splash_potion.png", "item/potion.png"});
        FILES.put(Category.GAPPLE, new String[] {"item/golden_apple.png"});
        FILES.put(Category.PEARL, new String[] {"item/ender_pearl.png"});
        FILES.put(Category.FIRE, new String[] {"block/fire_0.png"});
        FILES.put(Category.BLOCKS, new String[] {"block/obsidian.png"});
        FILES.put(Category.ITEMS, new String[] {"item/diamond.png"});
        FILES.put(Category.PARTICLES, new String[] {"particle/flame.png"});
        FILES.put(Category.SKY, new String[] {"environment/sun.png"});
    }

    /** ARGB-Pixel (res*res, zeilenweise) oder null, wenn das Pack dafür kein Bild hat. */
    public static int[] load(PackSource pack, Category cat, int res) {
        String[] candidates = FILES.get(cat);
        if (candidates == null) return null;
        for (String rel : candidates) {
            try (InputStream in = pack.open("assets/minecraft/textures/" + rel)) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) return downscale(img, res);
            } catch (IOException | RuntimeException e) {
                // Datei fehlt oder ist kaputt -> nächster Kandidat
            }
        }
        return null;
    }

    static int[] downscale(BufferedImage img, int res) {
        int w = img.getWidth();
        int h = img.getHeight();
        // Animierte Texturen (Streifen) -> nur das erste Bild
        if (h > w && h % w == 0) h = w;

        int[] out = new int[res * res];
        for (int dy = 0; dy < res; dy++) {
            for (int dx = 0; dx < res; dx++) {
                int x0 = dx * w / res, x1 = Math.max(x0 + 1, (dx + 1) * w / res);
                int y0 = dy * h / res, y1 = Math.max(y0 + 1, (dy + 1) * h / res);
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int p = img.getRGB(x, y);
                        int pa = p >>> 24;
                        a += pa;
                        r += (long) ((p >> 16) & 0xFF) * pa;
                        g += (long) ((p >> 8) & 0xFF) * pa;
                        b += (long) (p & 0xFF) * pa;
                        n++;
                    }
                }
                if (n == 0 || a == 0) {
                    out[dy * res + dx] = 0; // transparent
                } else {
                    int ia = (int) (a / n);
                    out[dy * res + dx] = (ia << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
            }
        }
        return out;
    }
}
