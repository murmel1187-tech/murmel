package de.murmelsvisuals.client;

import de.murmelsvisuals.core.Category;
import de.murmelsvisuals.core.MixSettings;
import de.murmelsvisuals.core.PackMixer;
import de.murmelsvisuals.core.PackScanner;
import de.murmelsvisuals.core.PackSource;
import de.murmelsvisuals.core.Preview;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MixScreen extends Screen {
    private static final int LINE_H = 9;
    private static final String BLOCK = "\u2588";

    private final Screen parent;
    private final Path packsDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("murmelsvisuals.properties");
    private final MixSettings settings = MixSettings.load(settingsFile);

    private List<String> packs = List.of();
    private StringWidget status;
    private String statusText = null; // null = Standardtext (Seite x/y)
    private int catIndex = 0;
    private int page = 0;
    private final Map<String, int[]> previewCache = new HashMap<>();

    public MixScreen(Screen parent) {
        super(Component.literal("MurmelsVisuals - Pack-Mixer"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        try {
            packs = PackScanner.listPackIds(packsDir);
        } catch (IOException e) {
            packs = List.of();
        }

        final Category[] cats = Category.values();
        final Category cat = cats[catIndex];
        final int cx = this.width / 2;

        addRenderableWidget(centeredText(6, this.title));

        // Basis-Pack
        addRenderableWidget(Button.builder(baseLabel(190), b -> {
            settings.basePack = nextBase(settings.basePack);
            rebuildWidgets();
        }).bounds(cx - 100, 20, 200, 20).build());

        // Schalter links und rechts neben dem Basis-Pack-Button
        Button shieldBtn = Button.builder(shieldLabel(), b -> {
            settings.sideShield = !settings.sideShield;
            b.setMessage(shieldLabel());
        }).bounds(cx - 100 - 104, 20, 100, 20).build();
        addRenderableWidget(shieldBtn);
        Button fireBtn = Button.builder(fireLabel(), b -> {
            settings.lowFire = (settings.lowFire + 1) % 3;
            b.setMessage(fireLabel());
        }).bounds(cx + 100 + 4, 20, 100, 20).build();
        addRenderableWidget(fireBtn);

        // Kategorie wechseln:  <  [Name]  >
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            catIndex = (catIndex + cats.length - 1) % cats.length;
            page = 0;
            rebuildWidgets();
        }).bounds(cx - 100, 44, 20, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(cat.label() + "  (" + (catIndex + 1) + "/" + cats.length + ")"), b -> {
            catIndex = (catIndex + 1) % cats.length;
            page = 0;
            rebuildWidgets();
        }).bounds(cx - 78, 44, 156, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            catIndex = (catIndex + 1) % cats.length;
            page = 0;
            rebuildWidgets();
        }).bounds(cx + 80, 44, 20, 20).build());

        // Auswahlfelder: Basis, Aus, dann alle Packs
        List<String> options = new ArrayList<>();
        options.add(MixSettings.INHERIT);
        options.add(MixSettings.OFF);
        options.addAll(packs);

        int top = 72;
        int bottom = this.height - 46;
        int res = Math.max(6, Math.min(16, (bottom - top - 22) / LINE_H));
        int blockW = Math.max(1, this.font.width(BLOCK));
        int cpp = Math.max(1, Math.round(9f / blockW)); // Zeichen pro Bildpunkt, damit er etwa quadratisch wirkt
        int pxW = cpp * blockW;
        int cellW = res * pxW + 8;
        int cellH = res * LINE_H + 8 + 12;
        int cols = Math.max(1, (this.width - 56) / (cellW + 4));
        int pages = Math.max(1, (options.size() + cols - 1) / cols);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * cols;
        int to = Math.min(options.size(), from + cols);
        int shown = to - from;
        int x0 = cx - (shown * cellW + (shown - 1) * 4) / 2;

        String current = settings.choiceFor(cat);
        for (int i = from; i < to; i++) {
            final String value = options.get(i);
            int x = x0 + (i - from) * (cellW + 4);

            addRenderableWidget(Button.builder(Component.empty(), b -> {
                settings.choices.put(cat, value);
                rebuildWidgets();
            }).bounds(x, top, cellW, cellH).build());

            // Welches Pack liefert das Bild für dieses Feld?
            String previewPack = value.equals(MixSettings.OFF) ? null
                    : value.equals(MixSettings.INHERIT) ? settings.basePack : value;
            int[] px = previewPack == null ? null : preview(cat, previewPack, res);

            if (px != null) {
                for (int r = 0; r < res; r++) {
                    MutableComponent row = Component.empty();
                    for (int c = 0; c < res; c++) {
                        int p = px[r * res + c];
                        boolean solid = (p >>> 24) >= 100;
                        int rgb = solid ? (p & 0xFFFFFF) : 0x262626;
                        row.append(Component.literal(BLOCK.repeat(cpp)).withStyle(Style.EMPTY.withColor(rgb)));
                    }
                    addRenderableWidget(new StringWidget(x + (cellW - res * pxW) / 2, top + 4 + r * LINE_H,
                            res * pxW, LINE_H, row, this.font));
                }
            } else {
                String hint = value.equals(MixSettings.OFF) ? "Vanilla"
                        : (previewPack == null ? "-" : "kein Vorschaubild");
                Component hc = Component.literal(this.font.plainSubstrByWidth(hint, cellW - 6))
                        .withStyle(Style.EMPTY.withColor(0xAAAAAA));
                int hw = this.font.width(hc);
                addRenderableWidget(new StringWidget(x + (cellW - hw) / 2, top + cellH / 2 - 10, hw, LINE_H, hc, this.font));
            }

            boolean selected = current.equals(value);
            String name = value.equals(MixSettings.INHERIT) ? "Basis"
                    : value.equals(MixSettings.OFF) ? "Aus" : shortName(value);
            String text = this.font.plainSubstrByWidth((selected ? "\u2714 " : "") + name, cellW - 6);
            Component nc = Component.literal(text).withStyle(Style.EMPTY.withColor(selected ? 0x55FF55 : 0xFFFFFF));
            int nw = this.font.width(nc);
            addRenderableWidget(new StringWidget(x + (cellW - nw) / 2, top + cellH - 12, nw, LINE_H, nc, this.font));
        }

        // Status
        status = centeredText(this.height - 38, Component.literal(
                statusText != null ? statusText
                : packs.isEmpty() ? "Keine Packs in .minecraft/resourcepacks gefunden."
                : "Seite " + (page + 1) + "/" + pages + "  -  " + packs.size() + " Packs"));
        addRenderableWidget(status);

        // Unten: Seite zurück | Anwenden | Fertig | Seite vor
        int by = this.height - 26;
        final int lastPage = pages - 1;
        Button prev = Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(cx - 108, by, 20, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        addRenderableWidget(Button.builder(Component.literal("Anwenden"), b -> apply())
                .bounds(cx - 84, by, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx + 4, by, 80, 20).build());
        Button next = Button.builder(Component.literal(">"), b -> {
            page = Math.min(lastPage, page + 1);
            rebuildWidgets();
        }).bounds(cx + 88, by, 20, 20).build();
        next.active = page < lastPage;
        addRenderableWidget(next);
    }

    // ------------------------------------------------------------------ Helfer

    private StringWidget centeredText(int y, Component text) {
        int tw = this.font.width(text);
        return new StringWidget((this.width - tw) / 2, y, tw, 9, text, this.font);
    }

    private void setStatus(String text) {
        statusText = text;
        Component c = Component.literal(text);
        int tw = this.font.width(c);
        status.setMessage(c);
        status.setWidth(tw);
        status.setX((this.width - tw) / 2);
    }

    private int[] preview(Category cat, String packId, int res) {
        String key = cat.id() + "|" + packId + "|" + res;
        if (previewCache.containsKey(key)) return previewCache.get(key);
        int[] px = null;
        try (PackSource src = PackScanner.open(packsDir, packId)) {
            px = Preview.load(src, cat, res);
        } catch (Exception ignored) {
            // Pack nicht lesbar -> kein Vorschaubild
        }
        previewCache.put(key, px);
        return px;
    }

    /** Basis: kein Pack -> Pack 1 -> Pack 2 -> ... -> kein Pack */
    private String nextBase(String current) {
        if (packs.isEmpty()) return null;
        if (current == null) return packs.get(0);
        int i = packs.indexOf(current);
        return (i < 0 || i + 1 >= packs.size()) ? null : packs.get(i + 1);
    }

    private Component baseLabel(int maxWidth) {
        String name = settings.basePack == null ? "keins" : shortName(settings.basePack);
        return Component.literal(this.font.plainSubstrByWidth("Basis-Pack: " + name, maxWidth));
    }

    private Component shieldLabel() {
        return Component.literal("Side Shield: " + (settings.sideShield ? "An" : "Aus"));
    }

    private Component fireLabel() {
        String v = settings.lowFire == 0 ? "Aus" : settings.lowFire == 1 ? "Mittel" : "Niedrig";
        return Component.literal("Low Fire: " + v);
    }

    private static String shortName(String id) {
        return id.toLowerCase().endsWith(".zip") ? id.substring(0, id.length() - 4) : id;
    }

    // ------------------------------------------------------------------ Mischen & aktivieren

    private void apply() {
        final Minecraft mc = this.minecraft;
        setStatus("Mische Packs ...");
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
                setStatus("Fehler: " + t.getMessage());
                return;
            }
            enableAndReload(mc);
            setStatus(result.warnings().isEmpty()
                    ? result.filesCopied() + " Dateien gemischt und aktiviert."
                    : "Aktiviert, aber: " + result.warnings().get(0));
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
        Compat.openScreen(this.minecraft, parent);
    }
}
