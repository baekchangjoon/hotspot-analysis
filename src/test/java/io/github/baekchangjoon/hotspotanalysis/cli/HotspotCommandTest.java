package io.github.baekchangjoon.hotspotanalysis.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the root Picocli command surface in isolation (no Spring context).
 *
 * <p>Verifies the CLI contract defined for T1:
 * <ul>
 *   <li>{@code hotspot --version} prints a recognisable version line and exits 0.</li>
 *   <li>{@code hotspot --help} prints the standard usage block and exits 0.</li>
 *   <li>{@code hotspot} (no args) prints the usage block and exits 0.</li>
 * </ul>
 */
class HotspotCommandTest {

    private HotspotCommand command;
    private CommandLine cmd;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() {
        command = new HotspotCommand();
        cmd = new CommandLine(command);
        out = new StringWriter();
        err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
    }

    @Test
    @DisplayName("--version prints version information and exits with code 0")
    void shouldPrintVersion() {
        int exit = cmd.execute("--version");

        assertThat(exit).isZero();
        assertThat(out.toString())
                .contains("hotspot")
                .containsPattern("\\d+\\.\\d+\\.\\d+");  // version-agnostic
    }

    @Test
    @DisplayName("--help prints usage information and exits with code 0")
    void shouldPrintHelp() {
        int exit = cmd.execute("--help");

        assertThat(exit).isZero();
        assertThat(out.toString())
                .contains("Usage:")
                .contains("hotspot");
    }

    @Test
    @DisplayName("invocation without arguments prints usage and exits with code 0")
    void shouldPrintUsageWhenNoArguments() {
        int exit = cmd.execute();

        assertThat(exit).isZero();
        // No-args behaviour prints the usage block to stdout.
        assertThat(out.toString()).contains("Usage:");
    }

    @Test
    @DisplayName("unknown command exits with non-zero code and prints error")
    void shouldFailOnUnknownOption() {
        int exit = cmd.execute("--no-such-option");

        assertThat(exit).isNotZero();
        assertThat(err.toString()).isNotBlank();
    }
}
