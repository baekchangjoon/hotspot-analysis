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
        @Min(value = 0, message = "output.topN must be >= 0 (0 means all rows)") Integer topN,
        ApiLayout apiLayout,
        Boolean coverageBreakdown
) {

    public OutputConfig {
        if (topN == null) {
            topN = 0; // omitted → all rows, instead of a raw null-int mapping error
        }
        if (apiLayout == null) {
            apiLayout = ApiLayout.BOTH;
        }
        if (coverageBreakdown == null) {
            coverageBreakdown = Boolean.FALSE;
        }
        if (formats != null) {
            formats = List.copyOf(formats);
        }
    }

    public OutputConfig(List<OutputFormat> formats, String path, int topN) {
        this(formats, path, topN, ApiLayout.BOTH, Boolean.FALSE);
    }

    public OutputConfig(List<OutputFormat> formats, String path, int topN, ApiLayout apiLayout) {
        this(formats, path, topN, apiLayout, Boolean.FALSE);
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
            try {
                return ApiLayout.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "\"" + raw + "\" is not one of " + java.util.Arrays.toString(values()));
            }
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
            try {
                return OutputFormat.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "\"" + raw + "\" is not one of " + java.util.Arrays.toString(values()));
            }
        }
    }
}
