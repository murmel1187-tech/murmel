package de.murmelsvisuals.core;

import java.util.regex.Pattern;

/**
 * Kategorien, aus denen du pro Kategorie ein Quell-Pack wählen kannst.
 * Die Reihenfolge ist wichtig: Der erste passende Eintrag gewinnt
 * (spezifische Kategorien stehen deshalb vor den allgemeinen).
 */
public enum Category {
    CRYSTAL("Crystals", Pat.join(
        Pat.dir("assets/[^/]+/textures/entity/end_crystal"),
        Pat.item("end_crystal"))),
    TOTEM("Totems", Pat.item("totem_of_undying")),
    SWORDS("Swords", Pat.item("(wooden|stone|iron|golden|diamond|netherite|copper)_sword|mace|trident|(.*_)?spear")),
    AXES("Axes", Pat.item("(wooden|stone|iron|golden|diamond|netherite|copper)_axe")),
    TOOLS("Tools", Pat.item("(wooden|stone|iron|golden|diamond|netherite|copper)_(pickaxe|shovel|hoe)")),
    BOWS("Bows & Arrows", Pat.item("bow(_pulling_\\d)?|crossbow(_[a-z_]+)?|arrow|tipped_arrow|spectral_arrow")),
    ARMOR("Armor", Pat.join(
        Pat.dir("assets/[^/]+/textures/entity/equipment"),
        Pat.dir("assets/[^/]+/textures/models/armor"),
        Pat.dir("assets/[^/]+/textures/trims"),
        Pat.dir("assets/[^/]+/equipment"),
        Pat.item(".*_(helmet|chestplate|leggings|boots)|elytra"))),
    SHIELD("Shields", Pat.join(
        Pat.dir("assets/[^/]+/textures/entity/shield"),
        Pat.item("shield(_blocking)?"))),
    POTIONS("Potions", Pat.item("potion|splash_potion|lingering_potion|glass_bottle|experience_bottle")),
    GAPPLE("Golden Apples", Pat.item("golden_apple|enchanted_golden_apple|apple")),
    PEARL("Pearls", Pat.item("ender_pearl|ender_eye|fire_charge|firework_rocket|wind_charge")),
    FIRE("Fire", Pat.of(
        "assets/[^/]+/textures/block/(soul_)?fire_[01]\\.png(\\.mcmeta)?",
        "assets/[^/]+/textures/block/campfire_fire\\.png(\\.mcmeta)?")),
    BLOCKS("Blocks", Pat.join(
        Pat.dir("assets/[^/]+/textures/block"),
        Pat.dir("assets/[^/]+/models/block"),
        Pat.dir("assets/[^/]+/blockstates"))),
    ITEMS("Other Items", Pat.join(
        Pat.dir("assets/[^/]+/textures/item"),
        Pat.dir("assets/[^/]+/models/item"),
        Pat.dir("assets/[^/]+/items"))),
    PARTICLES("Particles", Pat.join(
        Pat.dir("assets/[^/]+/textures/particle"),
        Pat.dir("assets/[^/]+/particles"))),
    HEARTS("Hearts", Pat.join(
        Pat.dir("assets/[^/]+/textures/gui/sprites/hud/heart"),
        Pat.of("assets/[^/]+/textures/gui/sprites/hud/armor_[a-z_]+\\.png(\\.mcmeta)?"))),
    HUNGER("Hunger", Pat.of(
        "assets/[^/]+/textures/gui/sprites/hud/food_[a-z_]+\\.png(\\.mcmeta)?")),
    HOTBAR("Hotbar", Pat.of(
        "assets/[^/]+/textures/gui/sprites/hud/hotbar[a-z_]*\\.png(\\.mcmeta)?",
        "assets/[^/]+/textures/gui/widgets\\.png")),
    EXP("XP Bar", Pat.of(
        "assets/[^/]+/textures/gui/sprites/hud/experience_[a-z_]+\\.png(\\.mcmeta)?",
        "assets/[^/]+/textures/gui/sprites/hud/jump_bar_[a-z_]+\\.png(\\.mcmeta)?")),
    GUI("GUI & Menus", Pat.join(
        Pat.dir("assets/[^/]+/textures/gui"),
        Pat.dir("assets/[^/]+/textures/misc"))),
    CUSTOM_SKY("Custom Skybox", Pat.join(
        Pat.dir("assets/[^/]+/optifine/sky"),
        Pat.dir("assets/[^/]+/mcpatcher/sky"),
        Pat.dir("assets/[^/]+/sky"),
        Pat.dir("assets/[^/]+/textures/sky"),
        Pat.dir("assets/fabricskyboxes"))),
    SKY("Sky & Weather", Pat.join(
        Pat.dir("assets/[^/]+/textures/environment"),
        Pat.dir("assets/[^/]+/textures/colormap"))),
    FONT("Fonts", Pat.join(
        Pat.dir("assets/[^/]+/font"),
        Pat.dir("assets/[^/]+/textures/font"))),
    SOUNDS("Sounds", Pat.join(
        Pat.dir("assets/[^/]+/sounds"),
        Pat.of("assets/[^/]+/sounds\\.json"))),
    MOBS("Mobs", Pat.dir("assets/[^/]+/textures/entity"));

    private final String label;
    private final Pattern[] patterns;

    Category(String label, String[] regexes) {
        this.label = label;
        this.patterns = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) patterns[i] = Pattern.compile(regexes[i]);
    }

    public String label() { return label; }

    public String id() { return name().toLowerCase(); }

    public boolean matches(String lowerPath) {
        for (Pattern p : patterns) if (p.matcher(lowerPath).matches()) return true;
        return false;
    }

    /** Erste passende Kategorie oder null, wenn der Pfad zu keiner gehört. */
    public static Category of(String path) {
        String lower = path.toLowerCase();
        for (Category c : values()) if (c.matches(lower)) return c;
        return null;
    }

    public static Category byId(String id) {
        for (Category c : values()) if (c.id().equals(id)) return c;
        return null;
    }
}
