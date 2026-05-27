package io.github.baekchangjoon.hotspotanalysis.output;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes 1-based ranks for a list of hotspots under an arbitrary
 * ordering. Used by output writers to attach both a Simple Rank
 * (ordered by simple score) and a Composite Rank (ordered by composite
 * score) to every row.
 *
 * <p>Identity-based lookup keeps the helper agnostic of record equality:
 * two rows with identical metric values still get distinct rank entries
 * because callers always reuse the same hotspot instances.</p>
 */
public final class Rankings {

    private Rankings() {
    }

    /**
     * Returns a map of {@code item -> 1-based rank}. Items are ranked
     * by sorting a copy of {@code items} with the supplied comparator
     * (descending orders are the caller's responsibility — pass
     * {@code Comparator.reversed()} when the bigger value should win).
     */
    public static <T> Map<T, Integer> rank(List<T> items, Comparator<T> order) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(order);
        Map<T, Integer> ranks = new IdentityHashMap<>(items.size());
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i), i + 1);
        }
        return ranks;
    }
}
