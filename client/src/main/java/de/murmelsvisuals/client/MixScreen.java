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
import java.nio.file.Files;
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
    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private final Path packsDir = gameDir.resolve("resourcepacks");
    /** Eigener Ordner nur für diese Mod: Packs hier tauchen nicht in Minecrafts normaler Pack-Liste auf. */
    private final Path extraDir = gameDir.resolve("murmelspacks");
    private final List<Path> packDirs = List.of(packsDir, extraDir);
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("murmelsvisuals.properties");
    private final MixSettings settings = MixSettings.load(settingsFile);

    private List<String> packs = List.of();
    private StringWidget status;
    private String statusText = null; // null = Standardtext
    private int centerX = 0;          // Mitte des Gitterbereichs (für den Status)
    private int sidebarW = 120;
    private int catIndex = 0;
    private int catScroll = 0;        // Scrollposition der Kategorie-Liste links
    private int rowScroll = 0;        // Scrollposition des Pack-Gitters (in Zeilen)
    private final Map<String, int[]> previewCache = new HashMap<>();

    public MixScreen(Screen parent) {
        super(Component.literal("Pack-Mixer"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        try {
            Files.createDirectories(extraDir);
        } catch (IOException ignored) {
            // nicht schlimm, dann gibt es den Zusatzordner eben nicht
        }
        try {
            packs = PackScanner.listPackIds(packDirs);
        } catch (IOException e) {
            packs = List.of();
        }

        MenuBlur.disable(this.minecraft.options); // Hintergrund klar statt unscharf

        final Category[] cats = Category.values();
        final Category cat = cats[catIndex];

        sidebarW = Math.max(90, Math.min(130, this.width / 5));
        int gx0 = sidebarW + 16;          // Gitterbereich links
        int gx1 = this.width - 30;        // Gitterbereich rechts (rechts daneben: Scroll-Knöpfe)
        int gw = gx1 - gx0;
        centerX = gx0 + gw / 2;

        addRenderableWidget(centerText(centerX, 6, this.title));

        // ---- Schalter oben: Side Shield | Basis-Pack | Low Fire
        int bw = (gw - 8) / 3;
        addRenderableWidget(Button.builder(shieldLabel(), b -> {
            settings.sideShield = !settings.sideShield;
            b.setMessage(shieldLabel());
        }).bounds(gx0, 20, bw, 20).build());
        addRenderableWidget(Button.builder(baseLabel(bw - 10), b -> {
            settings.basePack = nextBase(settings.basePack);
            rebuildWidgets();
        }).bounds(gx0 + bw + 4, 20, bw, 20).build());
        addRenderableWidget(Button.builder(fireLabel(), b -> {
            settings.lowFire = (settings.lowFire + 1) % 3;
            b.setMessage(fireLabel());
        }).bounds(gx0 + 2 * (bw + 4), 20, bw, 20).build());

        // ---- Kategorien links am Rand (scrollbar)
        // Titel oben in der Seitenleiste
        Component brand = Component.literal("MurmelsVisuals")
                .withStyle(Style.EMPTY.withBold(true).withColor(0xC9A8FF));
        if (this.font.width(brand) > sidebarW) {
            brand = Component.literal("Murmels").withStyle(Style.EMPTY.withBold(true).withColor(0xC9A8FF));
        }
        addRenderableWidget(centerText(8 + sidebarW / 2, 10, brand));

        int sTop = 26;
        int sBottom = this.height - 8;
        int itemH = 18;
        int step = itemH + 2;
        int fit = Math.max(1, (sBottom - sTop) / step);
        boolean overflow = cats.length > fit;
        int listFit = overflow ? Math.max(1, fit - 2) : fit; // bei Überlauf zwei Plätze für die Pfeile
        int maxCatScroll = Math.max(0, cats.length - listFit);
        catScroll = Math.max(0, Math.min(catScroll, maxCatScroll));
        int sy = sTop;
        if (overflow) {
            Button up = Button.builder(Component.literal("^"), b -> {
                catScroll = Math.max(0, catScroll - 3);
                rebuildWidgets();
            }).bounds(8, sy, sidebarW, itemH).build();
            up.active = catScroll > 0;
            addRenderableWidget(up);
            sy += step;
        }
        for (int i = catScroll; i < Math.min(cats.length, catScroll + listFit); i++) {
            final int idx = i;
            Button cb = Button.builder(
                    Component.literal(this.font.plainSubstrByWidth(cats[i].label(), sidebarW - 10)), b -> {
                catIndex = idx;
                rowScroll = 0;
                rebuildWidgets();
            }).bounds(8, sy, sidebarW, itemH).build();
            cb.active = i != catIndex; // gewählte Kategorie ist ausgegraut
            addRenderableWidget(cb);
            sy += step;
        }
        if (overflow) {
            Button down = Button.builder(Component.literal("v"), b -> {
                catScroll = Math.min(maxCatScroll, catScroll + 3);
                rebuildWidgets();
            }).bounds(8, sBottom - itemH, sidebarW, itemH).build();
            down.active = catScroll < maxCatScroll;
            addRenderableWidget(down);
        }

        // ---- Überschrift
        addRenderableWidget(centerText(centerX, 46,
                Component.literal(cat.label() + "  -  " + packs.size() + " Packs")));

        // ---- Auswahlfelder: Basis, Aus, dann alle Packs
        List<String> options = new ArrayList<>();
        options.add(MixSettings.INHERIT);
        options.add(MixSettings.OFF);
        options.addAll(packs);

        int top = 60;
        int bottom = this.height - 48;
        int blockW = Math.max(1, this.font.width(BLOCK));
        int cpp = Math.max(1, Math.round(9f / blockW)); // Zeichen pro Bildpunkt, damit er etwa quadratisch wirkt
        int pxW = cpp * blockW;

        // Größte Vorschau wählen, bei der mindestens 6 Felder gleichzeitig sichtbar sind
        int res = 6, cols = 1, rows = 1, cellW = 0, cellH = 0;
        for (int r = 20; r >= 6; r--) {
            int cw = r * pxW + 8;
            int ch = r * LINE_H + 8 + 12;
            int c = Math.max(1, (gw + 4) / (cw + 4));
            int rw = Math.max(1, (bottom - top + 4) / (ch + 4));
            res = r; cols = c; rows = rw; cellW = cw; cellH = ch;
            if (c * rw >= 6) break;
        }
        int totalRows = Math.max(1, (options.size() + cols - 1) / cols);
        int maxRowScroll = Math.max(0, totalRows - rows);
        rowScroll = Math.max(0, Math.min(rowScroll, maxRowScroll));
        int from = rowScroll * cols;
        int to = Math.min(options.size(), from + cols * rows);
        int usedCols = Math.min(cols, options.size());
        int x0 = gx0 + (gw - (usedCols * cellW + (usedCols - 1) * 4)) / 2;

        String current = settings.choiceFor(cat);
        for (int i = from; i < to; i++) {
            final String value = options.get(i);
            int k = i - from;
            int x = x0 + (k % cols) * (cellW + 4);
            int y = top + (k / cols) * (cellH + 4);

            addRenderableWidget(Button.builder(Component.empty(), b -> {
                settings.choices.put(cat, value);
                rebuildWidgets();
            }).bounds(x, y, cellW, cellH).build());

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
                    addRenderableWidget(new StringWidget(x + (cellW - res * pxW) / 2, y + 4 + r * LINE_H,
                            res * pxW, LINE_H, row, this.font));
                }
            } else {
                String hint = value.equals(MixSettings.OFF) ? "Vanilla"
                        : (previewPack == null ? "-" : "kein Bild");
                Component hc = Component.literal(this.font.plainSubstrByWidth(hint, cellW - 6))
                        .withStyle(Style.EMPTY.withColor(0xAAAAAA));
                int hw = this.font.width(hc);
                addRenderableWidget(new StringWidget(x + (cellW - hw) / 2, y + cellH / 2 - 10, hw, LINE_H, hc, this.font));
            }

            boolean selected = current.equals(value);
            String name = value.equals(MixSettings.INHERIT) ? "Basis"
                    : value.equals(MixSettings.OFF) ? "Aus" : shortName(value);
            String text = this.font.plainSubstrByWidth((selected ? "\u2714 " : "") + name, cellW - 6);
            Component nc = Component.literal(text).withStyle(Style.EMPTY.withColor(selected ? 0x55FF55 : 0xFFFFFF));
            int nw = this.font.width(nc);
            addRenderableWidget(new StringWidget(x + (cellW - nw) / 2, y + cellH - 12, nw, LINE_H, nc, this.font));
        }

        // ---- Scroll-Knöpfe rechts neben dem Gitter (zusätzlich zum Mausrad)
        Button gridUp = Button.builder(Component.literal("^"), b -> {
            rowScroll = Math.max(0, rowScroll - 1);
            rebuildWidgets();
        }).bounds(gx1 + 6, top, 20, 20).build();
        gridUp.active = rowScroll > 0;
        addRenderableWidget(gridUp);
        final int lastScroll = maxRowScroll;
        Button gridDown = Button.builder(Component.literal("v"), b -> {
            rowScroll = Math.min(lastScroll, rowScroll + 1);
            rebuildWidgets();
        }).bounds(gx1 + 6, bottom - 20, 20, 20).build();
        gridDown.active = rowScroll < maxRowScroll;
        addRenderableWidget(gridDown);

        // ---- Status
        status = centerText(centerX, this.height - 38, Component.literal(
                statusText != null ? statusText
                : packs.isEmpty() ? "Keine Packs gefunden: lege ZIPs in .minecraft/murmelspacks"
                : "Zeile " + (rowScroll + 1) + " von " + totalRows + "  -  Mausrad zum Scrollen"));
        addRenderableWidget(status);

        // ---- Unten: Anwenden | Fertig
        int by = this.height - 26;
        addRenderableWidget(Button.builder(Component.literal("Anwenden"), b -> apply())
                .bounds(centerX - 104, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(centerX + 4, by, 100, 20).build());
    }

    /** Mausrad: links über der Kategorie-Liste scrollt die Liste, sonst das Pack-Gitter. */
    // (bewusst ohne @Override: so bricht der Bau nicht, falls sich die Signatur in einer Version ändert)
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = scrollY > 0 ? -1 : 1;
        if (mouseX < sidebarW + 12) catScroll += dir;
        else rowScroll += dir;
        rebuildWidgets();
        return true;
    }

    // ------------------------------------------------------------------ Helfer

    private StringWidget centerText(int cx, int y, Component text) {
        int tw = this.font.width(text);
        return new StringWidget(cx - tw / 2, y, tw, 9, text, this.font);
    }

    private void setStatus(String text) {
        statusText = text;
        Component c = Component.literal(text);
        int tw = this.font.width(c);
        status.setMessage(c);
        status.setWidth(tw);
        status.setX(centerX - tw / 2);
    }

    private int[] preview(Category cat, String packId, int res) {
        String key = cat.id() + "|" + packId + "|" + res;
        if (previewCache.containsKey(key)) return previewCache.get(key);
        int[] px = null;
        try (PackSource src = PackScanner.open(packDirs, packId)) {
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
        return Component.literal(this.font.plainSubstrByWidth("Basis: " + name, maxWidth));
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
                return PackMixer.build(packsDir, packDirs, settings, VersionInfo.PACK_FORMAT);
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
        MenuBlur.restore();
        Compat.openScreen(this.minecraft, parent);
    }

    /** Wird aufgerufen, wenn das Menü auf irgendeinem Weg geschlossen wird. */
    // (bewusst ohne @Override, siehe mouseScrolled)
    public void removed() {
        MenuBlur.restore();
    }
}
