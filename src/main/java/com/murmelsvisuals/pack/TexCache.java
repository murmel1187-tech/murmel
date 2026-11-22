package com.murmelsvisuals.pack;

import com.mojang.blaze3d.platform.NativeImage;
import com.murmelsvisuals.MurmelsVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads images out of resource packs (pack.png and a few sample textures) into GL textures for the
 * preview panel. File reading happens on a worker thread; texture creation happens on the render
 * thread in {@link #tick()}. Everything is released in {@link #clear()} when the GUI closes, so no
 * textures leak.
 */
public final class TexCache {
	public record Tex(Identifier id, int width, int height) {}

	private record Loaded(String key, byte[] bytes) {}

	private static final Tex FAILED = new Tex(null, 0, 0);

	private final Map<String, Tex> ready = new HashMap<>();
	private final Map<String, Boolean> requested = new HashMap<>();
	private final ConcurrentLinkedQueue<Loaded> arrived = new ConcurrentLinkedQueue<>();
	private final List<Identifier> owned = new ArrayList<>();
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "MurmelsVisuals-TexIO");
		t.setDaemon(true);
		return t;
	});
	private int counter;

	/** Returns the texture if it is loaded, else null (and starts loading). {@code FAILED} entries return null. */
	public Tex get(Path pack, String entry) {
		String key = pack.toString() + "|" + entry;
		Tex t = ready.get(key);
		if (t != null) return t == FAILED ? null : t;
		if (requested.putIfAbsent(key, Boolean.TRUE) == null) {
			io.execute(() -> {
				byte[] bytes = null;
				try { bytes = PackScanner.readEntry(pack, entry); } catch (Exception ignored) { /* shown as missing */ }
				arrived.add(new Loaded(key, bytes));
			});
		}
		return null;
	}

	public boolean failed(Path pack, String entry) {
		return ready.get(pack.toString() + "|" + entry) == FAILED;
	}

	/** Call once per frame on the render thread. */
	public void tick() {
		Loaded l;
		int budget = 3; // limit uploads per frame
		while (budget-- > 0 && (l = arrived.poll()) != null) {
			if (l.bytes() == null) { ready.put(l.key(), FAILED); continue; }
			try {
				NativeImage img = NativeImage.read(new ByteArrayInputStream(l.bytes()));
				Identifier id = Identifier.fromNamespaceAndPath(MurmelsVisuals.MOD_ID, "preview/" + (counter++));
				DynamicTexture tex = new DynamicTexture(() -> "murmelsvisuals preview", img);
				Minecraft.getInstance().getTextureManager().register(id, tex);
				owned.add(id);
				ready.put(l.key(), new Tex(id, img.getWidth(), img.getHeight()));
			} catch (Exception | LinkageError e) {
				MurmelsVisuals.LOGGER.debug("Could not decode preview image {}", l.key(), e);
				ready.put(l.key(), FAILED);
			}
		}
	}

	/** Release every texture and stop the worker. */
	public void clear() {
		io.shutdownNow();
		var tm = Minecraft.getInstance().getTextureManager();
		for (Identifier id : owned) {
			try { tm.release(id); } catch (Exception ignored) { /* already gone */ }
		}
		owned.clear();
		ready.clear();
		requested.clear();
		arrived.clear();
	}
}
