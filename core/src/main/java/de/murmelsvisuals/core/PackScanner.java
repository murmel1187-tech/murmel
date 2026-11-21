package de.murmelsvisuals.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Findet alle Resource Packs im resourcepacks-Ordner. */
public final class PackScanner {
    private PackScanner() {}

    /** Name des Ordners, den die Mod selbst erzeugt (wird beim Scannen ausgelassen). */
    public static final String OUTPUT_NAME = "MurmelsVisuals_Mix";

    public static List<String> listPackIds(Path resourcepacksDir) throws IOException {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(resourcepacksDir)) return ids;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(resourcepacksDir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.equals(OUTPUT_NAME)) continue;
                boolean zip = Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                boolean dir = Files.isDirectory(p) && Files.isDirectory(p.resolve("assets"));
                if (zip || dir) ids.add(name);
            }
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    public static PackSource open(Path resourcepacksDir, String id) throws IOException {
        Path p = resourcepacksDir.resolve(id).normalize();
        if (!p.startsWith(resourcepacksDir.normalize())) throw new IOException("Ungültiger Pack-Name: " + id);
        if (Files.isDirectory(p)) return new DirPackSource(p);
        return new ZipPackSource(p);
    }
}
