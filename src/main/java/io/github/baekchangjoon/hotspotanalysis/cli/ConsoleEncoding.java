package io.github.baekchangjoon.hotspotanalysis.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Aligns {@code System.err} with the charset Picocli uses for the command's
 * own writers, so warnings and the summary render the same way.
 *
 * <p>Since JDK 18 ({@code file.encoding} defaults to UTF-8) the JVM still
 * encodes {@code System.err} with {@code stderr.encoding}, which follows the
 * OS locale. Under a POSIX/C locale (CI containers, minimal Docker images)
 * that is US-ASCII, so every non-ASCII character in a warning is printed as
 * {@code ?} while the Picocli-routed summary in the same run prints fine.
 * Picocli resolves its charset as {@code sun.stdout.encoding} /
 * {@code sun.stderr.encoding} when set (Windows console), otherwise
 * {@link Charset#defaultCharset()}; this class applies the same rule.</p>
 */
public final class ConsoleEncoding {

    private ConsoleEncoding() {
    }

    /** Replaces {@code System.err} with a stream using {@link #resolveStderr()}. */
    public static void alignStderrWithPicocli() {
        System.setErr(new PrintStream(
                new FileOutputStream(FileDescriptor.err), true, resolveStderr()));
    }

    /** The charset Picocli would pick for stderr on this JVM. */
    public static Charset resolveStderr() {
        return resolve(System.getProperty("sun.stderr.encoding"), Charset.defaultCharset());
    }

    /**
     * @param consoleOverride the {@code sun.stderr.encoding} value, or null
     * @param fallback        the charset to use when there is no valid override
     */
    static Charset resolve(String consoleOverride, Charset fallback) {
        if (consoleOverride == null || consoleOverride.isBlank()) {
            return fallback;
        }
        try {
            return Charset.forName(consoleOverride.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return fallback;
        }
    }
}
