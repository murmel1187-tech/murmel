package com.murmelsvisuals.pack;

/** The twelve GUI categories. Also used to describe which assets a resource pack replaces. */
public enum PackCategory {
	BLOCKS("Blocks", "Block textures, models and block states."),
	ITEMS("Items", "Item textures and item models."),
	POTIONS("Potions", "Potion bottles, tipped arrows and status effect icons."),
	WEAPONS("Weapons", "Swords, bows, tridents, maces, shields and arrows."),
	TOOLS("Tools", "Pickaxes, axes, shovels, hoes, shears, rods and utility items."),
	ARMOR("Armor", "Armor pieces, trims, elytra and equipment models."),
	SKY("Sky", "Sun, moon, clouds, the End sky and custom skyboxes."),
	ENVIRONMENT("Environment", "Rain, snow, colour maps, water, lava and other world atmosphere."),
	PARTICLES("Particles", "Particle textures and particle definitions."),
	GUI("GUI", "Menus, inventory screens, widgets and fonts."),
	HUD("HUD", "Hotbar, health, hunger, experience and other in-game overlays."),
	SOUNDS("Sounds", "Sound events and audio files.");

	public final String title;
	public final String description;

	PackCategory(String title, String description) {
		this.title = title;
		this.description = description;
	}
}
