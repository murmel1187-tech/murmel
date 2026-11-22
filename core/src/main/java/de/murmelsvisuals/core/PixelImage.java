package de.murmelsvisuals.core;

/** A small ARGB image (row by row) that the menu draws pixel by pixel. */
public final class PixelImage {
    public final int w, h;
    public final int[] argb;

    public PixelImage(int w, int h, int[] argb) {
        this.w = w;
        this.h = h;
        this.argb = argb;
    }
}
