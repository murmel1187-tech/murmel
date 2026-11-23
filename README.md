# MurmelsVisuals

Client-side Fabric mod: mix textures from several resource packs into **one** pack, right in game.

## How to use
1. Put your packs (ZIP files or folders) into `.minecraft/murmelspacks`
   (this folder is only seen by the mod, so Minecraft's normal pack list stays clean) or into `.minecraft/resourcepacks`.
   The **Open Folder** button in the menu opens `murmelspacks` for you.
2. In a world, press **M** (rebindable under Options > Controls) or type `/murmelsvisuals`.
3. Pick a **Base** pack (it provides everything you do not assign on its own).
4. Choose a category on the left (Crystals, Totems, Swords, Armor, Hearts, Hotbar, ...) and click the pack you want for it.
   `Base` follows the base pack, `Off` keeps the vanilla look. Scroll with the mouse wheel.
5. **Apply** creates the pack `MurmelsVisuals_Mix`, enables it on top and reloads. Your original packs are never changed.

## Previews
Every field shows the real picture from the pack. Items that have a 3D model in the pack (for example the end crystal in the xyzoom packs) are drawn as a 3D render like in the inventory. Pictures load in the background. If a pack has nothing custom for a category, the vanilla look is shown instead. Crystals are drawn like in the world (three tilted cubes with the pack's entity texture).

## Extras
- **Enchant Glint** category: pick the glint look (item, entity and armor glint) from any pack.
- **Low Fire**: Off / Medium / Low. Cuts the top of the first-person fire overlay.
- **Side Shield**: Off / Small / Normal. The shield rests at the side in first person and is smaller while blocking.
- **Dim**: how dark the menu background is (Off = completely clear).

## Building
Needs JDK 21 (1.21.11) or JDK 25 (26.3) and Gradle 9.6.0.

    cd mc-1.21.11 && gradle wrapper --gradle-version 9.6.0 && ./gradlew build
    cd mc-26.3    && gradle wrapper --gradle-version 9.6.0 && ./gradlew build

The jar ends up in `build/libs/` (use the one without `-sources`).

## Notes
- Resource pack format: 1.21.11 = 75, 26.3 = 97 (see `VersionInfo.java` in each version folder).
- No third-party textures are included. Respect the licenses of the packs you mix and do not redistribute the mixed pack.
