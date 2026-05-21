package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Test-only {@link VcsProvider} implementation backed by an in-memory commit
 * list. Filters commits by the given {@link WindowConfig} and returns them
 * in chronological order.
 *
 * <p>Used to verify that {@link VcsProviderContract} is internally consistent
 * before any real provider (T4, T5) is wired in.</p>
 */
final class InMemoryVcsProvider implements VcsProvider {

    private final List<CommitRecord> commits;

    InMemoryVcsProvider(List<CommitRecord> commits) {
        Objects.requireNonNull(commits, "commits must not be null");
        this.commits = commits.stream()
                .sorted(Comparator.comparing(CommitRecord::committedAt))
                .toList();
    }

    @Override
    public List<CommitRecord> loadCommits(WindowConfig window) {
        Instant lowerBound = resolveLowerBound(window);
        Instant upperBound = resolveUpperBound(window);
        return commits.stream()
                .filter(c -> !c.committedAt().isBefore(lowerBound))
                .filter(c -> !c.committedAt().isAfter(upperBound))
                .toList();
    }

    private static Instant resolveLowerBound(WindowConfig window) {
        if (window.since() != null) {
            return window.since().atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (window.days() != null) {
            return Instant.now().minus(Duration.ofDays(window.days()));
        }
        return Instant.EPOCH;
    }

    private static Instant resolveUpperBound(WindowConfig window) {
        if (window.until() != null) {
            // Inclusive end-of-day for the 'until' date.
            return window.until().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        }
        return Instant.now();
    }
}
