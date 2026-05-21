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
 * </ul>
 */
@Component
@Command(
        name = "analyze",
        description = "Analyse a repository and emit hotspot reports.",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"},
            required = true,
            description = "Path to the YAML configuration file.")
    private Path configPath;

    @Option(names = {"-q", "--quiet"},
            description = "Suppress the summary output on stdout.")
    private boolean quiet;

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
            return 1;
        }

        try {
            AnalysisConfig config = configLoader.load(configPath);
            AnalysisResult result = analyzer.analyze(config);
            outputDispatcher.dispatch(result, config.output());
            if (!quiet) {
                printSummary(out, result);
            }
            return 0;
        } catch (ConfigLoadException e) {
            err.println("ERROR: invalid configuration: " + e.getMessage());
            return 1;
        } catch (UnsupportedOperationException e) {
            err.println("ERROR: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("ERROR: analysis failed: " + e.getMessage());
            return 1;
        }
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
