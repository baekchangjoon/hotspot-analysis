package io.github.baekchangjoon.hotspotanalysis.output;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a git remote URL into a browsable "blob" URL prefix for the report's
 * source links. Only GitHub- and GitLab-style hosts are recognised, because
 * both serve {@code /<owner>/<repo>/blob/<rev>/<path>#L10-L20}; any other host
 * yields no link rather than a guessed, broken one.
 *
 * <p>Credentials embedded in the remote ({@code https://token@host/...}) are
 * always dropped — the report is meant to be shared.</p>
 */
public final class SourceLinks {

    /** {@code git@host:owner/repo.git} (scp-like syntax). */
    private static final Pattern SCP_LIKE =
            Pattern.compile("^[^@/\\s]+@([^:/\\s]+):(.+)$");
    /** {@code scheme://[user[:pass]@]host[:port]/owner/repo.git}. */
    private static final Pattern URL_LIKE =
            Pattern.compile("^(?:https?|ssh|git)://(?:[^@/\\s]+@)?([^:/\\s]+)(?::\\d+)?/(.+)$");
    /** One path segment of {@code owner/repo}; segments are checked one by one, never as a nested repetition. */
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9._~-]+$");
    private static final int MAX_REMOTE_LENGTH = 2048;
    private static final Pattern SAFE_HOST = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final Pattern SAFE_REVISION = Pattern.compile("^[0-9a-fA-F]{7,64}$");

    private SourceLinks() {
    }

    /**
     * @return e.g. {@code https://github.com/acme/app/blob/<revision>/}, or
     *         empty when the remote is missing, malformed or on an
     *         unrecognised host
     */
    public static Optional<String> blobBase(String remoteUrl, String revision) {
        if (remoteUrl == null || revision == null || remoteUrl.length() > MAX_REMOTE_LENGTH
                || !SAFE_REVISION.matcher(revision).matches()) {
            return Optional.empty();
        }
        String trimmed = remoteUrl.trim();
        Matcher matcher = URL_LIKE.matcher(trimmed);
        if (!matcher.matches()) {
            matcher = SCP_LIKE.matcher(trimmed);
            if (!matcher.matches()) {
                return Optional.empty();
            }
        }
        String host = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
        String repoPath = stripSuffix(stripSuffix(matcher.group(2), "/"), ".git");
        if (!isRecognisedHost(host) || !isSafeRepoPath(repoPath)) {
            return Optional.empty();
        }
        return Optional.of("https://" + host + "/" + repoPath + "/blob/" + revision + "/");
    }

    private static boolean isRecognisedHost(String host) {
        if (!SAFE_HOST.matcher(host).matches()) {
            return false;
        }
        return host.equals("github.com") || host.equals("gitlab.com")
                || host.startsWith("github.") || host.startsWith("gitlab.");
    }

    /** At least {@code owner/repo}, every segment made only of URL-safe characters. */
    private static boolean isSafeRepoPath(String repoPath) {
        String[] segments = repoPath.split("/", -1);
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (!SAFE_PATH_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
