package de.murmelsvisuals.client;

import de.murmelsvisuals.core.Category;
import de.murmelsvisuals.core.MixSettings;
import de.murmelsvisuals.core.PackMixer;
import de.murmelsvisuals.core.PackScanner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MixScreen extends Screen {
    private final Screen parent;
    private final Path packsDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("murmelsvisuals.properties");
    private final MixSettings settings = MixSettings.load(settingsFile);

    private List<String> packs = List.of();
    private StringWidget status;

    public MixScreen(Screen parent) {
        super(Component.literal("MurmelsVisuals - Pack-Mixer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        try {
            packs = PackScanner.listPackIds(packsDir);
        } catch (IOException e) {
            packs = List.of();
        }

        addRenderableWidget(new StringWidget(0, 8, this.width, 9, this.title, this.font).alignCenter());

        final int cols = 4, gap = 4;
        int w = Math.min(130, (this.width - 20 - gap * (cols - 1)) / cols);
        int total = cols * w + gap * (cols - 1);
        int x0 = (this.width - total) / 2;
        int y = 24;

        // Basis-Pack (alles, was keiner Kategorie einzeln zugeordnet ist, kommt von hier)
        Button base = Button.builder(baseLabel(total - 12), b -> {
            settings.basePack = next(settings.basePack);
            b.setMessage(baseLabel(total - 12));
        }).bounds(x0, y, total, 20).build();
        addRenderableWidget(base);
        y += 26;

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            final Category cat = cats[i];
            final int bw = w - 8;
            Button b = Button.builder(catLabel(cat, bw), btn -> {
                settings.choices.put(cat, nextChoice(settings.choiceFor(cat)));
                btn.setMessage(catLabel(cat, bw));
            }).bounds(x0 + (i % cols) * (w + gap), y + (i / cols) * 24, w, 20).build();
            addRenderableWidget(b);
        }
        int rows = (cats.length + cols - 1) / cols;
        int sy = y + rows * 24 + 4;

        status = new StringWidget(0, sy, this.width, 9,
                Component.literal(packs.isEmpty()
                        ? "Keine Packs in .minecraft/resourcepacks gefunden."
                        : packs.size() + " Packs gefunden. Klick = nächstes Pack."),
                this.font).alignCenter();
        addRenderableWidget(status);

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("Anwenden"), b -> apply())
                .bounds(this.width / 2 - 102, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(this.width / 2 + 2, by, 100, 20).build());
    }

    // ---- Auswahl durchschalten ----

    /** Basis: null -> Pack 1 -> Pack 2 -> ... -> null */
    private String next(String current) {
        if (packs.isEmpty()) return null;
        if (current == null) return packs.get(0);
        int i = packs.indexOf(current);
        return (i < 0 || i + 1 >= packs.size()) ? null : packs.get(i + 1);
    }

    /** Kategorie: Basis -> Aus -> Pack 1 -> Pack 2 -> ... -> Basis */
    private String nextChoice(String current) {
        if (current.equals(MixSettings.INHERIT)) return MixSettings.OFF;
        if (current.equals(MixSettings.OFF)) return packs.isEmpty() ? MixSettings.INHERIT : packs.get(0);
        int i = packs.indexOf(current);
        return (i < 0 || i + 1 >= packs.size()) ? MixSettings.INHERIT : packs.get(i + 1);
    }

    private Component baseLabel(int maxWidth) {
        String name = settings.basePack == null ? "keins" : shortName(settings.basePack);
        return Component.literal(this.font.plainSubstrByWidth("Basis-Pack: " + name, maxWidth));
    }

    private Component catLabel(Category cat, int maxWidth) {
        String ch = settings.choiceFor(cat);
        String v = ch.equals(MixSettings.INHERIT) ? "Basis" : ch.equals(MixSettings.OFF) ? "Aus" : shortName(ch);
        return Component.literal(this.font.plainSubstrByWidth(cat.label() + ": " + v, maxWidth));
    }

    private static String shortName(String id) {
        return id.toLowerCase().endsWith(".zip") ? id.substring(0, id.length() - 4) : id;
    }

    // ---- Mischen & aktivieren ----

    private void apply() {
        final Minecraft mc = this.minecraft;
        status.setMessage(Component.literal("Mische Packs ..."));
        CompletableFuture.supplyAsync(() -> {
            try {
                settings.save(settingsFile);
                return PackMixer.build(packsDir, settings, VersionInfo.PACK_FORMAT);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> mc.execute(() -> {
            if (error != null) {
                Throwable t = error.getCause() != null ? error.getCause() : error;
                status.setMessage(Component.literal("Fehler: " + t.getMessage()));
                return;
            }
            enableAndReload(mc);
            status.setMessage(Component.literal(result.filesCopied() + " Dateien gemischt und aktiviert."));
        }));
    }

    private void enableAndReload(Minecraft mc) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String id = "file/" + PackScanner.OUTPUT_NAME;
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        List<String> after = new ArrayList<>(before);
        after.remove(id);
        after.add(id); // ganz oben = höchste Priorität
        repo.setSelected(after);
        mc.options.updateResourcePacks(repo);
        if (after.equals(before)) {
            mc.reloadResourcePacks(); // Pack war schon aktiv, Inhalt hat sich aber geändert
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
