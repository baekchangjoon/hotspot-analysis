package io.github.baekchangjoon.hotspotanalysis.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {

    @Test
    @DisplayName("writes the bundled sample to the requested destination (exit 0)")
    void shouldWriteSampleConfig(@TempDir Path tempDir) {
        Path output = tempDir.resolve("hotspot.yml");
        CommandLine cli = new CommandLine(new InitCommand());

        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exit = cli.execute("--output", output.toString());

        assertThat(exit).isZero();
        assertThat(Files.exists(output)).isTrue();
        String content = readFile(output);
        assertThat(content).contains("analysis:");
        assertThat(content).contains("scoring:");
        assertThat(sw.toString()).contains("Wrote sample configuration");
    }

    @Test
    @DisplayName("refuses to overwrite an existing file unless --force is set (exit 1)")
    void shouldRefuseOverwriteWithoutForce(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("hotspot.yml");
        Files.writeString(output, "# existing content\n");

        CommandLine cli = new CommandLine(new InitCommand());
        StringWriter errWriter = new StringWriter();
        cli.setErr(new PrintWriter(errWriter));

        int exit = cli.execute("--output", output.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(errWriter.toString()).contains("already exists");
        assertThat(readFile(output)).contains("# existing content");
    }

    @Test
    @DisplayName("overwrites an existing file when --force is provided (exit 0)")
    void shouldOverwriteWithForce(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("hotspot.yml");
        Files.writeString(output, "# existing content\n");

        CommandLine cli = new CommandLine(new InitCommand());

        int exit = cli.execute("--output", output.toString(), "--force");

        assertThat(exit).isZero();
        assertThat(readFile(output)).doesNotContain("# existing content");
        assertThat(readFile(output)).contains("analysis:");
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    @Test
    @DisplayName("F4: refuses to replace a directory even with --force (never silently deletes it)")
    void shouldRefuseDirectoryTargetEvenWithForce(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("adir");
        Files.createDirectory(dir);
        CommandLine cli = new CommandLine(new InitCommand());
        StringWriter ew = new StringWriter();
        cli.setErr(new PrintWriter(ew));

        int exit = cli.execute("--force", "--output", dir.toString());

        assertThat(exit).isEqualTo(1);
        assertThat(ew.toString()).contains("directory");
        assertThat(Files.isDirectory(dir)).isTrue(); // still a directory, not replaced
    }

}
