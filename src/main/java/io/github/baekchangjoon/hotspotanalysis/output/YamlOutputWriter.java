package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the analysis result as a single {@code hotspots.yml} file, preserving
 * the structure of {@code AnalysisResult} (meta + fileHotspots + methodHotspots).
 *
 * <p>Uses Jackson with the YAML factory; {@code Instant} is rendered as ISO-8601
 * via {@link JavaTimeModule}.</p>
 */
@Component
public class YamlOutputWriter implements OutputWriter {

    private final ObjectMapper yamlMapper;

    public YamlOutputWriter() {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(factory)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public OutputConfig.OutputFormat format() {
        return OutputConfig.OutputFormat.YAML;
    }

    @Override
    public void write(AnalysisResult result, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("hotspots.yml");
            yamlMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(target.toFile(), result);
        } catch (IOException e) {
            throw new OutputException("Failed to write YAML output to " + outputDir, e);
        }
    }
}
