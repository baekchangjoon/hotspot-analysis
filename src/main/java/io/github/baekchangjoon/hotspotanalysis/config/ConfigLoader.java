package io.github.baekchangjoon.hotspotanalysis.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Loads a YAML configuration file and turns it into a validated
 * {@link AnalysisConfig} instance.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Read the raw file contents.</li>
 *   <li>Substitute {@code ${ENV_VAR}} placeholders via {@link EnvironmentVariableResolver}.</li>
 *   <li>Parse the resolved YAML into the strongly-typed record tree.</li>
 *   <li>Run Jakarta Bean Validation against the tree and surface any violations.</li>
 * </ol>
 * Any failure converts to a {@link ConfigLoadException} (or its
 * {@link ConfigValidationException} subtype) with a human-readable message.</p>
 */
@Component
public class ConfigLoader {

    private final EnvironmentVariableResolver environmentVariableResolver;
    private final ObjectMapper yamlMapper;
    private final Validator validator;

    public ConfigLoader(EnvironmentVariableResolver environmentVariableResolver) {
        this.environmentVariableResolver = environmentVariableResolver;
        this.yamlMapper = newYamlMapper();
        this.validator = newValidator();
    }

    public AnalysisConfig load(Path configFile) {
        String raw = readFile(configFile);
        String resolved = environmentVariableResolver.resolve(raw);
        AnalysisConfig parsed = parse(resolved);
        validate(parsed);
        return parsed;
    }

    private static String readFile(Path configFile) {
        try {
            return Files.readString(configFile);
        } catch (IOException ex) {
            throw new ConfigLoadException(
                    "Failed to read configuration file: " + configFile, ex);
        }
    }

    private AnalysisConfig parse(String resolvedYaml) {
        try {
            return yamlMapper.readValue(resolvedYaml, AnalysisConfig.class);
        } catch (UnrecognizedPropertyException ex) {
            if ("formula".equals(ex.getPropertyName())) {
                throw new ConfigLoadException(
                        "scoring.formula has been removed in v0.2 — all reports now"
                                + " include both Simple and Composite scores. Delete this line.",
                        ex);
            }
            throw new ConfigLoadException(
                    "Unknown configuration key: " + ex.getPropertyName(), ex);
        } catch (InvalidFormatException ex) {
            throw new ConfigLoadException(
                    "Invalid value for '" + lastFieldName(ex) + "': \"" + ex.getValue()
                            + "\" (expected " + ex.getTargetType().getSimpleName() + ")", ex);
        } catch (ValueInstantiationException ex) {
            // Enum @JsonCreator / record compact-constructor rejections (e.g. an
            // unknown output format, decayHalfLifeDays <= 0). Surface the field
            // path and the underlying reason instead of a generic parse error.
            String reason = ex.getCause() != null && ex.getCause().getMessage() != null
                    ? ex.getCause().getMessage()
                    : ex.getOriginalMessage();
            throw new ConfigLoadException(
                    "Invalid value for '" + pathOf(ex) + "': " + reason, ex);
        } catch (MismatchedInputException ex) {
            // Covers missing required (record component) fields among other shape errors.
            throw new ConfigValidationException(
                    "Configuration shape error: " + ex.getOriginalMessage(),
                    List.of(ex.getOriginalMessage()));
        } catch (IOException ex) {
            throw new ConfigLoadException("Failed to parse YAML configuration", ex);
        }
    }

    private void validate(AnalysisConfig config) {
        Set<ConstraintViolation<AnalysisConfig>> violations = validator.validate(config);
        if (violations.isEmpty()) {
            return;
        }
        List<String> messages = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
        throw new ConfigValidationException(
                "Configuration validation failed: " + String.join("; ", messages),
                messages);
    }

    private static String pathOf(com.fasterxml.jackson.databind.JsonMappingException ex) {
        if (ex.getPath() == null || ex.getPath().isEmpty()) {
            return "<unknown>";
        }
        StringBuilder sb = new StringBuilder();
        for (com.fasterxml.jackson.databind.JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(ref.getFieldName());
            }
        }
        return sb.length() > 0 ? sb.toString() : "<unknown>";
    }

    private static String lastFieldName(InvalidFormatException ex) {
        if (ex.getPath() == null || ex.getPath().isEmpty()) {
            return "<unknown>";
        }
        return ex.getPath().get(ex.getPath().size() - 1).getFieldName();
    }

    private static ObjectMapper newYamlMapper() {
        return YAMLMapper.builder()
                .addModule(new JavaTimeModule())
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    private static Validator newValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }
}
