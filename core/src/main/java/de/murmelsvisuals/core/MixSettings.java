package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Deine Auswahl: ein Basis-Pack plus pro Kategorie ein Quell-Pack. */
public final class MixSettings {
    /** Kategorie folgt dem Basis-Pack. */
    public static final String INHERIT = "";
    /** Kategorie wird komplett weggelassen (Vanilla-Aussehen). */
    public static final String OFF = "<off>";

    public String basePack = null; // null = kein Basis-Pack
    public final Map<Category, String> choices = new EnumMap<>(Category.class);

    public String choiceFor(Category c) {
        return choices.getOrDefault(c, INHERIT);
    }

    /** Welches Pack liefert diese Kategorie? null = keines (Vanilla). */
    public String effectiveSource(Category c) {
        String ch = choiceFor(c);
        if (ch.equals(OFF)) return null;
        if (ch.equals(INHERIT)) return basePack;
        return ch;
    }

    public static MixSettings load(Path file) {
        MixSettings s = new MixSettings();
        if (!Files.isRegularFile(file)) return s;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return s;
        }
        String base = p.getProperty("base", "");
        s.basePack = base.isEmpty() ? null : base;
        for (Category c : Category.values()) {
            String v = p.getProperty("cat." + c.id());
            if (v != null && !v.isEmpty()) s.choices.put(c, v);
        }
        return s;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("base", basePack == null ? "" : basePack);
        for (Map.Entry<Category, String> e : choices.entrySet()) {
            if (!e.getValue().isEmpty()) p.setProperty("cat." + e.getKey().id(), e.getValue());
        }
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "MurmelsVisuals");
        }
    }
}
