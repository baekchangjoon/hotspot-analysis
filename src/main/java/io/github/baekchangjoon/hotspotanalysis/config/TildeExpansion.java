package io.github.baekchangjoon.hotspotanalysis.config;

/**
 * Expands a leading {@code ~/} to the user's home directory.
 *
 * <p>Config values like {@code output.path: ~/reports} arrive unexpanded (the
 * shell only expands unquoted {@code ~}); without this, a literal {@code ~}
 * directory appears under the cwd — a classic footgun when users later try to
 * delete it.</p>
 */
public final class TildeExpansion {

    private TildeExpansion() {
    }

    public static String expand(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("~")) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
