package io.github.baekchangjoon.hotspotanalysis.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

/**
 * {@code hotspot init [--output path/to/hotspot.yml]} subcommand: writes the
 * bundled sample configuration ({@code templates/hotspot.example.yml}) to the
 * requested location so the user has a working starting point.
 */
@Component
@Command(
        name = "init",
        description = "Generate a sample hotspot.yml configuration file.",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    private static final String TEMPLATE_PATH = "/templates/hotspot.example.yml";

    @Option(names = {"-o", "--output"},
            defaultValue = "hotspot.yml",
            description = "Destination path (default: ./hotspot.yml).")
    private Path outputPath;

    @Option(names = {"-f", "--force"},
            description = "Overwrite the destination if it already exists.")
    private boolean force;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Path target = outputPath.toAbsolutePath().normalize();

        if (Files.isDirectory(target)) {
            // REPLACE_EXISTING would silently delete an empty directory and
            // put a file in its place — never do that, --force or not.
            err.println("ERROR: " + target + " is a directory — pass a file path, e.g. "
                    + target.resolve("hotspot.yml"));
            return 1;
        }
        if (Files.exists(target) && !force) {
            err.println("ERROR: " + target + " already exists. Use --force to overwrite.");
            return 1;
        }

        try (InputStream template = getClass().getResourceAsStream(TEMPLATE_PATH)) {
            if (template == null) {
                err.println("ERROR: bundled template not found at " + TEMPLATE_PATH);
                return 1;
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(template, target, StandardCopyOption.REPLACE_EXISTING);
            out.println("Wrote sample configuration to " + target);
            return 0;
        } catch (IOException e) {
            err.println("ERROR: failed to write template: " + e.getMessage());
            return 1;
        }
    }
}
