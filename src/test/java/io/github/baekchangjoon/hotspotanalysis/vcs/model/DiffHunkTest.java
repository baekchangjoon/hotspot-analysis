package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiffHunkTest {

    @Test
    @DisplayName("constructs a single-line hunk")
    void shouldAcceptSingleLineHunk() {
        DiffHunk hunk = new DiffHunk(10, 10);

        assertThat(hunk.newStart()).isEqualTo(10);
        assertThat(hunk.newEnd()).isEqualTo(10);
    }

    @Test
    @DisplayName("rejects newStart < 1")
    void shouldRejectNewStartBelowOne() {
        assertThatThrownBy(() -> new DiffHunk(0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newStart");
    }

    @Test
    @DisplayName("rejects newEnd < newStart")
    void shouldRejectNewEndBelowNewStart() {
        assertThatThrownBy(() -> new DiffHunk(10, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newEnd");
    }

    @Test
    @DisplayName("overlaps detects a containing range")
    void shouldDetectContainingRange() {
        DiffHunk hunk = new DiffHunk(10, 20);

        assertThat(hunk.overlaps(5, 25)).isTrue();   // hunk fully inside
        assertThat(hunk.overlaps(15, 18)).isTrue();  // range fully inside
        assertThat(hunk.overlaps(20, 30)).isTrue();  // touch at boundary
        assertThat(hunk.overlaps(5, 10)).isTrue();   // touch at boundary
    }

    @Test
    @DisplayName("overlaps returns false for disjoint ranges")
    void shouldRejectDisjointRange() {
        DiffHunk hunk = new DiffHunk(10, 20);

        assertThat(hunk.overlaps(1, 9)).isFalse();
        assertThat(hunk.overlaps(21, 30)).isFalse();
    }
}
