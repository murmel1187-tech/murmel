package com.murmelsvisuals.gui;

import com.murmelsvisuals.MurmelsVisualsClient;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.gui.Ui.Style;
import com.murmelsvisuals.pack.PackCategory;
import com.murmelsvisuals.pack.PackController;
import com.murmelsvisuals.pack.PackEntry;
import com.murmelsvisuals.pack.PackScan;
import com.murmelsvisuals.pack.PackSorter;
import com.murmelsvisuals.pack.TexCache;
import com.murmelsvisuals.util.Compat;
import com.murmelsvisuals.util.HookStatus;
import com.murmelsvisuals.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The MurmelsVisuals main screen: sidebar (12 categories + Packs + Settings), search, pages and
 * dialogs. Rendering is immediate-mode through {@link Ui}; see that class for the widget model.
 */
public final class MurmelsScreen extends Screen {
	private record SearchEntry(String title, String desc, String keywords, String target) {}

	private static final int[] ACCENT_PRESETS = {0xA855F7, 0xEC4899, 0x8B5CF6, 0x3B82F6, 0x06B6D4, 0x22C55E, 0xF59E0B, 0xEF4444};
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private static final List<SearchEntry> INDEX = buildIndex();

	private final Screen parent;
	private final Ui ui;
	private final TexCache tex = new TexCache();
	private PackController packs;

	private final TextField search = new TextField();
	private final TextField importField = new TextField();
	private final TextField profileField = new TextField();
	private final TextField hexField = new TextField();

	private String page;
	private String selectedPack;
	private boolean importOpen;
	private boolean resetConfirm;
	private float openAnim;
	private float pageFade = 1f;
	private long lastNs = System.nanoTime();
	private Float pendingUiScale;
	private String lastSearch = "";
	private final Map<PackCategory, Integer> showSel = new EnumMap<>(PackCategory.class);

	public MurmelsScreen(Screen parent) {
		super(Component.literal("MurmelsVisuals"));
		this.parent = parent;
		this.ui = new Ui(Minecraft.getInstance().font);
		this.page = ConfigManager.get().lastPage;
		if (!validPage(page)) page = "cat:BLOCKS";
	}

	// ---- lifecycle --------------------------------------------------------------------------------------

	@Override
	protected void init() {
		if (packs == null) packs = new PackController();
		else packs.refresh(false);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void onClose() {
		ConfigManager.saveIfDirty();
		Compat.setScreen(parent);
	}

	@Override
	public void removed() {
		ConfigManager.saveIfDirty();
		tex.clear();
		ItemShowcase.clear();
		if (packs != null) packs.close();
	}

	private static boolean validPage(String p) {
		if (p.equals("packs") || p.equals("settings")) return true;
		if (!p.startsWith("cat:")) return false;
		try { PackCategory.valueOf(p.substring(4)); return true; } catch (IllegalArgumentException e) { return false; }
	}

	private ModConfig cfg() {
		return ConfigManager.get();
	}

	private int u(float v) {
		return ui.u(v);
	}

	private void go(String target) {
		if (target.equals(page)) return;
		page = target;
		pageFade = 0f;
		cfg().lastPage = target;
		ConfigManager.markDirty();
	}

	// ---- input ------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ui.click(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (ui.isDragging()) { ui.drag(event.x()); return true; }
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (ui.isDragging()) { ui.release(); return true; }
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (ui.wheel(x, y, scrollY)) return true;
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		int mods = event.modifiers();
		if (key == Keys.ESCAPE) {
			if (ui.focus != null) { ui.focus = null; return true; }
			if (importOpen || resetConfirm) { importOpen = false; resetConfirm = false; return true; }
			onClose();
			return true;
		}
		if (ui.focus != null) {
			if (key == Keys.ENTER || key == Keys.KP_ENTER) {
				if (ui.focus == importField) doImport();
				else if (ui.focus == profileField) { packs.saveProfile(profileField.text); profileField.clear(); ui.focus = null; }
				else ui.focus = null;
				return true;
			}
			ui.focus.key(key, mods, Compat.clipboard());
			afterTextEdit();
			return true;
		}
		boolean ctrl = Keys.ctrl(mods);
		if (ctrl && key == Keys.F) { ui.focus = search; return true; }
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (ui.focus != null) {
			ui.focus.type(event.codepoint());
			afterTextEdit();
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		if (packs == null) return;
		int ok = 0;
		for (Path p : files) {
			String before = packs.message();
			packs.importPack(p);
			if (packs.message().startsWith("Imported")) ok++;
			else if (files.size() > 1) packs.setMessage(before + " | " + packs.message());
		}
		if (ok > 0) go("packs");
	}

	private void afterTextEdit() {
		if (ui.focus == hexField) {
			int rgb = Theme.parseHex(hexField.text);
			if (rgb >= 0) { cfg().accent = rgb; ConfigManager.markDirty(); }
		}
		if (!search.text.equals(lastSearch)) {
			lastSearch = search.text;
			ui.scrollReset("search");
			ui.scrollReset("packlist");
		}
	}

	private void doImport() {
		String raw = importField.text.trim();
		if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
		if (raw.isEmpty()) { packs.setMessage("Enter or paste a path first."); return; }
		try {
			packs.importPack(Path.of(raw));
		} catch (InvalidPathException e) {
			packs.setMessage("That is not a valid path.");
		}
		importOpen = false;
		importField.clear();
		ui.focus = null;
		go("packs");
	}

	// ---- frame ------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = (now - lastNs) / 1_000_000_000f;
		lastNs = now;
		ModConfig c = cfg();

		float fit = Math.min((width - 24) / 640f, (height - 24) / 380f);
		float scale = Math.max(0.5f, Math.min(pendingUiScale != null && ui.isDragging() ? c.uiScale : c.uiScale, fit));
		ui.modalOpen = importOpen || resetConfirm;
		ui.begin(g, mouseX, mouseY, dt, scale);
		tex.tick();
		packs.refresh(false);

		openAnim = c.animations ? Math.min(1f, openAnim + dt * 4.5f * c.animationSpeed) : 1f;
		pageFade = c.animations ? Math.min(1f, pageFade + dt * 6f * c.animationSpeed) : 1f;
		float ease = 1f - (1f - openAnim) * (1f - openAnim) * (1f - openAnim);

		drawBackdrop(g, c, ease);

		int pw = Math.min(width - u(24), u(900));
		int ph = Math.min(height - u(24), u(540));
		int px = (width - pw) / 2;
		int py = (height - ph) / 2 + Math.round((1f - ease) * u(16));
		int r = u(10);

		Draw.shadow(g, px, py, pw, ph, r, u(14), ease);
		Draw.round(g, px, py, pw, ph, r, Theme.a(Theme.PANEL, c.panelOpacity));
		Draw.outline(g, px, py, pw, ph, r, Theme.opaque(Theme.BORDER));
		Draw.gradientH(g, px + r, py, pw - 2 * r, Math.max(1, u(2)), Theme.accent(), Theme.accent2());
		ui.block(px, py, pw, ph);

		int headerH = u(46);
		drawHeader(g, px, py, pw, headerH);

		int sx = px + u(10), sy = py + headerH + u(6), sw = u(170), sh = py + ph - sy - u(10);
		drawSidebar(g, sx, sy, sw, sh);

		int cx = sx + sw + u(10), cw = px + pw - u(10) - cx;
		Draw.round(g, cx, sy, cw, sh, u(8), Theme.a(Theme.PANEL2, Math.min(1f, c.panelOpacity + 0.05f)));
		int slide = Math.round((1f - pageFade) * u(10));
		String q = search.text.trim();
		if (!q.isEmpty() && !page.equals("packs")) searchPage(g, cx + slide, sy, cw - slide, sh, q);
		else if (page.startsWith("cat:")) categoryPage(PackCategory.valueOf(page.substring(4)), cx + slide, sy, cw - slide, sh);
		else if (page.equals("packs")) packsPage(cx + slide, sy, cw - slide, sh);
		else settingsPage(cx + slide, sy, cw - slide, sh);
		if (pageFade < 1f) Draw.round(g, cx, sy, cw, sh, u(8), Theme.a(Theme.PANEL2, (1f - pageFade) * 0.85f));

		if (openAnim < 1f) Draw.round(g, px, py, pw, ph, r, Theme.a(Theme.PANEL, (1f - ease) * 0.9f));

		if (importOpen) drawImportDialog(g);
		if (resetConfirm) drawResetDialog(g);
		ui.drawTooltip(width, height);
	}

	private void drawBackdrop(GuiGraphicsExtractor g, ModConfig c, float ease) {
		switch (c.background) {
			case DIM -> g.fill(0, 0, width, height, Theme.a(0x000000, 0.55f * ease));
			case GRADIENT -> g.fillGradient(0, 0, width, height,
					Theme.a(Theme.mix(Theme.opaque(Theme.BG), Theme.accent(), 0.22f), 0.90f * ease),
					Theme.a(Theme.mix(Theme.opaque(Theme.BG), Theme.accent2(), 0.10f), 0.96f * ease));
			case SOLID -> g.fill(0, 0, width, height, Theme.a(Theme.BG, ease));
			case CLEAR -> { /* leave the game visible */ }
		}
	}

	// ---- header -----------------------------------------------------------------------------------------

	private void drawHeader(GuiGraphicsExtractor g, int px, int py, int pw, int headerH) {
		int logo = u(26), lx = px + u(14), ly = py + (headerH - logo) / 2 + u(1);
		Draw.roundGradient(g, lx, ly, logo, logo, u(7), Theme.accent(), Theme.accent2());
		ui.textCentered("M", lx + logo / 2, ly + (logo - ui.th(1.3f)) / 2 + 1, 0xFFFFFFFF, 1.3f);
		int tx = lx + logo + u(10);
		ui.text("MURMELS", tx, ly + u(1), Theme.accent(), 1.15f);
		ui.text("VISUALS", tx + ui.tw("MURMELS ", 1.15f), ly + u(1), Theme.TEXT, 1.15f);
		ui.text("client visuals hub", tx, ly + u(15), Theme.FAINT, 0.8f);

		int close = u(26), cx = px + pw - u(14) - close;
		ui.button("close", cx, ly, close, close, "", Style.SUBTLE, this::onClose);
		Draw.icon(g, Draw.ICON_CLOSE, cx + (close - 8 * Math.max(1, u(1))) / 2, ly + (close - 8 * Math.max(1, u(1))) / 2,
				Math.max(1, u(1)), Theme.DIM | 0xFF000000);
		int sw = Math.min(u(250), pw / 3);
		ui.textField("search", cx - u(10) - sw, ly, sw, close, "Search features, packs...  (Ctrl+F)", search, Draw.ICON_SEARCH);
		g.fill(px + u(10), py + headerH, px + pw - u(10), py + headerH + 1, Theme.opaque(Theme.BORDER));
	}

	// ---- sidebar ----------------------------------------------------------------------------------------

	private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Draw.round(g, x, y, w, h, u(8), Theme.a(Theme.PANEL2, Math.min(1f, cfg().panelOpacity + 0.05f)));
		int off = ui.scrollBegin("side", x, y, w, h);
		int cy = y - off + u(8), ix = x + u(8), iw = w - u(16);
		ui.text("CATEGORIES", ix + u(4), cy, Theme.FAINT, 0.75f);
		cy += ui.th(0.75f) + u(6);
		for (PackCategory cat : PackCategory.values()) {
			boolean dot = (cat == PackCategory.HUD && cfg().lowFireEnabled) || (cat == PackCategory.WEAPONS && cfg().sideShieldEnabled)
					|| (cat == PackCategory.ITEMS && cfg().glintEnabled);
			sideItem(g, "cat:" + cat.name(), cat.title, cat.ordinal(), ix, cy, iw, dot ? "on" : null);
			cy += u(28);
		}
		cy += u(6);
		g.fill(ix, cy, ix + iw, cy + 1, Theme.opaque(Theme.BORDER));
		cy += u(10);
		ui.text("TOOLS", ix + u(4), cy, Theme.FAINT, 0.75f);
		cy += ui.th(0.75f) + u(6);
		String badge = packs.hasPending() ? "!" : null;
		sideItem(g, "packs", "Resource Packs", Draw.ICON_PACKS, ix, cy, iw, badge);
		cy += u(28);
		sideItem(g, "settings", "Settings", Draw.ICON_SETTINGS, ix, cy, iw, null);
		cy += u(28) + u(8);
		ui.scrollEnd("side", cy - (y - off));
	}

	private void sideItem(GuiGraphicsExtractor g, String target, String label, int icon, int x, int y, int w, String badge) {
		int h = u(26);
		boolean sel = target.equals(page) && search.text.isBlank() || (target.equals(page) && page.equals("packs"));
		boolean hv = ui.hover(x, y, w, h);
		float k = ui.anim("side:" + target, sel ? 1 : (hv ? 0.5f : 0), 16);
		if (k > 0.01f) {
			Draw.round(g, x, y, w, h, u(6), Theme.mix(Theme.a(Theme.CARD, 0f), Theme.opaque(sel ? Theme.CARD_HOVER : Theme.CARD), k));
		}
		if (sel) Draw.roundGradient(g, x, y + u(5), Math.max(2, u(3)), h - u(10), u(2), Theme.accent(), Theme.accent2());
		int px = Math.max(1, u(1));
		int ix = x + u(12) + Math.round(k * u(2));
		int iconCol = Theme.mix(Theme.opaque(Theme.DIM), Theme.accent(), sel ? 1 : k * 0.6f);
		Draw.icon(g, icon, ix, y + (h - 8 * px) / 2, px, iconCol);
		ui.text(label, ix + 8 * px + u(8), y + (h - ui.th(1f)) / 2 + 1,
				Theme.mix(Theme.opaque(Theme.DIM), Theme.opaque(Theme.TEXT), sel ? 1 : k), 1f);
		if (badge != null) {
			int d = u(7);
			Draw.round(g, x + w - u(14), y + (h - d) / 2, d, d, d / 2, Theme.opaque("on".equals(badge) ? Theme.GOOD : Theme.WARN));
		}
		ui.hit(x, y, w, h, () -> { search.clear(); go(target); }, null);
	}

	// ---- shared bits ------------------------------------------------------------------------------------

	private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Draw.round(g, x, y, w, h, u(8), Theme.opaque(Theme.CARD));
		Draw.outline(g, x, y, w, h, u(8), Theme.opaque(Theme.BORDER));
	}

	private void sectionTitle(String text, int x, int y) {
		ui.text(text, x, y, Theme.TEXT, 1.1f);
	}

	private void packIcon(GuiGraphicsExtractor g, PackEntry p, int x, int y, int size) {
		if (p.path != null) {
			TexCache.Tex t = tex.get(p.path, "pack.png");
			if (t != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, 0f, size, size, t.width(), t.height(), t.width(), t.height());
				Draw.outline(g, x, y, size, size, 0, Theme.opaque(Theme.BORDER));
				return;
			}
		}
		int hue = Math.abs(p.id.hashCode()) % 360;
		Draw.round(g, x, y, size, size, Math.max(2, size / 6), Theme.opaque(Theme.hsv(hue / 360f, 0.45f, 0.50f)));
		String letter = p.title.isEmpty() ? "?" : p.title.substring(0, 1).toUpperCase(Locale.ROOT);
		float rel = Math.max(0.8f, size / (float) u(1) / 18f);
		ui.textCentered(letter, x + size / 2, y + (size - ui.th(rel)) / 2 + 1, 0xFFFFFFFF, rel);
	}

	private static String formatSize(long bytes) {
		if (bytes <= 0) return "-";
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
		return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
	}

	private void chip(GuiGraphicsExtractor g, String text, int x, int y, int color, float rel) {
		int w = ui.tw(text, rel) + u(12), h = ui.th(rel) + u(6);
		Draw.round(g, x, y, w, h, h / 2, Theme.a(color, 0.22f));
		Draw.outline(g, x, y, w, h, h / 2, Theme.a(color, 0.55f));
		ui.text(text, x + u(6), y + u(3) + 1, Theme.mix(Theme.opaque(color), 0xFFFFFFFF, 0.35f), rel);
	}

	private int chipWidth(String text, float rel) {
		return ui.tw(text, rel) + u(12);
	}

	// ---- category page ----------------------------------------------------------------------------------

	private void categoryPage(PackCategory cat, int x, int y, int w, int h) {
		GuiGraphicsExtractor g = ui.g;
		int pad = u(14);
		String id = "cat:" + cat.name();
		int off = ui.scrollBegin(id, x, y, w, h);
		int cx = x + pad, cw = w - pad * 2 - u(6);
		int cy = y - off + pad;

		ui.text(cat.title, cx, cy, Theme.TEXT, 1.7f);
		cy += ui.th(1.7f) + u(4);
		for (String line : ui.wrap(cat.description, cw, 1f, 3)) {
			ui.text(line, cx, cy, Theme.DIM, 1f);
			cy += ui.th(1f) + u(2);
		}
		cy += u(10);
		if (cat == PackCategory.HUD) cy = lowFireCard(g, cx, cy, cw) + u(12);
		if (cat == PackCategory.WEAPONS) cy = shieldCard(g, cx, cy, cw) + u(12);
		if (cat == PackCategory.ITEMS) cy = glintCard(g, cx, cy, cw) + u(12);
		if (ItemShowcase.has(cat)) cy = showcase(g, cat, cx, cy, cw) + u(12);
		cy = packSection(g, cat, cx, cy, cw) + u(8);
		ui.scrollEnd(id, cy - (y - off) + pad);
	}

	private int hookLine(GuiGraphicsExtractor g, HookStatus hook, int x, int y, String idleText) {
		HookStatus.State st = hook.state();
		int col = st == HookStatus.State.ACTIVE ? Theme.GOOD : (st == HookStatus.State.NOT_TRIGGERING ? Theme.BAD : Theme.FAINT);
		String msg = switch (st) {
			case ACTIVE -> "Hook verified: this feature has modified rendering in your game.";
			case NOT_TRIGGERING -> "Hook is NOT triggering although it should. The game version may have changed - see latest.log.";
			case IDLE -> idleText;
		};
		int d = u(7);
		Draw.round(g, x, y + (ui.th(0.9f) - d) / 2 + 1, d, d, d / 2, Theme.opaque(col));
		ui.text(msg, x + d + u(6), y, Theme.mix(Theme.opaque(col), Theme.opaque(Theme.TEXT), 0.3f), 0.9f);
		return y + ui.th(0.9f) + u(4);
	}

	private int lowFireCard(GuiGraphicsExtractor g, int x, int y, int w) {
		ModConfig c = cfg();
		int p = u(14);
		String desc = "Shrinks the fire overlay drawn over your screen while you are burning. Height sets how far up the screen "
				+ "the flames reach, width makes them narrower. Independent of every other feature.";
		List<String> lines = ui.wrap(desc, w - p * 2, 0.95f, 0);
		int lh = ui.th(0.95f) + u(2);
		int h = p + ui.th(1.2f) + u(8) + lines.size() * lh + u(8) + 2 * u(34) + u(4) + ui.th(0.9f) + p;
		card(g, x, y, w, h);
		int cy = y + p;
		ui.text("Low Fire", x + p, cy, Theme.TEXT, 1.2f);
		ui.toggle("lowfire", x + w - p - u(36), cy - u(2), c.lowFireEnabled, v -> { c.lowFireEnabled = v; ConfigManager.markDirty(); });
		cy += ui.th(1.2f) + u(8);
		for (String l : lines) { ui.text(l, x + p, cy, Theme.DIM, 0.95f); cy += lh; }
		cy += u(8);
		cy += ui.slider("lf-h", x + p, cy, w - p * 2, "Fire height", Math.round(c.lowFireHeight * 100) + "%", c.lowFireHeight,
				0.10, 1.00, 0.01, v -> { c.lowFireHeight = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += ui.slider("lf-w", x + p, cy, w - p * 2, "Fire width", Math.round(c.lowFireWidth * 100) + "%", c.lowFireWidth,
				0.30, 1.00, 0.01, v -> { c.lowFireWidth = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += u(4);
		hookLine(g, HookStatus.LOW_FIRE, x + p, cy, "Not seen yet - set yourself on fire (lava, fire, flint and steel) with the toggle on.");
		return y + h;
	}

	private int shieldCard(GuiGraphicsExtractor g, int x, int y, int w) {
		ModConfig c = cfg();
		int p = u(14);
		String desc = "Draws your first-person shield off to the side of the screen instead of in front of the view. The hand that holds "
				+ "it decides which side it moves to. Independent of Low Fire.";
		List<String> lines = ui.wrap(desc, w - p * 2, 0.95f, 0);
		int lh = ui.th(0.95f) + u(2);
		int h = p + ui.th(1.2f) + u(8) + lines.size() * lh + u(8) + 4 * u(34) + u(30) + u(4) + ui.th(0.9f) + p;
		card(g, x, y, w, h);
		int cy = y + p;
		ui.text("Side Shield", x + p, cy, Theme.TEXT, 1.2f);
		ui.toggle("sideshield", x + w - p - u(36), cy - u(2), c.sideShieldEnabled, v -> { c.sideShieldEnabled = v; ConfigManager.markDirty(); });
		cy += ui.th(1.2f) + u(8);
		for (String l : lines) { ui.text(l, x + p, cy, Theme.DIM, 0.95f); cy += lh; }
		cy += u(8);
		cy += ui.slider("sh-side", x + p, cy, w - p * 2, "Sideways shift", Math.round(c.shieldSide * 100) + "%", c.shieldSide,
				0, 1, 0.01, v -> { c.shieldSide = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += ui.slider("sh-down", x + p, cy, w - p * 2, "Lower", Math.round(c.shieldDown * 100) + "%", c.shieldDown,
				0, 1, 0.01, v -> { c.shieldDown = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += ui.slider("sh-scale", x + p, cy, w - p * 2, "Size", Math.round(c.shieldScale * 100) + "%", c.shieldScale,
				0.40, 1.20, 0.01, v -> { c.shieldScale = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += ui.slider("sh-tilt", x + p, cy, w - p * 2, "Outward tilt", Math.round(c.shieldTilt) + " deg", c.shieldTilt,
				-45, 45, 1, v -> { c.shieldTilt = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		ui.text("Keep centered while blocking", x + p, cy + u(4), Theme.DIM, 0.95f);
		ui.toggle("sh-block", x + w - p - u(36), cy, c.shieldCenterWhileBlocking, v -> { c.shieldCenterWhileBlocking = v; ConfigManager.markDirty(); });
		cy += u(30) + u(4);
		hookLine(g, HookStatus.SIDE_SHIELD, x + p, cy, "Not seen yet - hold a shield in first person with the toggle on.");
		return y + h;
	}

	private int glintCard(GuiGraphicsExtractor g, int x, int y, int w) {
		ModConfig c = cfg();
		int p = u(14);
		String desc = "Changes how strong and how fast the enchantment shimmer is on every enchanted item and armor piece. "
				+ "It drives the game's own Glint Strength and Glint Speed options (0-100 %), so it cannot go above vanilla's maximum. "
				+ "Your original values are restored when you turn it off.";
		List<String> lines = ui.wrap(desc, w - p * 2, 0.95f, 0);
		int lh = ui.th(0.95f) + u(2);
		int h = p + ui.th(1.2f) + u(8) + lines.size() * lh + u(8) + 2 * u(34) + u(4) + ui.th(0.9f) + p;
		card(g, x, y, w, h);
		int cy = y + p;
		ui.text("Enchantment Glint", x + p, cy, Theme.TEXT, 1.2f);
		ui.toggle("glint", x + w - p - u(36), cy - u(2), c.glintEnabled, v -> { c.glintEnabled = v; ConfigManager.markDirty(); });
		cy += ui.th(1.2f) + u(8);
		for (String l : lines) { ui.text(l, x + p, cy, Theme.DIM, 0.95f); cy += lh; }
		cy += u(8);
		cy += ui.slider("gl-str", x + p, cy, w - p * 2, "Glint strength", Math.round(c.glintStrength * 100) + "%", c.glintStrength,
				0, 1, 0.01, v -> { c.glintStrength = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += ui.slider("gl-spd", x + p, cy, w - p * 2, "Glint speed", Math.round(c.glintSpeed * 100) + "%", c.glintSpeed,
				0, 1, 0.01, v -> { c.glintSpeed = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);
		cy += u(4);
		boolean avail = com.murmelsvisuals.util.GlintFeature.available();
		int col = !avail ? Theme.BAD : (c.glintEnabled ? Theme.GOOD : Theme.FAINT);
		String msg = !avail ? "Glint options not found in this game version - feature unavailable."
				: (c.glintEnabled ? "Active: overriding the game's glint options." : "Off: your own game settings are used.");
		int d = u(7);
		Draw.round(g, x + p, cy + (ui.th(0.9f) - d) / 2 + 1, d, d, d / 2, Theme.opaque(col));
		ui.text(msg, x + p + d + u(6), cy, Theme.mix(Theme.opaque(col), Theme.opaque(Theme.TEXT), 0.3f), 0.9f);
		return y + h;
	}

	/** Item grid + large preview. Block items are real 3D models; the preview adds bobbing and mouse parallax. */
	private int showcase(GuiGraphicsExtractor g, PackCategory cat, int x, int y, int w) {
		ModConfig c = cfg();
		List<ItemStack> stacks = ItemShowcase.stacks(cat, c.showcaseGlint);
		if (stacks.isEmpty()) return y;
		int sel = Math.max(0, Math.min(showSel.getOrDefault(cat, 0), stacks.size() - 1));
		int p = u(14), box = u(150), cell = u(40), cg = u(6);
		int gridW = w - p * 2 - box - u(14);
		int cols = Math.max(2, (gridW + cg) / (cell + cg));
		int rows = (stacks.size() + cols - 1) / cols;
		int h = p + ui.th(1.2f) + u(10) + Math.max(box + ui.th(1f) + u(8), rows * (cell + cg) + u(8)) + p;
		card(g, x, y, w, h);
		int cy = y + p;
		ui.text("3D item preview", x + p, cy, Theme.TEXT, 1.2f);
		ui.text("Glint", x + w - p - u(36) - u(8) - ui.tw("Glint", 0.95f), cy + u(1), Theme.DIM, 0.95f);
		ui.toggle("show-glint", x + w - p - u(36), cy - u(2), c.showcaseGlint, v -> { c.showcaseGlint = v; ConfigManager.markDirty(); });
		cy += ui.th(1.2f) + u(10);

		// large preview box
		int bx = x + p;
		Draw.round(g, bx, cy, box, box, u(10), Theme.opaque(0x120D1C));
		Draw.round(g, bx + u(10), cy + u(10), box - u(20), box - u(20), u(10), Theme.a(Theme.accent(), 0.10f));
		Draw.round(g, bx + u(30), cy + u(30), box - u(60), box - u(60), u(10), Theme.a(Theme.accent2(), 0.08f));
		Draw.outline(g, bx, cy, box, box, u(10), Theme.opaque(Theme.BORDER));
		double t = System.nanoTime() / 1_000_000_000.0;
		float bob = c.animations ? (float) Math.sin(t * 2.0) * u(3) : 0f;
		float ccx = bx + box / 2f, ccy = cy + box / 2f;
		float dx = 0, dy = 0;
		if (ui.hover(bx, cy, box, box) && c.animations) {
			dx = Math.max(-1f, Math.min(1f, (ui.mx - ccx) / (box / 2f))) * u(8);
			dy = Math.max(-1f, Math.min(1f, (ui.my - ccy) / (box / 2f))) * u(6);
		}
		float shadowW = u(50) * (1f - bob / (u(3) * 14f));
		Draw.round(g, Math.round(ccx - shadowW / 2f), cy + box - u(26), Math.round(shadowW), u(7), u(4), Theme.a(0x000000, 0.35f));
		float k = box * 0.56f / 16f;
		g.pose().pushMatrix();
		g.pose().translate(ccx + dx, ccy + dy + bob - u(4));
		g.pose().scale(k, k);
		g.item(stacks.get(sel), -8, -8);
		g.pose().popMatrix();
		String name = ui.fit(stacks.get(sel).getHoverName().getString(), box, 0.95f);
		ui.textCentered(name, bx + box / 2, cy + box + u(6), Theme.TEXT, 0.95f);

		// grid
		int gx = bx + box + u(14);
		for (int i = 0; i < stacks.size(); i++) {
			int cxp = gx + (i % cols) * (cell + cg), cyp = cy + (i / cols) * (cell + cg);
			boolean hv = ui.hover(cxp, cyp, cell, cell), on = i == sel;
			float a = ui.anim("sc:" + cat + i, on ? 1f : (hv ? 0.5f : 0f), 16);
			Draw.round(g, cxp, cyp, cell, cell, u(7), Theme.mix(Theme.opaque(Theme.PANEL2), Theme.opaque(Theme.CARD_HOVER), a));
			if (on) Draw.outline(g, cxp, cyp, cell, cell, u(7), Theme.accent());
			float k2 = cell * 0.62f / 16f * (1f + a * 0.12f);
			g.pose().pushMatrix();
			g.pose().translate(cxp + cell / 2f, cyp + cell / 2f);
			g.pose().scale(k2, k2);
			g.item(stacks.get(i), -8, -8);
			g.pose().popMatrix();
			final int idx = i;
			ui.hit(cxp, cyp, cell, cell, () -> showSel.put(cat, idx), null);
			if (hv) ui.tip(stacks.get(i).getHoverName().getString());
		}
		return y + h;
	}

	private int packSection(GuiGraphicsExtractor g, PackCategory cat, int x, int y, int w) {
		List<PackEntry> mine = new ArrayList<>();
		int scanning = 0;
		for (PackEntry p : packs.all()) {
			PackScan sc = p.scan;
			if (p.path != null && sc == null) scanning++;
			if (sc != null && sc.valid() && sc.affects(cat)) mine.add(p);
		}
		mine.sort((a, b) -> {
			int ra = packs.priorityRank(a.id), rb = packs.priorityRank(b.id);
			if (ra >= 0 && rb >= 0) return Integer.compare(ra, rb);
			if (ra >= 0) return -1;
			if (rb >= 0) return 1;
			return a.title.compareToIgnoreCase(b.title);
		});
		sectionTitle("Installed packs that change " + cat.title, x, y);
		y += ui.th(1.1f) + u(4);
		ui.text("Highest priority wins. Click a pack to manage it.", x, y, Theme.FAINT, 0.9f);
		y += ui.th(0.9f) + u(8);
		if (scanning > 0) {
			ui.text("Scanning " + scanning + " pack(s)...", x, y, Theme.WARN, 0.95f);
			y += ui.th(0.95f) + u(6);
		}
		if (mine.isEmpty() && scanning == 0) {
			card(g, x, y, w, u(54));
			ui.text("No installed resource pack changes " + cat.title + ".", x + u(14), y + u(12), Theme.DIM, 1f);
			ui.text("Import packs in the Resource Packs tab; they show up here automatically.", x + u(14), y + u(12) + ui.th(1f) + u(4), Theme.FAINT, 0.9f);
			return y + u(54);
		}
		boolean activeTaken = false;
		for (PackEntry p : mine) {
			int rh = u(44);
			boolean on = packs.isEnabled(p.id);
			boolean hv = ui.hover(x, y, w, rh);
			float k = ui.anim("catrow:" + cat + p.id, hv ? 1 : 0, 16);
			Draw.round(g, x, y, w, rh, u(7), Theme.mix(Theme.opaque(Theme.CARD), Theme.opaque(Theme.CARD_HOVER), k));
			Draw.outline(g, x, y, w, rh, u(7), Theme.mix(Theme.opaque(Theme.BORDER), Theme.accent(), k * 0.7f));
			int is = u(30);
			packIcon(g, p, x + u(8), y + (rh - is) / 2, is);
			int tx = x + u(8) + is + u(10);
			ui.text(ui.fit(p.title, w - (tx - x) - u(110), 1f), tx, y + u(8), Theme.TEXT, 1f);
			ui.text(p.scan.count(cat) + " files replaced  -  " + p.sourceLabel(), tx, y + u(8) + ui.th(1f) + u(3), Theme.DIM, 0.85f);
			String tag; int col;
			if (!on) { tag = "DISABLED"; col = Theme.FAINT; }
			else if (!activeTaken) { tag = "ACTIVE"; col = Theme.GOOD; activeTaken = true; }
			else { tag = "OVERRIDDEN"; col = Theme.WARN; }
			int cwid = chipWidth(tag, 0.8f);
			chip(g, tag, x + w - cwid - u(10), y + (rh - ui.th(0.8f) - u(6)) / 2, col, 0.8f);
			ui.hit(x, y, w, rh, () -> { selectedPack = p.id; search.clear(); go("packs"); }, null);
			y += rh + u(5);
		}
		return y;
	}

	// ---- packs page -------------------------------------------------------------------------------------

	private void packsPage(int x, int y, int w, int h) {
		GuiGraphicsExtractor g = ui.g;
		ModConfig c = cfg();
		int pad = u(12), gap = u(8);
		int ix = x + pad, iw = w - pad * 2;
		int ty = y + pad, bh = u(26);

		int total = iw - 4 * gap;
		int sortW = Math.round(total * 0.27f), filterW = Math.round(total * 0.20f);
		int rest = (total - sortW - filterW) / 3;
		int bx = ix;
		ui.cycle("sort", bx, ty, sortW, bh, "Sort", c.packSort.label,
				() -> { c.packSort = ModConfig.PackSort.values()[(c.packSort.ordinal() + 1) % ModConfig.PackSort.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.PackSort.values().length; c.packSort = ModConfig.PackSort.values()[(c.packSort.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		bx += sortW + gap;
		ui.cycle("filter", bx, ty, filterW, bh, "Show", c.packFilter.label,
				() -> { c.packFilter = ModConfig.PackFilter.values()[(c.packFilter.ordinal() + 1) % ModConfig.PackFilter.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.PackFilter.values().length; c.packFilter = ModConfig.PackFilter.values()[(c.packFilter.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		bx += filterW + gap;
		ui.button("refresh", bx, ty, rest, bh, "Refresh", Style.GHOST, () -> { packs.refresh(true); packs.setMessage("Pack list refreshed."); });
		bx += rest + gap;
		ui.button("folder", bx, ty, rest, bh, "Open folder", Style.GHOST, () -> Compat.openFolder(packs.packDir()));
		bx += rest + gap;
		ui.button("import", bx, ty, rest, bh, "Import...", Style.PRIMARY, () -> { importOpen = true; ui.focus = importField; });

		// status / apply bar
		int sy = ty + bh + gap, barH = u(30);
		boolean pending = packs.hasPending();
		Draw.round(g, ix, sy, iw, barH, u(7), Theme.opaque(pending ? Theme.mix(Theme.opaque(Theme.CARD), Theme.accent(), 0.18f) : Theme.CARD));
		Draw.outline(g, ix, sy, iw, barH, u(7), pending ? Theme.accent() : Theme.opaque(Theme.BORDER));
		int enabledCount = packs.staged().size();
		String status;
		int statusCol = Theme.DIM;
		if (packs.isReloading()) { status = "Reloading resources..."; statusCol = Theme.WARN; }
		else if (pending) { status = "Unapplied changes - press Apply to reload resources."; statusCol = Theme.TEXT; }
		else if (!packs.message().isEmpty()) { status = packs.message(); statusCol = Theme.TEXT; }
		else status = packs.all().size() + " packs available, " + enabledCount + " enabled";
		int btnW = u(78);
		int textMax = iw - u(20) - (pending ? btnW * 2 + gap * 2 : 0);
		ui.text(ui.fit(status, textMax, 0.95f), ix + u(10), sy + (barH - ui.th(0.95f)) / 2 + 1, statusCol, 0.95f);
		if (pending) {
			int by = sy + (barH - u(22)) / 2;
			ui.button("revert", ix + iw - btnW * 2 - gap - u(6), by, btnW, u(22), "Revert", Style.GHOST, !packs.isReloading(), packs::revert);
			ui.button("apply", ix + iw - btnW - u(6), by, btnW, u(22), "Apply", Style.PRIMARY, !packs.isReloading(), packs::apply);
		}

		int ly = sy + barH + gap, lh = y + h - pad - ly;
		int leftW = Math.round((iw - gap) * 0.52f), rightW = iw - gap - leftW;
		packList(g, ix, ly, leftW, lh);
		packPreview(g, ix + leftW + gap, ly, rightW, lh);
	}

	private void packList(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		ModConfig c = cfg();
		Draw.round(g, x, y, w, h, u(8), Theme.opaque(Theme.CARD));
		Draw.outline(g, x, y, w, h, u(8), Theme.opaque(Theme.BORDER));
		List<PackEntry> list = PackSorter.apply(packs.all(), packs::isEnabled, search.text, c.packSort, c.packFilter);
		if (selectedPack == null || packs.get(selectedPack) == null) {
			selectedPack = list.isEmpty() ? null : list.get(0).id;
		}
		int off = ui.scrollBegin("packlist", x + u(2), y + u(2), w - u(4), h - u(4));
		int rowH = u(46), gapR = u(4);
		int ry = y - off + u(8), rx = x + u(8), rw = w - u(16) - u(4);
		if (list.isEmpty()) {
			String msg = search.text.isBlank() ? "No packs match this filter." : "No packs match \"" + search.text.trim() + "\".";
			ui.text(msg, rx + u(4), ry + u(6), Theme.DIM, 1f);
			ry += u(30);
		}
		for (PackEntry p : list) {
			boolean on = packs.isEnabled(p.id);
			boolean sel = p.id.equals(selectedPack);
			boolean hv = ui.hover(rx, ry, rw, rowH);
			float k = ui.anim("row:" + p.id, sel ? 1f : (hv ? 0.5f : 0f), 16);
			Draw.round(g, rx, ry, rw, rowH, u(7), Theme.mix(Theme.opaque(Theme.PANEL2), Theme.opaque(Theme.CARD_HOVER), k));
			if (sel) Draw.outline(g, rx, ry, rw, rowH, u(7), Theme.accent());
			if (on) {
				int rank = packs.priorityRank(p.id) + 1;
				ui.textCentered("#" + rank, rx + u(15), ry + (rowH - ui.th(0.85f)) / 2 + 1, Theme.accent(), 0.85f);
			}
			int is = u(32);
			packIcon(g, p, rx + u(30), ry + (rowH - is) / 2, is);
			int tx = rx + u(30) + is + u(10);
			int rightPad = u(60) + (p.compatible ? 0 : u(34));
			ui.text(ui.fit(p.title, rw - (tx - rx) - rightPad, 1f), tx, ry + u(8), Theme.TEXT, 1f);
			String d = p.description.replace('\n', ' ');
			ui.text(ui.fit(d, rw - (tx - rx) - rightPad, 0.85f), tx, ry + u(8) + ui.th(1f) + u(3), Theme.DIM, 0.85f);
			int tr = rx + rw - u(8);
			if (p.required) {
				int ps = Math.max(1, u(1));
				Draw.icon(g, Draw.ICON_LOCK, tr - 8 * ps - u(8), ry + (rowH - 8 * ps) / 2, ps, Theme.FAINT | 0xFF000000);
				ui.hit(rx, ry, rw, rowH, () -> selectedPack = p.id, null);
			} else {
				ui.hit(rx, ry, rw, rowH, () -> selectedPack = p.id, null);
				ui.toggle("pk:" + p.id, tr - u(36), ry + (rowH - u(18)) / 2, on, v -> { packs.setEnabled(p.id, v); selectedPack = p.id; });
			}
			if (!p.compatible) chip(g, "OLD", tr - u(36) - u(8) - chipWidth("OLD", 0.75f), ry + (rowH - ui.th(0.75f) - u(6)) / 2, Theme.WARN, 0.75f);
			ry += rowH + gapR;
		}
		ui.scrollEnd("packlist", ry - (y - off) + u(8));
	}

	private void packPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Draw.round(g, x, y, w, h, u(8), Theme.opaque(Theme.CARD));
		Draw.outline(g, x, y, w, h, u(8), Theme.opaque(Theme.BORDER));
		PackEntry p = selectedPack == null ? null : packs.get(selectedPack);
		int off = ui.scrollBegin("preview", x + u(2), y + u(2), w - u(4), h - u(4));
		int pad = u(12), px = x + pad, pw = w - pad * 2 - u(6);
		int cy = y - off + pad;

		if (p == null) {
			ui.text("Select a pack to see its details.", px, cy, Theme.DIM, 1f);
			cy += u(24);
		} else {
			boolean on = packs.isEnabled(p.id);
			int isz = Math.min(u(cfg().previewSize.px), pw / 2);
			packIcon(g, p, px, cy, isz);
			int tx = px + isz + u(12), tw = pw - isz - u(12);
			int ty = cy;
			for (String l : ui.wrap(p.title, tw, 1.2f, 2)) { ui.text(l, tx, ty, Theme.TEXT, 1.2f); ty += ui.th(1.2f) + u(2); }
			ty += u(2);
			int chx = tx;
			chx += chipRow(g, on ? "ENABLED" : "DISABLED", chx, ty, on ? Theme.GOOD : Theme.FAINT) + u(4);
			if (p.required) chx += chipRow(g, "LOCKED", chx, ty, Theme.DIM) + u(4);
			if (!p.compatible) chipRow(g, "OLD FORMAT", chx, ty, Theme.WARN);
			ty += ui.th(0.8f) + u(10);
			ui.text("Source: " + p.sourceLabel(), tx, ty, Theme.DIM, 0.9f); ty += ui.th(0.9f) + u(2);
			ui.text("Size: " + formatSize(p.sizeBytes), tx, ty, Theme.DIM, 0.9f); ty += ui.th(0.9f) + u(2);
			if (p.modifiedMs > 0) { ui.text("Modified: " + DATE.format(Instant.ofEpochMilli(p.modifiedMs)), tx, ty, Theme.DIM, 0.9f); ty += ui.th(0.9f) + u(2); }
			if (on) { ui.text("Priority: #" + (packs.priorityRank(p.id) + 1) + " (1 = top)", tx, ty, Theme.accent(), 0.9f); ty += ui.th(0.9f) + u(2); }
			cy = Math.max(cy + isz, ty) + u(10);

			for (String l : ui.wrap(p.description, pw, 0.95f, 5)) { ui.text(l, px, cy, Theme.TEXT, 0.95f); cy += ui.th(0.95f) + u(2); }
			ui.text("ID: " + ui.fit(p.id, pw - u(24), 0.8f), px, cy + u(2), Theme.FAINT, 0.8f);
			cy += ui.th(0.8f) + u(12);

			// action buttons
			int bw = (pw - u(12)) / 3, bh = u(24);
			if (!p.required) {
				ui.button("pv-toggle", px, cy, bw, bh, on ? "Disable" : "Enable", on ? Style.DANGER : Style.PRIMARY,
						() -> packs.setEnabled(p.id, !on));
			} else {
				ui.button("pv-toggle", px, cy, bw, bh, "Locked", Style.GHOST, false, () -> {});
			}
			ui.button("pv-up", px + bw + u(6), cy, bw, bh, "Higher", Style.GHOST, on && !p.fixedPosition, () -> packs.move(p.id, true));
			ui.button("pv-down", px + (bw + u(6)) * 2, cy, bw, bh, "Lower", Style.GHOST, on && !p.fixedPosition, () -> packs.move(p.id, false));
			cy += bh + u(14);

			// what the pack replaces
			sectionTitle("Replaces", px, cy);
			cy += ui.th(1.1f) + u(5);
			PackScan sc = p.scan;
			if (p.path == null) {
				ui.text("Built-in packs are not scanned.", px, cy, Theme.FAINT, 0.9f);
				cy += ui.th(0.9f) + u(8);
			} else if (sc == null) {
				ui.text("Scanning...", px, cy, Theme.WARN, 0.9f);
				cy += ui.th(0.9f) + u(8);
			} else if (!sc.valid()) {
				for (String l : ui.wrap("Problem: " + sc.problem(), pw, 0.9f, 3)) { ui.text(l, px, cy, Theme.BAD, 0.9f); cy += ui.th(0.9f) + u(2); }
				cy += u(6);
			} else {
				int chx2 = px, rowH = ui.th(0.8f) + u(10);
				boolean any = false;
				for (PackCategory cat : PackCategory.values()) {
					if (!sc.affects(cat)) continue;
					any = true;
					String label = cat.title + " " + sc.count(cat);
					int cw2 = chipWidth(label, 0.8f);
					if (chx2 + cw2 > px + pw) { chx2 = px; cy += rowH; }
					final PackCategory target = cat;
					chip(g, label, chx2, cy, Theme.accent() & 0xFFFFFF, 0.8f);
					ui.hit(chx2, cy, cw2, ui.th(0.8f) + u(6), () -> { search.clear(); go("cat:" + target.name()); }, null);
					chx2 += cw2 + u(5);
				}
				if (!any) ui.text("No recognised textures or sounds.", px, cy, Theme.FAINT, 0.9f);
				cy += rowH + u(6);

				// sample textures
				List<String> samples = sc.previewEntries();
				if (!samples.isEmpty()) {
					sectionTitle("Sample textures", px, cy);
					cy += ui.th(1.1f) + u(5);
					int cols = 6, sg = u(4);
					int cell = (pw - sg * (cols - 1)) / cols;
					for (int i = 0; i < samples.size(); i++) {
						int cxs = px + (i % cols) * (cell + sg), cys = cy + (i / cols) * (cell + sg);
						Draw.round(g, cxs, cys, cell, cell, u(4), Theme.opaque(0x120D1C));
						TexCache.Tex t = tex.get(p.path, samples.get(i));
						if (t != null) {
							int in = u(3);
							g.blit(RenderPipelines.GUI_TEXTURED, t.id(), cxs + in, cys + in, 0f, 0f, cell - in * 2, cell - in * 2,
									t.width(), t.width(), t.width(), t.width());
						}
					}
					cy += ((samples.size() + cols - 1) / cols) * (cell + sg) + u(10);
				}
			}

			// quick switch / profiles
			cy = profilesSection(g, px, cy, pw);
		}
		ui.scrollEnd("preview", cy - (y - off) + pad);
	}

	private int chipRow(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		chip(g, text, x, y, color, 0.8f);
		return chipWidth(text, 0.8f);
	}

	private int profilesSection(GuiGraphicsExtractor g, int x, int y, int w) {
		sectionTitle("Quick switch", x, y);
		y += ui.th(1.1f) + u(5);
		boolean idle = !packs.isReloading();
		Map<String, List<String>> profiles = packs.profiles();
		int bh = u(24);
		ui.button("prev-set", x, y, w, bh, "Switch to previous pack set", Style.GHOST, idle, packs::swapToPrevious);
		y += bh + u(6);
		if (profiles.isEmpty()) {
			ui.text("No saved profiles yet. Arrange your packs, name the set below and save it.", x, y, Theme.FAINT, 0.9f);
			y += ui.th(0.9f) * 2 + u(10);
		}
		for (String name : new ArrayList<>(profiles.keySet())) {
			int xw = u(24);
			ui.button("prof:" + name, x, y, w - xw - u(4), bh, name + "  (" + profiles.get(name).size() + ")", Style.GHOST, idle, () -> packs.applyProfile(name));
			ui.button("profx:" + name, x + w - xw, y, xw, bh, "x", Style.SUBTLE, () -> packs.deleteProfile(name));
			y += bh + u(4);
		}
		y += u(4);
		int saveW = u(64);
		ui.textField("profname", x, y, w - saveW - u(6), bh, "Profile name", profileField, -1);
		ui.button("prof-save", x + w - saveW, y, saveW, bh, "Save", Style.PRIMARY, () -> { packs.saveProfile(profileField.text); profileField.clear(); });
		y += bh + u(6);
		ui.text("Saves the currently staged pack list, including order.", x, y, Theme.FAINT, 0.8f);
		return y + ui.th(0.8f) + u(8);
	}

	// ---- settings page ----------------------------------------------------------------------------------

	private void settingsPage(int x, int y, int w, int h) {
		GuiGraphicsExtractor g = ui.g;
		ModConfig c = cfg();
		int pad = u(14);
		int off = ui.scrollBegin("settings", x, y, w, h);
		int cx = x + pad, cw = w - pad * 2 - u(6);
		int cy = y - off + pad;
		ui.text("Settings", cx, cy, Theme.TEXT, 1.7f);
		cy += ui.th(1.7f) + u(4);
		ui.text("Everything here is saved automatically to config/murmelsvisuals.json.", cx, cy, Theme.DIM, 1f);
		cy += ui.th(1f) + u(12);

		// ---- appearance card
		int p = u(14);
		int cardH = p + ui.th(1.2f) + u(10)
				+ u(34)                 // ui scale
				+ u(30)                 // animations toggle
				+ u(34)                 // anim speed
				+ u(32)                 // background
				+ u(34)                 // opacity
				+ ui.th(0.95f) + u(26) + u(32) // accent label, swatches, hex
				+ u(32)                 // preview size
				+ u(28)                 // default sort / filter
				+ p;
		card(g, cx, cy, cw, cardH);
		int iy = cy + p, ix = cx + p, iw = cw - p * 2;
		ui.text("Appearance", ix, iy, Theme.TEXT, 1.2f);
		iy += ui.th(1.2f) + u(10);

		float shownScale = pendingUiScale != null ? pendingUiScale : c.uiScale;
		iy += ui.slider("uiscale", ix, iy, iw, "UI scale (applies when you release)", Math.round(shownScale * 100) + "%", shownScale,
				0.75, 1.50, 0.05, v -> pendingUiScale = (float) v,
				() -> { if (pendingUiScale != null) { c.uiScale = pendingUiScale; pendingUiScale = null; ConfigManager.markDirty(); ConfigManager.saveIfDirty(); } }) + u(2);

		ui.text("Animations", ix, iy + u(4), Theme.DIM, 0.95f);
		ui.toggle("anims", ix + iw - u(36), iy, c.animations, v -> { c.animations = v; ConfigManager.markDirty(); });
		iy += u(30);
		iy += ui.slider("animspeed", ix, iy, iw, "Animation speed", String.format(Locale.ROOT, "%.1fx", c.animationSpeed), c.animationSpeed,
				0.5, 2.0, 0.1, v -> { c.animationSpeed = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);

		ui.cycle("bg", ix, iy, iw, u(26), "Background", c.background.label,
				() -> { c.background = ModConfig.BackgroundStyle.values()[(c.background.ordinal() + 1) % ModConfig.BackgroundStyle.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.BackgroundStyle.values().length; c.background = ModConfig.BackgroundStyle.values()[(c.background.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		iy += u(32);
		iy += ui.slider("opacity", ix, iy, iw, "Panel opacity", Math.round(c.panelOpacity * 100) + "%", c.panelOpacity,
				0.50, 1.00, 0.01, v -> { c.panelOpacity = (float) v; ConfigManager.markDirty(); }, ConfigManager::saveIfDirty) + u(2);

		ui.text("Accent colour", ix, iy, Theme.DIM, 0.95f);
		iy += ui.th(0.95f) + u(6);
		int sw = u(22), sg = u(8);
		for (int i = 0; i < ACCENT_PRESETS.length; i++) {
			int col = ACCENT_PRESETS[i];
			int sxp = ix + i * (sw + sg);
			boolean selected = (c.accent & 0xFFFFFF) == col;
			boolean hv = ui.hover(sxp, iy, sw, sw);
			if (selected || hv) Draw.round(g, sxp - u(2), iy - u(2), sw + u(4), sw + u(4), (sw + u(4)) / 2, selected ? 0xFFFFFFFF : Theme.a(0xFFFFFF, 0.4f));
			Draw.round(g, sxp, iy, sw, sw, sw / 2, Theme.opaque(col));
			ui.hit(sxp, iy, sw, sw, () -> { c.accent = col; hexField.set(String.format("#%06X", col)); ConfigManager.markDirty(); ConfigManager.saveIfDirty(); }, null);
		}
		iy += sw + u(10);
		if (ui.focus != hexField) hexField.set(String.format("#%06X", c.accent & 0xFFFFFF));
		ui.textField("hex", ix, iy, u(120), u(22), "#RRGGBB", hexField, -1);
		ui.text("Type a hex colour, e.g. #FF3EA5", ix + u(130), iy + (u(22) - ui.th(0.9f)) / 2 + 1, Theme.FAINT, 0.9f);
		iy += u(32);

		ui.cycle("psize", ix, iy, iw, u(26), "Pack preview size", c.previewSize.label,
				() -> { c.previewSize = ModConfig.PreviewSize.values()[(c.previewSize.ordinal() + 1) % ModConfig.PreviewSize.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.PreviewSize.values().length; c.previewSize = ModConfig.PreviewSize.values()[(c.previewSize.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		iy += u(32);
		int half = (iw - u(8)) / 2;
		ui.cycle("dsort", ix, iy, half, u(22), "Pack sort", c.packSort.label,
				() -> { c.packSort = ModConfig.PackSort.values()[(c.packSort.ordinal() + 1) % ModConfig.PackSort.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.PackSort.values().length; c.packSort = ModConfig.PackSort.values()[(c.packSort.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		ui.cycle("dfilter", ix + half + u(8), iy, half, u(22), "Pack filter", c.packFilter.label,
				() -> { c.packFilter = ModConfig.PackFilter.values()[(c.packFilter.ordinal() + 1) % ModConfig.PackFilter.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.PackFilter.values().length; c.packFilter = ModConfig.PackFilter.values()[(c.packFilter.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		cy += cardH + u(12);

		// ---- behaviour card
		int bH = p + ui.th(1.2f) + u(10) + u(30) + u(32) + u(30) + p;
		card(g, cx, cy, cw, bH);
		iy = cy + p;
		ui.text("Menu & keys", ix, iy, Theme.TEXT, 1.2f);
		iy += ui.th(1.2f) + u(10);
		ui.text("Button on the Minecraft main menu", ix, iy + u(4), Theme.DIM, 0.95f);
		ui.toggle("menubtn", ix + iw - u(36), iy, c.mainMenuButton, v -> { c.mainMenuButton = v; ConfigManager.markDirty(); });
		iy += u(30);
		ui.cycle("corner", ix, iy, iw, u(26), "Main menu button position", c.mainMenuCorner.label,
				() -> { c.mainMenuCorner = ModConfig.MenuCorner.values()[(c.mainMenuCorner.ordinal() + 1) % ModConfig.MenuCorner.values().length]; ConfigManager.markDirty(); },
				() -> { int n = ModConfig.MenuCorner.values().length; c.mainMenuCorner = ModConfig.MenuCorner.values()[(c.mainMenuCorner.ordinal() + n - 1) % n]; ConfigManager.markDirty(); });
		iy += u(32);
		ui.text("Open GUI key: " + MurmelsVisualsClient.openKeyLabel(), ix, iy + u(5), Theme.DIM, 0.95f);
		int kbw = u(130);
		ui.button("keybinds", ix + iw - kbw, iy, kbw, u(24), "Edit key binds", Style.GHOST, () -> {
			ConfigManager.saveIfDirty();
			if (!Compat.openKeyBinds(this)) packs.setMessage("Could not open the controls screen - use Options > Controls > Key Binds.");
		});
		cy += bH + u(12);

		ui.button("reset", cx, cy, u(170), u(26), "Reset appearance...", Style.DANGER, () -> resetConfirm = true);
		cy += u(26) + u(14);
		ui.scrollEnd("settings", cy - (y - off) + pad);
	}

	// ---- search results ---------------------------------------------------------------------------------

	private static List<SearchEntry> buildIndex() {
		List<SearchEntry> l = new ArrayList<>();
		for (PackCategory c : PackCategory.values()) l.add(new SearchEntry(c.title, c.description, "category", "cat:" + c.name()));
		l.add(new SearchEntry("Low Fire", "Shrink the burning fire overlay", "fire burning overlay height width screen flames", "cat:HUD"));
		l.add(new SearchEntry("Side Shield", "Show the shield at the side of the screen", "shield offhand block side tilt first person", "cat:WEAPONS"));
		l.add(new SearchEntry("Enchantment Glint", "Change how strong and fast the enchant shimmer is", "glint enchant enchanted shimmer strength speed", "cat:ITEMS"));
		l.add(new SearchEntry("3D Item Preview", "Preview items in 3D inside the GUI", "item preview 3d showcase model", "cat:ITEMS"));
		l.add(new SearchEntry("Resource Pack Manager", "Enable, disable, reorder, import and preview packs", "resourcepack texture pack import sort search preview profile quick switch folder priority", "packs"));
		l.add(new SearchEntry("UI scale", "Make this GUI larger or smaller", "appearance size zoom", "settings"));
		l.add(new SearchEntry("Animations", "Turn GUI animations on or off, change speed", "appearance motion speed", "settings"));
		l.add(new SearchEntry("Background", "Dim, gradient, solid or clear backdrop", "appearance blur", "settings"));
		l.add(new SearchEntry("Panel opacity", "How see-through the panel is", "appearance transparency", "settings"));
		l.add(new SearchEntry("Accent colour", "Pick the highlight colour", "appearance purple pink color theme hex", "settings"));
		l.add(new SearchEntry("Preview size", "Size of the pack icon preview", "appearance pack icon", "settings"));
		l.add(new SearchEntry("Main menu button", "Show or move the button on the title screen", "menu title corner", "settings"));
		l.add(new SearchEntry("Key bind", "Change the key that opens this GUI", "keybind hotkey controls shortcut", "settings"));
		return l;
	}

	private void searchPage(GuiGraphicsExtractor g, int x, int y, int w, int h, String q) {
		int pad = u(14);
		int off = ui.scrollBegin("search", x, y, w, h);
		int cx = x + pad, cw = w - pad * 2 - u(6);
		int cy = y - off + pad;
		ui.text("Search results", cx, cy, Theme.TEXT, 1.5f);
		cy += ui.th(1.5f) + u(10);
		String lower = q.toLowerCase(Locale.ROOT);
		int found = 0;
		for (SearchEntry e : INDEX) {
			String hay = (e.title + " " + e.desc + " " + e.keywords).toLowerCase(Locale.ROOT);
			boolean all = true;
			for (String t : lower.split("\\s+")) if (!t.isEmpty() && !hay.contains(t)) { all = false; break; }
			if (!all) continue;
			found++;
			cy = resultRow(g, e.title, e.desc, e.target.startsWith("cat:") ? "Category" : (e.target.equals("packs") ? "Resource packs" : "Settings"), cx, cy, cw, () -> { search.clear(); go(e.target); });
		}
		for (PackEntry p : PackSorter.apply(packs.all(), id -> false, q, cfg().packSort, ModConfig.PackFilter.ALL)) {
			found++;
			cy = resultRow(g, p.title, p.description.replace('\n', ' '), "Resource pack", cx, cy, cw, () -> { selectedPack = p.id; go("packs"); });
		}
		if (found == 0) ui.text("Nothing matches \"" + q + "\".", cx, cy, Theme.DIM, 1f);
		ui.scrollEnd("search", cy - (y - off) + pad + (found == 0 ? u(20) : 0));
	}

	private int resultRow(GuiGraphicsExtractor g, String title, String desc, String kind, int x, int y, int w, Runnable click) {
		int rh = u(42);
		boolean hv = ui.hover(x, y, w, rh);
		float k = ui.anim("res:" + title + kind, hv ? 1 : 0, 16);
		Draw.round(g, x, y, w, rh, u(7), Theme.mix(Theme.opaque(Theme.CARD), Theme.opaque(Theme.CARD_HOVER), k));
		Draw.outline(g, x, y, w, rh, u(7), Theme.mix(Theme.opaque(Theme.BORDER), Theme.accent(), k * 0.7f));
		ui.text(ui.fit(title, w - u(130), 1f), x + u(12), y + u(7), Theme.TEXT, 1f);
		ui.text(ui.fit(desc, w - u(130), 0.85f), x + u(12), y + u(7) + ui.th(1f) + u(3), Theme.DIM, 0.85f);
		chip(g, kind, x + w - chipWidth(kind, 0.8f) - u(10), y + (rh - ui.th(0.8f) - u(6)) / 2, Theme.accent() & 0xFFFFFF, 0.8f);
		ui.hit(x, y, w, rh, click, null);
		return y + rh + u(5);
	}

	// ---- dialogs ----------------------------------------------------------------------------------------

	private void drawImportDialog(GuiGraphicsExtractor g) {
		ui.layer = 1;
		g.fill(0, 0, width, height, Theme.a(0x000000, 0.6f));
		ui.block(0, 0, width, height);
		int w = Math.min(width - u(24), u(500)), h = u(200);
		int x = (width - w) / 2, y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, u(10), u(12), 1f);
		Draw.round(g, x, y, w, h, u(10), Theme.opaque(Theme.PANEL));
		Draw.outline(g, x, y, w, h, u(10), Theme.accent());
		ui.block(x, y, w, h);
		int p = u(16), cy = y + p;
		ui.text("Import resource pack", x + p, cy, Theme.TEXT, 1.3f);
		cy += ui.th(1.3f) + u(8);
		for (String l : ui.wrap("Drag a .zip or pack folder onto this window, or paste its full path below. The pack is copied into your "
				+ "resourcepacks folder (nothing is overwritten) and shows up disabled.", w - p * 2, 0.95f, 4)) {
			ui.text(l, x + p, cy, Theme.DIM, 0.95f);
			cy += ui.th(0.95f) + u(2);
		}
		cy += u(8);
		ui.textField("import-path", x + p, cy, w - p * 2, u(26), "C:\\Users\\you\\Downloads\\MyPack.zip", importField, Draw.ICON_FOLDER);
		cy += u(34);
		int bw = (w - p * 2 - u(18)) / 4;
		ui.button("imp-paste", x + p, cy, bw, u(26), "Paste", Style.GHOST, () -> { importField.set(Compat.clipboard().trim()); ui.focus = importField; });
		ui.button("imp-folder", x + p + bw + u(6), cy, bw, u(26), "Open folder", Style.GHOST, () -> Compat.openFolder(packs.packDir()));
		ui.button("imp-cancel", x + p + (bw + u(6)) * 2, cy, bw, u(26), "Cancel", Style.GHOST, () -> { importOpen = false; ui.focus = null; });
		ui.button("imp-go", x + p + (bw + u(6)) * 3, cy, bw, u(26), "Import", Style.PRIMARY, this::doImport);
		ui.layer = 0;
	}

	private void drawResetDialog(GuiGraphicsExtractor g) {
		ui.layer = 1;
		g.fill(0, 0, width, height, Theme.a(0x000000, 0.6f));
		ui.block(0, 0, width, height);
		int w = Math.min(width - u(24), u(380)), h = u(120);
		int x = (width - w) / 2, y = (height - h) / 2;
		Draw.shadow(g, x, y, w, h, u(10), u(12), 1f);
		Draw.round(g, x, y, w, h, u(10), Theme.opaque(Theme.PANEL));
		Draw.outline(g, x, y, w, h, u(10), Theme.opaque(Theme.BAD));
		ui.block(x, y, w, h);
		int p = u(16);
		ui.text("Reset appearance?", x + p, y + p, Theme.TEXT, 1.3f);
		int ty = y + p + ui.th(1.3f) + u(8);
		for (String l : ui.wrap("Resets scale, animations, background, opacity, accent, preview size and menu button. "
				+ "Low Fire, Side Shield and your pack profiles are kept.", w - p * 2, 0.95f, 4)) {
			ui.text(l, x + p, ty, Theme.DIM, 0.95f);
			ty += ui.th(0.95f) + u(2);
		}
		int bw = (w - p * 2 - u(6)) / 2, by = y + h - p - u(26);
		ui.button("rs-cancel", x + p, by, bw, u(26), "Cancel", Style.GHOST, () -> resetConfirm = false);
		ui.button("rs-ok", x + p + bw + u(6), by, bw, u(26), "Reset", Style.DANGER, () -> {
			cfg().resetAppearance();
			pendingUiScale = null;
			ConfigManager.markDirty();
			ConfigManager.saveIfDirty();
			resetConfirm = false;
		});
		ui.layer = 0;
	}
}
