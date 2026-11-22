package de.murmelsvisuals.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Findet alle Resource Packs in einem oder mehreren Ordnern. */
public final class PackScanner {
    private PackScanner() {}

    /** Name des Ordners, den die Mod selbst erzeugt (wird beim Scannen ausgelassen). */
    public static final String OUTPUT_NAME = "MurmelsVisuals_Mix";

    public static List<String> listPackIds(List<Path> dirs) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (name.equals(OUTPUT_NAME)) continue;
                    boolean zip = Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                    boolean folder = Files.isDirectory(p) && Files.isDirectory(p.resolve("assets"));
                    if (zip || folder) ids.add(name);
                }
            }
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /** Öffnet das Pack aus dem ersten Ordner, in dem es liegt. */
    public static PackSource open(List<Path> dirs, String id) throws IOException {
        for (Path dir : dirs) {
            Path p = dir.resolve(id).normalize();
            if (!p.startsWith(dir.normalize())) throw new IOException("Invalid pack name: " + id);
            if (Files.isDirectory(p)) return new DirPackSource(p);
            if (Files.isRegularFile(p)) return new ZipPackSource(p);
        }
        throw new IOException("Pack not found: " + id);
    }
}
