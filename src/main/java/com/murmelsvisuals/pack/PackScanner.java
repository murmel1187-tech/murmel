package com.murmelsvisuals.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Inspects a resource pack (zip file or folder) without loading it into Minecraft. Pure Java so it
 * can run on a worker thread and be unit-tested.
 */
public final class PackScanner {
	private PackScanner() {}

	/** Textures tried (in order) for the preview mosaic; the first {@link #MAX_PREVIEW} that exist are used. */
	static final String[] PREVIEW_CANDIDATES = {
			"assets/minecraft/textures/block/grass_block_side.png",
			"assets/minecraft/textures/block/stone.png",
			"assets/minecraft/textures/block/dirt.png",
			"assets/minecraft/textures/block/oak_planks.png",
			"assets/minecraft/textures/block/cobblestone.png",
			"assets/minecraft/textures/block/diamond_ore.png",
			"assets/minecraft/textures/block/oak_log.png",
			"assets/minecraft/textures/block/sand.png",
			"assets/minecraft/textures/item/diamond_sword.png",
			"assets/minecraft/textures/item/diamond_pickaxe.png",
			"assets/minecraft/textures/item/apple.png",
			"assets/minecraft/textures/item/golden_apple.png",
			"assets/minecraft/textures/item/bow.png",
			"assets/minecraft/textures/item/diamond.png",
			"assets/minecraft/textures/block/crafting_table_front.png",
			"assets/minecraft/textures/block/tnt_side.png",
	};
	public static final int MAX_PREVIEW = 12;

	/** Scan a pack. Never throws; problems are reported in {@link PackScan#problem()}. */
	public static PackScan scan(Path pack) {
		try {
			List<String> names = listEntries(pack);
			Set<String> nameSet = new HashSet<>(names);
			if (!nameSet.contains("pack.mcmeta")) {
				return PackScan.invalid("pack.mcmeta not found at the pack root");
			}
			Map<PackCategory, Integer> counts = new EnumMap<>(PackCategory.class);
			for (String n : names) {
				for (PackCategory c : classify(n)) counts.merge(c, 1, Integer::sum);
			}
			List<String> preview = new ArrayList<>();
			for (String cand : PREVIEW_CANDIDATES) {
				if (nameSet.contains(cand)) preview.add(cand);
				if (preview.size() >= MAX_PREVIEW) break;
			}
			return new PackScan(true, "", names.size(), counts, Collections.unmodifiableList(preview));
		} catch (IOException | RuntimeException e) {
			return PackScan.invalid(String.valueOf(e.getMessage()));
		}
	}

	/** Lower-cased, '/'-separated relative file names of a zip or folder pack. */
	static List<String> listEntries(Path pack) throws IOException {
		List<String> out = new ArrayList<>();
		if (Files.isDirectory(pack)) {
			try (Stream<Path> walk = Files.walk(pack, 16)) {
				walk.filter(Files::isRegularFile).forEach(p ->
						out.add(pack.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT)));
			}
		} else {
			try (ZipFile zip = new ZipFile(pack.toFile())) {
				var en = zip.entries();
				while (en.hasMoreElements()) {
					ZipEntry e = en.nextElement();
					if (!e.isDirectory()) out.add(e.getName().replace('\\', '/').toLowerCase(Locale.ROOT));
				}
			}
		}
		return out;
	}

	/** Read one file of a pack (exact, case-sensitive name as returned by the scan). Null if absent. */
	public static byte[] readEntry(Path pack, String name) throws IOException {
		if (Files.isDirectory(pack)) {
			Path p = pack.resolve(name).normalize();
			if (!p.startsWith(pack.normalize()) || !Files.isRegularFile(p)) return null;
			return Files.readAllBytes(p);
		}
		try (ZipFile zip = new ZipFile(pack.toFile())) {
			ZipEntry e = zip.getEntry(name);
			if (e == null) {
				// the scan lower-cases names; fall back to a case-insensitive lookup
				var en = zip.entries();
				while (en.hasMoreElements()) {
					ZipEntry c = en.nextElement();
					if (c.getName().equalsIgnoreCase(name)) { e = c; break; }
				}
			}
			if (e == null || e.getSize() > 16L * 1024 * 1024) return null;
			try (InputStream in = zip.getInputStream(e)) {
				return in.readAllBytes();
			}
		}
	}

	/** Which categories a single (lower-case) pack file belongs to. A file may belong to several. */
	public static Set<PackCategory> classify(String path) {
		Set<PackCategory> r = java.util.EnumSet.noneOf(PackCategory.class);
		if (!path.startsWith("assets/")) return r;
		// strip "assets/<namespace>/"
		int second = path.indexOf('/', 7);
		if (second < 0) return r;
		String rel = path.substring(second + 1);
		String file = rel.substring(rel.lastIndexOf('/') + 1);

		// ---- sounds
		if (rel.startsWith("sounds/") || rel.equals("sounds.json")) r.add(PackCategory.SOUNDS);

		// ---- particles
		if (rel.startsWith("textures/particle/") || rel.startsWith("particles/")) r.add(PackCategory.PARTICLES);

		// ---- sky / environment
		if (rel.startsWith("textures/environment/")) {
			if (file.startsWith("sun") || file.startsWith("moon") || file.startsWith("clouds")
					|| file.startsWith("end_sky") || file.startsWith("celestial")) r.add(PackCategory.SKY);
			else r.add(PackCategory.ENVIRONMENT);
		}
		if (rel.startsWith("optifine/sky/") || rel.startsWith("mcpatcher/sky/")
				|| rel.startsWith("fabricskyboxes/sky/") || rel.startsWith("sky/")) r.add(PackCategory.SKY);
		if (rel.startsWith("textures/colormap/") || rel.startsWith("textures/misc/")
				|| rel.startsWith("shaders/") || rel.startsWith("post_effect/")
				|| (rel.startsWith("textures/block/") && (file.startsWith("water_") || file.startsWith("lava_")))) {
			r.add(PackCategory.ENVIRONMENT);
		}

		// ---- GUI / HUD
		if (rel.startsWith("textures/gui/sprites/hud/") || rel.startsWith("textures/gui/sprites/boss_bar/")
				|| rel.equals("textures/gui/icons.png") || rel.equals("textures/gui/widgets.png")) {
			r.add(PackCategory.HUD);
		} else if (rel.startsWith("textures/gui/") || rel.startsWith("font/") || rel.startsWith("textures/font/")) {
			r.add(PackCategory.GUI);
		}

		// ---- blocks
		if (rel.startsWith("textures/block/") || rel.startsWith("models/block/") || rel.startsWith("blockstates/")) {
			r.add(PackCategory.BLOCKS);
		}

		// ---- items & item-derived categories
		boolean itemFile = rel.startsWith("textures/item/") || rel.startsWith("models/item/") || rel.startsWith("items/");
		if (itemFile) {
			r.add(PackCategory.ITEMS);
			if (file.contains("potion") || file.contains("tipped_arrow")) r.add(PackCategory.POTIONS);
			if (hasAny(file, "sword", "bow", "crossbow", "trident", "mace", "shield", "arrow", "spear"))
				r.add(PackCategory.WEAPONS);
			if (hasAny(file, "pickaxe", "shovel", "_hoe", "hoe.", "_axe", "axe.", "shears", "fishing_rod",
					"flint_and_steel", "brush", "spyglass", "compass", "clock"))
				r.add(PackCategory.TOOLS);
			if (hasAny(file, "helmet", "chestplate", "leggings", "boots", "elytra", "horse_armor", "wolf_armor",
					"turtle_scute", "armor_trim", "_smithing_template"))
				r.add(PackCategory.ARMOR);
		}
		if (rel.startsWith("textures/mob_effect/")) r.add(PackCategory.POTIONS);
		if (rel.startsWith("textures/entity/shield") || rel.startsWith("textures/entity/trident")
				|| rel.startsWith("textures/entity/projectiles/")) r.add(PackCategory.WEAPONS);
		if (rel.startsWith("textures/entity/equipment/") || rel.startsWith("textures/models/armor/")
				|| rel.startsWith("textures/trims/") || rel.startsWith("equipment/")
				|| rel.startsWith("textures/entity/elytra")) r.add(PackCategory.ARMOR);
		return r;
	}

	private static boolean hasAny(String s, String... needles) {
		for (String n : needles) if (s.contains(n)) return true;
		return false;
	}
}
