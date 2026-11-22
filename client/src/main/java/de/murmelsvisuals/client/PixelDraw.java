package de.murmelsvisuals.client;

import de.murmelsvisuals.core.PixelImage;

/** A picture that is drawn pixel by pixel into the box x, y, w, h. */
final class PixelDraw {
    final int x, y, w, h;
    final PixelImage img;

    PixelDraw(int x, int y, int w, int h, PixelImage img) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.img = img;
    }
}
