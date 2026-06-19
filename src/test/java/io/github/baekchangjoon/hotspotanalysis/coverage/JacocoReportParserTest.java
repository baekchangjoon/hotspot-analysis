package io.github.baekchangjoon.hotspotanalysis.coverage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    @DisplayName("getMethodLineCounts returns covered/executable counts; no data -> (0,0)")
    void shouldReturnLineCounts(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("jacoco.xml");
        Files.writeString(reportPath, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="Foo.java">
                            <line nr="5" mi="0" ci="1" mb="0" cb="0"/>
                            <line nr="6" mi="1" ci="0" mb="0" cb="0"/>
                            <line nr="7" mi="0" ci="2" mb="0" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);
        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(reportPath);

        // Lines 5-7: 3 instrumented, 2 covered (5 and 7).
        JacocoReportParser.LineCounts c = parser.getMethodLineCounts("com/example/Foo.java", 5, 7);
        assertThat(c.executable()).isEqualTo(3);
        assertThat(c.covered()).isEqualTo(2);

        // Single missed line.
        assertThat(parser.getMethodLineCounts("com/example/Foo.java", 6, 6))
                .isEqualTo(new JacocoReportParser.LineCounts(0, 1));

        // Out-of-range / no data -> (0,0), so line-weighted aggregates ignore it.
        assertThat(parser.getMethodLineCounts("com/example/Foo.java", 20, 30))
                .isEqualTo(new JacocoReportParser.LineCounts(0, 0));
        assertThat(parser.getMethodLineCounts("Missing.java", 1, 10))
                .isEqualTo(new JacocoReportParser.LineCounts(0, 0));

        // getMethodCoverage stays consistent (covered/executable).
        assertThat(parser.getMethodCoverage("com/example/Foo.java", 5, 7))
                .isEqualTo(2.0 / 3.0);

        // Whole-file counts back the file-level breakdown.
        assertThat(parser.getFileLineCounts("com/example/Foo.java"))
                .isEqualTo(new JacocoReportParser.LineCounts(2, 3));
        assertThat(parser.getFileLineCounts("Missing.java"))
                .isEqualTo(new JacocoReportParser.LineCounts(0, 0));
    }

    @Test
    @DisplayName("should handle missing file and return 0.0 coverage")
    void shouldHandleMissingFile() {
        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(Path.of("nonexistent.xml"));

        assertThat(parser.getFileCoverage("AnyFile.java")).isZero();
        assertThat(parser.getMethodCoverage("AnyFile.java", 1, 10)).isZero();
    }

    @Test
    @DisplayName("hasData reflects whether the report yielded coverage: true for a valid report")
    void hasDataTrueForValidReport(@TempDir Path tempDir) throws Exception {
        Path good = tempDir.resolve("good.xml");
        Files.writeString(good, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="test">
                    <package name="com/example">
                        <sourcefile name="Foo.java">
                            <line nr="5" mi="0" ci="1" mb="0" cb="0"/>
                        </sourcefile>
                    </package>
                </report>
                """);
        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(good);
        assertThat(parser.hasData()).isTrue();
    }

    @Test
    @DisplayName("malformed XML is swallowed without throwing and leaves hasData false (no coverage)")
    void hasDataFalseForMalformedReport(@TempDir Path tempDir) throws Exception {
        Path bad = tempDir.resolve("bad.xml");
        // Truncated / unclosed XML: SAX parsing must fail, but parse() must not throw.
        Files.writeString(bad, "<report><package><sourcefile name=\"Foo.java\"><line nr=\"5\" ci=\"1\"");

        JacocoReportParser parser = new JacocoReportParser();
        assertThatCode(() -> parser.parse(bad)).doesNotThrowAnyException();
        assertThat(parser.hasData()).isFalse();
    }

    @Test
    @DisplayName("missing report file leaves hasData false")
    void hasDataFalseForMissingFile() {
        JacocoReportParser parser = new JacocoReportParser();
        parser.parse(Path.of("nonexistent.xml"));
        assertThat(parser.hasData()).isFalse();
    }
}
