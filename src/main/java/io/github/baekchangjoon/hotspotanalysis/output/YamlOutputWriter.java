package io.github.baekchangjoon.hotspotanalysis.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisResult;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.ApiHotspot;
import io.github.baekchangjoon.hotspotanalysis.analysis.model.SharedComponentHotspot;
import io.github.baekchangjoon.hotspotanalysis.config.OutputConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the analysis result as a single {@code hotspots.yml} or {@code api_report.yml} file,
 * preserving the structure of {@code AnalysisResult}.
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
        write(result, outputDir, new OutputConfig(List.of(OutputConfig.OutputFormat.YAML), outputDir.toString(), 0), false);
    }

    @Override
    public void write(AnalysisResult result, Path outputDir, OutputConfig outputConfig, boolean apiEnabled) {
        try {
            Files.createDirectories(outputDir);

            boolean combined = outputConfig.apiLayout() == OutputConfig.ApiLayout.COMBINED ||
                               outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;
            boolean standalone = outputConfig.apiLayout() == OutputConfig.ApiLayout.STANDALONE ||
                                 outputConfig.apiLayout() == OutputConfig.ApiLayout.BOTH;

            if (!apiEnabled) {
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("hotspots.yml").toFile(), result);
                return;
            }

            if (combined) {
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("hotspots.yml").toFile(), result);
            }

            if (standalone) {
                ApiReportYaml apiReport = new ApiReportYaml(
                        result.meta(),
                        result.apiHotspots(),
                        result.sharedComponents()
                );
                yamlMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(outputDir.resolve("api_report.yml").toFile(), apiReport);
            }
        } catch (IOException e) {
            throw new OutputException("Failed to write YAML output to " + outputDir, e);
        }
    }

    private record ApiReportYaml(
            io.github.baekchangjoon.hotspotanalysis.analysis.model.AnalysisMeta meta,
            List<ApiHotspot> apiHotspots,
            List<SharedComponentHotspot> sharedComponents
    ) {}
}
