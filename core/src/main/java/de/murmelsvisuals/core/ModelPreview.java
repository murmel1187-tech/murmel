package de.murmelsvisuals.core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the item model of a pack the way Minecraft shows it in the inventory: the model JSON is read from the
 * pack, its cuboids are rotated with the "gui" display transform and painted with the pack's texture.
 * Pure Java (no Minecraft classes), so it can be tested outside the game.
 */
final class ModelPreview {
    private ModelPreview() {}

    private static final Map<Category, String[]> ITEMS = new EnumMap<>(Category.class);

    static {
        ITEMS.put(Category.CRYSTAL, new String[] {"end_crystal"});
        ITEMS.put(Category.TOTEM, new String[] {"totem_of_undying"});
        ITEMS.put(Category.SWORDS, new String[] {"netherite_sword", "diamond_sword"});
        ITEMS.put(Category.AXES, new String[] {"netherite_axe", "diamond_axe"});
        ITEMS.put(Category.TOOLS, new String[] {"netherite_pickaxe", "diamond_pickaxe"});
        ITEMS.put(Category.BOWS, new String[] {"bow", "crossbow"});
        ITEMS.put(Category.ARMOR, new String[] {"netherite_chestplate", "diamond_chestplate"});
        ITEMS.put(Category.POTIONS, new String[] {"splash_potion", "potion"});
        ITEMS.put(Category.GAPPLE, new String[] {"golden_apple"});
        ITEMS.put(Category.PEARL, new String[] {"ender_pearl"});
        ITEMS.put(Category.ITEMS, new String[] {"diamond"});
    }

    private static final String[] DIRS = {"down", "up", "north", "south", "west", "east"};

    private static final String CUBE =
        "[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{"
        + "\"down\":{\"texture\":\"#all\"},\"up\":{\"texture\":\"#all\"},\"north\":{\"texture\":\"#all\"},"
        + "\"south\":{\"texture\":\"#all\"},\"west\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"}}}]";

    /** @return a size x size picture (or the plain sprite for flat items), or null if nothing is available */
    static PixelImage render(PackSource pack, Category cat, int size) {
        try {
            if (cat == Category.BLOCKS) return renderBlock(pack, "block/obsidian", size);
            String[] names = ITEMS.get(cat);
            if (names == null) return null;
            for (String name : names) {
                PixelImage img = renderItem(pack, name, size);
                if (img != null) return img;
            }
        } catch (RuntimeException e) {
            // a broken model must never break the menu
        }
        return null;
    }

    // ------------------------------------------------------------------ model loading

    private static final class Model {
        final Map<String, String> textures = new HashMap<>();
        List<Object> elements;
        Map<String, Object> gui;
    }

    private static Model resolve(PackSource pack, String id, int depth) {
        Model m = new Model();
        if (depth > 8) return m;
        String ns = "minecraft";
        String path = id;
        int c = id.indexOf(':');
        if (c >= 0) {
            ns = id.substring(0, c);
            path = id.substring(c + 1);
        }
        String text = readText(pack, "assets/" + ns + "/models/" + path + ".json");
        if (text == null) return m; // vanilla model that is not in the pack
        Map<String, Object> root = Json.obj(Json.parse(text));
        if (root == null) return m;

        Object parent = root.get("parent");
        if (parent instanceof String) {
            Model p = resolve(pack, (String) parent, depth + 1);
            m.textures.putAll(p.textures);
            m.elements = p.elements;
            m.gui = p.gui;
        }
        Map<String, Object> tx = Json.obj(root.get("textures"));
        if (tx != null) {
            for (Map.Entry<String, Object> e : tx.entrySet()) {
                if (e.getValue() instanceof String) m.textures.put(e.getKey(), (String) e.getValue());
            }
        }
        List<Object> el = Json.arr(root.get("elements"));
        if (el != null) m.elements = el;
        Map<String, Object> disp = Json.obj(root.get("display"));
        if (disp != null && Json.obj(disp.get("gui")) != null) m.gui = Json.obj(disp.get("gui"));
        return m;
    }

    /** The item definition (items/x.json) can point to a differently named model. */
    private static String modelIdFor(PackSource pack, String name) {
        String text = readText(pack, "assets/minecraft/items/" + name + ".json");
        if (text != null) {
            String id = findModel(Json.parse(text));
            if (id != null) return id;
        }
        return "minecraft:item/" + name;
    }

    private static String findModel(Object o) {
        Map<String, Object> m = Json.obj(o);
        if (m != null) {
            Object type = m.get("type");
            Object model = m.get("model");
            if (model instanceof String && (type == null || "minecraft:model".equals(type) || "model".equals(type))) {
                return (String) model;
            }
            for (Object v : m.values()) {
                String r = findModel(v);
                if (r != null) return r;
            }
            return null;
        }
        List<Object> l = Json.arr(o);
        if (l != null) {
            for (Object v : l) {
                String r = findModel(v);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static String readText(PackSource pack, String path) {
        try (InputStream in = pack.open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static final class Textures {
        private final PackSource pack;
        private final Map<String, BufferedImage> cache = new HashMap<>();

        Textures(PackSource pack) { this.pack = pack; }

        BufferedImage byPath(String path) {
            if (cache.containsKey(path)) return cache.get(path);
            BufferedImage img = null;
            try (InputStream in = pack.open(path)) {
                img = ImageIO.read(in);
            } catch (IOException | RuntimeException e) {
                // missing
            }
            if (img != null && img.getHeight() > img.getWidth() && img.getHeight() % img.getWidth() == 0) {
                img = img.getSubimage(0, 0, img.getWidth(), img.getWidth()); // animation strip -> first frame
            }
            cache.put(path, img);
            return img;
        }

        BufferedImage byRef(String ref, Map<String, String> vars) {
            String r = ref;
            int guard = 0;
            while (r != null && r.startsWith("#") && guard++ < 10) r = vars.get(r.substring(1));
            if (r == null) return null;
            String ns = "minecraft";
            int c = r.indexOf(':');
            if (c >= 0) {
                ns = r.substring(0, c);
                r = r.substring(c + 1);
            }
            return byPath("assets/" + ns + "/textures/" + r + ".png");
        }
    }

    // ------------------------------------------------------------------ items and blocks

    private static PixelImage renderItem(PackSource pack, String name, int size) {
        Model m = resolve(pack, modelIdFor(pack, name), 0);
        Textures tex = new Textures(pack);
        if (m.elements != null && !m.elements.isEmpty()) {
            PixelImage img = draw(m, tex, size);
            if (img != null) return img;
        }
        // flat item: layer0 of the model, or the default texture
        BufferedImage t = null;
        String layer0 = m.textures.get("layer0");
        if (layer0 != null) t = tex.byRef(layer0, m.textures);
        if (t == null) t = tex.byPath("assets/minecraft/textures/item/" + name + ".png");
        return t == null ? null : Preview.fromImage(t, size);
    }

    private static PixelImage renderBlock(PackSource pack, String texture, int size) {
        Model m = new Model();
        m.textures.put("all", texture);
        m.elements = Json.arr(Json.parse(CUBE));
        return draw(m, new Textures(pack), size);
    }

    // ------------------------------------------------------------------ 3D drawing

    private static final class Quad {
        final double[][] p = new double[4][3]; // corners (x, y up, z towards the viewer) in view space
        final double[][] uv = new double[4][2];
        BufferedImage tex;
        double shade;
    }

    private static PixelImage draw(Model m, Textures tex, int size) {
        double[] rot = {30, 225, 0};
        double[] sc = {0.625, 0.625, 0.625};
        double[] tr = {0, 0, 0};
        if (m.gui != null) { // the model has its own gui transform
            rot = new double[] {0, 0, 0};
            sc = new double[] {1, 1, 1};
            double[] a = Json.nums(m.gui.get("rotation"), 3);
            if (a != null) rot = a;
            a = Json.nums(m.gui.get("scale"), 3);
            if (a != null) sc = a;
            a = Json.nums(m.gui.get("translation"), 3);
            if (a != null) tr = a;
        }

        List<Quad> quads = new ArrayList<>();
        for (Object eo : m.elements) {
            Map<String, Object> e = Json.obj(eo);
            if (e == null) continue;
            double[] f = Json.nums(e.get("from"), 3);
            double[] t = Json.nums(e.get("to"), 3);
            Map<String, Object> faces = Json.obj(e.get("faces"));
            if (f == null || t == null || faces == null) continue;
            Map<String, Object> rotMap = Json.obj(e.get("rotation"));
            for (String dir : DIRS) {
                Map<String, Object> face = Json.obj(faces.get(dir));
                if (face == null || !(face.get("texture") instanceof String)) continue;
                BufferedImage img = tex.byRef((String) face.get("texture"), m.textures);
                if (img == null) continue;
                double[] uv = Json.nums(face.get("uv"), 4);
                if (uv == null) uv = defaultUv(dir, f, t);
                int steps = 0;
                if (face.get("rotation") instanceof Double) {
                    steps = (((int) Math.round((Double) face.get("rotation") / 90.0)) % 4 + 4) % 4;
                }
                double[][] verts = faceVerts(dir, f, t);
                if (rotMap != null) rotateElement(verts, rotMap);
                double[][] corners = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
                Quad q = new Quad();
                q.tex = img;
                for (int i = 0; i < 4; i++) {
                    q.uv[i] = corners[(i + steps) % 4];
                    q.p[i] = toView(verts[i], sc, rot, tr);
                }
                double[] n = cross(sub(q.p[1], q.p[0]), sub(q.p[2], q.p[0]));
                if (n[2] <= 1e-9) continue; // faces away from the viewer
                double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                double dot = (-0.354 * n[0] + 0.809 * n[1] + 0.506 * n[2]) / len;
                q.shade = 0.58 + 0.42 * Math.max(0, dot);
                quads.add(q);
            }
        }
        if (quads.isEmpty()) return null;

        double[] zbuf = new double[size * size];
        Arrays.fill(zbuf, -1e9);
        int[] out = new int[size * size];
        for (int pass = 0; pass < 2; pass++) {
            for (Quad q : quads) {
                tri(q, 0, 1, 2, size, zbuf, out, pass);
                tri(q, 0, 2, 3, size, zbuf, out, pass);
            }
        }
        for (int px : out) {
            if ((px >>> 24) != 0) return new PixelImage(size, size, out);
        }
        return null;
    }

    private static double[][] faceVerts(String dir, double[] f, double[] t) {
        double x1 = f[0], y1 = f[1], z1 = f[2], x2 = t[0], y2 = t[1], z2 = t[2];
        switch (dir) {
            case "down":  return new double[][] {{x1, y1, z2}, {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case "up":    return new double[][] {{x1, y2, z1}, {x1, y2, z2}, {x2, y2, z2}, {x2, y2, z1}};
            case "north": return new double[][] {{x2, y2, z1}, {x2, y1, z1}, {x1, y1, z1}, {x1, y2, z1}};
            case "south": return new double[][] {{x1, y2, z2}, {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}};
            case "west":  return new double[][] {{x1, y2, z1}, {x1, y1, z1}, {x1, y1, z2}, {x1, y2, z2}};
            default:      return new double[][] {{x2, y2, z2}, {x2, y1, z2}, {x2, y1, z1}, {x2, y2, z1}};
        }
    }

    private static double[] defaultUv(String dir, double[] f, double[] t) {
        double x1 = f[0], y1 = f[1], z1 = f[2], x2 = t[0], y2 = t[1], z2 = t[2];
        switch (dir) {
            case "down":  return new double[] {x1, 16 - z2, x2, 16 - z1};
            case "up":    return new double[] {x1, z1, x2, z2};
            case "north": return new double[] {16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case "south": return new double[] {x1, 16 - y2, x2, 16 - y1};
            case "west":  return new double[] {z1, 16 - y2, z2, 16 - y1};
            default:      return new double[] {16 - z2, 16 - y2, 16 - z1, 16 - y1};
        }
    }

    private static void rotateElement(double[][] v, Map<String, Object> r) {
        double[] o = Json.nums(r.get("origin"), 3);
        if (o == null) o = new double[] {8, 8, 8};
        double angle = r.get("angle") instanceof Double ? (Double) r.get("angle") : 0;
        String axis = r.get("axis") instanceof String ? (String) r.get("axis") : "y";
        for (double[] p : v) {
            double[] q = {p[0] - o[0], p[1] - o[1], p[2] - o[2]};
            q = axis.equals("x") ? rotX(q, angle) : axis.equals("z") ? rotZ(q, angle) : rotY(q, angle);
            p[0] = q[0] + o[0];
            p[1] = q[1] + o[1];
            p[2] = q[2] + o[2];
        }
    }

    /** Model space (0..16) -> view space: scale, then rotate (Z, then Y, then X), then move. 1.0 = one inventory slot. */
    private static double[] toView(double[] v, double[] sc, double[] rot, double[] tr) {
        double[] p = {(v[0] - 8) / 16 * sc[0], (v[1] - 8) / 16 * sc[1], (v[2] - 8) / 16 * sc[2]};
        p = rotZ(p, rot[2]);
        p = rotY(p, rot[1]);
        p = rotX(p, rot[0]);
        return new double[] {p[0] + tr[0] / 16, p[1] + tr[1] / 16, p[2] + tr[2] / 16};
    }

    private static double[] rotX(double[] p, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[] {p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c};
    }

    private static double[] rotY(double[] p, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[] {p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c};
    }

    private static double[] rotZ(double[] p, double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[] {p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /**
     * Paints one triangle of a quad. Pass 0 paints solid texels (with depth test), pass 1 blends glass-like
     * texels on top of what is already painted (only where nothing solid is closer).
     */
    private static void tri(Quad q, int a, int b, int c, int size, double[] zbuf, int[] out, int pass) {
        double ax = size / 2.0 + q.p[a][0] * size, ay = size / 2.0 - q.p[a][1] * size;
        double bx = size / 2.0 + q.p[b][0] * size, by = size / 2.0 - q.p[b][1] * size;
        double cx = size / 2.0 + q.p[c][0] * size, cy = size / 2.0 - q.p[c][1] * size;
        double den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(den) < 1e-12) return;
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        int tw = q.tex.getWidth(), th = q.tex.getHeight();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den;
                double w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-9 || w1 < -1e-9 || w2 < -1e-9) continue;
                double z = w0 * q.p[a][2] + w1 * q.p[b][2] + w2 * q.p[c][2];
                double u = w0 * q.uv[a][0] + w1 * q.uv[b][0] + w2 * q.uv[c][0];
                double v = w0 * q.uv[a][1] + w1 * q.uv[b][1] + w2 * q.uv[c][1];
                int tx = Math.max(0, Math.min(tw - 1, (int) Math.floor(u / 16.0 * tw)));
                int ty = Math.max(0, Math.min(th - 1, (int) Math.floor(v / 16.0 * th)));
                int argb = q.tex.getRGB(tx, ty);
                int al = argb >>> 24;
                int idx = y * size + x;
                int r = (int) (((argb >> 16) & 255) * q.shade);
                int g = (int) (((argb >> 8) & 255) * q.shade);
                int bl = (int) ((argb & 255) * q.shade);
                if (pass == 0) {
                    if (al >= 250 && z > zbuf[idx]) {
                        zbuf[idx] = z;
                        out[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                    }
                } else if (al > 8 && al < 250 && z > zbuf[idx]) {
                    int dst = out[idx];
                    int da = dst >>> 24;
                    double sa = al / 255.0;
                    double oa = sa + (da / 255.0) * (1 - sa);
                    int nr = (int) ((r * sa + ((dst >> 16) & 255) * (da / 255.0) * (1 - sa)) / oa);
                    int ng = (int) ((g * sa + ((dst >> 8) & 255) * (da / 255.0) * (1 - sa)) / oa);
                    int nb = (int) ((bl * sa + (dst & 255) * (da / 255.0) * (1 - sa)) / oa);
                    out[idx] = ((int) (oa * 255) << 24) | (nr << 16) | (ng << 8) | nb;
                }
            }
        }
    }
}
