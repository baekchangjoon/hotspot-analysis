package io.github.baekchangjoon.hotspotanalysis.vcs.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.baekchangjoon.hotspotanalysis.config.GithubConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level contract test for {@link KohsukeGithubClient}. The GitHub REST
 * API is stubbed with WireMock so the test is hermetic (no network access)
 * yet exercises the real {@code github-api} HTTP client end to end.
 */
class KohsukeGithubClientWireMockTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startServer() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("lists commits with their file changes via HTTP")
    void shouldListCommitsViaHttp() {
        stubRepoMetadata();
        stubCommitsList();
        stubSingleCommit();

        GithubConfig config = new GithubConfig("owner", "repo", "main", "token");
        KohsukeGithubClient client = new KohsukeGithubClient(config, wireMockServer.baseUrl());

        List<GhCommit> commits = client.listCommits(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(commits).hasSize(1);
        GhCommit only = commits.get(0);
        assertThat(only.sha()).isEqualTo("abc1234567890abcdef1234567890abcdef12345");
        assertThat(only.files()).hasSize(1);
        assertThat(only.files().get(0).filename()).isEqualTo("src/Foo.java");
        assertThat(only.files().get(0).status()).isEqualTo("added");
        assertThat(only.files().get(0).additions()).isEqualTo(5);
    }

    private void stubRepoMetadata() {
        wireMockServer.stubFor(get(urlPathEqualTo("/repos/owner/repo"))
                .willReturn(jsonResponse("""
                        {
                          "id": 1,
                          "node_id": "MDEwOlJlcG9zaXRvcnkx",
                          "name": "repo",
                          "full_name": "owner/repo",
                          "private": false,
                          "owner": {"login": "owner", "id": 1, "type": "User"},
                          "default_branch": "main",
                          "url": "%s/repos/owner/repo"
                        }
                        """.formatted(wireMockServer.baseUrl()))));
    }

    private void stubCommitsList() {
        wireMockServer.stubFor(get(urlPathEqualTo("/repos/owner/repo/commits"))
                .willReturn(jsonResponse("""
                        [
                          {
                            "sha": "abc1234567890abcdef1234567890abcdef12345",
                            "node_id": "C_1",
                            "commit": {
                              "author":    {"name": "Alice", "email": "alice@example.com", "date": "2026-01-15T10:00:00Z"},
                              "committer": {"name": "Alice", "email": "alice@example.com", "date": "2026-01-15T10:00:00Z"},
                              "message": "Initial commit",
                              "tree": {"sha": "t1", "url": "%1$s/repos/owner/repo/git/trees/t1"},
                              "comment_count": 0
                            },
                            "url": "%1$s/repos/owner/repo/commits/abc1234567890abcdef1234567890abcdef12345",
                            "parents": []
                          }
                        ]
                        """.formatted(wireMockServer.baseUrl()))));
    }

    private void stubSingleCommit() {
        wireMockServer.stubFor(get(urlPathMatching("/repos/owner/repo/commits/abc[0-9a-f]+"))
                .willReturn(jsonResponse("""
                        {
                          "sha": "abc1234567890abcdef1234567890abcdef12345",
                          "node_id": "C_1",
                          "commit": {
                            "author":    {"name": "Alice", "email": "alice@example.com", "date": "2026-01-15T10:00:00Z"},
                            "committer": {"name": "Alice", "email": "alice@example.com", "date": "2026-01-15T10:00:00Z"},
                            "message": "Initial commit",
                            "tree": {"sha": "t1", "url": "%1$s/repos/owner/repo/git/trees/t1"},
                            "comment_count": 0
                          },
                          "url": "%1$s/repos/owner/repo/commits/abc1234567890abcdef1234567890abcdef12345",
                          "parents": [],
                          "stats": {"total": 5, "additions": 5, "deletions": 0},
                          "files": [
                            {
                              "sha": "f1",
                              "filename": "src/Foo.java",
                              "status": "added",
                              "additions": 5,
                              "deletions": 0,
                              "changes": 5,
                              "blob_url":     "%1$s/owner/repo/blob/abc/src/Foo.java",
                              "raw_url":      "%1$s/owner/repo/raw/abc/src/Foo.java",
                              "contents_url": "%1$s/repos/owner/repo/contents/src/Foo.java"
                            }
                          ]
                        }
                        """.formatted(wireMockServer.baseUrl()))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBody(body);
    }
}
