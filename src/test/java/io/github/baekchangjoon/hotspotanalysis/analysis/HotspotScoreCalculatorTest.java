package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.config.ScoringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
