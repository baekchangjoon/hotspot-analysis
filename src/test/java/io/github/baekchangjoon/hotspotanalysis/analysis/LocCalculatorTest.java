package io.github.baekchangjoon.hotspotanalysis.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocCalculatorTest {

    private final LocCalculator calculator = new LocCalculator();

    @Test
    @DisplayName("returns 0 for an empty file")
    void shouldReturnZeroForEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Empty.java");
        Files.writeString(file, "");

        assertThat(calculator.countLines(file)).isZero();
    }

    @Test
    @DisplayName("returns the newline count for a multi-line file")
    void shouldCountLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Foo.java");
        Files.writeString(file, "line1\nline2\nline3\n");

        assertThat(calculator.countLines(file)).isEqualTo(3);
    }

    @Test
    @DisplayName("counts the final line even when it lacks a trailing newline")
    void shouldCountLineWithoutTrailingNewline(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Foo.java");
        Files.writeString(file, "line1\nline2");

        assertThat(calculator.countLines(file)).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects a path that does not exist")
    void shouldRejectMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nope.java");

        assertThatThrownBy(() -> calculator.countLines(missing))
                .isInstanceOf(LocCalculationException.class)
                .hasMessageContaining("nope.java");
    }

    @Test
    @DisplayName("rejects a directory argument")
    void shouldRejectDirectory(@TempDir Path tempDir) {
        assertThatThrownBy(() -> calculator.countLines(tempDir))
                .isInstanceOf(LocCalculationException.class)
                .hasMessageContaining("directory");
    }

    @Test
    @DisplayName("bulk-counts lines for multiple relative paths, mapping missing files to 0")
    void shouldBulkCountWithFallback(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "a\nb\nc\n");
        Files.writeString(tempDir.resolve("B.java"), "single-line");

        Map<String, Integer> result = calculator.countLines(
                tempDir, List.of("A.java", "B.java", "missing.java"));

        assertThat(result)
                .containsEntry("A.java", 3)
                .containsEntry("B.java", 1)
                .containsEntry("missing.java", 0);
    }
}
