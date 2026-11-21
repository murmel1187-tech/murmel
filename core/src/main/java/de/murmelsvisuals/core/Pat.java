package de.murmelsvisuals.core;

/** Kleine Helfer zum Bauen der Regex-Muster (Pfade sind immer klein geschrieben, mit "/"). */
final class Pat {
    private Pat() {}

    /** Item-Textur, Item-Modell und Item-Definition für Items, deren Name auf nameRegex passt. */
    static String[] item(String nameRegex) {
        return new String[] {
            "assets/[^/]+/textures/item/(" + nameRegex + ")(_\\d+)?\\.png(\\.mcmeta)?",
            "assets/[^/]+/models/item/(" + nameRegex + ")\\.json",
            "assets/[^/]+/items/(" + nameRegex + ")\\.json"
        };
    }

    /** Alles unterhalb eines Ordners. */
    static String[] dir(String path) {
        return new String[] { path + "/.*" };
    }

    static String[] of(String... regexes) {
        return regexes;
    }

    static String[] join(String[]... groups) {
        int n = 0;
        for (String[] g : groups) n += g.length;
        String[] out = new String[n];
        int i = 0;
        for (String[] g : groups) for (String s : g) out[i++] = s;
        return out;
    }
}
