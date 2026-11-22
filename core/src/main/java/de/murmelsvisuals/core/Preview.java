package de.murmelsvisuals.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Builds the preview picture of a category for one pack: a 3D render of the item model, or the plain texture. */
public final class Preview {
    private Preview() {}

    /** Plain pictures used when a category has no item model (hearts, hotbar, particles, ...). */
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

    /**
     * @param box side length (in screen pixels) of the area the picture will be shown in
     * @return the picture, or null if the pack has nothing for this category
     */
    public static PixelImage image(PackSource pack, Category cat, int box) {
        PixelImage img = imageFrom(pack, cat, box);
        // the pack has nothing custom for this category -> show the vanilla look instead of an empty field
        if (img == null && !(pack instanceof VanillaPackSource)) {
            img = imageFrom(new VanillaPackSource(), cat, box);
        }
        return img;
    }

    private static PixelImage imageFrom(PackSource pack, Category cat, int box) {
        int r = Math.max(16, (box * 5 / 8) & ~1);
        if (cat == Category.GLINT) return glint(pack, r);
        PixelImage img = ModelPreview.render(pack, cat, r);
        if (img != null) return img;
        return sprite(pack, cat, r);
    }

    /** The glint texture is grey; Minecraft tints it purple, so the preview does the same. */
    private static PixelImage glint(PackSource pack, int maxDim) {
        String[] files = {"misc/enchanted_glint_item.png", "misc/enchanted_item_glint.png", "misc/enchanted_glint_entity.png"};
        for (String rel : files) {
            try (InputStream in = pack.open("assets/minecraft/textures/" + rel)) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) continue;
                PixelImage px = fromImage(img, maxDim);
                int[] out = new int[px.argb.length];
                for (int i = 0; i < out.length; i++) {
                    int p = px.argb[i];
                    int r = Math.min(255, (int) (((p >> 16) & 255) * 0.9 * 2.2));
                    int g = Math.min(255, (int) (((p >> 8) & 255) * 0.45 * 2.2));
                    int b = Math.min(255, (int) ((p & 255) * 1.44 * 2.2));
                    out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                return new PixelImage(px.w, px.h, out);
            } catch (IOException | RuntimeException e) {
                // next file
            }
        }
        return null;
    }

    static PixelImage sprite(PackSource pack, Category cat, int maxDim) {
        String[] candidates = FILES.get(cat);
        if (candidates == null) return null;
        for (String rel : candidates) {
            try (InputStream in = pack.open("assets/minecraft/textures/" + rel)) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) return fromImage(img, maxDim);
            } catch (IOException | RuntimeException e) {
                // missing or broken -> next candidate
            }
        }
        return null;
    }

    /** Converts a texture to a PixelImage: first frame of animations, long bars cut, big images shrunk. */
    static PixelImage fromImage(BufferedImage img, int maxDim) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (h > w && h % w == 0) h = w;   // animated strip -> first frame only
        if (w > 3 * h) w = 3 * h;         // very wide bars -> left part only
        int dw = w, dh = h;
        int longest = Math.max(w, h);
        if (longest > maxDim) {
            double f = (double) maxDim / longest;
            dw = Math.max(1, (int) Math.round(w * f));
            dh = Math.max(1, (int) Math.round(h * f));
        }
        int[] out = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            for (int dx = 0; dx < dw; dx++) {
                int x0 = dx * w / dw, x1 = Math.max(x0 + 1, (dx + 1) * w / dw);
                int y0 = dy * h / dh, y1 = Math.max(y0 + 1, (dy + 1) * h / dh);
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
                if (n > 0 && a > 0) {
                    int ia = (int) (a / n);
                    out[dy * dw + dx] = (ia << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
            }
        }
        return new PixelImage(dw, dh, out);
    }
}
