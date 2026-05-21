package io.github.baekchangjoon.hotspotanalysis.cli;

import io.github.baekchangjoon.hotspotanalysis.analysis.HotspotAnalyzer;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoadException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoader;
import io.github.baekchangjoon.hotspotanalysis.output.OutputDispatcher;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code hotspot analyze --config <file>} subcommand: loads a YAML
 * configuration, runs the analysis pipeline, writes the requested output
 * formats, and prints a short summary.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>{@code 0} — analysis completed and outputs were written</li>
 *   <li>{@code 1} — fatal error (configuration invalid, repository unreachable,
 *       parser failure, …)</li>
 *   <li>{@code 2} — unknown / unsupported configuration option (handled by Picocli)</li>
 *   <li>{@code 3} — {@code --strict} was set and the analysis produced an
 *       empty result (zero commits in window or zero files matching scope)</li>
 * </ul>
 */
@Component
@Command(
        name = "analyze",
        description = "Analyse a repository and emit hotspot reports.",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_STRICT_EMPTY = 3;

    @Option(names = {"-c", "--config"},
            required = true,
            description = "Path to the YAML configuration file.")
    private Path configPath;

    @Option(names = {"-q", "--quiet"},
            description = "Suppress the summary output on stdout.")
    private boolean quiet;

    @Option(names = {"-s", "--strict"},
            description = "Exit with code 3 when the analysis result is empty "
                    + "(zero commits in window or zero files matching scope). "
                    + "Useful for CI to fail loudly instead of producing empty reports.")
    private boolean strict;

    @Spec
    private CommandSpec spec;

    private final ConfigLoader configLoader;
    private final HotspotAnalyzer analyzer;
    private final OutputDispatcher outputDispatcher;

    public AnalyzeCommand(ConfigLoader configLoader,
                          HotspotAnalyzer analyzer,
                          OutputDispatcher outputDispatcher) {
        this.configLoader = configLoader;
        this.analyzer = analyzer;
        this.outputDispatcher = outputDispatcher;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.isRegularFile(configPath)) {
            err.println("ERROR: configuration file not found: " + configPath);
            return EXIT_FAILURE;
        }

        try {
            AnalysisConfig config = configLoader.load(configPath);
            AnalysisResult result = analyzer.analyze(config);
            outputDispatcher.dispatch(result, config.output());
            if (!quiet) {
                printSummary(out, result);
            }
            if (strict && isEmpty(result)) {
                printStrictFailure(err, result);
                return EXIT_STRICT_EMPTY;
            }
            return EXIT_OK;
        } catch (ConfigLoadException e) {
            err.println("ERROR: invalid configuration: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (UnsupportedOperationException e) {
            err.println("ERROR: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (RuntimeException e) {
            err.println("ERROR: analysis failed: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    private static boolean isEmpty(AnalysisResult result) {
        return result.meta().totalCommits() == 0 || result.meta().totalFiles() == 0;
    }

    private static void printStrictFailure(PrintWriter err, AnalysisResult result) {
        err.println("ERROR: --strict was set but the analysis produced an empty result.");
        err.println("  Commits matching window: " + result.meta().totalCommits());
        err.println("  Files matching scope:   " + result.meta().totalFiles());
        err.println("  Methods extracted:      " + result.meta().totalMethods());
        err.println("Hints:");
        err.println("  - Multi-module projects need '**/' in scope.include "
                + "(e.g. '**/src/main/java/**/*.java').");
        err.println("  - Widen analysis.window.days or switch to absolute "
                + "since/until covering actual commit activity.");
        err.println("  - Verify analysis.target.path points at a directory "
                + "that contains a .git/ folder.");
    }

    private static void printSummary(PrintWriter out, AnalysisResult result) {
        out.println("Hotspot analysis complete.");
        out.println("  Target:      " + result.meta().targetDescription());
        out.println("  Commits:     " + result.meta().totalCommits());
        out.println("  Files:       " + result.meta().totalFiles());
        out.println("  Methods:     " + result.meta().totalMethods());
        if (!result.fileHotspots().isEmpty()) {
            FileHotspot top = result.fileHotspots().get(0);
            out.printf("  Top file:    %s (rev=%d, loc=%d, score=%.0f)%n",
                    top.path(), top.revisions(), top.loc(), top.score());
        }
    }
}
