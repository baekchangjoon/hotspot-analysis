package io.github.baekchangjoon.hotspotanalysis.analysis;

import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.DiffHunk;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.FileChange;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts revisions per file and per method from a list of {@link CommitRecord}s.
 *
 * <p>"Revisions" is the count of commits that touched the artifact at least
 * once. Multiple file-changes for the same path within a single commit count
 * as <strong>one</strong> revision, not many — that matches the semantics of
 * {@code git log --oneline -- <path> | wc -l}.</p>
 *
 * <p>Method-level resolution uses hunk-overlap when the underlying
 * {@link FileChange} carries hunk information ({@link DiffHunk} list, supplied
 * by {@code LocalGitProvider}). When hunks are absent (e.g. {@code GithubProvider}),
 * the calculator falls back to a file-level approximation: every method in
 * the file is credited with the file's revision count.</p>
 */
@Component
public class RevisionsCalculator {

    /**
     * @return immutable map: file path (POSIX, post-rename) → number of
     *         commits that touched it.
     */
    public Map<String, Integer> calculateFileRevisions(List<CommitRecord> commits) {
        Map<String, Integer> counts = new HashMap<>();
        for (CommitRecord commit : commits) {
            Set<String> pathsTouchedInThisCommit = new HashSet<>();
            for (FileChange change : commit.changes()) {
                pathsTouchedInThisCommit.add(change.path());
            }
            for (String path : pathsTouchedInThisCommit) {
                counts.merge(path, 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    /**
     * Calculates revisions for each method, keyed by {@link MethodSignature}.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Initialise every known method's count to 0.</li>
     *   <li>For each commit, iterate every {@link FileChange} that maps to a
     *       file known to {@code methodsByFile}.</li>
     *   <li>If the change has hunks: for every (hunk, method) pair where the
     *       hunk's new-file line range overlaps the method's line range,
     *       credit the method with one revision — but at most once per commit.</li>
     *   <li>If the change has no hunks: credit every method in the file with
     *       one revision — but at most once per commit.</li>
     * </ol>
     *
     * @param commits        commits to analyse (any order; idempotent)
     * @param methodsByFile  file path → methods declared in that file
     * @return immutable map: every method signature in {@code methodsByFile}
     *         → its revision count (0 if untouched)
     */
    public Map<MethodSignature, Integer> calculateMethodRevisions(
            List<CommitRecord> commits,
            Map<String, List<MethodInfo>> methodsByFile) {

        Map<MethodSignature, Integer> result = new HashMap<>();
        for (List<MethodInfo> methods : methodsByFile.values()) {
            for (MethodInfo method : methods) {
                result.put(method.signature(), 0);
            }
        }

        for (CommitRecord commit : commits) {
            Set<MethodSignature> creditedInThisCommit = new HashSet<>();
            for (FileChange change : commit.changes()) {
                List<MethodInfo> methods = methodsByFile.get(change.path());
                if (methods == null || methods.isEmpty()) {
                    continue;
                }
                if (change.hunks().isEmpty()) {
                    creditAllMethodsInFile(methods, creditedInThisCommit, result);
                } else {
                    creditMethodsOverlappingHunks(
                            change.hunks(), methods, creditedInThisCommit, result);
                }
            }
        }
        return Map.copyOf(result);
    }

    private void creditAllMethodsInFile(List<MethodInfo> methods,
                                        Set<MethodSignature> credited,
                                        Map<MethodSignature, Integer> counts) {
        for (MethodInfo method : methods) {
            if (credited.add(method.signature())) {
                counts.merge(method.signature(), 1, Integer::sum);
            }
        }
    }

    private void creditMethodsOverlappingHunks(List<DiffHunk> hunks,
                                               List<MethodInfo> methods,
                                               Set<MethodSignature> credited,
                                               Map<MethodSignature, Integer> counts) {
        for (DiffHunk hunk : hunks) {
            for (MethodInfo method : methods) {
                if (hunk.overlaps(method.startLine(), method.endLine())
                        && credited.add(method.signature())) {
                    counts.merge(method.signature(), 1, Integer::sum);
                }
            }
        }
    }
}
