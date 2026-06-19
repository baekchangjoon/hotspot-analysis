package io.github.baekchangjoon.hotspotanalysis.cli;

import io.github.baekchangjoon.hotspotanalysis.analysis.HotspotAnalyzer;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.FileHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.AnalysisConfig;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoadException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigLoader;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializeException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSerializer;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesisException;
import io.github.baekchangjoon.hotspotanalysis.config.ConfigSynthesizer;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import io.github.baekchangjoon.hotspotanalysis.output.OutputDispatcher;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code hotspot analyze [path] [--config <file>]} subcommand.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>File mode</b> — {@code --config <file>} loads a YAML configuration
 *       (unchanged behaviour).</li>
 *   <li><b>Zero-config mode</b> — no {@code --config}; the configuration is
 *       synthesized from {@code [path]} (default: current directory) by
 *       {@link ConfigSynthesizer}.</li>
 * </ul>
 * {@code --print-config} (zero-config only) prints the synthesized YAML to
 * stdout and exits without analysing.</p>
 *
 * <p>Exit codes: {@code 0} success; {@code 1} fatal error (bad config,
 * unreadable repo, illegal flag combination, synthesis failure); {@code 3}
 * {@code --strict} with an empty result.</p>
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

    @Parameters(index = "0", arity = "0..1",
            description = "Repository to analyse in zero-config mode "
                    + "(default: current directory). Mutually exclusive with --config.")
    private Path path;

    @Option(names = {"-c", "--config"},
            description = "Path to the YAML configuration file. If omitted, the "
                    + "configuration is auto-detected from [path].")
    private Path configPath;

    @Option(names = {"--print-config"},
            description = "Print the auto-detected configuration as YAML and exit "
                    + "(zero-config mode only).")
    private boolean printConfig;

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
    private final ConfigSynthesizer configSynthesizer;
    private final ConfigSerializer configSerializer;
    private final HotspotAnalyzer analyzer;
    private final OutputDispatcher outputDispatcher;

    public AnalyzeCommand(ConfigLoader configLoader,
                          ConfigSynthesizer configSynthesizer,
                          ConfigSerializer configSerializer,
                          HotspotAnalyzer analyzer,
                          OutputDispatcher outputDispatcher) {
        this.configLoader = configLoader;
        this.configSynthesizer = configSynthesizer;
        this.configSerializer = configSerializer;
        this.analyzer = analyzer;
        this.outputDispatcher = outputDispatcher;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        // 1. Preflight validation (before any I/O).
        if (configPath != null && path != null) {
            err.println("ERROR: --config and [path] are mutually exclusive.");
            return EXIT_FAILURE;
        }
        if (configPath != null && printConfig) {
            err.println("ERROR: --print-config applies only to zero-config mode (remove --config).");
            return EXIT_FAILURE;
        }

        // 2. Build the config.
        AnalysisConfig config;
        boolean zeroConfig = configPath == null;
        try {
            if (zeroConfig) {
                Path base = (path != null) ? path : Path.of("").toAbsolutePath();
                config = configSynthesizer.synthesize(base);
                if (printConfig) {
                    out.print(configSerializer.serialize(config));
                    out.flush();
                    return EXIT_OK;
                }
                if (!quiet) {
                    printDetectionSummary(err, config);
                }
            } else {
                if (!Files.isRegularFile(configPath)) {
                    err.println("ERROR: configuration file not found: " + configPath);
                    return EXIT_FAILURE;
                }
                config = configLoader.load(configPath);
            }
        } catch (ConfigSynthesisException | ConfigSerializeException e) {
            err.println("ERROR: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (ConfigLoadException e) {
            err.println("ERROR: invalid configuration: " + e.getMessage());
            return EXIT_FAILURE;
        }

        // 3. Analyse.
        try {
            AnalysisResult result = analyzer.analyze(config);
            boolean apiEnabled = config.analysis().apiAnalysis() != null
                    && config.analysis().apiAnalysis().enabled();
            boolean excludeCoverage = config.analysis().scoring() != null
                    && Boolean.TRUE.equals(config.analysis().scoring().excludeCoverage());
            outputDispatcher.dispatch(result, config.output(), apiEnabled, excludeCoverage);
            if (!quiet) {
                printSummary(out, result, config.output());
            }
            if (strict && isEmpty(result)) {
                printStrictFailure(err, result);
                return EXIT_STRICT_EMPTY;
            }
            return EXIT_OK;
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

    private static void printDetectionSummary(PrintWriter err, AnalysisConfig config) {
        String include = config.analysis().scope().include().get(0);
        boolean multiModule = include.startsWith("**/");
        String jacoco = config.analysis().jacocoReportPath();
        boolean api = config.analysis().apiAnalysis() != null
                && config.analysis().apiAnalysis().enabled();
        err.println("Detected (zero-config):");
        err.println("  Repo:           " + config.analysis().target().path() + " (.git found)");
        err.println("  Module layout:  " + (multiModule ? "multi-module (**/src/main/java)"
                : "single-module (src/main/java)"));
        err.println("  JaCoCo:         " + (jacoco != null ? jacoco : "none"));
        err.println("  API analysis:   " + (api ? "ON (spring-web detected)"
                : "OFF (no spring-web on build)"));
        if (!api) {
            err.println("                  (Spring web app hidden behind a BOM/version catalog? "
                    + "enable it via --print-config + analysis.apiAnalysis.enabled)");
        }
        err.println("  → run with --print-config to save this as hotspot.yml");
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

    private static void printSummary(PrintWriter out, AnalysisResult result, OutputConfig output) {
        out.println("Hotspot analysis complete.");
        out.println("  Target:      " + result.meta().targetDescription());
        out.println("  Commits:     " + result.meta().totalCommits());
        out.println("  Files:       " + result.meta().totalFiles());
        out.println("  Methods:     " + result.meta().totalMethods());
        var files = result.fileHotspots();
        if (!files.isEmpty()) {
            // fileHotspots are already sorted by composite score, descending.
            out.println("  Top hotspots (by composite score):");
            int n = Math.min(3, files.size());
            for (int i = 0; i < n; i++) {
                FileHotspot f = files.get(i);
                out.printf("    %d. %s (composite=%.1f, rev=%d, loc=%d)%n",
                        i + 1, f.path(), f.compositeScore(), f.revisions(), f.loc());
            }
        }
        if (output.formats().contains(OutputConfig.OutputFormat.HTML)) {
            Path html = Path.of(output.path()).toAbsolutePath().normalize().resolve("hotspots.html");
            out.println("  Report:      " + html);
        }
    }
}
