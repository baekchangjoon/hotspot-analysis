package io.github.baekchangjoon.hotspotanalysis.analysis.model;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Targeted coverage for the {@code lineCoverage} range check that every
 * hotspot record gained when {@code scoring.excludeCoverage} was added.
 * Each record must accept null and values in [0.0, 1.0] but reject values
 * outside that range.
 */
class LineCoverageValidationTest {

    private static final MethodSignature SIG = new MethodSignature("a.B", "m", List.of());

    @Test
    @DisplayName("FileHotspot rejects lineCoverage outside [0.0, 1.0]")
    void fileHotspotValidatesLineCoverage() {
        assertThatThrownBy(() -> new FileHotspot(
                "A.java", 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, /* lineCoverage */ -0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineCoverage");
        assertThatThrownBy(() -> new FileHotspot(
                "A.java", 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, /* lineCoverage */ 1.01))
                .isInstanceOf(IllegalArgumentException.class);
        // Boundary + null are accepted.
        assertThatCode(() -> new FileHotspot(
                "A.java", 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0)).doesNotThrowAnyException();
        assertThatCode(() -> new FileHotspot(
                "A.java", 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)).doesNotThrowAnyException();
        assertThatCode(() -> new FileHotspot(
                "A.java", 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, (Double) null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MethodHotspot rejects lineCoverage outside [0.0, 1.0]")
    void methodHotspotValidatesLineCoverage() {
        assertThatThrownBy(() -> new MethodHotspot(
                SIG, "A.java", 1, 5, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineCoverage");
        assertThatThrownBy(() -> new MethodHotspot(
                SIG, "A.java", 1, 5, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ApiHotspot rejects lineCoverage outside [0.0, 1.0]")
    void apiHotspotValidatesLineCoverage() {
        assertThatThrownBy(() -> new ApiHotspot(
                "GET", "/x", SIG, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, List.of(), -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineCoverage");
        assertThatThrownBy(() -> new ApiHotspot(
                "GET", "/x", SIG, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, List.of(), 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SharedComponentHotspot rejects lineCoverage outside [0.0, 1.0]")
    void sharedComponentHotspotValidatesLineCoverage() {
        assertThatThrownBy(() -> new SharedComponentHotspot(
                SIG, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, List.of(), -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineCoverage");
        assertThatThrownBy(() -> new SharedComponentHotspot(
                SIG, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, List.of(), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
