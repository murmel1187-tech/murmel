package com.murmelsvisuals.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Rounded rectangles, gradients and the pixel-art icon set. Only uses {@code fill} so it is cheap and API-stable. */
final class Draw {
	private Draw() {}

	/** Filled rounded rectangle. */
	static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) { g.fill(x, y, x + w, y + h, argb); return; }
		g.fill(x, y + r, x + w, y + h - r, argb);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, argb);
		}
	}

	/** 1px rounded outline (does not draw the inside, so translucent panels stay clean). */
	static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int argb) {
		if (w <= 1 || h <= 1) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + 1, argb);
			g.fill(x, y + h - 1, x + w, y + h, argb);
			g.fill(x, y + 1, x + 1, y + h - 1, argb);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
			return;
		}
		int prevInset = -1;
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
			if (i == 0) {
				g.fill(x + inset, y, x + w - inset, y + 1, argb);
				g.fill(x + inset, y + h - 1, x + w - inset, y + h, argb);
			} else {
				int segW = Math.max(1, prevInset - inset);
				g.fill(x + inset, y + i, x + inset + segW, y + i + 1, argb);
				g.fill(x + w - inset - segW, y + i, x + w - inset, y + i + 1, argb);
				g.fill(x + inset, y + h - 1 - i, x + inset + segW, y + h - i, argb);
				g.fill(x + w - inset - segW, y + h - 1 - i, x + w - inset, y + h - i, argb);
			}
			prevInset = inset;
		}
		g.fill(x, y + r, x + 1, y + h - r, argb);
		g.fill(x + w - 1, y + r, x + w, y + h - r, argb);
	}

	/** Horizontal two-colour gradient built from vertical strips (max 32 fills). */
	static void gradientH(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		if (w <= 0 || h <= 0) return;
		int steps = Math.min(32, w);
		for (int i = 0; i < steps; i++) {
			int x1 = x + (int) ((long) w * i / steps);
			int x2 = x + (int) ((long) w * (i + 1) / steps);
			g.fill(x1, y, x2, y + h, Theme.mix(c1, c2, steps == 1 ? 0 : i / (float) (steps - 1)));
		}
	}

	/** Rounded rect filled with a horizontal gradient (rows are clipped by the corner insets). */
	static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c1, int c2) {
		r = Math.min(r, Math.min(w, h) / 2);
		int steps = Math.min(24, w);
		for (int i = 0; i < steps; i++) {
			int x1 = x + (int) ((long) w * i / steps);
			int x2 = x + (int) ((long) w * (i + 1) / steps);
			int col = Theme.mix(c1, c2, steps == 1 ? 0 : i / (float) (steps - 1));
			// strips fully inside the straight part
			if (x1 >= x + r && x2 <= x + w - r) {
				g.fill(x1, y, x2, y + h, col);
			} else {
				for (int row = 0; row < h; row++) {
					int edge = row < r ? row : (row >= h - r ? h - 1 - row : r);
					int inset = 0;
					if (edge < r) {
						double dy = r - edge - 0.5;
						inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
					}
					int a = Math.max(x1, x + inset), b = Math.min(x2, x + w - inset);
					if (b > a) g.fill(a, y + row, b, y + row + 1, col);
				}
			}
		}
	}

	/** Soft drop shadow: a few growing translucent rounded rects. */
	static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int spread, float strength) {
		for (int i = spread; i >= 1; i--) {
			float a = strength * (1f - i / (float) (spread + 1)) * 0.35f;
			round(g, x - i, y - i + spread / 2, w + 2 * i, h + 2 * i, r + i, Theme.a(0x000000, a));
		}
	}

	// ---- icons ------------------------------------------------------------------------------------------

	/** Icon ids: 0-11 = PackCategory ordinal, then the extras. */
	static final int ICON_PACKS = 12, ICON_SETTINGS = 13, ICON_SEARCH = 14, ICON_CLOSE = 15, ICON_LOCK = 16,
			ICON_UP = 17, ICON_DOWN = 18, ICON_FOLDER = 19, ICON_CHECK = 20;

	private static final String[][] ICONS = {
			// BLOCKS
			{"########", "#......#", "#.####.#", "#.#..#.#", "#.#..#.#", "#.####.#", "#......#", "########"},
			// ITEMS (gem)
			{"...##...", "..####..", ".######.", "########", ".######.", "..####..", "...##...", "........"},
			// POTIONS
			{"..####..", "...##...", "..####..", ".######.", ".######.", ".######.", "..####..", "........"},
			// WEAPONS (sword)
			{".......#", "......##", ".....##.", "#...##..", ".#.##...", "..###...", "..##....", ".#..#..."},
			// TOOLS (pickaxe)
			{".######.", "##....##", "#..##..#", "...##...", "...##...", "...##...", "...##...", "...##..."},
			// ARMOR
			{".######.", "########", "##.##.##", "##.##.##", "########", ".######.", ".##..##.", ".##..##."},
			// SKY (sun)
			{"....#...", ".#..#..#", "..####..", "#.####.#", "..####..", ".#..#..#", "....#...", "........"},
			// ENVIRONMENT (tree)
			{"...##...", "..####..", ".######.", "########", ".######.", "...##...", "...##...", "...##..."},
			// PARTICLES
			{"...#....", "...#.#..", ".#####..", "...#.#..", ".#.#....", "#######.", ".#.#.#..", "...#...."},
			// GUI (window)
			{"########", "#......#", "########", "#.#....#", "#.#.##.#", "#.#....#", "#......#", "########"},
			// HUD (bars)
			{"########", "#......#", "#.####.#", "#......#", "#.##...#", "#......#", "#.#####.", "########"},
			// SOUNDS
			{"...#....", "..##..#.", "###..#.#", "###.#.#.", "###.#.#.", "###..#.#", "..##..#.", "...#...."},
			// PACKS (layers)
			{"..####..", ".######.", "########", ".######.", "########", ".######.", "########", "..####.."},
			// SETTINGS (gear)
			{"...##...", ".#.##.#.", "..####..", "###..###", "###..###", "..####..", ".#.##.#.", "...##..."},
			// SEARCH
			{".####...", "#....#..", "#....#..", "#....#..", ".####...", "....##..", ".....##.", "......##"},
			// CLOSE
			{"#......#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#......#"},
			// LOCK
			{"..####..", ".#....#.", ".#....#.", "########", "########", "###..###", "###..###", "########"},
			// UP
			{"...##...", "..####..", ".######.", "########", "...##...", "...##...", "...##...", "...##..."},
			// DOWN
			{"...##...", "...##...", "...##...", "...##...", "########", ".######.", "..####..", "...##..."},
			// FOLDER
			{"###.....", "########", "#......#", "#......#", "#......#", "#......#", "#......#", "########"},
			// CHECK
			{"........", "......##", ".....##.", "#...##..", "##.##...", ".###....", "..#.....", "........"},
	};

	/** Draws an 8x8 pixel icon scaled by {@code px} screen pixels per icon pixel. */
	static void icon(GuiGraphicsExtractor g, int id, int x, int y, int px, int argb) {
		if (id < 0 || id >= ICONS.length || px <= 0) return;
		String[] rows = ICONS[id];
		for (int r = 0; r < 8; r++) {
			int c = 0;
			while (c < 8) {
				if (rows[r].charAt(c) == '#') {
					int start = c;
					while (c < 8 && rows[r].charAt(c) == '#') c++;
					g.fill(x + start * px, y + r * px, x + c * px, y + (r + 1) * px, argb);
				} else {
					c++;
				}
			}
		}
	}
}
