package de.murmelsvisuals.client;

/** A flat rectangle that is drawn behind the widgets (the mod draws its own look instead of Minecraft buttons). */
final class Panel {
    static final int GLASS = 0, BUTTON = 1, ACTIVE = 2, DISABLED = 3, CELL = 4, CELL_SELECTED = 5, TAB_ACTIVE = 6;

    final int x, y, w, h, style;
    final boolean interactive;

    Panel(int x, int y, int w, int h, int style, boolean interactive) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.style = style;
        this.interactive = interactive;
    }
}
