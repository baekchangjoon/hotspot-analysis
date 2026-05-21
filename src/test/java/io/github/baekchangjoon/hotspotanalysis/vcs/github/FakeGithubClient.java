package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import java.time.Instant;
import java.util.List;

/**
 * Test-only {@link GithubClient} backed by an in-memory commit list, used to
 * drive {@link io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderContract}
 * against {@link GithubProvider} without any HTTP traffic.
 */
final class FakeGithubClient implements GithubClient {

    private final List<GhCommit> commits;

    FakeGithubClient(List<GhCommit> commits) {
        this.commits = List.copyOf(commits);
    }

    @Override
    public List<GhCommit> listCommits(Instant since, Instant until) {
        return commits.stream()
                .filter(c -> !c.committedAt().isBefore(since))
                .filter(c -> !c.committedAt().isAfter(until))
                .toList();
    }
}
