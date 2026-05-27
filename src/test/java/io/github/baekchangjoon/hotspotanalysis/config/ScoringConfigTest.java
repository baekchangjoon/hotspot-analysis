package io.github.baekchangjoon.hotspotanalysis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures the default behaviour for {@link ScoringConfig#excludeCoverage()}
 * stays backwards-compatible: omitting the field continues to mean
 * "coverage is part of the Composite Score".
 */
class ScoringConfigTest {

    @Test
    @DisplayName("default constructor sets excludeCoverage to false (legacy behaviour)")
    void defaultExcludeCoverageIsFalse() {
        ScoringConfig config = new ScoringConfig();
        assertThat(config.decayHalfLifeDays()).isEqualTo(90);
        assertThat(config.excludeCoverage()).isFalse();
    }

    @Test
    @DisplayName("legacy single-arg constructor preserves excludeCoverage=false")
    void legacySingleArgConstructorDefaultsExcludeCoverageToFalse() {
        ScoringConfig config = new ScoringConfig(180);
        assertThat(config.decayHalfLifeDays()).isEqualTo(180);
        assertThat(config.excludeCoverage()).isFalse();
    }

    @Test
    @DisplayName("explicit excludeCoverage=true round-trips through the canonical constructor")
    void excludeCoverageHonoursExplicitTrue() {
        ScoringConfig config = new ScoringConfig(90, true);
        assertThat(config.excludeCoverage()).isTrue();
    }

    @Test
    @DisplayName("null excludeCoverage normalised to false")
    void nullExcludeCoverageNormalisedToFalse() {
        ScoringConfig config = new ScoringConfig(90, null);
        assertThat(config.excludeCoverage()).isFalse();
    }
}
