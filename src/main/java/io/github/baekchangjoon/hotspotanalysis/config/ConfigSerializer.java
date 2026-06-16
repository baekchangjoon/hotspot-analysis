package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Serialises an {@link AnalysisConfig} back to YAML for {@code analyze
 * --print-config}. Output is tuned (not the loader's parse mapper): null fields
 * are omitted and dates are ISO-8601 strings, so the printed YAML re-loads
 * through {@link ConfigLoader} to an equivalent config.
 *
 * <p>{@code @AssertTrue} validation methods on the config records (e.g.
 * {@code TargetConfig.isPathPresentWhenLocalGit()},
 * {@code WindowConfig.isSinceNotAfterUntil()}) are annotated with
 * {@code @JsonIgnore} directly, so they are NOT emitted as bogus YAML keys that
 * {@link ConfigLoader} would reject.</p>
 */
@Component
public class ConfigSerializer {

    private final ObjectMapper mapper = YAMLMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();

    public String serialize(AnalysisConfig config) {
        try {
            return mapper.writeValueAsString(config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ConfigSerializeException(
                    "failed to serialise synthesized config: " + e.getMessage(), e);
        }
    }
}
