package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;

import java.util.List;

/**
 * Source-agnostic facade over a version control system.
 *
 * <p>Concrete implementations include:
 * <ul>
 *   <li>{@code LocalGitProvider} — reads a local git directory via JGit.</li>
 *   <li>{@code GithubProvider} — reads a remote repository via the GitHub REST API.</li>
 * </ul>
 *
 * <p>All implementations are expected to satisfy the behavioural contract
 * defined by {@code VcsProviderContract} in the test sources. The contract
 * states, in summary:
 * <ol>
 *   <li>Returns a non-null list of commits within the given window.</li>
 *   <li>Excludes commits outside the window.</li>
 *   <li>Returns an empty list for windows that match no commits.</li>
 *   <li>Each returned {@link CommitRecord} satisfies its own invariants.</li>
 *   <li>Calls are idempotent for identical inputs.</li>
 * </ol>
 */
public interface VcsProvider {

    /**
     * Loads every commit whose commit time falls inside the given window.
     *
     * @param window the time window to apply (see {@link WindowConfig})
     * @return commits in chronological order (oldest first); never null
     * @throws VcsException if the repository cannot be accessed
     */
    List<CommitRecord> loadCommits(WindowConfig window);
}
