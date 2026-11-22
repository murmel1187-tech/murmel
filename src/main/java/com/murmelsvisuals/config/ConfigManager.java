package com.murmelsvisuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.murmelsvisuals.MurmelsVisuals;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link ModConfig}.
 *
 * <ul>
 *   <li>Writes are atomic (temp file + move) so a crash mid-write cannot corrupt the config.</li>
 *   <li>A corrupted file is renamed to {@code *.broken-<timestamp>} and defaults are used.</li>
 *   <li>GUI code calls {@link #markDirty()}; the client tick, the screen's close and the
 *       client-stopping event call {@link #saveIfDirty()}, so values survive a restart.</li>
 * </ul>
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path file;
	private static ModConfig config = new ModConfig();
	private static volatile boolean dirty;

	private ConfigManager() {}

	/** Initialise with the config file location and load it. */
	public static synchronized void init(Path configFile) {
		file = configFile;
		config = load(configFile);
	}

	public static ModConfig get() {
		return config;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void saveIfDirty() {
		if (dirty) save();
	}

	public static synchronized void save() {
		if (file == null) return;
		try {
			config.sanitize();
			write(file, config);
			dirty = false;
		} catch (IOException e) {
			MurmelsVisuals.LOGGER.error("Could not save {}", file, e);
		}
	}

	/** Reads a config file; never throws. Missing file -> defaults; broken file -> backup + defaults. */
	public static ModConfig load(Path path) {
		if (!Files.isRegularFile(path)) {
			ModConfig fresh = new ModConfig();
			try { write(path, fresh); } catch (IOException ignored) { /* retried on next save */ }
			return fresh;
		}
		try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ModConfig loaded = GSON.fromJson(in, ModConfig.class);
			if (loaded == null) loaded = new ModConfig();
			loaded.sanitize();
			return loaded;
		} catch (IOException | JsonSyntaxException | IllegalStateException e) {
			MurmelsVisuals.LOGGER.error("Config {} is unreadable, using defaults", path, e);
			try {
				Files.move(path, path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis()),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) { /* best effort */ }
			return new ModConfig();
		}
	}

	/** Atomic write. Package-visible for tests. */
	static void write(Path path, ModConfig cfg) throws IOException {
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(cfg, out);
		}
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
