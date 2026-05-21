package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import io.github.baekchangjoon.hotspotanalysis.parser.model.ParameterInfo;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.DiffHunk;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.FileChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RevisionsCalculatorTest {

    private final RevisionsCalculator calculator = new RevisionsCalculator();
    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    // ---------------------------------------------------------------
    // File-level revisions
    // ---------------------------------------------------------------

    @Test
    @DisplayName("counts file revisions across multiple commits")
    void shouldCountFileRevisionsAcrossCommits() {
        List<CommitRecord> commits = List.of(
                commit("c1", change("A.java"), change("B.java")),
                commit("c2", change("A.java")),
                commit("c3", change("B.java"), change("C.java")));

        Map<String, Integer> revisions = calculator.calculateFileRevisions(commits);

        assertThat(revisions)
                .containsEntry("A.java", 2)
                .containsEntry("B.java", 2)
                .containsEntry("C.java", 1);
    }

    @Test
    @DisplayName("returns an empty map for no commits")
    void shouldReturnEmptyForNoCommits() {
        Map<String, Integer> revisions = calculator.calculateFileRevisions(List.of());

        assertThat(revisions).isEmpty();
    }

    @Test
    @DisplayName("counts a file at most once per commit even with multiple change entries")
    void shouldDedupePathsWithinSameCommit() {
        FileChange c1 = new FileChange("A.java", null, 1, 0, ChangeType.MODIFIED);
        FileChange c2 = new FileChange("A.java", null, 2, 0, ChangeType.MODIFIED);
        List<CommitRecord> commits = List.of(
                new CommitRecord("sha1", "alice", T0, "msg", List.of(c1, c2)));

        Map<String, Integer> revisions = calculator.calculateFileRevisions(commits);

        assertThat(revisions).containsEntry("A.java", 1);
    }

    // ---------------------------------------------------------------
    // Method-level revisions — hunk-aware path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("credits a method when a hunk overlaps its line range")
    void shouldCreditMethodWhenHunkOverlaps() {
        MethodInfo m1 = method("Foo", "alpha", 5, 15);
        MethodInfo m2 = method("Foo", "beta", 20, 30);

        FileChange withHunk = new FileChange(
                "Foo.java", null, 1, 0, ChangeType.MODIFIED,
                List.of(new DiffHunk(10, 12)));
        List<CommitRecord> commits = List.of(
                new CommitRecord("c1", "alice", T0, "msg", List.of(withHunk)));

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                commits, Map.of("Foo.java", List.of(m1, m2)));

        assertThat(rev.get(m1.signature())).isEqualTo(1);
        assertThat(rev.get(m2.signature())).isZero();
    }

    @Test
    @DisplayName("credits a method only once per commit even if multiple hunks overlap it")
    void shouldCreditOncePerCommit() {
        MethodInfo m1 = method("Foo", "alpha", 5, 25);
        FileChange manyHunks = new FileChange(
                "Foo.java", null, 4, 0, ChangeType.MODIFIED,
                List.of(new DiffHunk(7, 8), new DiffHunk(15, 16), new DiffHunk(22, 23)));
        List<CommitRecord> commits = List.of(
                new CommitRecord("c1", "alice", T0, "msg", List.of(manyHunks)));

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                commits, Map.of("Foo.java", List.of(m1)));

        assertThat(rev.get(m1.signature())).isEqualTo(1);
    }

    @Test
    @DisplayName("counts independent commits cumulatively")
    void shouldAccumulateAcrossCommits() {
        MethodInfo m1 = method("Foo", "alpha", 5, 15);
        FileChange edit = new FileChange(
                "Foo.java", null, 1, 0, ChangeType.MODIFIED,
                List.of(new DiffHunk(10, 11)));
        List<CommitRecord> commits = List.of(
                new CommitRecord("c1", "alice", T0, "msg1", List.of(edit)),
                new CommitRecord("c2", "alice", T0, "msg2", List.of(edit)),
                new CommitRecord("c3", "alice", T0, "msg3", List.of(edit)));

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                commits, Map.of("Foo.java", List.of(m1)));

        assertThat(rev.get(m1.signature())).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // Method-level revisions — fallback path (no hunks)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("falls back to file-level credit when hunks are absent")
    void shouldFallBackToFileLevelWhenNoHunks() {
        MethodInfo m1 = method("Foo", "alpha", 5, 15);
        MethodInfo m2 = method("Foo", "beta", 20, 30);

        FileChange noHunks = new FileChange("Foo.java", null, 1, 0, ChangeType.MODIFIED);
        List<CommitRecord> commits = List.of(
                new CommitRecord("c1", "alice", T0, "msg", List.of(noHunks)));

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                commits, Map.of("Foo.java", List.of(m1, m2)));

        assertThat(rev.get(m1.signature())).isEqualTo(1);
        assertThat(rev.get(m2.signature())).isEqualTo(1);
    }

    @Test
    @DisplayName("reports zero for methods in files not touched by any commit")
    void shouldReportZeroForUntouchedFile() {
        MethodInfo m1 = method("Bar", "alpha", 1, 5);

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                List.of(), Map.of("Bar.java", List.of(m1)));

        assertThat(rev.get(m1.signature())).isZero();
    }

    @Test
    @DisplayName("ignores commits touching files outside the method scope")
    void shouldIgnoreOutOfScopeFiles() {
        MethodInfo m1 = method("Foo", "alpha", 1, 10);
        FileChange unrelated = new FileChange("Other.java", null, 1, 0, ChangeType.MODIFIED);
        List<CommitRecord> commits = List.of(
                new CommitRecord("c1", "alice", T0, "msg", List.of(unrelated)));

        Map<MethodSignature, Integer> rev = calculator.calculateMethodRevisions(
                commits, Map.of("Foo.java", List.of(m1)));

        assertThat(rev.get(m1.signature())).isZero();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static FileChange change(String path) {
        return new FileChange(path, null, 1, 0, ChangeType.MODIFIED);
    }

    private static CommitRecord commit(String sha, FileChange... changes) {
        return new CommitRecord(sha, "alice", T0, "msg", List.of(changes));
    }

    private static MethodInfo method(String fqcn, String name, int start, int end) {
        return new MethodInfo(
                new MethodSignature(fqcn, name, List.of()),
                start, end, List.<ParameterInfo>of());
    }
}
