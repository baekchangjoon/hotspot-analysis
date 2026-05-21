package io.github.baekchangjoon.hotspotanalysis.vcs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileChangeTest {

    @Test
    @DisplayName("constructs a regular MODIFIED change with expected field values")
    void shouldConstructModified() {
        FileChange change = new FileChange(
                "src/main/java/Foo.java", null, 10, 3, ChangeType.MODIFIED);

        assertThat(change.path()).isEqualTo("src/main/java/Foo.java");
        assertThat(change.previousPath()).isNull();
        assertThat(change.linesAdded()).isEqualTo(10);
        assertThat(change.linesDeleted()).isEqualTo(3);
        assertThat(change.type()).isEqualTo(ChangeType.MODIFIED);
    }

    @Test
    @DisplayName("constructs a RENAMED change with previousPath")
    void shouldConstructRenamed() {
        FileChange change = new FileChange(
                "src/Bar.java", "src/Foo.java", 0, 0, ChangeType.RENAMED);

        assertThat(change.path()).isEqualTo("src/Bar.java");
        assertThat(change.previousPath()).isEqualTo("src/Foo.java");
        assertThat(change.type()).isEqualTo(ChangeType.RENAMED);
    }

    @Test
    @DisplayName("rejects null path")
    void shouldRejectNullPath() {
        assertThatThrownBy(() ->
                new FileChange(null, null, 0, 0, ChangeType.ADDED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("path");
    }

    @Test
    @DisplayName("rejects blank path")
    void shouldRejectBlankPath() {
        assertThatThrownBy(() ->
                new FileChange("   ", null, 0, 0, ChangeType.ADDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    @DisplayName("rejects null type")
    void shouldRejectNullType() {
        assertThatThrownBy(() ->
                new FileChange("Foo.java", null, 0, 0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("rejects negative linesAdded")
    void shouldRejectNegativeLinesAdded() {
        assertThatThrownBy(() ->
                new FileChange("Foo.java", null, -1, 0, ChangeType.MODIFIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linesAdded");
    }

    @Test
    @DisplayName("rejects negative linesDeleted")
    void shouldRejectNegativeLinesDeleted() {
        assertThatThrownBy(() ->
                new FileChange("Foo.java", null, 0, -2, ChangeType.MODIFIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linesDeleted");
    }

    @Test
    @DisplayName("rejects RENAMED without previousPath")
    void shouldRejectRenamedWithoutPreviousPath() {
        assertThatThrownBy(() ->
                new FileChange("Foo.java", null, 0, 0, ChangeType.RENAMED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previousPath");
    }

    @Test
    @DisplayName("rejects non-RENAMED with previousPath")
    void shouldRejectNonRenamedWithPreviousPath() {
        assertThatThrownBy(() ->
                new FileChange("Foo.java", "Old.java", 0, 0, ChangeType.MODIFIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previousPath");
    }
}
