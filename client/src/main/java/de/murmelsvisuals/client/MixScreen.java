package de.murmelsvisuals.client;

import de.murmelsvisuals.core.Category;
import de.murmelsvisuals.core.MixSettings;
import de.murmelsvisuals.core.PackMixer;
import de.murmelsvisuals.core.PackScanner;
import de.murmelsvisuals.core.PackSource;
import de.murmelsvisuals.core.PixelImage;
import de.murmelsvisuals.core.Preview;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MixScreen extends PanelScreen {
    private static final Object LOADING = new Object();
    private static final Object NONE = new Object();
    /** One background thread loads the preview pictures, so the menu never freezes. */
    private static final ExecutorService PREVIEW_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MurmelsVisuals-previews");
        t.setDaemon(true);
        return t;
    });
    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0x8A8A9A;
    private static final int VIOLET = 0xC9A8FF;
    private static final int PINK = 0xF9A8D4;

    private final Screen parent;
    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private final Path packsDir = gameDir.resolve("resourcepacks");
    /** Own folder for this mod: packs placed here do not show up in the normal Minecraft pack list. */
    private final Path extraDir = gameDir.resolve("murmelspacks");
    private final List<Path> packDirs = List.of(packsDir, extraDir);
    private final Path settingsFile = FabricLoader.getInstance().getConfigDir().resolve("murmelsvisuals.properties");
    private final MixSettings settings = MixSettings.load(settingsFile);

    private List<String> packs = List.of();
    private StringWidget status;
    private String statusText = null; // null = default text
    private int centerX = 0;
    private int sidebarW = 120;
    private int catIndex = 0;
    private int catScroll = 0;
    private int rowScroll = 0;
    private final Map<String, Object> previewCache = new ConcurrentHashMap<>();
    private final AtomicBoolean rebuildQueued = new AtomicBoolean(false);
    private volatile boolean active = true;

    public MixScreen(Screen parent) {
        super(Component.literal("Pack Mixer"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        panels.clear();
        images.clear();
        this.dim = settings.dim;
        try {
            Files.createDirectories(extraDir);
        } catch (IOException ignored) {
            // fine, the extra folder just will not exist
        }
        try {
            packs = PackScanner.listPackIds(packDirs);
        } catch (IOException e) {
            packs = List.of();
        }

        final Category[] cats = Category.values();
        final Category cat = cats[catIndex];

        sidebarW = Math.max(90, Math.min(130, this.width / 5));
        int gx0 = sidebarW + 16;
        int gx1 = this.width - 30;
        int gw = gx1 - gx0;
        centerX = gx0 + gw / 2;

        // glass backgrounds
        panels.add(new Panel(4, 4, sidebarW + 8, this.height - 8, Panel.GLASS, false));
        panels.add(new Panel(gx0 - 6, 4, this.width - 4 - (gx0 - 6), this.height - 8, Panel.GLASS, false));

        addRenderableWidget(centerText(centerX, 9, this.title, VIOLET));

        // ---- top row: Side Shield | Base pack | Low Fire
        int bw = (gw - 8) / 3;
        flat(gx0, 22, bw, 20,
                "Side Shield: " + (settings.sideShield == 0 ? "Off" : settings.sideShield == 1 ? "Small" : "Normal"),
                settings.sideShield > 0 ? Panel.ACTIVE : Panel.BUTTON, true, b -> {
            settings.sideShield = (settings.sideShield + 1) % 3;
            rebuildWidgets();
        });
        flat(gx0 + bw + 4, 22, bw, 20,
                "Base: " + (settings.basePack == null ? "none" : shortName(settings.basePack)),
                settings.basePack != null ? Panel.ACTIVE : Panel.BUTTON, true, b -> {
            settings.basePack = nextBase(settings.basePack);
            rebuildWidgets();
        });
        flat(gx0 + 2 * (bw + 4), 22, bw, 20,
                "Low Fire: " + (settings.lowFire == 0 ? "Off" : settings.lowFire == 1 ? "Medium" : "Low"),
                settings.lowFire > 0 ? Panel.ACTIVE : Panel.BUTTON, true, b -> {
            settings.lowFire = (settings.lowFire + 1) % 3;
            rebuildWidgets();
        });

        // ---- sidebar with the categories (scrollable)
        addRenderableWidget(centerText(8 + sidebarW / 2, 11, Component.literal("MurmelsVisuals")
                .withStyle(Style.EMPTY.withBold(true).withColor(VIOLET)), -1));
        int sTop = 28;
        int sBottom = this.height - 10;
        int itemH = 18;
        int step = itemH + 2;
        int fit = Math.max(1, (sBottom - sTop) / step);
        boolean overflow = cats.length > fit;
        int listFit = overflow ? Math.max(1, fit - 2) : fit;
        int maxCatScroll = Math.max(0, cats.length - listFit);
        catScroll = Math.max(0, Math.min(catScroll, maxCatScroll));
        int sy = sTop;
        if (overflow) {
            flat(8, sy, sidebarW, itemH, "^", Panel.BUTTON, catScroll > 0, b -> {
                catScroll = Math.max(0, catScroll - 3);
                rebuildWidgets();
            });
            sy += step;
        }
        for (int i = catScroll; i < Math.min(cats.length, catScroll + listFit); i++) {
            final int idx = i;
            flat(8, sy, sidebarW, itemH, cats[i].label(), i == catIndex ? Panel.TAB_ACTIVE : Panel.BUTTON, true, b -> {
                catIndex = idx;
                rowScroll = 0;
                rebuildWidgets();
            });
            sy += step;
        }
        if (overflow) {
            final int lastCat = maxCatScroll;
            flat(8, sBottom - itemH, sidebarW, itemH, "v", Panel.BUTTON, catScroll < maxCatScroll, b -> {
                catScroll = Math.min(lastCat, catScroll + 3);
                rebuildWidgets();
            });
        }

        // ---- heading
        addRenderableWidget(centerText(centerX, 49,
                Component.literal(cat.label() + "  -  " + packs.size() + " packs"), WHITE));

        // ---- selection grid: Base, Off, then every pack
        List<String> options = new ArrayList<>();
        options.add(MixSettings.INHERIT);
        options.add(MixSettings.OFF);
        options.addAll(packs);

        int top = 64;
        int bottom = this.height - 48;
        // biggest picture box (a multiple of 16 pixels, so 16x16 textures stay crisp) that still shows 12 cells
        int box = 48, cols = 1, rows = 1, cellW = 0, cellH = 0;
        for (int sc = 8; sc >= 3; sc--) {
            int bx = 16 * sc;
            int cw = bx + 8;
            int ch = bx + 8 + 12;
            int c = Math.max(1, (gw + 4) / (cw + 4));
            int rw = Math.max(1, (bottom - top + 4) / (ch + 4));
            box = bx; cols = c; rows = rw; cellW = cw; cellH = ch;
            if (c * rw >= 12) break;
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
            boolean selected = current.equals(value);

            panels.add(new Panel(x, y, cellW, cellH, selected ? Panel.CELL_SELECTED : Panel.CELL, true));
            Button hit = Button.builder(Component.empty(), b -> {
                settings.choices.put(cat, value);
                rebuildWidgets();
            }).bounds(x, y, cellW, cellH).build();
            Compat.hide(hit);
            addRenderableWidget(hit);

            String previewPack = value.equals(MixSettings.OFF) ? null
                    : value.equals(MixSettings.INHERIT) ? settings.basePack : value;
            Object pv = previewPack == null ? null : previewFor(cat, previewPack, box);

            if (pv instanceof PixelImage img) {
                images.add(new PixelDraw(x + 4, y + 4, box, box, img));
            } else {
                String hint = value.equals(MixSettings.OFF) ? "Vanilla"
                        : previewPack == null ? "-"
                        : pv == LOADING ? "..." : "no preview";
                addRenderableWidget(centerText(x + cellW / 2, y + 4 + box / 2 - 4,
                        Component.literal(this.font.plainSubstrByWidth(hint, cellW - 6)), GREY));
            }

            String name = value.equals(MixSettings.INHERIT) ? "Base"
                    : value.equals(MixSettings.OFF) ? "Off" : shortName(value);
            String text = this.font.plainSubstrByWidth((selected ? "\u2714 " : "") + name, cellW - 6);
            addRenderableWidget(centerText(x + cellW / 2, y + cellH - 12, Component.literal(text),
                    selected ? PINK : WHITE));
        }

        // ---- scroll buttons next to the grid (in addition to the mouse wheel)
        flat(gx1 + 6, top, 20, 20, "^", Panel.BUTTON, rowScroll > 0, b -> {
            rowScroll = Math.max(0, rowScroll - 1);
            rebuildWidgets();
        });
        final int lastScroll = maxRowScroll;
        flat(gx1 + 6, bottom - 20, 20, 20, "v", Panel.BUTTON, rowScroll < maxRowScroll, b -> {
            rowScroll = Math.min(lastScroll, rowScroll + 1);
            rebuildWidgets();
        });

        // ---- status line
        status = centerText(centerX, this.height - 38, Component.literal(
                statusText != null ? statusText
                : packs.isEmpty() ? "No packs found: put ZIPs into .minecraft/murmelspacks (use Open Folder)"
                : cat.label() + ": " + (current.equals(MixSettings.INHERIT)
                        ? (settings.basePack == null ? "Base (none)" : "Base = " + shortName(settings.basePack))
                        : current.equals(MixSettings.OFF) ? "Off (vanilla)" : shortName(current))
                  + "   -   row " + (rowScroll + 1) + "/" + totalRows + ", mouse wheel scrolls"), GREY);
        addRenderableWidget(status);

        // ---- bottom row: Dim | Open Folder | Apply | Done
        int by = this.height - 26;
        int b4 = Math.min(104, (gw - 12) / 4);
        int startX = centerX - (4 * b4 + 12) / 2;
        flat(startX, by, b4, 20,
                "Dim: " + (settings.dim == 0 ? "Off" : settings.dim == 1 ? "Light" : "Dark"),
                Panel.BUTTON, true, b -> {
            settings.dim = (settings.dim + 1) % 3;
            rebuildWidgets();
        });
        flat(startX + (b4 + 4), by, b4, 20, "Open Folder", Panel.BUTTON, true, b -> openFolder());
        flat(startX + 2 * (b4 + 4), by, b4, 20, "Apply", Panel.ACTIVE, true, b -> apply());
        flat(startX + 3 * (b4 + 4), by, b4, 20, "Done", Panel.BUTTON, true, b -> onClose());
    }

    /** Mouse wheel: over the category list it scrolls the list, otherwise the pack grid. */
    // (deliberately without @Override so the build cannot break if a version changes the signature)
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = scrollY > 0 ? -1 : 1;
        if (mouseX < sidebarW + 12) catScroll += dir;
        else rowScroll += dir;
        rebuildWidgets();
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** A flat button: invisible vanilla button for the clicks, our panel behind it, our label on top. */
    private Button flat(int x, int y, int w, int h, String label, int style, boolean enabled, Button.OnPress press) {
        panels.add(new Panel(x, y, w, h, enabled ? style : Panel.DISABLED, enabled));
        Button b = Button.builder(Component.empty(), press).bounds(x, y, w, h).build();
        Compat.hide(b);
        b.active = enabled;
        addRenderableWidget(b);
        if (!label.isEmpty()) {
            String t = this.font.plainSubstrByWidth(label, w - 8);
            addRenderableWidget(centerText(x + w / 2, y + (h - 8) / 2, Component.literal(t), enabled ? WHITE : GREY));
        }
        return b;
    }

    private StringWidget centerText(int cx, int y, Component text, int color) {
        Component c = color < 0 ? text : text.copy().withStyle(Style.EMPTY.withColor(color));
        int tw = this.font.width(c);
        return new StringWidget(cx - tw / 2, y, tw, 9, c, this.font);
    }

    private void setStatus(String text) {
        statusText = text;
        Component c = Component.literal(text).withStyle(Style.EMPTY.withColor(GREY));
        int tw = this.font.width(c);
        status.setMessage(c);
        status.setWidth(tw);
        status.setX(centerX - tw / 2);
    }

    /** Returns the picture, LOADING (a background load was started) or NONE (the pack has nothing for this). */
    private Object previewFor(Category cat, String packId, int box) {
        String key = cat.id() + "|" + packId + "|" + box;
        Object v = previewCache.get(key);
        if (v != null) return v;
        previewCache.put(key, LOADING);
        final Minecraft mc = this.minecraft;
        PREVIEW_POOL.execute(() -> {
            Object result = NONE;
            try (PackSource src = PackScanner.open(packDirs, packId)) {
                PixelImage img = Preview.image(src, cat, box);
                if (img != null) result = img;
            } catch (Exception ignored) {
                // pack unreadable -> no preview
            }
            previewCache.put(key, result);
            if (active && rebuildQueued.compareAndSet(false, true)) {
                mc.execute(() -> {
                    rebuildQueued.set(false);
                    if (active) rebuildWidgets();
                });
            }
        });
        return LOADING;
    }

    /** Base pack: none -> pack 1 -> pack 2 -> ... -> none */
    private String nextBase(String current) {
        if (packs.isEmpty()) return null;
        if (current == null) return packs.get(0);
        int i = packs.indexOf(current);
        return (i < 0 || i + 1 >= packs.size()) ? null : packs.get(i + 1);
    }

    private static String shortName(String id) {
        return id.toLowerCase().endsWith(".zip") ? id.substring(0, id.length() - 4) : id;
    }

    private void openFolder() {
        final Minecraft mc = this.minecraft;
        CompletableFuture.runAsync(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(extraDir.toFile());
                    return;
                }
            } catch (Exception ignored) {
                // fall through to the path hint
            }
            mc.execute(() -> setStatus("Folder: " + extraDir));
        });
    }

    // ------------------------------------------------------------------ mix & enable

    private void apply() {
        final Minecraft mc = this.minecraft;
        setStatus("Mixing packs ...");
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
                setStatus("Error: " + t.getMessage());
                return;
            }
            enableAndReload(mc);
            setStatus(result.warnings().isEmpty()
                    ? result.filesCopied() + " files mixed and enabled."
                    : "Enabled, but: " + result.warnings().get(0));
        }));
    }

    private void enableAndReload(Minecraft mc) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String id = "file/" + PackScanner.OUTPUT_NAME;
        List<String> before = new ArrayList<>(repo.getSelectedIds());
        List<String> after = new ArrayList<>(before);
        after.remove(id);
        after.add(id); // top of the list = highest priority
        repo.setSelected(after);
        mc.options.updateResourcePacks(repo);
        if (after.equals(before)) {
            mc.reloadResourcePacks(); // pack was already active but its content changed
        }
    }

    @Override
    public void onClose() {
        active = false;
        Compat.openScreen(this.minecraft, parent);
    }
}
