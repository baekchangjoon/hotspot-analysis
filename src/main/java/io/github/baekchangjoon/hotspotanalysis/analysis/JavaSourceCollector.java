package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.config.ScopeConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Walks a repository working tree and returns the set of Java source files
 * matching the scope's glob patterns.
 *
 * <p>Matching follows {@link FileSystem#getPathMatcher(String) "glob:"}
 * semantics. {@code **} matches across directory boundaries; a leading
 * {@code **} is therefore the right way to write "anywhere".</p>
 */
@Component
public class JavaSourceCollector {

    public List<Path> collect(Path repoRoot, ScopeConfig scope) {
        FileSystem fs = repoRoot.getFileSystem();
        List<PathMatcher> includes = scope.include().stream()
                .map(g -> fs.getPathMatcher("glob:" + g))
                .toList();
        List<PathMatcher> excludes = scope.exclude().stream()
                .map(g -> fs.getPathMatcher("glob:" + g))
                .toList();

        List<Path> collected = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        Path relative = repoRoot.relativize(p);
                        if (matchesAny(relative, includes) && !matchesAny(relative, excludes)) {
                            collected.add(p);
                        }
                    });
        } catch (IOException e) {
            throw new SourceScanException(
                    "Failed to walk repository root: " + repoRoot, e);
        }
        Collections.sort(collected);
        return List.copyOf(collected);
    }

    private static boolean matchesAny(Path relative, List<PathMatcher> matchers) {
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(relative)) {
                return true;
            }
        }
        return false;
    }
}
