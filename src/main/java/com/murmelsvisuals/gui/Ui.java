package com.murmelsvisuals.gui;

import com.murmelsvisuals.config.ConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * A tiny immediate-mode UI toolkit. Each frame the screen calls the widget methods; widgets draw
 * themselves and register a hit region. Clicks are resolved against the regions of the previous frame
 * (what the player actually saw). Sizes are in real GUI pixels - callers multiply by {@link #s}.
 */
final class Ui {
	enum Style { PRIMARY, GHOST, DANGER, SUBTLE }

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
		boolean contains(int mx, int my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
	}

	static final int LINE = 9; // vanilla font height

	final Font font;
	GuiGraphicsExtractor g;
	float s = 1f;
	int mx, my;
	float dt;
	/** While a modal dialog is open, everything drawn with {@code layer == 0} ignores the mouse. */
	boolean modalOpen;
	int layer;
	TextField focus;

	private List<Hit> hits = new ArrayList<>();
	private List<Hit> nextHits = new ArrayList<>();
	private final Map<String, Float> anims = new HashMap<>();
	private int[] clip;
	private final Map<String, float[]> scroll = new HashMap<>();
	private Map<String, int[]> areas = new LinkedHashMap<>();
	private Map<String, int[]> nextAreas = new LinkedHashMap<>();
	private DoubleConsumer dragFn;
	private Runnable dragRelease;
	private int dragX, dragW;
	private double dragMin, dragMax, dragStep;
	private String tooltip;

	Ui(Font font) {
		this.font = font;
	}

	// ---- frame ------------------------------------------------------------------------------------------

	void begin(GuiGraphicsExtractor g, int mx, int my, float dt, float scale) {
		this.g = g;
		this.mx = mx;
		this.my = my;
		this.dt = Math.min(dt, 0.1f);
		this.s = scale;
		hits = nextHits;
		nextHits = new ArrayList<>();
		areas = nextAreas;
		nextAreas = new LinkedHashMap<>();
		clip = null;
		layer = 0;
		tooltip = null;
	}

	int u(float v) {
		return Math.round(v * s);
	}

	// ---- animation --------------------------------------------------------------------------------------

	/** Smoothly move a stored value toward {@code target}. Snaps instantly when animations are off. */
	float anim(String key, float target, float speed) {
		var cfg = ConfigManager.get();
		Float cur = anims.get(key);
		if (cur == null) { anims.put(key, target); return target; }
		if (!cfg.animations) { anims.put(key, target); return target; }
		float v = cur + (target - cur) * (1f - (float) Math.exp(-dt * speed * cfg.animationSpeed));
		if (Math.abs(v - target) < 0.002f) v = target;
		anims.put(key, v);
		return v;
	}

	// ---- hit testing ------------------------------------------------------------------------------------

	boolean hover(int x, int y, int w, int h) {
		if (modalOpen && layer == 0) return false;
		if (mx < x || my < y || mx >= x + w || my >= y + h) return false;
		return clip == null || (mx >= clip[0] && my >= clip[1] && mx < clip[2] && my < clip[3]);
	}

	void hit(int x, int y, int w, int h, Runnable left, Runnable right) {
		if (modalOpen && layer == 0) return;
		int x1 = x, y1 = y, x2 = x + w, y2 = y + h;
		if (clip != null) {
			x1 = Math.max(x1, clip[0]); y1 = Math.max(y1, clip[1]);
			x2 = Math.min(x2, clip[2]); y2 = Math.min(y2, clip[3]);
		}
		if (x2 <= x1 || y2 <= y1) return;
		nextHits.add(new Hit(x1, y1, x2 - x1, y2 - y1, left, right));
	}

	/** Swallows clicks in a rectangle (used by modal backdrops and panels). */
	void block(int x, int y, int w, int h) {
		hit(x, y, w, h, () -> {}, () -> {});
	}

	boolean click(double px, double py, int button) {
		int ix = (int) px, iy = (int) py;
		focus = null;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(ix, iy)) {
				Runnable r = button == 1 ? h.right : h.left;
				if (r != null) { r.run(); return true; }
				if (button == 0) return false;
			}
		}
		return false;
	}

	void drag(double px) {
		if (dragFn != null) applyDrag(px);
	}

	void release() {
		if (dragFn != null) {
			dragFn = null;
			if (dragRelease != null) dragRelease.run();
			dragRelease = null;
		}
	}

	boolean isDragging() {
		return dragFn != null;
	}

	private void applyDrag(double px) {
		double t = Math.max(0, Math.min(1, (px - dragX) / Math.max(1, dragW)));
		double val = dragMin + t * (dragMax - dragMin);
		if (dragStep > 0) val = Math.round(val / dragStep) * dragStep;
		dragFn.accept(Math.max(dragMin, Math.min(dragMax, val)));
	}

	// ---- text -------------------------------------------------------------------------------------------

	/** Text scale for a size relative to the base font. */
	float ts(float rel) {
		return s * rel;
	}

	int th(float rel) {
		return Math.round(LINE * ts(rel));
	}

	int tw(String str, float rel) {
		return Math.round(font.width(str) * ts(rel));
	}

	void text(String str, int x, int y, int color, float rel) {
		if (str == null || str.isEmpty()) return;
		float k = ts(rel);
		if (Math.abs(k - 1f) < 0.01f) {
			g.text(font, str, x, y, color, false);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate((float) x, (float) y);
		g.pose().scale(k, k);
		g.text(font, str, 0, 0, color, false);
		g.pose().popMatrix();
	}

	void textCentered(String str, int cx, int y, int color, float rel) {
		text(str, cx - tw(str, rel) / 2, y, color, rel);
	}

	/** Shorten with an ellipsis so the text fits {@code maxPx}. */
	String fit(String str, int maxPx, float rel) {
		if (str == null) return "";
		if (tw(str, rel) <= maxPx) return str;
		String ell = "...";
		int end = str.length();
		while (end > 0 && tw(str.substring(0, end) + ell, rel) > maxPx) end--;
		return str.substring(0, Math.max(0, end)) + ell;
	}

	List<String> wrap(String str, int maxPx, float rel, int maxLines) {
		List<String> lines = new ArrayList<>();
		if (str == null) return lines;
		for (String para : str.split("\n")) {
			StringBuilder cur = new StringBuilder();
			for (String word : para.split(" ")) {
				String test = cur.isEmpty() ? word : cur + " " + word;
				if (tw(test, rel) <= maxPx || cur.isEmpty()) cur = new StringBuilder(test);
				else { lines.add(cur.toString()); cur = new StringBuilder(word); }
			}
			lines.add(cur.toString());
		}
		if (maxLines > 0 && lines.size() > maxLines) {
			List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
			cut.set(maxLines - 1, fit(cut.get(maxLines - 1) + "...", maxPx, rel));
			return cut;
		}
		return lines;
	}

	void tip(String t) {
		tooltip = t;
	}

	void drawTooltip(int screenW, int screenH) {
		if (tooltip == null || tooltip.isEmpty()) return;
		layer = 2;
		List<String> lines = wrap(tooltip, u(220), 0.9f, 6);
		int w = 0;
		for (String l : lines) w = Math.max(w, tw(l, 0.9f));
		int pad = u(6), h = lines.size() * th(0.9f) + pad * 2 + (lines.size() - 1) * u(1);
		int x = Math.min(mx + u(12), screenW - w - pad * 2 - u(4));
		int y = Math.min(my + u(12), screenH - h - u(4));
		Draw.round(g, x, y, w + pad * 2, h, u(5), Theme.a(0x0B0812, 0.97f));
		Draw.outline(g, x, y, w + pad * 2, h, u(5), Theme.a(Theme.accent(), 0.7f));
		int ty = y + pad;
		for (String l : lines) { text(l, x + pad, ty, Theme.TEXT, 0.9f); ty += th(0.9f) + u(1); }
	}

	// ---- widgets ----------------------------------------------------------------------------------------

	boolean button(String id, int x, int y, int w, int h, String label, Style st, boolean enabled, Runnable click) {
		boolean hv = enabled && hover(x, y, w, h);
		float k = anim("b:" + id, hv ? 1 : 0, 16);
		int r = u(5);
		int accent = Theme.accent(), accent2 = Theme.accent2();
		int textCol = enabled ? Theme.TEXT : Theme.FAINT;
		switch (st) {
			case PRIMARY -> {
				int a = enabled ? Theme.mix(accent, 0xFFFFFFFF, k * 0.12f) : Theme.mix(accent, Theme.opaque(Theme.PANEL), 0.7f);
				int b = enabled ? Theme.mix(accent2, 0xFFFFFFFF, k * 0.12f) : Theme.mix(accent2, Theme.opaque(Theme.PANEL), 0.7f);
				Draw.roundGradient(g, x, y, w, h, r, a, b);
				if (!enabled) textCol = Theme.DIM;
			}
			case DANGER -> Draw.round(g, x, y, w, h, r, Theme.mix(Theme.opaque(0xB3363B), Theme.opaque(Theme.BAD), k));
			case GHOST -> {
				Draw.round(g, x, y, w, h, r, Theme.mix(Theme.opaque(Theme.CARD), Theme.opaque(Theme.CARD_HOVER), k));
				Draw.outline(g, x, y, w, h, r, Theme.mix(Theme.opaque(Theme.BORDER), accent, k * 0.8f));
			}
			case SUBTLE -> Draw.round(g, x, y, w, h, r, Theme.a(Theme.CARD_HOVER, k * 0.9f));
		}
		String shown = fit(label, w - u(10), 1f);
		textCentered(shown, x + w / 2, y + (h - th(1f)) / 2 + 1, textCol, 1f);
		if (enabled) hit(x, y, w, h, click, null);
		return hv;
	}

	boolean button(String id, int x, int y, int w, int h, String label, Style st, Runnable click) {
		return button(id, x, y, w, h, label, st, true, click);
	}

	/** Pill toggle (36x18 virtual px). */
	void toggle(String id, int x, int y, boolean value, Consumer<Boolean> set) {
		int w = u(36), h = u(18);
		float k = anim("t:" + id, value ? 1 : 0, 18);
		boolean hv = hover(x, y, w, h);
		int off = Theme.opaque(0x3A3055);
		int track = Theme.mix(off, Theme.accent(), k);
		if (hv) track = Theme.mix(track, 0xFFFFFFFF, 0.08f);
		Draw.round(g, x, y, w, h, h / 2, track);
		int knob = h - u(4);
		int kx = x + u(2) + Math.round(k * (w - knob - u(4)));
		Draw.round(g, kx, y + u(2), knob, knob, knob / 2, 0xFFFFFFFF);
		hit(x - u(2), y - u(2), w + u(4), h + u(4), () -> set.accept(!value), null);
	}

	/** Labelled slider. Returns the row height used. {@code onRelease} runs when the drag ends. */
	int slider(String id, int x, int y, int w, String label, String valueText, double v, double min, double max,
			   double step, DoubleConsumer set, Runnable onRelease) {
		int rowH = u(32);
		boolean active = dragFn != null && id.equals(dragId());
		boolean hv = hover(x, y, w, rowH) || active;
		float k = anim("s:" + id, hv ? 1 : 0, 14);
		text(label, x, y, Theme.mix(Theme.opaque(Theme.DIM), Theme.opaque(Theme.TEXT), k), 0.95f);
		int vw = tw(valueText, 0.95f);
		text(valueText, x + w - vw, y, Theme.accent(), 0.95f);
		int ty = y + u(17), th = u(5);
		Draw.round(g, x, ty, w, th, th / 2, Theme.opaque(0x2E2545));
		float t = (float) ((v - min) / (max - min));
		t = Math.max(0, Math.min(1, t));
		int fw = Math.max(th, Math.round(t * w));
		Draw.roundGradient(g, x, ty, fw, th, th / 2, Theme.accent(), Theme.accent2());
		int kn = u(11) + Math.round(k * u(2));
		Draw.round(g, x + Math.round(t * (w - kn) + 0) , ty + th / 2 - kn / 2, kn, kn, kn / 2, 0xFFFFFFFF);
		hit(x, y, w, rowH, () -> {
			dragFn = set;
			dragRelease = onRelease;
			dragX = x;
			dragW = w;
			dragMin = min;
			dragMax = max;
			dragStep = step;
			dragIdValue = id;
			applyDrag(mx);
		}, null);
		return rowH;
	}

	private String dragIdValue = "";

	private String dragId() {
		return dragIdValue;
	}

	/** Button that shows "Label: value"; left click = next, right click = previous. */
	void cycle(String id, int x, int y, int w, int h, String label, String value, Runnable next, Runnable prev) {
		boolean hv = hover(x, y, w, h);
		float k = anim("c:" + id, hv ? 1 : 0, 16);
		int r = u(5);
		Draw.round(g, x, y, w, h, r, Theme.mix(Theme.opaque(Theme.CARD), Theme.opaque(Theme.CARD_HOVER), k));
		Draw.outline(g, x, y, w, h, r, Theme.mix(Theme.opaque(Theme.BORDER), Theme.accent(), k * 0.8f));
		int ty = y + (h - th(0.95f)) / 2 + 1;
		text(label, x + u(8), ty, Theme.DIM, 0.95f);
		String shown = fit(value, w - tw(label, 0.95f) - u(24), 0.95f);
		text(shown, x + w - u(8) - tw(shown, 0.95f), ty, Theme.TEXT, 0.95f);
		hit(x, y, w, h, next, prev);
	}

	/** Single-line text field. */
	void textField(String id, int x, int y, int w, int h, String placeholder, TextField f, int iconId) {
		boolean focused = focus == f;
		boolean hv = hover(x, y, w, h);
		float k = anim("f:" + id, focused ? 1 : (hv ? 0.4f : 0), 14);
		int r = u(5);
		Draw.round(g, x, y, w, h, r, Theme.opaque(0x120D1C));
		Draw.outline(g, x, y, w, h, r, Theme.mix(Theme.opaque(Theme.BORDER), Theme.accent(), k));
		int tx = x + u(8);
		if (iconId >= 0) {
			Draw.icon(g, iconId, tx, y + (h - 8 * Math.max(1, u(1))) / 2, Math.max(1, u(1)), Theme.FAINT | 0xFF000000);
			tx += 8 * Math.max(1, u(1)) + u(6);
		}
		int ty = y + (h - th(1f)) / 2 + 1;
		int avail = x + w - u(8) - tx;
		if (f.text.isEmpty() && !focused) {
			text(fit(placeholder, avail, 1f), tx, ty, Theme.FAINT, 1f);
		} else {
			// scroll the visible window so the cursor stays in view
			String before = f.text.substring(0, Math.min(f.cursor, f.text.length()));
			int start = 0;
			while (start < before.length() && tw(before.substring(start), 1f) > avail - u(4)) start++;
			String visible = f.text.substring(start);
			int end = visible.length();
			while (end > 0 && tw(visible.substring(0, end), 1f) > avail) end--;
			visible = visible.substring(0, end);
			text(visible, tx, ty, Theme.TEXT, 1f);
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = tx + tw(before.substring(start), 1f);
				g.fill(cx, ty - 1, cx + Math.max(1, u(1)), ty + th(1f), 0xFFFFFFFF);
			}
		}
		hit(x, y, w, h, () -> {
			focus = f;
			f.cursor = f.text.length();
		}, null);
	}

	// ---- scroll areas -----------------------------------------------------------------------------------

	/** Starts a clipped scroll region; returns the pixel offset to subtract from content y. */
	int scrollBegin(String id, int x, int y, int w, int h) {
		float[] st = scroll.computeIfAbsent(id, k -> new float[4]);
		st[3] = h;
		float max = Math.max(0, st[2] - h);
		st[1] = Math.max(0, Math.min(max, st[1]));
		var cfg = ConfigManager.get();
		st[0] = cfg.animations ? st[0] + (st[1] - st[0]) * (1f - (float) Math.exp(-dt * 22f)) : st[1];
		if (Math.abs(st[0] - st[1]) < 0.4f) st[0] = st[1];
		g.enableScissor(x, y, x + w, y + h);
		clip = new int[] {x, y, x + w, y + h};
		nextAreas.put(id, new int[] {x, y, w, h});
		return Math.round(st[0]);
	}

	void scrollEnd(String id, int contentHeight) {
		g.disableScissor();
		clip = null;
		float[] st = scroll.get(id);
		if (st == null) return;
		st[2] = contentHeight;
		int[] a = nextAreas.get(id);
		float view = st[3];
		if (a != null && st[2] > view + 1) {
			int trackX = a[0] + a[2] - u(4), trackY = a[1] + u(2), trackH = a[3] - u(4);
			int thumbH = Math.max(u(18), Math.round(trackH * (view / st[2])));
			float max = st[2] - view;
			int thumbY = trackY + Math.round((trackH - thumbH) * (max <= 0 ? 0 : st[0] / max));
			Draw.round(g, trackX, trackY, u(3), trackH, u(2), Theme.a(0xFFFFFF, 0.04f));
			Draw.round(g, trackX, thumbY, u(3), thumbH, u(2), Theme.a(Theme.accent(), 0.8f));
		}
	}

	void scrollReset(String id) {
		float[] st = scroll.get(id);
		if (st != null) { st[0] = 0; st[1] = 0; }
	}

	boolean wheel(double px, double py, double dy) {
		if (modalOpen) return false;
		for (var e : areas.entrySet()) {
			int[] a = e.getValue();
			if (px >= a[0] && py >= a[1] && px < a[0] + a[2] && py < a[1] + a[3]) {
				float[] st = scroll.get(e.getKey());
				if (st == null) continue;
				st[1] -= (float) dy * u(38);
				float max = Math.max(0, st[2] - st[3]);
				st[1] = Math.max(0, Math.min(max, st[1]));
				return true;
			}
		}
		return false;
	}
}
