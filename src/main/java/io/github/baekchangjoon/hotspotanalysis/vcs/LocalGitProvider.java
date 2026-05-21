package io.github.baekchangjoon.hotspotanalysis.vcs;

import io.github.baekchangjoon.hotspotanalysis.config.WindowConfig;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.ChangeType;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.CommitRecord;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.DiffHunk;
import io.github.baekchangjoon.hotspotanalysis.vcs.model.FileChange;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reads commit history from a local git working tree via JGit.
 *
 * <p>Behavioural contract enforced by
 * {@code io.github.baekchangjoon.hotspotanalysis.vcs.VcsProviderContract}
 * via {@code LocalGitProviderTest}.</p>
 */
public class LocalGitProvider implements VcsProvider {

    private final Path repositoryPath;

    public LocalGitProvider(Path repositoryPath) {
        this.repositoryPath = Objects.requireNonNull(repositoryPath, "repositoryPath");
        if (!Files.isDirectory(repositoryPath)) {
            throw new VcsException(
                    "Repository path does not exist or is not a directory: " + repositoryPath);
        }
    }

    @Override
    public List<CommitRecord> loadCommits(WindowConfig window) {
        Instant lowerBound = resolveLowerBound(window);
        Instant upperBound = resolveUpperBound(window);

        try (Git git = Git.open(repositoryPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId headId = repo.resolve(Constants.HEAD);
            if (headId == null) {
                return List.of();
            }

            List<CommitRecord> collected = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(headId));
                for (RevCommit commit : walk) {
                    Instant committedAt = Instant.ofEpochSecond(commit.getCommitTime());
                    if (committedAt.isBefore(lowerBound) || committedAt.isAfter(upperBound)) {
                        continue;
                    }
                    collected.add(toCommitRecord(repo, walk, commit));
                }
            }
            collected.sort(Comparator.comparing(CommitRecord::committedAt));
            return List.copyOf(collected);
        } catch (IOException e) {
            throw new VcsException("Failed to read repository at " + repositoryPath, e);
        }
    }

    private CommitRecord toCommitRecord(Repository repo, RevWalk walk, RevCommit commit)
            throws IOException {
        List<FileChange> changes = collectChanges(repo, walk, commit);
        return new CommitRecord(
                commit.getName(),
                commit.getAuthorIdent().getName(),
                Instant.ofEpochSecond(commit.getCommitTime()),
                commit.getFullMessage(),
                changes);
    }

    private List<FileChange> collectChanges(Repository repo, RevWalk walk, RevCommit commit)
            throws IOException {
        if (commit.getParentCount() == 0) {
            return changesFromEmpty(repo, commit);
        }
        RevCommit parent = walk.parseCommit(commit.getParent(0));
        return changesAgainstParent(repo, parent.getTree(), commit.getTree());
    }

    private List<FileChange> changesAgainstParent(Repository repo, RevTree oldTree, RevTree newTree)
            throws IOException {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repo);
            formatter.setDetectRenames(true);
            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            List<FileChange> result = new ArrayList<>(entries.size());
            for (DiffEntry entry : entries) {
                result.add(toFileChange(formatter, entry));
            }
            return result;
        }
    }

    private List<FileChange> changesFromEmpty(Repository repo, RevCommit commit) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            List<FileChange> result = new ArrayList<>();
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                ObjectId blobId = treeWalk.getObjectId(0);
                int lines = countLines(repo, blobId);
                List<DiffHunk> hunks = lines > 0
                        ? List.of(new DiffHunk(1, lines))
                        : List.of();
                result.add(new FileChange(path, null, lines, 0, ChangeType.ADDED, hunks));
            }
            return result;
        }
    }

    private FileChange toFileChange(DiffFormatter formatter, DiffEntry entry) throws IOException {
        ChangeType mappedType = mapChangeType(entry.getChangeType());
        FileHeader header = formatter.toFileHeader(entry);
        EditList edits = header.toEditList();

        int added = 0;
        int deleted = 0;
        List<DiffHunk> hunks = new ArrayList<>();
        for (Edit edit : edits) {
            added += edit.getEndB() - edit.getBeginB();
            deleted += edit.getEndA() - edit.getBeginA();
            if (edit.getEndB() > edit.getBeginB()) {
                hunks.add(new DiffHunk(edit.getBeginB() + 1, edit.getEndB()));
            }
        }

        return switch (mappedType) {
            case RENAMED -> new FileChange(
                    entry.getNewPath(), entry.getOldPath(),
                    added, deleted, ChangeType.RENAMED, hunks);
            case DELETED -> new FileChange(
                    entry.getOldPath(), null,
                    added, deleted, ChangeType.DELETED, List.of());
            default -> new FileChange(
                    entry.getNewPath(), null,
                    added, deleted, mappedType, hunks);
        };
    }

    private static ChangeType mapChangeType(DiffEntry.ChangeType source) {
        return switch (source) {
            case ADD -> ChangeType.ADDED;
            case MODIFY -> ChangeType.MODIFIED;
            case DELETE -> ChangeType.DELETED;
            case RENAME, COPY -> ChangeType.RENAMED;
        };
    }

    private static int countLines(Repository repo, ObjectId blobId) throws IOException {
        byte[] bytes = repo.open(blobId).getBytes();
        if (bytes.length == 0) {
            return 0;
        }
        int count = 0;
        for (byte b : bytes) {
            if (b == '\n') {
                count++;
            }
        }
        if (bytes[bytes.length - 1] != '\n') {
            count++;
        }
        return count;
    }

    private static Instant resolveLowerBound(WindowConfig window) {
        if (window.since() != null) {
            return window.since().atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (window.days() != null) {
            return Instant.now().minus(Duration.ofDays(window.days()));
        }
        return Instant.EPOCH;
    }

    private static Instant resolveUpperBound(WindowConfig window) {
        if (window.until() != null) {
            return window.until().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        }
        return Instant.now();
    }
}
