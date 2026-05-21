package io.github.baekchangjoon.hotspotanalysis.config;

import jakarta.validation.constraints.NotBlank;

/**
 * GitHub-target connection details. Required when
 * {@link TargetConfig#type()} is {@link TargetConfig.TargetType#GITHUB}.
 */
public record GithubConfig(
        @NotBlank String owner,
        @NotBlank String repo,
        @NotBlank String branch,
        @NotBlank String token
) {
}
