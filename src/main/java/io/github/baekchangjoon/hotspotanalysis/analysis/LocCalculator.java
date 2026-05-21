package io.github.baekchangjoon.hotspotanalysis.analysis;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Counts lines of code (LOC) for source files.
 *
 * <p>Phase 1 definition of LOC: the number of newline-terminated lines in the
 * file's UTF-8 content. This is intentionally simple — no comment stripping,
 * no blank-line stripping. The hotspot score multiplies this value by the
 * revision count, so any bias is the same across all files and does not
 * distort the ranking.</p>
 */
@Component
public class LocCalculator {

    /** Counts lines in a single source file. */
    public int countLines(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            throw new LocCalculationException(
                    "Source file does not exist: " + file,
                    new IllegalArgumentException("missing file"));
        }
        if (Files.isDirectory(file)) {
            throw new LocCalculationException(
                    "Path is a directory, not a file: " + file,
                    new IllegalArgumentException("directory"));
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return (int) lines.count();
        } catch (IOException e) {
            throw new LocCalculationException(
                    "Failed to read source file: " + file, e);
        }
    }

    /**
     * Bulk-counts lines for a set of repository-relative POSIX paths.
     *
     * <p>Paths that do not resolve to an existing file map to {@code 0} —
     * this is the right behaviour for files that were renamed or deleted
     * but still appear in commit history.</p>
     */
    public Map<String, Integer> countLines(Path repoRoot, Collection<String> relativePaths) {
        Objects.requireNonNull(repoRoot, "repoRoot");
        Objects.requireNonNull(relativePaths, "relativePaths");
        Map<String, Integer> result = new HashMap<>();
        for (String relative : relativePaths) {
            Path file = repoRoot.resolve(relative);
            if (Files.isRegularFile(file)) {
                result.put(relative, countLines(file));
            } else {
                result.put(relative, 0);
            }
        }
        return Map.copyOf(result);
    }
}
