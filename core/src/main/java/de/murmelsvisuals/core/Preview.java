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
        FILES.put(Category.HEARTS, new String[] {"gui/sprites/hud/heart/full.png", "gui/sprites/hud/heart/container.png"});
        FILES.put(Category.HUNGER, new String[] {"gui/sprites/hud/food_full.png", "gui/sprites/hud/food_empty.png"});
        FILES.put(Category.HOTBAR, new String[] {"gui/sprites/hud/hotbar_selection.png", "gui/sprites/hud/hotbar.png"});
        FILES.put(Category.EXP, new String[] {"gui/sprites/hud/experience_bar_progress.png", "gui/sprites/hud/experience_bar_background.png"});
        FILES.put(Category.GUI, new String[] {"gui/container/inventory.png"});
        FILES.put(Category.SKY, new String[] {"environment/sun.png"});
    }

    /** Kategorien, die als Gegenstand gezeigt werden und deshalb einen 3D-Look (Tiefe) bekommen. */
    static boolean isItemLike(Category c) {
        switch (c) {
            case CRYSTAL: case TOTEM: case SWORDS: case AXES: case TOOLS: case BOWS: case ARMOR:
            case SHIELD: case POTIONS: case GAPPLE: case PEARL: case ITEMS:
                return true;
            default:
                return false;
        }
    }

    /** ARGB-Pixel (res*res, zeilenweise) oder null, wenn das Pack dafür kein Bild hat. */
    public static int[] load(PackSource pack, Category cat, int res) {
        int depth = isItemLike(cat) && res >= 10 ? (res >= 16 ? 2 : 1) : 0;
        int[] flat = loadFlat(pack, cat, res - depth);
        if (flat == null) return null;
        return depth == 0 ? flat : extrude(flat, res - depth, res, depth);
    }

    /**
     * Pseudo-3D: Das Bild bekommt nach rechts unten eine abgedunkelte "Dicke", wie ein Gegenstand,
     * der schräg von der Seite gesehen wird.
     */
    static int[] extrude(int[] src, int srcRes, int res, int depth) {
        int[] out = new int[res * res];
        for (int k = depth; k >= 1; k--) {
            double shade = 0.62 - 0.12 * (k - 1);
            for (int y = 0; y < srcRes; y++) {
                for (int x = 0; x < srcRes; x++) {
                    int p = src[y * srcRes + x];
                    if ((p >>> 24) < 100) continue;
                    int tx = x + k, ty = y + k;
                    if (tx >= res || ty >= res) continue;
                    int r = (int) (((p >> 16) & 255) * shade);
                    int g = (int) (((p >> 8) & 255) * shade);
                    int b = (int) ((p & 255) * shade);
                    out[ty * res + tx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        for (int y = 0; y < srcRes; y++) {
            for (int x = 0; x < srcRes; x++) {
                int p = src[y * srcRes + x];
                if ((p >>> 24) >= 100) out[y * res + x] = p;
            }
        }
        return out;
    }

    static int[] loadFlat(PackSource pack, Category cat, int res) {
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
        if (h > w && h % w == 0) h = w;   // animierte Texturen (Streifen) -> nur das erste Bild
        if (w > 3 * h) w = 3 * h;         // sehr breite Leisten -> nur der linke Teil

        // Seitenverhältnis behalten, in der Mitte des res x res Felds platzieren
        int dw, dh;
        if (w >= h) {
            dw = res;
            dh = Math.max(1, Math.round((float) res * h / w));
        } else {
            dh = res;
            dw = Math.max(1, Math.round((float) res * w / h));
        }
        int ox = (res - dw) / 2;
        int oy = (res - dh) / 2;

        int[] out = new int[res * res]; // 0 = transparent
        for (int dy = 0; dy < dh; dy++) {
            for (int dx = 0; dx < dw; dx++) {
                int x0 = dx * w / dw, x1 = Math.max(x0 + 1, (dx + 1) * w / dw);
                int y0 = dy * h / dh, y1 = Math.max(y0 + 1, (dy + 1) * h / dh);
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int px = img.getRGB(x, y);
                        int pa = px >>> 24;
                        a += pa;
                        r += (long) ((px >> 16) & 0xFF) * pa;
                        g += (long) ((px >> 8) & 0xFF) * pa;
                        b += (long) (px & 0xFF) * pa;
                        n++;
                    }
                }
                if (n > 0 && a > 0) {
                    int ia = (int) (a / n);
                    out[(oy + dy) * res + ox + dx] = (ia << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
            }
        }
        return out;
    }
}
