package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProvider;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderContract;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the shared {@code VcsProviderContract} against {@link GithubProvider},
 * driven by an in-memory {@link FakeGithubClient}. Production HTTP behaviour
 * is verified separately by {@code KohsukeGithubClientWireMockTest}.
 */
class GithubProviderTest extends VcsProviderContract {

    private static final Instant T1 = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T11:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-15T12:00:00Z");

    @Override
    protected VcsProvider providerWithKnownHistory() {
        return new GithubProvider(new FakeGithubClient(List.of(
                new GhCommit("sha1", "alice", T1, "c1: init", List.of(
                        new GhFileChange("src/Foo.java", null, "added", 5, 0))),
                new GhCommit("sha2", "bob", T2, "c2: tweak", List.of(
                        new GhFileChange("src/Foo.java", null, "modified", 2, 1))),
                new GhCommit("sha3", "alice", T3, "c3: add Bar", List.of(
                        new GhFileChange("src/Bar.java", null, "added", 3, 0)))
        )));
    }

    @Override
    protected WindowConfig fullWindow() {
        return new WindowConfig(LocalDate.parse("2026-01-15"), LocalDate.parse("2026-01-15"), null);
    }

    @Override
    protected WindowConfig emptyWindow() {
        return new WindowConfig(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-12-31"), null);
    }

    @Override
    protected int expectedCommitCount() {
        return 3;
    }

    // ---------------------------------------------------------------
    // Additional GitHub-specific status-mapping tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("maps GitHub status 'added' to ChangeType.ADDED")
    void shouldMapAddedStatus() {
        CommitRecord commit = singleFileCommit("added", "src/A.java", null);

        assertThat(commit.changes().get(0).type()).isEqualTo(ChangeType.ADDED);
    }

    @Test
    @DisplayName("maps GitHub status 'removed' to ChangeType.DELETED")
    void shouldMapRemovedStatus() {
        CommitRecord commit = singleFileCommit("removed", "src/A.java", null);

        assertThat(commit.changes().get(0).type()).isEqualTo(ChangeType.DELETED);
        assertThat(commit.changes().get(0).previousPath()).isNull();
    }

    @Test
    @DisplayName("maps GitHub status 'renamed' to ChangeType.RENAMED with previousPath")
    void shouldMapRenamedStatus() {
        CommitRecord commit = singleFileCommit("renamed", "src/B.java", "src/A.java");

        assertThat(commit.changes().get(0).type()).isEqualTo(ChangeType.RENAMED);
        assertThat(commit.changes().get(0).path()).isEqualTo("src/B.java");
        assertThat(commit.changes().get(0).previousPath()).isEqualTo("src/A.java");
    }

    @Test
    @DisplayName("maps GitHub status 'modified' to ChangeType.MODIFIED")
    void shouldMapModifiedStatus() {
        CommitRecord commit = singleFileCommit("modified", "src/A.java", null);

        assertThat(commit.changes().get(0).type()).isEqualTo(ChangeType.MODIFIED);
    }

    @Test
    @DisplayName("treats unknown status values as MODIFIED")
    void shouldDefaultUnknownStatusToModified() {
        CommitRecord commit = singleFileCommit("changed", "src/A.java", null);

        assertThat(commit.changes().get(0).type()).isEqualTo(ChangeType.MODIFIED);
    }

    private CommitRecord singleFileCommit(String status, String filename, String previousFilename) {
        Instant instant = Instant.parse("2026-01-15T10:00:00Z");
        GithubProvider provider = new GithubProvider(new FakeGithubClient(List.of(
                new GhCommit("sha", "alice", instant, "msg", List.of(
                        new GhFileChange(filename, previousFilename, status, 1, 0))))));
        return provider.loadCommits(fullWindow()).get(0);
    }
}
