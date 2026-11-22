package com.murmelsvisuals.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All persisted settings of MurmelsVisuals. Serialised to {@code config/murmelsvisuals.json} with Gson.
 *
 * <p>Every field has a default, so a missing key in an old/partial file simply keeps its default.
 * {@link #sanitize()} clamps ranges and replaces {@code null}s (unknown enum names deserialise to
 * {@code null}), so a hand-edited or corrupted value can never crash the mod.</p>
 */
public final class ModConfig {

	public enum BackgroundStyle {
		DIM("Dim"), GRADIENT("Gradient"), SOLID("Solid"), CLEAR("Clear");
		public final String label;
		BackgroundStyle(String label) { this.label = label; }
	}

	public enum PreviewSize {
		SMALL("Small", 64), MEDIUM("Medium", 96), LARGE("Large", 128);
		public final String label;
		public final int px;
		PreviewSize(String label, int px) { this.label = label; this.px = px; }
	}

	public enum PackSort {
		NAME_ASC("Name A-Z"), NAME_DESC("Name Z-A"), ENABLED_FIRST("Enabled first"),
		NEWEST("Recently modified"), LARGEST("Largest"), COMPATIBILITY("Compatibility");
		public final String label;
		PackSort(String label) { this.label = label; }
	}

	public enum PackFilter {
		ALL("All"), ENABLED("Enabled"), DISABLED("Disabled");
		public final String label;
		PackFilter(String label) { this.label = label; }
	}

	public enum MenuCorner {
		TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left");
		public final String label;
		MenuCorner(String label) { this.label = label; }
	}

	/** Bumped when the file layout changes in an incompatible way. */
	public int version = 1;

	// ---- Feature: Low Fire --------------------------------------------------------------
	public boolean lowFireEnabled = false;
	/** Visible fire height relative to vanilla, 0.10 .. 1.00. */
	public float lowFireHeight = 0.40f;
	/** Horizontal size of the fire relative to vanilla, 0.30 .. 1.00. */
	public float lowFireWidth = 1.00f;

	// ---- Feature: Side Shield -----------------------------------------------------------
	public boolean sideShieldEnabled = false;
	/** Sideways shift toward the screen edge, 0 .. 1. */
	public float shieldSide = 0.30f;
	/** Downward shift, 0 .. 1. */
	public float shieldDown = 0.10f;
	/** Size multiplier, 0.40 .. 1.20. */
	public float shieldScale = 0.85f;
	/** Outward tilt in degrees, -45 .. 45. */
	public float shieldTilt = 10f;
	/** While actively blocking, draw the shield at its vanilla position. */
	public boolean shieldCenterWhileBlocking = true;

	// ---- Feature: Enchantment Glint ------------------------------------------------------
	public boolean glintEnabled = false;
	/** Glint strength 0..1 (the game's own "Glint Strength" option range). */
	public float glintStrength = 1.0f;
	/** Glint speed 0..1 (the game's own "Glint Speed" option range). */
	public float glintSpeed = 0.5f;
	/** The player's own vanilla values, saved while the feature overrides them (-1 = nothing saved). */
	public float glintBackupStrength = -1f;
	public float glintBackupSpeed = -1f;
	/** Show enchanted (glinting) items in the GUI item showcase. */
	public boolean showcaseGlint = true;

	// ---- Customisation ------------------------------------------------------------------
	public float uiScale = 1.0f;
	public boolean animations = true;
	public float animationSpeed = 1.0f;
	public BackgroundStyle background = BackgroundStyle.GRADIENT;
	public float panelOpacity = 0.94f;
	/** Accent colour as 0xRRGGBB. */
	public int accent = 0xA855F7;
	public PreviewSize previewSize = PreviewSize.MEDIUM;
	public PackSort packSort = PackSort.NAME_ASC;
	public PackFilter packFilter = PackFilter.ALL;
	public boolean mainMenuButton = true;
	public MenuCorner mainMenuCorner = MenuCorner.TOP_LEFT;
	public String lastPage = "cat:BLOCKS";

	// ---- Resource pack manager ----------------------------------------------------------
	/** Saved pack sets: profile name -> ordered pack ids (lowest priority first, as Minecraft stores them). */
	public Map<String, List<String>> packProfiles = new LinkedHashMap<>();
	/** The pack set that was active before the last change; used by the quick-switch key. */
	public List<String> previousPackSet = new ArrayList<>();

	/** Clamp every value into its legal range and repair nulls. Safe to call repeatedly. */
	public void sanitize() {
		lowFireHeight = clamp(lowFireHeight, 0.10f, 1.00f);
		lowFireWidth = clamp(lowFireWidth, 0.30f, 1.00f);
		shieldSide = clamp(shieldSide, 0f, 1f);
		shieldDown = clamp(shieldDown, 0f, 1f);
		shieldScale = clamp(shieldScale, 0.40f, 1.20f);
		shieldTilt = clamp(shieldTilt, -45f, 45f);
		glintStrength = clamp(glintStrength, 0f, 1f);
		glintSpeed = clamp(glintSpeed, 0f, 1f);
		if (glintBackupStrength != -1f) glintBackupStrength = clamp(glintBackupStrength, 0f, 1f);
		if (glintBackupSpeed != -1f) glintBackupSpeed = clamp(glintBackupSpeed, 0f, 1f);
		uiScale = clamp(uiScale, 0.75f, 1.50f);
		animationSpeed = clamp(animationSpeed, 0.5f, 2.0f);
		panelOpacity = clamp(panelOpacity, 0.50f, 1.00f);
		accent &= 0xFFFFFF;
		if (background == null) background = BackgroundStyle.GRADIENT;
		if (previewSize == null) previewSize = PreviewSize.MEDIUM;
		if (packSort == null) packSort = PackSort.NAME_ASC;
		if (packFilter == null) packFilter = PackFilter.ALL;
		if (mainMenuCorner == null) mainMenuCorner = MenuCorner.TOP_LEFT;
		if (lastPage == null || lastPage.isBlank()) lastPage = "cat:BLOCKS";
		if (packProfiles == null) packProfiles = new LinkedHashMap<>();
		if (previousPackSet == null) previousPackSet = new ArrayList<>();
		packProfiles.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
	}

	/** Reset only the appearance/behaviour options (keeps feature toggles and pack profiles). */
	public void resetAppearance() {
		ModConfig d = new ModConfig();
		uiScale = d.uiScale;
		animations = d.animations;
		animationSpeed = d.animationSpeed;
		background = d.background;
		panelOpacity = d.panelOpacity;
		accent = d.accent;
		previewSize = d.previewSize;
		packSort = d.packSort;
		packFilter = d.packFilter;
		mainMenuButton = d.mainMenuButton;
		mainMenuCorner = d.mainMenuCorner;
	}

	private static float clamp(float v, float min, float max) {
		if (Float.isNaN(v)) return min;
		return Math.max(min, Math.min(max, v));
	}
}
