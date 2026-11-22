package com.murmelsvisuals.pack;

import java.nio.file.Path;

/**
 * Snapshot of one available resource pack. Pure data (no Minecraft types), so sorting/filtering can
 * be tested without the game. {@link #scan} is filled in later by a background thread.
 */
public final class PackEntry {
	public final String id;
	public final String title;
	public final String description;
	/** Cannot be disabled (vanilla, mod built-ins). */
	public final boolean required;
	/** Position cannot be changed. */
	public final boolean fixedPosition;
	public final boolean compatible;
	/** Prefers the top of the stack (e.g. server packs). Used when inserting a newly enabled pack. */
	public final boolean topPosition;
	/** Location inside the resourcepacks folder; null for built-in / mod packs. */
	public final Path path;
	public final long sizeBytes;
	public final long modifiedMs;

	public volatile PackScan scan;

	public PackEntry(String id, String title, String description, boolean required, boolean fixedPosition,
					 boolean compatible, boolean topPosition, Path path, long sizeBytes, long modifiedMs) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.required = required;
		this.fixedPosition = fixedPosition;
		this.compatible = compatible;
		this.topPosition = topPosition;
		this.path = path;
		this.sizeBytes = sizeBytes;
		this.modifiedMs = modifiedMs;
	}

	public boolean isFolderPack() {
		return path != null;
	}

	public String sourceLabel() {
		return path == null ? "Built-in" : (java.nio.file.Files.isDirectory(path) ? "Folder" : "Zip");
	}
}
