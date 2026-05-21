package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import io.github.baekchangjoon.hotspotanalysis.config.GithubConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.VcsException;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Production {@link GithubClient} backed by the {@code org.kohsuke:github-api}
 * library. The endpoint is overridable so tests can point it at a WireMock
 * stub instead of {@code https://api.github.com}.
 */
public class KohsukeGithubClient implements GithubClient {

    /** The public api.github.com endpoint, used by default. */
    public static final String DEFAULT_ENDPOINT = "https://api.github.com";

    private final GHRepository repository;
    private final String branch;

    public KohsukeGithubClient(GithubConfig config) {
        this(config, DEFAULT_ENDPOINT);
    }

    public KohsukeGithubClient(GithubConfig config, String endpoint) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(endpoint, "endpoint");
        this.branch = config.branch();
        try {
            GitHub gh = new GitHubBuilder()
                    .withEndpoint(endpoint)
                    .withOAuthToken(config.token())
                    .build();
            this.repository = gh.getRepository(config.owner() + "/" + config.repo());
        } catch (IOException e) {
            throw new VcsException(
                    "Failed to initialise GitHub client for "
                            + config.owner() + "/" + config.repo(),
                    e);
        }
    }

    @Override
    public List<GhCommit> listCommits(Instant since, Instant until) {
        try {
            GHCommitQueryBuilder query = repository.queryCommits()
                    .from(branch)
                    .since(Date.from(since))
                    .until(Date.from(until));

            List<GhCommit> commits = new ArrayList<>();
            for (GHCommit ghc : query.list()) {
                commits.add(convert(ghc));
            }
            return commits;
        } catch (IOException e) {
            throw new VcsException(
                    "Failed to query GitHub commits for branch '" + branch + "'", e);
        }
    }

    private static GhCommit convert(GHCommit source) throws IOException {
        List<GhFileChange> files = new ArrayList<>();
        for (GHCommit.File f : source.getFiles()) {
            files.add(new GhFileChange(
                    f.getFileName(),
                    f.getPreviousFilename(),
                    f.getStatus(),
                    f.getLinesAdded(),
                    f.getLinesDeleted()));
        }
        String authorName = "unknown";
        try {
            if (source.getAuthor() != null && source.getAuthor().getLogin() != null) {
                authorName = source.getAuthor().getLogin();
            } else if (source.getCommitShortInfo() != null
                    && source.getCommitShortInfo().getAuthor() != null) {
                authorName = source.getCommitShortInfo().getAuthor().getName();
            }
        } catch (IOException ignored) {
            // Fall back to "unknown" when the user lookup fails.
        }
        return new GhCommit(
                source.getSHA1(),
                authorName,
                source.getCommitDate().toInstant(),
                source.getCommitShortInfo().getMessage(),
                files);
    }
}
