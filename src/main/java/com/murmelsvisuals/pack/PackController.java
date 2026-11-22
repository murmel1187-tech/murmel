package com.murmelsvisuals.pack;

import com.murmelsvisuals.MurmelsVisuals;
import com.murmelsvisuals.config.ConfigManager;
import com.murmelsvisuals.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bridge between the GUI and Minecraft's {@link PackRepository}.
 *
 * <p><b>Safety model.</b> The GUI edits a <i>staged</i> list; nothing touches the game until
 * {@link #apply()}. {@code apply()} goes through the same calls the vanilla pack screen uses
 * ({@code PackRepository#setSelected}, {@code Options#updateResourcePacks}, then a resource reload).
 * Required packs (vanilla, mod built-ins) can never be disabled and fixed-position packs are never
 * reordered, so existing setups keep working. If the reload fails, the previous pack set is restored.</p>
 *
 * <p>Selected ids are stored lowest priority first, exactly like Minecraft's {@code options.txt}.</p>
 */
public final class PackController {
	private final Minecraft mc = Minecraft.getInstance();
	private final ExecutorService scanner = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "MurmelsVisuals-PackScan");
		t.setDaemon(true);
		return t;
	});

	private final Map<String, PackEntry> byId = new LinkedHashMap<>();
	private List<String> applied = new ArrayList<>();
	private List<String> staged = new ArrayList<>();
	private volatile String message = "";
	private volatile boolean reloading;

	public PackController() {
		refresh(true);
	}

	// ---- reading ----------------------------------------------------------------------------------

	public List<PackEntry> all() { return new ArrayList<>(byId.values()); }
	public PackEntry get(String id) { return byId.get(id); }
	public boolean isEnabled(String id) { return staged.contains(id); }
	public boolean hasPending() { return !staged.equals(applied); }
	public boolean isReloading() { return reloading; }
	public String message() { return message; }
	public void setMessage(String m) { message = m == null ? "" : m; }
	public Path packDir() { return mc.getResourcePackDirectory(); }
	public List<String> staged() { return List.copyOf(staged); }

	/** 0 = highest priority among enabled packs; -1 if disabled. */
	public int priorityRank(String id) {
		int idx = staged.indexOf(id);
		return idx < 0 ? -1 : staged.size() - 1 - idx;
	}

	/** Enabled pack ids, highest priority first. */
	public List<String> enabledByPriority() {
		List<String> out = new ArrayList<>(staged);
		java.util.Collections.reverse(out);
		return out;
	}

	// ---- refresh ----------------------------------------------------------------------------------

	/** Re-read the pack list. {@code rescanDisk} also re-lists the resourcepacks folder. */
	public void refresh(boolean rescanDisk) {
		PackRepository repo = mc.getResourcePackRepository();
		if (rescanDisk) {
			try { repo.reload(); } catch (RuntimeException e) { MurmelsVisuals.LOGGER.warn("Pack rescan failed", e); }
		}
		Map<String, PackEntry> old = new LinkedHashMap<>(byId);
		byId.clear();
		Path dir = packDir();
		for (Pack p : repo.getAvailablePacks()) {
			String id = p.getId();
			Path path = null;
			long size = 0, mod = 0;
			if (id.startsWith("file/")) {
				Path candidate = dir.resolve(id.substring(5));
				if (Files.exists(candidate)) {
					path = candidate;
					try {
						mod = Files.getLastModifiedTime(candidate).toMillis();
						size = Files.isDirectory(candidate) ? 0 : Files.size(candidate);
					} catch (IOException ignored) { /* keep zeros */ }
				}
			}
			PackEntry e = new PackEntry(id, p.getTitle().getString(), p.getDescription().getString(),
					p.isRequired(), p.isFixedPosition(), p.getCompatibility().isCompatible(),
					p.getDefaultPosition() == Pack.Position.TOP, path, size, mod);
			PackEntry prev = old.get(id);
			if (prev != null && prev.scan != null && prev.modifiedMs == mod && prev.sizeBytes == size) e.scan = prev.scan;
			byId.put(id, e);
			if (e.scan == null && path != null) {
				final Path scanPath = path;
				scanner.execute(() -> e.scan = PackScanner.scan(scanPath));
			}
		}
		applied = new ArrayList<>(repo.getSelectedIds());
		if (!reloading) staged = new ArrayList<>(applied);
		else staged.removeIf(id -> !byId.containsKey(id));
	}

	// ---- editing (staged) -------------------------------------------------------------------------

	/** @return false when the change is not allowed (required pack). */
	public boolean setEnabled(String id, boolean on) {
		PackEntry p = byId.get(id);
		if (p == null) return false;
		boolean now = staged.contains(id);
		if (on == now) return true;
		if (!on) {
			if (p.required) { message = "\"" + p.title + "\" is required and cannot be disabled."; return false; }
			staged.remove(id);
			return true;
		}
		int index = staged.size();
		// stay below packs that insist on the top position
		while (index > 0) {
			PackEntry below = byId.get(staged.get(index - 1));
			if (below != null && below.fixedPosition && below.topPosition) index--; else break;
		}
		staged.add(index, id);
		if (!p.compatible) message = "\"" + p.title + "\" was made for a different game version and may look broken.";
		return true;
	}

	/** Move an enabled pack. {@code up == true} raises its priority. */
	public boolean move(String id, boolean up) {
		int i = staged.indexOf(id);
		if (i < 0) return false;
		PackEntry self = byId.get(id);
		if (self != null && self.fixedPosition) return false;
		int j = up ? i + 1 : i - 1;
		if (j < 0 || j >= staged.size()) return false;
		PackEntry other = byId.get(staged.get(j));
		if (other != null && other.fixedPosition) return false;
		java.util.Collections.swap(staged, i, j);
		return true;
	}

	public void revert() {
		staged = new ArrayList<>(applied);
		message = "Changes discarded.";
	}

	// ---- applying ---------------------------------------------------------------------------------

	/** Apply the staged pack list and reload resources. Restores the old list if anything fails. */
	public void apply() {
		if (reloading) return;
		List<String> before = new ArrayList<>(applied);
		List<String> target = new ArrayList<>(staged);
		if (target.equals(before)) return;
		ModConfig cfg = ConfigManager.get();
		cfg.previousPackSet = new ArrayList<>(before);
		ConfigManager.markDirty();
		reloading = true;
		message = "Applying...";
		try {
			commit(target);
			mc.reloadResourcePacks().whenComplete((v, err) -> mc.execute(() -> {
				if (err != null) {
					MurmelsVisuals.LOGGER.error("Resource reload failed, restoring previous packs", err);
					try {
						commit(before);
						mc.reloadResourcePacks().whenComplete((v2, e2) -> mc.execute(() -> finishReload("Reload failed - previous packs restored.")));
					} catch (RuntimeException e) {
						finishReload("Reload failed and restore failed: " + e.getMessage());
					}
				} else {
					finishReload("Packs applied.");
				}
			}));
		} catch (RuntimeException e) {
			MurmelsVisuals.LOGGER.error("Applying packs failed", e);
			try { commit(before); } catch (RuntimeException ignored) { /* nothing more to do */ }
			finishReload("Could not apply packs: " + e.getMessage());
		}
	}

	private void commit(List<String> ids) {
		PackRepository repo = mc.getResourcePackRepository();
		repo.setSelected(ids);
		mc.options.updateResourcePacks(repo); // saves options.txt like the vanilla screen does
	}

	private void finishReload(String msg) {
		reloading = false;
		refresh(false);
		message = msg;
	}

	// ---- profiles & quick switch --------------------------------------------------------------------

	public Map<String, List<String>> profiles() { return ConfigManager.get().packProfiles; }

	public void saveProfile(String name) {
		name = name == null ? "" : name.trim();
		if (name.isEmpty()) { message = "Enter a profile name first."; return; }
		ConfigManager.get().packProfiles.put(name, new ArrayList<>(staged));
		ConfigManager.markDirty();
		message = "Saved profile \"" + name + "\".";
	}

	public void deleteProfile(String name) {
		ConfigManager.get().packProfiles.remove(name);
		ConfigManager.markDirty();
		message = "Deleted profile \"" + name + "\".";
	}

	/** Stage a saved profile (ignoring packs that no longer exist) and apply it. */
	public void applyProfile(String name) {
		List<String> ids = ConfigManager.get().packProfiles.get(name);
		if (ids == null) return;
		stageExactly(ids);
		message = "Switching to \"" + name + "\"...";
		apply();
	}

	/** Swap to the pack set that was active before the last change. Used by the quick-switch key too. */
	public boolean swapToPrevious() {
		List<String> prev = ConfigManager.get().previousPackSet;
		if (prev == null || prev.isEmpty()) { message = "No previous pack set yet."; return false; }
		stageExactly(prev);
		apply();
		return true;
	}

	private void stageExactly(List<String> ids) {
		List<String> next = new ArrayList<>();
		for (String id : ids) if (byId.containsKey(id) && !next.contains(id)) next.add(id);
		// keep required packs the profile did not know about (e.g. a newly installed mod)
		for (String id : staged) {
			PackEntry p = byId.get(id);
			if (p != null && p.required && !next.contains(id)) next.add(0, id);
		}
		staged = next;
	}

	// ---- import -----------------------------------------------------------------------------------

	/** Import a zip/folder into the resourcepacks directory and refresh the list. */
	public void importPack(Path source) {
		try {
			Path dest = PackImporter.importPack(source, packDir());
			refresh(true);
			message = "Imported " + dest.getFileName() + " (disabled - enable it in the list).";
		} catch (IOException e) {
			message = e.getMessage();
		}
	}

	public void close() {
		scanner.shutdownNow();
	}
}
