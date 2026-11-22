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
    /** Category follows the base pack. */
    public static final String INHERIT = "";
    /** Category is left out completely (vanilla look). */
    public static final String OFF = "<off>";

    public String basePack = null; // null = kein Basis-Pack
    /** Low Fire: 0 = aus, 1 = mittel, 2 = niedrig. */
    public int lowFire = 0;
    /** Side Shield: 0 = aus, 1 = an (klein beim Blocken), 2 = an (normale Größe beim Blocken). */
    public int sideShield = 0;
    /** Menu background dimming: 0 = clear, 1 = light, 2 = dark. */
    public int dim = 1;
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
        try { s.lowFire = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("lowFire", "0").trim()))); }
        catch (NumberFormatException e) { s.lowFire = 0; }
        String ss = p.getProperty("sideShield", "0").trim();
        if (ss.equalsIgnoreCase("true")) s.sideShield = 1;
        else if (ss.equalsIgnoreCase("false")) s.sideShield = 0;
        else {
            try { s.sideShield = Math.max(0, Math.min(2, Integer.parseInt(ss))); }
            catch (NumberFormatException e) { s.sideShield = 0; }
        }
        try { s.dim = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("dim", "1").trim()))); }
        catch (NumberFormatException e) { s.dim = 1; }
        for (Category c : Category.values()) {
            String v = p.getProperty("cat." + c.id());
            if (v != null && !v.isEmpty()) s.choices.put(c, v);
        }
        return s;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("base", basePack == null ? "" : basePack);
        p.setProperty("lowFire", Integer.toString(lowFire));
        p.setProperty("sideShield", Integer.toString(sideShield));
        p.setProperty("dim", Integer.toString(dim));
        for (Map.Entry<Category, String> e : choices.entrySet()) {
            if (!e.getValue().isEmpty()) p.setProperty("cat." + e.getKey().id(), e.getValue());
        }
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "MurmelsVisuals");
        }
    }
}
