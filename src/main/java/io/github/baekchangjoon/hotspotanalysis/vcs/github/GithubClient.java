package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import java.time.Instant;
import java.util.List;

/**
 * Thin abstraction over the GitHub commits endpoint.
 *
 * <p>Production implementation: {@link KohsukeGithubClient}. Tests use an
 * in-memory fake so that {@link GithubProvider} can be exercised without HTTP
 * round-trips. A separate test suite verifies the real adapter against a
 * WireMock-stubbed GitHub API.</p>
 */
public interface GithubClient {

    /**
     * Lists commits whose commit-time falls in the half-open interval
     * {@code [since, until]} (inclusive on both ends).
     */
    List<GhCommit> listCommits(Instant since, Instant until);
}
