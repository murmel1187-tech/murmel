package com.murmelsvisuals.gui;

import com.murmelsvisuals.config.ConfigManager;

/** Colour palette. Everything is ARGB ints; the accent comes from the user's config. */
final class Theme {
	private Theme() {}

	static final int BG = 0x0B0812;
	static final int PANEL = 0x15101F;
	static final int PANEL2 = 0x1B1527;
	static final int CARD = 0x211A31;
	static final int CARD_HOVER = 0x2B2243;
	static final int BORDER = 0x33294C;
	static final int TEXT = 0xF3EEFB;
	static final int DIM = 0xA398BC;
	static final int FAINT = 0x6D6488;
	static final int GOOD = 0x4ADE80;
	static final int WARN = 0xFBBF24;
	static final int BAD = 0xF87171;

	/** Accent colour (opaque ARGB). */
	static int accent() {
		return 0xFF000000 | ConfigManager.get().accent;
	}

	/** Secondary accent: the accent hue shifted toward pink (default purple -> pink). */
	static int accent2() {
		int rgb = ConfigManager.get().accent;
		float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min, h;
		if (d == 0) h = 0;
		else if (max == r) h = ((g - b) / d + 6) % 6;
		else if (max == g) h = (b - r) / d + 2;
		else h = (r - g) / d + 4;
		h = (h / 6f + 0.10f) % 1f;
		float sat = max == 0 ? 0 : d / max;
		return 0xFF000000 | hsv(h, Math.max(0.55f, sat), Math.max(0.85f, max));
	}

	static int hsv(float h, float s, float v) {
		float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
		float r, g, b;
		int sector = (int) (h * 6) % 6;
		switch (sector) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
	}

	/** RGB + alpha 0..1 -> ARGB. */
	static int a(int rgb, float alpha) {
		int al = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	/** Linear blend of two ARGB colours (alpha included). */
	static int mix(int c1, int c2, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
		int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int opaque(int rgb) {
		return 0xFF000000 | rgb;
	}

	/** Parse "#RRGGBB" / "RRGGBB"; -1 if invalid. */
	static int parseHex(String s) {
		if (s == null) return -1;
		s = s.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() != 6) return -1;
		try {
			return Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
