package io.github.baekchangjoon.hotspotanalysis.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLinksTest {

    private static final String SHA = "52ede6df6d7309c84b5561bf0656f20d79579aac";
    private static final String EXPECTED = "https://github.com/acme/app/blob/" + SHA + "/";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/acme/app.git",
            "https://github.com/acme/app",
            "https://github.com/acme/app/",
            "git@github.com:acme/app.git",
            "ssh://git@github.com/acme/app.git",
            "ssh://git@github.com:22/acme/app.git",
            "  https://GitHub.com/acme/app.git  ",
    })
    @DisplayName("every common remote spelling resolves to the same https blob base")
    void shouldNormaliseRemoteForms(String remote) {
        assertThat(SourceLinks.blobBase(remote, SHA)).contains(EXPECTED);
    }

    @Test
    @DisplayName("credentials embedded in the remote never reach the report")
    void shouldDropCredentials() {
        String base = SourceLinks.blobBase("https://x-access-token:ghp_SECRET@github.com/acme/app.git", SHA)
                .orElseThrow();

        assertThat(base).isEqualTo(EXPECTED);
        assertThat(base).doesNotContain("SECRET").doesNotContain("@");
    }

    @Test
    @DisplayName("GitLab hosts, self-managed ones and nested groups are recognised")
    void shouldRecogniseGitlab() {
        assertThat(SourceLinks.blobBase("git@gitlab.example.co.kr:team/sub/app.git", SHA))
                .contains("https://gitlab.example.co.kr/team/sub/app/blob/" + SHA + "/");
        assertThat(SourceLinks.blobBase("https://github.example.com/acme/app.git", SHA))
                .contains("https://github.example.com/acme/app/blob/" + SHA + "/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://bitbucket.org/acme/app.git",       // different URL scheme for blobs
            "http://local_proxy@127.0.0.1:40577/git/acme/app",
            "/srv/git/app.git",
            "file:///srv/git/app.git",
            "https://github.com/app",                   // no owner segment
            "https://github.com/acme/app\"><script>",   // must not pass through
            "not a url",
            "",
    })
    @DisplayName("unknown hosts and malformed remotes yield no link instead of a guessed one")
    void shouldRejectUnknownOrMalformed(String remote) {
        assertThat(SourceLinks.blobBase(remote, SHA)).isEmpty();
    }

    @Test
    @DisplayName("a missing remote or a non-hex revision yields no link")
    void shouldRejectBadRevision() {
        assertThat(SourceLinks.blobBase(null, SHA)).isEmpty();
        assertThat(SourceLinks.blobBase("https://github.com/acme/app.git", null)).isEmpty();
        assertThat(SourceLinks.blobBase("https://github.com/acme/app.git", "main\"onload=")).isEmpty();
    }
}
