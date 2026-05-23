package io.github.baekchangjoon.hotspotanalysis.coverage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JacocoReportParserTest {

    @Test
    @DisplayName("should parse jacoco xml report and calculate correct coverage values")
    void shouldParseJacocoXmlReport(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("jacoco.xml");
        Files.writeString(reportPath, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <class name="com/example/Foo" sourcefilename="Foo.java">
                            <method name="hello" desc="()V" line="5">
                                <counter type="LINE" missed="0" covered="1"/>
                            </method>
                        </class>
                        <sourcefile name="Foo.java">
                            <line nr="5" mi="0" ci="1" mb="0" cb="0"/>
                            <line nr="6" mi="1" ci="0" mb="0" cb="0"/>
                            <counter type="LINE" missed="1" covered="1"/>
                        </sourcefile>
                    </package>
                </report>
                """);

        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(reportPath);

        // 1. File coverage: 2 lines total, 1 covered -> 0.5
        assertThat(parser.getFileCoverage("src/main/java/com/example/Foo.java")).isEqualTo(0.5);
        assertThat(parser.getFileCoverage("com/example/Foo.java")).isEqualTo(0.5);

        // 2. Method coverage: line 5 covered -> 1.0
        assertThat(parser.getMethodCoverage("com/example/Foo.java", 5, 5)).isEqualTo(1.0);

        // 3. Method coverage: line 6 missed -> 0.0
        assertThat(parser.getMethodCoverage("com/example/Foo.java", 6, 6)).isEqualTo(0.0);

        // 4. Method coverage: range 5-6 -> 0.5
        assertThat(parser.getMethodCoverage("com/example/Foo.java", 5, 6)).isEqualTo(0.5);

        // 5. Method coverage: out-of-range (e.g. line 7) -> 0.0
        assertThat(parser.getMethodCoverage("com/example/Foo.java", 7, 8)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should handle missing file and return 0.0 coverage")
    void shouldHandleMissingFile() {
        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(Path.of("nonexistent.xml"));

        assertThat(parser.getFileCoverage("AnyFile.java")).isZero();
        assertThat(parser.getMethodCoverage("AnyFile.java", 1, 10)).isZero();
    }
}
