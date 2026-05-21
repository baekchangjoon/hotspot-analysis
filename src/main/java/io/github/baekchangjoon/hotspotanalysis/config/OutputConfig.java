package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Output destination and formatting options.
 */
public record OutputConfig(
        @NotEmpty(message = "output.formats must not be empty") List<OutputFormat> formats,
        @NotBlank(message = "output.path must not be blank") String path,
        @Min(value = 0, message = "output.topN must be >= 0 (0 means all rows)") int topN
) {

    public enum OutputFormat {
        CSV,
        YAML,
        MD,
        HTML;

        @JsonCreator
        public static OutputFormat from(String raw) {
            if (raw == null) {
                return null;
            }
            return OutputFormat.valueOf(raw.trim().toUpperCase());
        }
    }
}
