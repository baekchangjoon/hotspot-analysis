package io.github.baekchangjoon.hotspotanalysis.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a fully-populated {@link AnalysisConfig} by inspecting a project on
 * disk, so {@code hotspot analyze} can run with no configuration file.
 *
 * <p>Symmetric to {@link ConfigLoader}: where the loader parses a YAML file,
 * the synthesizer derives the same record tree from filesystem signals. It has
 * no Spring collaborators; {@code @Component} only wires it into
 * {@code AnalyzeCommand}'s constructor injection.</p>
 */
@Component
public class ConfigSynthesizer {

    /** Max directory depth (below base) at which a module root may sit. */
    private static final int MAX_MODULE_DEPTH = 3; // group/module → depth 2
    private static final List<String> SKIP_DIRS =
            List.of("build", "target", ".git", "node_modules", ".gradle");
    private static final List<String> BUILD_FILES =
            List.of("build.gradle", "build.gradle.kts", "pom.xml");
    private static final List<String> SPRING_MARKERS =
            List.of("spring-boot-starter-web", "spring-webmvc", "spring-web");
    // Java NIO "**" does NOT match zero path segments, so "**/build/**" alone
    // would not exclude a ROOT-level build/ dir. Root-level variants are added
    // so generated/compiled output never leaks into the multi-module include.
    private static final List<String> EXCLUDES = List.of(
            "**/generated/**", "**/test/**",
            "**/build/**", "build/**",
            "**/target/**", "target/**");
    private static final List<String> CLASSPATH_CANDIDATES =
            List.of("build/classes/java/main", "target/classes", "build/libs");
    private static final List<String> JACOCO_CANDIDATES = List.of(
            "build/reports/jacoco/test/jacocoTestReport.xml",
            "target/site/jacoco/jacoco.xml");

    public AnalysisConfig synthesize(Path basePath) {
        Path base = basePath.toAbsolutePath().normalize();
        requireGitWorkTree(base);

        ModuleLayout layout = detectModules(base);

        TargetConfig target = new TargetConfig(
                TargetConfig.TargetType.LOCAL_GIT, base.toString(), null);
        WindowConfig window = new WindowConfig(null, null, 365);
        ScopeConfig scope = new ScopeConfig(
                List.of(ScopeConfig.Granularity.FILE, ScopeConfig.Granularity.METHOD),
                List.of(layout.includeGlob()),
                EXCLUDES);
        ScoringConfig scoring = new ScoringConfig(90, Boolean.FALSE);
        ApiAnalysisConfig api = new ApiAnalysisConfig(
                detectSpring(base, layout.moduleRoots()),
                ApiAnalysisConfig.SharedComponentMode.BOTH,
                detectClasspathDirs(base, layout.moduleRoots()));
        String jacoco = detectJacoco(base);

        AnalysisSection analysis = new AnalysisSection(
                target, window, scope, scoring, api, jacoco);
        OutputConfig output = new OutputConfig(
                List.of(OutputConfig.OutputFormat.CSV, OutputConfig.OutputFormat.YAML,
                        OutputConfig.OutputFormat.MD, OutputConfig.OutputFormat.HTML),
                "./hotspot-report", 50, OutputConfig.ApiLayout.BOTH, Boolean.FALSE);

        return new AnalysisConfig(analysis, output);
    }

    private void requireGitWorkTree(Path base) {
        // Accept .git as either a directory (normal checkout) or a pointer file
        // (linked worktree / submodule). JGit's Git.open resolves both.
        if (!Files.exists(base.resolve(".git"))) {
            throw new ConfigSynthesisException(
                    "not a git work tree: " + base
                            + ". Pass a path to a git repo, or use --config.");
        }
    }

    private ModuleLayout detectModules(Path base) {
        List<Path> roots = new ArrayList<>();
        collectModuleRoots(base, 0, roots);
        if (roots.isEmpty()) {
            throw new ConfigSynthesisException(
                    "no Java sources found under " + base
                            + " (looked for src/main/java). Pass a [path], or use"
                            + " --config for a custom scope.");
        }
        boolean single = roots.size() == 1 && roots.get(0).equals(base);
        String glob = single ? "src/main/java/**/*.java" : "**/src/main/java/**/*.java";
        return new ModuleLayout(glob, roots);
    }

    private void collectModuleRoots(Path dir, int depth, List<Path> roots) {
        if (Files.isDirectory(dir.resolve("src/main/java"))) {
            roots.add(dir);
            return; // a module root; do not descend further
        }
        if (depth >= MAX_MODULE_DEPTH) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory)
                    .filter(c -> !SKIP_DIRS.contains(c.getFileName().toString()))
                    .sorted()
                    .forEach(c -> collectModuleRoots(c, depth + 1, roots));
        } catch (IOException e) {
            // Surface as a clean CLI error (exit 1), not an uncaught crash.
            throw new ConfigSynthesisException(
                    "failed to scan for Java sources under " + dir + ": " + e.getMessage());
        }
    }

    private boolean detectSpring(Path base, List<Path> moduleRoots) {
        // LinkedHashSet dedups the single-module case where moduleRoots == [base].
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        dirs.add(base);
        dirs.addAll(moduleRoots);
        for (Path dir : dirs) {
            for (String bf : BUILD_FILES) {
                Path f = dir.resolve(bf);
                if (Files.isRegularFile(f) && containsSpringMarker(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsSpringMarker(Path file) {
        try {
            String content = Files.readString(file);
            return SPRING_MARKERS.stream().anyMatch(content::contains);
        } catch (IOException e) {
            return false; // unreadable / non-UTF8 build file → treat as no marker
        }
    }

    private List<String> detectClasspathDirs(Path base, List<Path> moduleRoots) {
        // Resolved by CallGraphBuilder against repoRoot (= base), so paths are
        // returned relative to base with forward slashes. For multi-module
        // projects each module's compiled-class dir is probed (e.g.
        // moduleA/build/classes/java/main).
        List<String> dirs = new ArrayList<>();
        for (Path root : moduleRoots) {
            String prefix = base.relativize(root).toString().replace('\\', '/');
            for (String c : CLASSPATH_CANDIDATES) {
                if (Files.isDirectory(root.resolve(c))) {
                    dirs.add(prefix.isEmpty() ? c : prefix + "/" + c);
                }
            }
        }
        return dirs;
    }

    private String detectJacoco(Path base) {
        for (String c : JACOCO_CANDIDATES) {
            if (Files.isRegularFile(base.resolve(c))) {
                return c;
            }
        }
        return null;
    }

    private record ModuleLayout(String includeGlob, List<Path> moduleRoots) {
    }
}
