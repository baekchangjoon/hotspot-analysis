package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.TargetConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.github.GithubProvider;
import io.github.baekchangjoon.hotspotanalysis.vcs.github.KohsukeGithubClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Chooses the {@link VcsProvider} implementation for the configured
 * {@link TargetConfig}.
 *
 * <p>Phase 1 supports {@code local-git} natively. {@code github} returns a
 * provider that reads via the GitHub REST API but does not pull source files
 * — see Phase 1 limitations in {@code docs/reports/T9-hotspot-analyzer.md}.</p>
 */
@Component
public class VcsProviderFactory {

    public VcsProvider create(TargetConfig target) {
        return switch (target.type()) {
            case LOCAL_GIT -> new LocalGitProvider(
                    Path.of(target.path()).toAbsolutePath().normalize());
            case GITHUB -> new GithubProvider(new KohsukeGithubClient(target.github()));
        };
    }
}
