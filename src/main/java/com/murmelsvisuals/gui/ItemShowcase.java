package com.murmelsvisuals.gui;

import com.murmelsvisuals.pack.PackCategory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Items shown in the category pages' 3D preview. Block items are real 3D models drawn with the game's
 * GUI item renderer (isometric); the large preview adds bobbing and mouse parallax on top.
 */
final class ItemShowcase {
	private ItemShowcase() {}

	private static final Map<PackCategory, List<ItemStack>> PLAIN = new EnumMap<>(PackCategory.class);
	private static final Map<PackCategory, List<ItemStack>> GLINT = new EnumMap<>(PackCategory.class);

	private static List<Item> items(PackCategory c) {
		return switch (c) {
			case BLOCKS -> List.of(Items.GRASS_BLOCK, Items.STONE, Items.OAK_PLANKS, Items.DIAMOND_ORE, Items.BOOKSHELF,
					Items.TNT, Items.GLASS, Items.CRAFTING_TABLE, Items.FURNACE, Items.SAND);
			case ITEMS -> List.of(Items.APPLE, Items.GOLDEN_APPLE, Items.DIAMOND, Items.EMERALD, Items.ENDER_PEARL,
					Items.BOOK, Items.COMPASS, Items.CLOCK, Items.BREAD, Items.NETHER_STAR);
			case POTIONS -> List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.GLASS_BOTTLE,
					Items.EXPERIENCE_BOTTLE, Items.TIPPED_ARROW);
			case WEAPONS -> List.of(Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT,
					Items.MACE, Items.SHIELD, Items.ARROW);
			case TOOLS -> List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
					Items.SHEARS, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.SPYGLASS);
			case ARMOR -> List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
					Items.NETHERITE_CHESTPLATE, Items.ELYTRA, Items.TURTLE_HELMET, Items.IRON_CHESTPLATE);
			default -> List.of();
		};
	}

	static boolean has(PackCategory c) {
		return !items(c).isEmpty();
	}

	/** Cached stacks for the category; the glint variant forces the enchantment shimmer on. */
	static List<ItemStack> stacks(PackCategory c, boolean glint) {
		Map<PackCategory, List<ItemStack>> cache = glint ? GLINT : PLAIN;
		return cache.computeIfAbsent(c, k -> {
			List<ItemStack> out = new ArrayList<>();
			for (Item it : items(k)) {
				ItemStack s = new ItemStack(it);
				if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
				out.add(s);
			}
			return out;
		});
	}

	/** Clear caches (called when the screen closes so no stale stacks survive a registry reload). */
	static void clear() {
		PLAIN.clear();
		GLINT.clear();
	}
}
