package de.c9n.radicale.utils;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemorizedDiffer {
  public static final String MEMORY_DEFAULT_BRANCH_NAME = "rsn";

  private final Path repositoryPath;

  public MemorizedDiffer(Path repositoryPath) {
    this.repositoryPath = requireNonNull(requireNonNull(repositoryPath));
    if (!Files.isDirectory(repositoryPath)) {
      throw new IllegalArgumentException("Unsupported git repository");
    }
  }

  @NotNull
  public DiffResult diff() throws IOException {
    try (Repository repo = FileRepositoryBuilder.create(repositoryPath.toFile())) {
      RevCommit compareDest = requireNonNull(getCommitByRef(repo, HEAD));
      RevCommit compareBase =
          requireNonNullElse(getCommitByRef(repo, MEMORY_DEFAULT_BRANCH_NAME), compareDest);

      List<DiffEntry> diffEntries = diffCommits(repo, compareBase, compareDest);
      return new DiffResult(repositoryPath, diffEntries, compareDest, MEMORY_DEFAULT_BRANCH_NAME);
    }
  }

  public static class DiffResult {
    private final Path repositoryPath;
    private final List<DiffEntry> diffEntries;
    private final RevCommit compareDest;

    private final String memoryBranchName;

    private boolean acknowledged = false;

    private DiffResult(
        Path repositoryPath,
        List<DiffEntry> diffEntries,
        RevCommit compareDest,
        String memoryBranchName) {
      this.repositoryPath = repositoryPath;
      this.diffEntries = diffEntries;
      this.compareDest = compareDest;
      this.memoryBranchName = memoryBranchName;
    }

    public synchronized void acknowledge() throws IOException {
      if (!acknowledged) {
        try (Repository repo = FileRepositoryBuilder.create(repositoryPath.toFile())) {
          RefUpdate updateRef = repo.updateRef(R_HEADS + memoryBranchName);

          updateRef.setNewObjectId(compareDest);
          updateRef.setRefLogMessage("Acknowledged changed entries", false);
          Result result = updateRef.forceUpdate();

          switch (result) {
            case NEW:
            case FORCED:
            case NO_CHANGE:
              // everything is fine
              break;
            default:
              // unexpected happened
              throw new RuntimeException("unable to acknowledge, update ref result: " + result);
          }
        }
      }
      acknowledged = true;
    }

    public List<DiffEntry> getDiffEntries() {
      return diffEntries;
    }

    public boolean isAcknowledged() {
      return acknowledged;
    }
  }

  static List<DiffEntry> diffCommits(Repository repo, RevCommit oldCommit, RevCommit newCommit)
      throws IOException {
    return diffCommits(repo, oldCommit, newCommit, emptyList());
  }

  static List<DiffEntry> diffCommits(
      Repository repo, RevCommit oldCommit, RevCommit newCommit, Collection<String> paths)
      throws IOException {

    TreeFilter filter = TreeFilter.ANY_DIFF;
    if (!paths.isEmpty()) {
      TreeFilter pathFilter = PathFilterGroup.createFromStrings(paths);
      filter = AndTreeFilter.create(pathFilter, TreeFilter.ANY_DIFF);
    }

    RevTree mainTree = newCommit.getTree();
    RevTree rsnTree = oldCommit.getTree();

    try (TreeWalk treeWalk = new TreeWalk(repo)) {
      treeWalk.setFilter(filter);
      treeWalk.setRecursive(true);

      treeWalk.addTree(rsnTree);
      treeWalk.addTree(mainTree);

      return DiffEntry.scan(treeWalk);
    }
  }

  @Nullable
  static RevCommit getCommitByRef(Repository repo, String refName) throws IOException {
    Ref ref = repo.findRef(refName);
    if (ref == null) {
      return null;
    }

    return repo.parseCommit(ref.getObjectId());
  }
}
