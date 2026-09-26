package io.github.baekchangjoon.hotspotanalysis.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleEncodingTest {

    @Test
    @DisplayName("no console override → Charset.defaultCharset() (UTF-8 on JDK 18+), same as Picocli")
    void fallsBackToDefaultCharset() {
        assertThat(ConsoleEncoding.resolve(null, StandardCharsets.UTF_8))
                .isEqualTo(StandardCharsets.UTF_8);
        assertThat(ConsoleEncoding.resolve("   ", StandardCharsets.UTF_8))
                .isEqualTo(StandardCharsets.UTF_8);
        assertThat(ConsoleEncoding.resolveStderr()).isEqualTo(Charset.defaultCharset());
    }

    @Test
    @DisplayName("a valid console override (Windows sun.stderr.encoding) wins over the default")
    void honoursConsoleOverride() {
        assertThat(ConsoleEncoding.resolve("ISO-8859-1", StandardCharsets.UTF_8))
                .isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("an unknown or malformed override never throws — falls back")
    void ignoresBrokenOverride() {
        assertThat(ConsoleEncoding.resolve("no-such-charset", StandardCharsets.UTF_8))
                .isEqualTo(StandardCharsets.UTF_8);
        assertThat(ConsoleEncoding.resolve("bad name!", StandardCharsets.UTF_8))
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("aligned stderr encodes non-ASCII warning text with the resolved charset instead of '?'")
    void alignedStderrKeepsNonAscii() {
        PrintStream original = System.err;
        // The replacement targets the real fd; assert the encoding contract
        // on an equivalent stream so the test stays hermetic.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream aligned = new PrintStream(bytes, true, ConsoleEncoding.resolveStderr())) {
            ConsoleEncoding.alignStderrWithPicocli();
            aligned.print("shallow clone — truncated");
            assertThat(bytes.toString(ConsoleEncoding.resolveStderr()))
                    .isEqualTo("shallow clone — truncated")
                    .doesNotContain("?");
            assertThat(original).isNotSameAs(System.err);
        } finally {
            System.setErr(original);
        }
    }
}
