package io.github.baekchangjoon.hotspotanalysis;

import io.github.baekchangjoon.hotspotanalysis.cli.AnalyzeCommand;
import io.github.baekchangjoon.hotspotanalysis.cli.HotspotCommand;
import io.github.baekchangjoon.hotspotanalysis.cli.InitCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot entry point for the {@code hotspot} CLI.
 *
 * <p>Bridges Spring's lifecycle with Picocli command dispatch:
 * the Spring container constructs commands (so they may use DI),
 * and Picocli parses arguments and invokes the matched command.
 * Picocli's exit code is propagated through Spring Boot's
 * {@link ExitCodeGenerator} so the JVM exits with the proper status.</p>
 */
@SpringBootApplication
public class HotspotApplication implements CommandLineRunner, ExitCodeGenerator {

    // Built once at construction (not per run): the command beans are Spring
    // singletons, so picocli must capture their clean initial @Option/@Parameters
    // values once. Rebuilding the CommandLine on every run() would re-capture the
    // previous run's leftover field values as "initial", leaking option state
    // across invocations (visible when one process issues several commands, e.g.
    // the @SpringBootTest end-to-end suite).
    private final CommandLine cli;
    private int exitCode = 0;

    public HotspotApplication(IFactory picocliFactory,
                              HotspotCommand rootCommand,
                              AnalyzeCommand analyzeCommand,
                              InitCommand initCommand) {
        this.cli = new CommandLine(rootCommand, picocliFactory)
                .addSubcommand("analyze", analyzeCommand)
                .addSubcommand("init", initCommand);
    }

    @Override
    public void run(String... args) {
        this.exitCode = cli.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(HotspotApplication.class, args)));
    }
}
