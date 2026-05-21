package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Describes the source repository to analyse: either a local git directory
 * or a remote GitHub repository.
 */
public record TargetConfig(
        @NotNull TargetType type,
        String path,
        @Valid GithubConfig github
) {

    @AssertTrue(message = "target.path is required when target.type=local-git")
    public boolean isPathPresentWhenLocalGit() {
        if (type != TargetType.LOCAL_GIT) {
            return true;
        }
        return path != null && !path.isBlank();
    }

    @AssertTrue(message = "target.github block is required when target.type=github")
    public boolean isGithubPresentWhenGithubType() {
        if (type != TargetType.GITHUB) {
            return true;
        }
        return github != null;
    }

    public enum TargetType {
        LOCAL_GIT,
        GITHUB;

        @JsonCreator
        public static TargetType from(String raw) {
            if (raw == null) {
                return null;
            }
            return TargetType.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        }
    }
}
