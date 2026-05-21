package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommitRecordTest {

    private static final Instant SAMPLE_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    @DisplayName("constructs a minimal commit with empty change list")
    void shouldConstructEmpty() {
        CommitRecord commit = new CommitRecord(
                "abc1234", "alice", SAMPLE_TIME, "Initial commit", List.of());

        assertThat(commit.hash()).isEqualTo("abc1234");
        assertThat(commit.author()).isEqualTo("alice");
        assertThat(commit.committedAt()).isEqualTo(SAMPLE_TIME);
        assertThat(commit.message()).isEqualTo("Initial commit");
        assertThat(commit.changes()).isEmpty();
    }

    @Test
    @DisplayName("makes the changes list defensively immutable")
    void shouldMakeChangesImmutable() {
        List<FileChange> mutable = new ArrayList<>();
        mutable.add(new FileChange("Foo.java", null, 1, 0, ChangeType.ADDED));

        CommitRecord commit = new CommitRecord(
                "abc1234", "alice", SAMPLE_TIME, "msg", mutable);

        mutable.clear();

        assertThat(commit.changes()).hasSize(1);
        assertThatThrownBy(() -> commit.changes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rejects null hash")
    void shouldRejectNullHash() {
        assertThatThrownBy(() -> new CommitRecord(
                null, "alice", SAMPLE_TIME, "msg", List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("rejects blank hash")
    void shouldRejectBlankHash() {
        assertThatThrownBy(() -> new CommitRecord(
                "  ", "alice", SAMPLE_TIME, "msg", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("rejects null author")
    void shouldRejectNullAuthor() {
        assertThatThrownBy(() -> new CommitRecord(
                "abc", null, SAMPLE_TIME, "msg", List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("author");
    }

    @Test
    @DisplayName("rejects null committedAt")
    void shouldRejectNullTimestamp() {
        assertThatThrownBy(() -> new CommitRecord(
                "abc", "alice", null, "msg", List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("committedAt");
    }

    @Test
    @DisplayName("rejects null changes list")
    void shouldRejectNullChanges() {
        assertThatThrownBy(() -> new CommitRecord(
                "abc", "alice", SAMPLE_TIME, "msg", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("changes");
    }

    @Test
    @DisplayName("treats null message as empty string")
    void shouldAcceptNullMessageAsEmpty() {
        CommitRecord commit = new CommitRecord(
                "abc", "alice", SAMPLE_TIME, null, List.of());

        assertThat(commit.message()).isEmpty();
    }
}
