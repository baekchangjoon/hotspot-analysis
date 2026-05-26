package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HotspotScoreCalculatorTest {

    private final HotspotScoreCalculator calculator = new HotspotScoreCalculator();

    @Test
    @DisplayName("SIMPLE formula returns revisions * loc")
    void shouldMultiplyForSimpleFormula() {
        double score = calculator.calculate(7, 42, ScoringConfig.Formula.SIMPLE);

        assertThat(score).isEqualTo(294.0);
    }

    @Test
    @DisplayName("score is 0 when revisions is 0")
    void shouldReturnZeroWhenRevisionsZero() {
        assertThat(calculator.calculate(0, 100, ScoringConfig.Formula.SIMPLE)).isZero();
    }

    @Test
    @DisplayName("score is 0 when loc is 0")
    void shouldReturnZeroWhenLocZero() {
        assertThat(calculator.calculate(10, 0, ScoringConfig.Formula.SIMPLE)).isZero();
    }

    @Test
    @DisplayName("rejects negative revisions")
    void shouldRejectNegativeRevisions() {
        assertThatThrownBy(() -> calculator.calculate(-1, 10, ScoringConfig.Formula.SIMPLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects negative loc")
    void shouldRejectNegativeLoc() {
        assertThatThrownBy(() -> calculator.calculate(10, -1, ScoringConfig.Formula.SIMPLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null formula")
    void shouldRejectNullFormula() {
        assertThatThrownBy(() -> calculator.calculate(1, 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void simpleMultipliesRevisionsByLoc() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThat(c.simple(3, 120)).isEqualTo(360.0);
    }

    @Test
    void simpleRejectsNegativeInputs() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThatThrownBy(() -> c.simple(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.simple(1, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeMultipliesAllThreeFactors() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThat(c.composite(25.0, 0.425, 0.9381))
                .isCloseTo(9.97, within(0.01));
    }

    @Test
    void compositeRejectsInvalidInputs() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThatThrownBy(() -> c.composite(-1, 0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.composite(1.0, -0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.composite(1.0, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplierReturnsOneWhenCoverageAbsent() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThat(c.multiplier(java.util.OptionalDouble.empty())).isEqualTo(1.0);
    }

    @Test
    void multiplierReturnsInverseShiftedCoverage() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThat(c.multiplier(java.util.OptionalDouble.of(0.0)))
                .isCloseTo(10.0, within(1e-9));
        assertThat(c.multiplier(java.util.OptionalDouble.of(1.0)))
                .isCloseTo(1.0 / 1.1, within(1e-9));
    }

    @Test
    void multiplierRejectsOutOfRangeCoverage() {
        HotspotScoreCalculator c = new HotspotScoreCalculator();
        assertThatThrownBy(() -> c.multiplier(java.util.OptionalDouble.of(-0.01)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.multiplier(java.util.OptionalDouble.of(1.01)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
