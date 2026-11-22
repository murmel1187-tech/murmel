package com.murmelsvisuals.pack;

import com.murmelsvisuals.config.ModConfig.PackFilter;
import com.murmelsvisuals.config.ModConfig.PackSort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Search, filter and sort logic for the pack list. Pure Java. */
public final class PackSorter {
	private PackSorter() {}

	/**
	 * @param all      every available pack
	 * @param enabled  predicate: is this pack id currently enabled (staged state)
	 * @param query    search text (matches title, id and description, case-insensitive); may be empty
	 */
	public static List<PackEntry> apply(Collection<PackEntry> all, Predicate<String> enabled, String query,
										PackSort sort, PackFilter filter) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<PackEntry> out = new ArrayList<>();
		for (PackEntry p : all) {
			boolean on = enabled.test(p.id);
			if (filter == PackFilter.ENABLED && !on) continue;
			if (filter == PackFilter.DISABLED && on) continue;
			if (!q.isEmpty() && !matches(p, q)) continue;
			out.add(p);
		}
		out.sort(comparator(sort, enabled));
		return out;
	}

	static boolean matches(PackEntry p, String lowerQuery) {
		for (String token : lowerQuery.split("\\s+")) {
			if (token.isEmpty()) continue;
			boolean hit = p.title.toLowerCase(Locale.ROOT).contains(token)
					|| p.id.toLowerCase(Locale.ROOT).contains(token)
					|| p.description.toLowerCase(Locale.ROOT).contains(token);
			if (!hit) return false;
		}
		return true;
	}

	static Comparator<PackEntry> comparator(PackSort sort, Predicate<String> enabled) {
		Comparator<PackEntry> byName = Comparator.comparing((PackEntry p) -> p.title.toLowerCase(Locale.ROOT))
				.thenComparing(p -> p.id);
		return switch (sort) {
			case NAME_ASC -> byName;
			case NAME_DESC -> byName.reversed();
			case ENABLED_FIRST -> Comparator.comparing((PackEntry p) -> !enabled.test(p.id)).thenComparing(byName);
			case NEWEST -> Comparator.comparingLong((PackEntry p) -> -p.modifiedMs).thenComparing(byName);
			case LARGEST -> Comparator.comparingLong((PackEntry p) -> -p.sizeBytes).thenComparing(byName);
			case COMPATIBILITY -> Comparator.comparing((PackEntry p) -> p.compatible).thenComparing(byName);
		};
	}
}
