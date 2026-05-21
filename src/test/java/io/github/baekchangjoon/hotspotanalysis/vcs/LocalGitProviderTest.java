package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the shared {@link VcsProviderContract} against {@link LocalGitProvider}
 * and adds JGit-specific behavioural tests.
 *
 * <p>The fixture is a synthetic git repository created via JGit in a
 * {@link TempDir}, with three commits whose times are pinned so that windowing
 * is deterministic.</p>
 */
class LocalGitProviderTest extends VcsProviderContract {

    private static final Instant C1_TIME = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant C2_TIME = Instant.parse("2026-01-15T11:00:00Z");
    private static final Instant C3_TIME = Instant.parse("2026-01-15T12:00:00Z");

    @TempDir
    Path tempDir;

    Path repoPath;

    @BeforeEach
    void initRepo() throws Exception {
        repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        try (Git git = Git.init().setDirectory(repoPath.toFile()).call()) {
            commitFile(git, "src/Foo.java", "class Foo {}\n", "alice", "c1: init", C1_TIME);
            commitFile(git, "src/Foo.java",
                    "class Foo {\n    int x;\n}\n", "bob", "c2: add field", C2_TIME);
            commitFile(git, "src/Bar.java", "class Bar {}\n", "alice", "c3: add Bar", C3_TIME);
        }
    }

    @Override
    protected VcsProvider providerWithKnownHistory() {
        return new LocalGitProvider(repoPath);
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
    // Additional JGit-specific behavioural tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("rejects a path that is not a directory")
    void shouldRejectNonDirectoryPath(@TempDir Path other) {
        Path missing = other.resolve("does-not-exist");

        assertThatThrownBy(() -> new LocalGitProvider(missing))
                .isInstanceOf(VcsException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    @DisplayName("rejects a directory that is not a git repository")
    void shouldFailWhenDirectoryIsNotGit(@TempDir Path other) {
        VcsProvider provider = new LocalGitProvider(other);

        assertThatThrownBy(() -> provider.loadCommits(fullWindow()))
                .isInstanceOf(VcsException.class);
    }

    @Test
    @DisplayName("returns an empty list when the repository has no commits")
    void shouldReturnEmptyListForEmptyRepo(@TempDir Path other) throws Exception {
        Path emptyRepo = other.resolve("empty");
        Files.createDirectories(emptyRepo);
        try (Git ignored = Git.init().setDirectory(emptyRepo.toFile()).call()) {
            // No commits.
        }

        List<CommitRecord> commits = new LocalGitProvider(emptyRepo).loadCommits(fullWindow());

        assertThat(commits).isEmpty();
    }

    @Test
    @DisplayName("classifies the initial commit as ADDED with no parent")
    void shouldClassifyInitialCommitAsAdded() {
        List<CommitRecord> commits = providerWithKnownHistory().loadCommits(fullWindow());

        CommitRecord first = commits.get(0);
        assertThat(first.changes()).hasSize(1);
        assertThat(first.changes().get(0).type()).isEqualTo(ChangeType.ADDED);
        assertThat(first.changes().get(0).path()).isEqualTo("src/Foo.java");
        assertThat(first.changes().get(0).linesAdded()).isEqualTo(1);
        assertThat(first.changes().get(0).linesDeleted()).isZero();
    }

    @Test
    @DisplayName("classifies a follow-up edit as MODIFIED with added/deleted lines counted")
    void shouldCountLinesForModifiedCommit() {
        List<CommitRecord> commits = providerWithKnownHistory().loadCommits(fullWindow());

        CommitRecord second = commits.get(1);
        assertThat(second.changes()).hasSize(1);
        assertThat(second.changes().get(0).type()).isEqualTo(ChangeType.MODIFIED);
        assertThat(second.changes().get(0).path()).isEqualTo("src/Foo.java");
        // Diff: -1 line (class Foo {}) + 3 lines (class Foo {\n    int x;\n}) = added 3, deleted 1
        assertThat(second.changes().get(0).linesAdded()).isEqualTo(3);
        assertThat(second.changes().get(0).linesDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("filters out commits whose committedAt falls outside the window")
    void shouldFilterByWindow() {
        // Window covering only the first commit at 10:00 UTC.
        // since/until are date-based with day-resolution, so we pick a day
        // where there are no commits.
        WindowConfig narrow = new WindowConfig(
                LocalDate.parse("2026-01-14"), LocalDate.parse("2026-01-14"), null);

        List<CommitRecord> commits = providerWithKnownHistory().loadCommits(narrow);

        assertThat(commits).isEmpty();
    }

    private static void commitFile(Git git,
                                   String relativePath,
                                   String content,
                                   String author,
                                   String message,
                                   Instant timestamp) throws Exception {
        Path workTree = git.getRepository().getWorkTree().toPath();
        Path file = workTree.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        git.add().addFilepattern(relativePath).call();
        PersonIdent ident = new PersonIdent(
                author, author + "@example.com",
                Date.from(timestamp), TimeZone.getTimeZone("UTC"));
        git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(message)
                .call();
    }
}
