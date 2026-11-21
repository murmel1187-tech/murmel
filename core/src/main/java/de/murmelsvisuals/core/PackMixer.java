package de.murmelsvisuals.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/** Baut aus mehreren Packs ein einziges neues Pack (als Ordner im resourcepacks-Verzeichnis). */
public final class PackMixer {
    private PackMixer() {}

    private static final String MARKER = ".murmelsvisuals";

    public record Result(int filesCopied, Path outputDir, List<String> warnings) {}

    /**
     * @param resourcepacksDir  Ordner mit deinen Packs
     * @param settings          Auswahl pro Kategorie
     * @param packFormat        Resource-Pack-Format der laufenden Version (1.21.11 = 75, 26.3 = 97)
     */
    public static Result build(Path resourcepacksDir, MixSettings settings, int packFormat) throws IOException {
        Path out = resourcepacksDir.resolve(PackScanner.OUTPUT_NAME).normalize();
        prepareOutput(out);

        Set<String> needed = new LinkedHashSet<>();
        if (settings.basePack != null) needed.add(settings.basePack);
        for (Category c : Category.values()) {
            String src = settings.effectiveSource(c);
            if (src != null) needed.add(src);
        }

        int copied = 0;
        for (String packId : needed) {
            try (PackSource pack = PackScanner.open(resourcepacksDir, packId)) {
                for (String path : pack.files()) {
                    Category cat = Category.of(path);
                    String wanted = cat == null ? settings.basePack : settings.effectiveSource(cat);
                    if (!packId.equals(wanted)) continue;

                    Path target = out.resolve(path).normalize();
                    if (!target.startsWith(out)) continue; // Schutz vor ../-Pfaden in fremden ZIPs
                    Files.createDirectories(target.getParent());
                    try (InputStream in = pack.open(path)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    copied++;
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        if (settings.lowFire > 0) {
            String w = LowFire.apply(out, settings.lowFire);
            if (w != null) warnings.add(w);
        }
        if (settings.sideShield) {
            SideShield.write(out,
                    SideShield.clean(settings.shieldLeft, "22,-2,-11"),
                    SideShield.clean(settings.shieldRight, "-22,-2,-11"));
        }

        String mcmeta = "{\"pack\":{\"description\":\"MurmelsVisuals Mix\","
                + "\"min_format\":" + packFormat + ",\"max_format\":" + packFormat + "}}";
        Files.writeString(out.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
        return new Result(copied, out, warnings);
    }

    private static void prepareOutput(Path out) throws IOException {
        if (Files.exists(out)) {
            // Nur löschen, was wir selbst erzeugt haben.
            if (!Files.exists(out.resolve(MARKER))) {
                throw new IOException("Ordner " + out.getFileName()
                        + " existiert schon und stammt nicht von MurmelsVisuals – bitte umbenennen.");
            }
            try (Stream<Path> s = Files.walk(out)) {
                s.sorted(Comparator.reverseOrder())
                 .filter(p -> !p.equals(out))
                 .forEach(p -> p.toFile().delete());
            }
        } else {
            Files.createDirectories(out);
        }
        Files.writeString(out.resolve(MARKER), "Von MurmelsVisuals erzeugt. Wird bei jedem Anwenden neu geschrieben.");
    }
}
