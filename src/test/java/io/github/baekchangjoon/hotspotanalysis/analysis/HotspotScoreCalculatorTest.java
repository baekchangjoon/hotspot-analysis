package io.github.baekchangjoon.hotspotanalysis.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HotspotScoreCalculatorTest {

    private final HotspotScoreCalculator calculator = new HotspotScoreCalculator();

    @Test
    void simpleMultipliesRevisionsByLoc() {
        assertThat(calculator.simple(3, 120)).isEqualTo(360.0);
    }

    @Test
    void simpleReturnsZeroForZeroInputs() {
        assertThat(calculator.simple(0, 100)).isZero();
        assertThat(calculator.simple(10, 0)).isZero();
    }

    @Test
    void simpleRejectsNegativeInputs() {
        assertThatThrownBy(() -> calculator.simple(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.simple(1, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeMultipliesAllThreeFactors() {
        assertThat(calculator.composite(25.0, 0.425, 0.9381))
                .isCloseTo(9.97, within(0.01));
    }

    @Test
    void compositeRejectsInvalidInputs() {
        assertThatThrownBy(() -> calculator.composite(-1, 0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.composite(1.0, -0.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.composite(1.0, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeTwoArgDropsCoverageTerm() {
        // Same cc/decay as the three-arg test: 25 * 0.425 = 10.625
        assertThat(calculator.composite(25.0, 0.425))
                .isCloseTo(10.625, within(1e-9));
    }

    @Test
    void compositeTwoArgRejectsNegativeInputs() {
        assertThatThrownBy(() -> calculator.composite(-1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.composite(1.0, -0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplierReturnsOneWhenCoverageAbsent() {
        assertThat(calculator.multiplier(java.util.OptionalDouble.empty())).isEqualTo(1.0);
    }

    @Test
    void multiplierReturnsInverseShiftedCoverage() {
        assertThat(calculator.multiplier(java.util.OptionalDouble.of(0.0)))
                .isCloseTo(10.0, within(1e-9));
        assertThat(calculator.multiplier(java.util.OptionalDouble.of(1.0)))
                .isCloseTo(1.0 / 1.1, within(1e-9));
    }

    @Test
    void multiplierRejectsOutOfRangeCoverage() {
        assertThatThrownBy(() -> calculator.multiplier(java.util.OptionalDouble.of(-0.01)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.multiplier(java.util.OptionalDouble.of(1.01)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
