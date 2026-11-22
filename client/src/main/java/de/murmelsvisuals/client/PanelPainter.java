package de.murmelsvisuals.client;

import de.murmelsvisuals.core.PixelImage;

import java.util.List;

/**
 * Draws the translucent dark/violet look with nothing but filled rectangles.
 * Pure logic without Minecraft classes, so it works the same in every version.
 */
final class PanelPainter {
    private PanelPainter() {}

    interface Fill {
        void fill(int x1, int y1, int x2, int y2, int argb);
    }

    private static final int[] VEIL = {0x00000000, 0x48000000, 0xA0000000};

    static void paintAll(List<Panel> panels, List<PixelDraw> images, int mouseX, int mouseY, int dim,
                         int width, int height, Fill f) {
        int veil = VEIL[Math.max(0, Math.min(2, dim))];
        if ((veil >>> 24) != 0) f.fill(0, 0, width, height, veil);
        for (Panel p : panels) paint(p, mouseX, mouseY, f);
        for (PixelDraw d : images) paintImage(d, f);
    }

    /**
     * Draws a picture as filled rectangles. Neighbouring pixels of the same colour are merged into one rectangle
     * (first sideways, then downwards), so even detailed pictures need far fewer rectangles than pixels.
     */
    static void paintImage(PixelDraw d, Fill f) {
        PixelImage im = d.img;
        double s = Math.min((double) d.w / im.w, (double) d.h / im.h);
        if (s >= 1 && Math.abs(s - Math.round(s)) < 0.05) s = Math.round(s); // whole numbers stay perfectly crisp
        final double sc = s;
        final double ox = d.x + (d.w - im.w * sc) / 2.0;
        final double oy = d.y + (d.h - im.h * sc) / 2.0;
        List<int[]> open = new java.util.ArrayList<>(); // {fromColumn, toColumn, argb, startRow}
        for (int r = 0; r <= im.h; r++) {
            List<int[]> now = new java.util.ArrayList<>();
            if (r < im.h) {
                int c = 0;
                while (c < im.w) {
                    int p = im.argb[r * im.w + c];
                    if ((p >>> 24) < 16) { c++; continue; }
                    int e = c + 1;
                    while (e < im.w && im.argb[r * im.w + e] == p) e++;
                    now.add(new int[] {c, e, p, r});
                    c = e;
                }
            }
            boolean[] used = new boolean[now.size()];
            for (int[] o : open) {
                boolean continued = false;
                for (int k = 0; k < now.size(); k++) {
                    int[] n = now.get(k);
                    if (!used[k] && n[0] == o[0] && n[1] == o[1] && n[2] == o[2]) {
                        used[k] = true;
                        n[3] = o[3]; // keep the start row
                        continued = true;
                        break;
                    }
                }
                if (!continued) f.fill((int) Math.round(ox + o[0] * sc), (int) Math.round(oy + o[3] * sc),
                            (int) Math.round(ox + o[1] * sc), (int) Math.round(oy + r * sc), o[2]);
            }
            open = now;
        }
    }

    static void paint(Panel p, int mx, int my, Fill f) {
        boolean hover = p.interactive && mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + p.h;
        int fill, border;
        switch (p.style) {
            case Panel.GLASS:
                fill = 0x58060612; border = 0x30FFFFFF; break;
            case Panel.ACTIVE:
                fill = hover ? 0xC05B3AA8 : 0xA04C2D8F; border = 0xFFB68CFF; break;
            case Panel.DISABLED:
                fill = 0x40101020; border = 0x22FFFFFF; break;
            case Panel.CELL:
                fill = hover ? 0xA02A2A55 : 0x70121224; border = hover ? 0xFFC4B5FD : 0x40A78BFA; break;
            case Panel.CELL_SELECTED:
                fill = hover ? 0xC04B3585 : 0xA03B2A6B; border = 0xFFF472B6; break;
            case Panel.TAB_ACTIVE:
                fill = 0xB04C2D8F; border = 0xFFB68CFF; break;
            default: // BUTTON
                fill = hover ? 0xB02B2B52 : 0x80141428; border = hover ? 0xFFC4B5FD : 0x55A78BFA; break;
        }
        int x = p.x, y = p.y, w = p.w, h = p.h;
        if (w < 3 || h < 3) return;
        // 1px border, the four corner pixels are left out -> softly rounded corners
        f.fill(x + 1, y, x + w - 1, y + 1, border);
        f.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        f.fill(x, y + 1, x + 1, y + h - 1, border);
        f.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        f.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (p.style == Panel.TAB_ACTIVE) f.fill(x + 1, y + 2, x + 4, y + h - 2, 0xFFF472B6); // pink accent bar
    }
}
