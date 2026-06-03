package io.github.baekchangjoon.hotspotanalysis.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root command for the {@code hotspot} CLI.
 *
 * <p>This command itself takes no action. Functionality is provided by
 * subcommands (e.g. {@code analyze}, {@code init}) which are wired in
 * subsequent tasks. When invoked without a subcommand, the usage block
 * is printed.</p>
 *
 * <p>{@link Command#mixinStandardHelpOptions} adds the standard
 * {@code -h/--help} and {@code -V/--version} flags.</p>
 */
@Component
@Command(
        name = "hotspot",
        mixinStandardHelpOptions = true,
        version = "hotspot 0.1.0",
        description = "Hotspot analysis CLI for Java codebases.",
        synopsisHeading = "%nUsage: ",
        descriptionHeading = "%nDescription:%n  ",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n"
)
public class HotspotCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
