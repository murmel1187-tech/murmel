package de.murmelsvisuals.client;

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

    static void paintAll(List<Panel> panels, int mouseX, int mouseY, int dim, int width, int height, Fill f) {
        int veil = VEIL[Math.max(0, Math.min(2, dim))];
        if ((veil >>> 24) != 0) f.fill(0, 0, width, height, veil);
        for (Panel p : panels) paint(p, mouseX, mouseY, f);
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
