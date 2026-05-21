package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.FileChange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Runs the shared {@link VcsProviderContract} against {@link InMemoryVcsProvider}.
 *
 * <p>This serves two purposes:
 * <ol>
 *   <li>Verifies the contract itself is internally consistent.</li>
 *   <li>Provides a working reference implementation the real providers
 *       (T4 local, T5 GitHub) can be checked against.</li>
 * </ol>
 */
class InMemoryVcsProviderContractTest extends VcsProviderContract {

    private static final Instant BASE_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @Override
    protected VcsProvider providerWithKnownHistory() {
        return new InMemoryVcsProvider(List.of(
                commit("a1b2c3d", "alice", BASE_TIME, ChangeType.ADDED),
                commit("e4f5g6h", "bob", BASE_TIME.plusSeconds(3600), ChangeType.MODIFIED),
                commit("i7j8k9l", "alice", BASE_TIME.plusSeconds(7200), ChangeType.MODIFIED)
        ));
    }

    @Override
    protected WindowConfig fullWindow() {
        return new WindowConfig(LocalDate.parse("2026-01-15"), LocalDate.parse("2026-01-16"), null);
    }

    @Override
    protected WindowConfig emptyWindow() {
        return new WindowConfig(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-12-31"), null);
    }

    @Override
    protected int expectedCommitCount() {
        return 3;
    }

    private static CommitRecord commit(String hash, String author, Instant at, ChangeType type) {
        FileChange change = new FileChange("src/Demo.java", null, 5, 1, type);
        return new CommitRecord(hash, author, at, "Demo commit", List.of(change));
    }
}
