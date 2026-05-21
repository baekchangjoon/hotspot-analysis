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

    private final IFactory picocliFactory;
    private final HotspotCommand rootCommand;
    private final AnalyzeCommand analyzeCommand;
    private final InitCommand initCommand;
    private int exitCode = 0;

    public HotspotApplication(IFactory picocliFactory,
                              HotspotCommand rootCommand,
                              AnalyzeCommand analyzeCommand,
                              InitCommand initCommand) {
        this.picocliFactory = picocliFactory;
        this.rootCommand = rootCommand;
        this.analyzeCommand = analyzeCommand;
        this.initCommand = initCommand;
    }

    @Override
    public void run(String... args) {
        CommandLine cli = new CommandLine(rootCommand, picocliFactory)
                .addSubcommand("analyze", analyzeCommand)
                .addSubcommand("init", initCommand);
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
