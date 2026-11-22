package com.murmelsvisuals;

import com.murmelsvisuals.config.ModConfig;
import com.murmelsvisuals.config.ModConfig.PackFilter;
import com.murmelsvisuals.config.ModConfig.PackSort;
import com.murmelsvisuals.pack.PackCategory;
import com.murmelsvisuals.pack.PackEntry;
import com.murmelsvisuals.pack.PackImporter;
import com.murmelsvisuals.pack.PackScan;
import com.murmelsvisuals.pack.PackScanner;
import com.murmelsvisuals.pack.PackSorter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for all Minecraft-independent logic. */
class PackLogicTest {

	private static Path zip(Path dir, String name, String... files) throws IOException {
		Path z = dir.resolve(name);
		try (OutputStream o = Files.newOutputStream(z); ZipOutputStream zo = new ZipOutputStream(o)) {
			for (String f : files) {
				zo.putNextEntry(new ZipEntry(f));
				zo.write(new byte[] {1, 2, 3});
				zo.closeEntry();
			}
		}
		return z;
	}

	private static PackEntry entry(String id, String title, String desc, long size, long mod, boolean compat) {
		return new PackEntry(id, title, desc, false, false, compat, false, null, size, mod);
	}

	@Test
	void classifiesAssets() {
		assertEquals(Set.of(PackCategory.BLOCKS), PackScanner.classify("assets/minecraft/textures/block/stone.png"));
		assertTrue(PackScanner.classify("assets/minecraft/textures/item/diamond_sword.png")
				.containsAll(Set.of(PackCategory.ITEMS, PackCategory.WEAPONS)));
		assertTrue(PackScanner.classify("assets/minecraft/textures/item/netherite_pickaxe.png")
				.contains(PackCategory.TOOLS));
		assertTrue(PackScanner.classify("assets/minecraft/textures/item/splash_potion.png")
				.contains(PackCategory.POTIONS));
		assertEquals(Set.of(PackCategory.SKY), PackScanner.classify("assets/minecraft/textures/environment/sun.png"));
		assertEquals(Set.of(PackCategory.ENVIRONMENT), PackScanner.classify("assets/minecraft/textures/environment/rain.png"));
		assertEquals(Set.of(PackCategory.HUD), PackScanner.classify("assets/minecraft/textures/gui/sprites/hud/heart/full.png"));
		assertEquals(Set.of(PackCategory.GUI), PackScanner.classify("assets/minecraft/textures/gui/title/minecraft.png"));
		assertEquals(Set.of(PackCategory.SOUNDS), PackScanner.classify("assets/minecraft/sounds.json"));
		assertEquals(Set.of(PackCategory.PARTICLES), PackScanner.classify("assets/minecraft/textures/particle/flame.png"));
		assertTrue(PackScanner.classify("assets/minecraft/textures/entity/equipment/humanoid/iron.png")
				.contains(PackCategory.ARMOR));
		assertTrue(PackScanner.classify("pack.mcmeta").isEmpty());
		assertTrue(PackScanner.classify("assets").isEmpty());
	}

	@Test
	void scansZipPack(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path z = zip(tmp, "demo.zip", "pack.mcmeta", "pack.png",
				"assets/minecraft/textures/block/stone.png", "assets/minecraft/textures/block/dirt.png",
				"assets/minecraft/sounds.json");
		PackScan s = PackScanner.scan(z);
		assertTrue(s.valid());
		assertEquals(2, s.count(PackCategory.BLOCKS));
		assertTrue(s.affects(PackCategory.SOUNDS));
		assertFalse(s.affects(PackCategory.SKY));
		assertTrue(s.previewEntries().contains("assets/minecraft/textures/block/stone.png"));
		assertEquals(2, s.previewEntries().size());
		assertNotNull(PackScanner.readEntry(z, "assets/minecraft/textures/block/stone.png"));
		assertNull(PackScanner.readEntry(z, "does/not/exist.png"));
	}

	@Test
	void rejectsPackWithoutMcmeta(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path z = zip(tmp, "bad.zip", "assets/minecraft/textures/block/stone.png");
		assertFalse(PackScanner.scan(z).valid());
		assertFalse(PackScanner.scan(tmp.resolve("missing.zip")).valid());
	}

	@Test
	void scansFolderPack(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("folderpack");
		Files.createDirectories(dir.resolve("assets/minecraft/textures/item"));
		Files.writeString(dir.resolve("pack.mcmeta"), "{}");
		Files.write(dir.resolve("assets/minecraft/textures/item/apple.png"), new byte[] {1});
		PackScan s = PackScanner.scan(dir);
		assertTrue(s.valid());
		assertEquals(1, s.count(PackCategory.ITEMS));
		assertArrayEquals(new byte[] {1}, PackScanner.readEntry(dir, "assets/minecraft/textures/item/apple.png"));
		assertNull(PackScanner.readEntry(dir, "../outside.txt"));
	}

	@Test
	void importsWithoutOverwriting(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path packs = tmp.resolve("resourcepacks");
		Path src = zip(tmp, "cool.zip", "pack.mcmeta");
		Path a = PackImporter.importPack(src, packs);
		Path b = PackImporter.importPack(src, packs);
		assertEquals("cool.zip", a.getFileName().toString());
		assertEquals("cool (2).zip", b.getFileName().toString());
		assertTrue(Files.exists(a) && Files.exists(b));
		assertThrows(IOException.class, () -> PackImporter.importPack(a, packs)); // already inside folder
		Path notPack = zip(tmp, "nope.zip", "readme.txt");
		assertThrows(IOException.class, () -> PackImporter.importPack(notPack, packs));
		Path txt = tmp.resolve("x.txt");
		Files.writeString(txt, "hi");
		assertThrows(IOException.class, () -> PackImporter.importPack(txt, packs));
	}

	@Test
	void importsFolderPack(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("mypack");
		Files.createDirectories(dir.resolve("assets/minecraft/textures/block"));
		Files.writeString(dir.resolve("pack.mcmeta"), "{}");
		Files.write(dir.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {9});
		Path dest = PackImporter.importPack(dir, tmp.resolve("rp"));
		assertTrue(Files.isRegularFile(dest.resolve("pack.mcmeta")));
		assertTrue(Files.isRegularFile(dest.resolve("assets/minecraft/textures/block/stone.png")));
	}

	@Test
	void sortsFiltersAndSearches() {
		PackEntry a = entry("file/Alpha.zip", "Alpha", "bright blocks", 100, 5, true);
		PackEntry b = entry("file/beta.zip", "beta", "dark sky", 300, 9, false);
		PackEntry c = entry("file/Gamma.zip", "Gamma", "sounds", 200, 1, true);
		List<PackEntry> all = List.of(c, a, b);
		var on = (java.util.function.Predicate<String>) id -> id.equals("file/Gamma.zip");

		assertEquals(List.of(a, b, c), PackSorter.apply(all, on, "", PackSort.NAME_ASC, PackFilter.ALL));
		assertEquals(List.of(c, b, a), PackSorter.apply(all, on, "", PackSort.NAME_DESC, PackFilter.ALL));
		assertEquals(c, PackSorter.apply(all, on, "", PackSort.ENABLED_FIRST, PackFilter.ALL).get(0));
		assertEquals(b, PackSorter.apply(all, on, "", PackSort.NEWEST, PackFilter.ALL).get(0));
		assertEquals(b, PackSorter.apply(all, on, "", PackSort.LARGEST, PackFilter.ALL).get(0));
		assertEquals(b, PackSorter.apply(all, on, "", PackSort.COMPATIBILITY, PackFilter.ALL).get(0));
		assertEquals(List.of(c), PackSorter.apply(all, on, "", PackSort.NAME_ASC, PackFilter.ENABLED));
		assertEquals(List.of(a, b), PackSorter.apply(all, on, "", PackSort.NAME_ASC, PackFilter.DISABLED));
		assertEquals(List.of(b), PackSorter.apply(all, on, "DARK  sky", PackSort.NAME_ASC, PackFilter.ALL));
		assertEquals(List.of(a), PackSorter.apply(all, on, "alpha.zip", PackSort.NAME_ASC, PackFilter.ALL));
		assertTrue(PackSorter.apply(all, on, "zzz", PackSort.NAME_ASC, PackFilter.ALL).isEmpty());
	}

	@Test
	void sanitizeRepairsBadValues() {
		ModConfig c = new ModConfig();
		c.uiScale = 99f;
		c.lowFireHeight = Float.NaN;
		c.shieldTilt = -500f;
		c.background = null;
		c.packProfiles = null;
		c.accent = 0x1FFFFFFF;
		c.lastPage = " ";
		c.sanitize();
		assertEquals(1.5f, c.uiScale);
		assertEquals(0.10f, c.lowFireHeight);
		assertEquals(-45f, c.shieldTilt);
		assertNotNull(c.background);
		assertNotNull(c.packProfiles);
		assertEquals(0xFFFFFF, c.accent);
		assertEquals("cat:BLOCKS", c.lastPage);
	}
}
