package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural contract that every {@link VcsProvider} implementation must
 * satisfy.
 *
 * <p>Concrete tests (LocalGitProviderTest, GithubProviderTest, in-memory
 * fakes) extend this class and supply the fixtures via the abstract hooks.
 * If a new contract test is added here, all implementations are checked
 * automatically by extension.</p>
 */
public abstract class VcsProviderContract {

    /**
     * Returns a provider backed by a repository whose entire commit history
     * fits into {@link #fullWindow()} and whose commit count is
     * {@link #expectedCommitCount()}.
     */
    protected abstract VcsProvider providerWithKnownHistory();

    /** A window broad enough to cover all commits in the fixture. */
    protected abstract WindowConfig fullWindow();

    /** A window that excludes every commit in the fixture (e.g. far future). */
    protected abstract WindowConfig emptyWindow();

    /** The number of commits the fixture is expected to expose for {@link #fullWindow()}. */
    protected abstract int expectedCommitCount();

    @Test
    @DisplayName("loadCommits returns a non-null list for the full window")
    void loadCommitsReturnsNonNull() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> commits = provider.loadCommits(fullWindow());

        assertThat(commits).isNotNull();
    }

    @Test
    @DisplayName("loadCommits returns the expected commit count for the full window")
    void loadCommitsReturnsExpectedCount() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> commits = provider.loadCommits(fullWindow());

        assertThat(commits).hasSize(expectedCommitCount());
    }

    @Test
    @DisplayName("loadCommits returns an empty list for a window that matches no commits")
    void loadCommitsReturnsEmptyForUnmatchedWindow() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> commits = provider.loadCommits(emptyWindow());

        assertThat(commits).isEmpty();
    }

    @Test
    @DisplayName("each returned commit satisfies the CommitRecord invariants")
    void everyCommitHasValidStructure() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> commits = provider.loadCommits(fullWindow());

        assertThat(commits).allSatisfy(commit -> {
            assertThat(commit.hash()).isNotBlank();
            assertThat(commit.author()).isNotNull();
            assertThat(commit.committedAt()).isNotNull();
            assertThat(commit.message()).isNotNull();
            assertThat(commit.changes()).isNotNull();
        });
    }

    @Test
    @DisplayName("repeated calls with the same window produce identical results")
    void loadCommitsIsIdempotent() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> first = provider.loadCommits(fullWindow());
        List<CommitRecord> second = provider.loadCommits(fullWindow());

        assertThat(second).hasSize(first.size());
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i).hash()).isEqualTo(first.get(i).hash());
        }
    }

    @Test
    @DisplayName("commits are returned in chronological order (oldest first)")
    void commitsAreChronological() {
        VcsProvider provider = providerWithKnownHistory();

        List<CommitRecord> commits = provider.loadCommits(fullWindow());

        for (int i = 1; i < commits.size(); i++) {
            assertThat(commits.get(i).committedAt())
                    .isAfterOrEqualTo(commits.get(i - 1).committedAt());
        }
    }
}
