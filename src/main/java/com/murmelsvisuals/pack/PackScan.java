package com.murmelsvisuals.pack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Result of scanning a resource pack's file list. Immutable. */
public record PackScan(
		boolean valid,
		String problem,
		int totalFiles,
		Map<PackCategory, Integer> counts,
		List<String> previewEntries) {

	public static PackScan invalid(String problem) {
		return new PackScan(false, problem, 0, new EnumMap<>(PackCategory.class), List.of());
	}

	/** Number of files this pack replaces in the given category. */
	public int count(PackCategory c) {
		return counts.getOrDefault(c, 0);
	}

	public boolean affects(PackCategory c) {
		return count(c) > 0;
	}
}
