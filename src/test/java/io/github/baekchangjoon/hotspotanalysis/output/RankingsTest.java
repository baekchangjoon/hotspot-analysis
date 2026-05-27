package io.github.baekchangjoon.hotspotanalysis.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour of the {@link Rankings#rank(List, Comparator)} helper that the
 * output writers rely on to compute Simple/Composite ranks side-by-side.
 */
class RankingsTest {

    @Test
    @DisplayName("ranks are 1-based and assigned in comparator order")
    void shouldAssign1BasedRanksInComparatorOrder() {
        // Sorted by value DESC → 30 (rank 1), 20 (rank 2), 10 (rank 3).
        List<Integer> items = List.of(10, 30, 20);
        Map<Integer, Integer> ranks = Rankings.rank(items, Comparator.<Integer>reverseOrder());

        assertThat(ranks).containsEntry(30, 1).containsEntry(20, 2).containsEntry(10, 3);
    }

    @Test
    @DisplayName("identity-based — equal-but-distinct instances each get a rank entry")
    void shouldUseIdentityNotEquality() {
        // Two String instances with identical content but distinct identity.
        // An equality-based map would collapse them into one entry; the
        // identity map must keep both, so ranks .values() carries 1 and 2.
        String a = new String("dup");
        String b = new String("dup");
        assertThat(a == b).isFalse();

        Map<String, Integer> ranks = Rankings.rank(List.of(a, b), Comparator.<String>naturalOrder());

        assertThat(ranks).hasSize(2);
        assertThat(ranks.values()).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("empty input yields empty map")
    void shouldReturnEmptyMapForEmptyList() {
        List<Integer> empty = List.of();
        assertThat(Rankings.rank(empty, Comparator.<Integer>naturalOrder())).isEmpty();
    }
}
