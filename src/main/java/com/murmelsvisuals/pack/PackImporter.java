package com.murmelsvisuals.pack;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

/**
 * Copies a resource pack (.zip or folder) into the game's resourcepacks directory. Validates first and
 * never overwrites an existing pack - a numeric suffix is added instead - so imports cannot break
 * packs the player already has.
 */
public final class PackImporter {
	private PackImporter() {}

	/**
	 * @return the destination path inside {@code packDir}
	 * @throws IOException with a human readable message if the source is not a valid pack
	 */
	public static Path importPack(Path source, Path packDir) throws IOException {
		if (source == null || !Files.exists(source)) throw new IOException("File not found: " + source);
		boolean isDir = Files.isDirectory(source);
		if (!isDir && !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			throw new IOException("Only .zip files or pack folders can be imported");
		}
		PackScan scan = PackScanner.scan(source);
		if (!scan.valid()) throw new IOException("Not a valid resource pack: " + scan.problem());

		Files.createDirectories(packDir);
		Path real = packDir.toAbsolutePath().normalize();
		Path src = source.toAbsolutePath().normalize();
		if (src.startsWith(real)) throw new IOException("This pack is already in the resourcepacks folder");

		Path dest = uniqueName(packDir, source.getFileName().toString(), isDir);
		if (isDir) copyTree(source, dest);
		else Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
		return dest;
	}

	static Path uniqueName(Path dir, String name, boolean isDir) {
		Path dest = dir.resolve(name);
		if (!Files.exists(dest)) return dest;
		String base = name, ext = "";
		int dot = name.lastIndexOf('.');
		if (!isDir && dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
		for (int i = 2; i < 10_000; i++) {
			Path c = dir.resolve(base + " (" + i + ")" + ext);
			if (!Files.exists(c)) return c;
		}
		throw new IllegalStateException("Could not find a free file name for " + name);
	}

	private static void copyTree(Path from, Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
