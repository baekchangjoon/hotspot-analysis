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
        @Min(value = 0, message = "output.topN must be >= 0 (0 means all rows)") int topN,
        ApiLayout apiLayout
) {

    public OutputConfig {
        if (apiLayout == null) {
            apiLayout = ApiLayout.BOTH;
        }
        if (formats != null) {
            formats = List.copyOf(formats);
        }
    }

    public OutputConfig(List<OutputFormat> formats, String path, int topN) {
        this(formats, path, topN, ApiLayout.BOTH);
    }

    public enum ApiLayout {
        COMBINED,
        STANDALONE,
        BOTH;

        @JsonCreator
        public static ApiLayout from(String raw) {
            if (raw == null) {
                return null;
            }
            return ApiLayout.valueOf(raw.trim().toUpperCase());
        }
    }

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
