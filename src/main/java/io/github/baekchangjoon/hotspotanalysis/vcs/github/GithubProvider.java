package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProvider;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.FileChange;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link VcsProvider} that delegates commit listing to a {@link GithubClient},
 * then converts each {@link GhCommit} into the source-agnostic
 * {@link CommitRecord} consumed by the rest of the pipeline.
 *
 * <p>The conversion logic is identical regardless of how commits are fetched,
 * which is why {@link GithubClient} is an interface: production uses the real
 * GitHub REST API, tests use an in-memory fake.</p>
 */
public class GithubProvider implements VcsProvider {

    private final GithubClient client;

    public GithubProvider(GithubClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<CommitRecord> loadCommits(WindowConfig window) {
        Instant lower = resolveLowerBound(window);
        Instant upper = resolveUpperBound(window);

        return client.listCommits(lower, upper).stream()
                .sorted(Comparator.comparing(GhCommit::committedAt))
                .map(GithubProvider::toCommitRecord)
                .toList();
    }

    private static CommitRecord toCommitRecord(GhCommit gh) {
        List<FileChange> changes = gh.files().stream()
                .map(GithubProvider::toFileChange)
                .toList();
        return new CommitRecord(
                gh.sha(),
                gh.author(),
                gh.committedAt(),
                gh.message(),
                changes);
    }

    private static FileChange toFileChange(GhFileChange file) {
        ChangeType type = mapStatus(file.status());
        return switch (type) {
            case RENAMED -> new FileChange(
                    file.filename(), file.previousFilename(),
                    file.additions(), file.deletions(), ChangeType.RENAMED);
            case DELETED -> new FileChange(
                    file.filename(), null,
                    file.additions(), file.deletions(), ChangeType.DELETED);
            default -> new FileChange(
                    file.filename(), null,
                    file.additions(), file.deletions(), type);
        };
    }

    private static ChangeType mapStatus(String status) {
        return switch (status.toLowerCase()) {
            case "added" -> ChangeType.ADDED;
            case "removed", "deleted" -> ChangeType.DELETED;
            case "renamed", "copied" -> ChangeType.RENAMED;
            default -> ChangeType.MODIFIED;
        };
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
            return window.until().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        }
        return Instant.now();
    }
}
