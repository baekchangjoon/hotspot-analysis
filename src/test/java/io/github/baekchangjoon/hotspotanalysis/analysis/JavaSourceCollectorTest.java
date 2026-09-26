package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.config.ScopeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceCollectorTest {

    @Test
    @DisplayName("root and module globs together collect each production file once")
    void rootAndModuleGlobsCollectEachFileOnce(@TempDir Path repo) throws Exception {
        // A leading "**" does not match zero path segments, so the root tree
        // needs its own glob. wiremock dogfooding analysed 3 of 1,327 files
        // when only one of the two was used.
        Files.createDirectories(repo.resolve("src/main/java/com/example"));
        Files.createDirectories(repo.resolve("core/src/main/java/com/example"));
        Files.createDirectories(repo.resolve("src/test/java/com/example"));
        Files.writeString(repo.resolve("src/main/java/com/example/Root.java"), "class Root {}");
        Files.writeString(repo.resolve("core/src/main/java/com/example/Mod.java"), "class Mod {}");
        Files.writeString(repo.resolve("src/test/java/com/example/Ignored.java"), "class Ignored {}");

        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE),
                List.of("src/main/java/**/*.java", "**/src/main/java/**/*.java"),
                List.of());

        List<String> relative = new JavaSourceCollector().collect(repo, scope).stream()
                .map(path -> repo.relativize(path).toString().replace('\\', '/'))
                .toList();

        assertThat(relative).containsExactlyInAnyOrder(
                "src/main/java/com/example/Root.java",
                "core/src/main/java/com/example/Mod.java");
    }
}
