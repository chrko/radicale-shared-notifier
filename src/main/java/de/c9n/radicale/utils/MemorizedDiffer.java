package de.c9n.radicale.utils;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemorizedDiffer {
  public static final String MEMORY_DEFAULT_BRANCH_NAME = "rsn";

  private final Path repositoryPath;

  private final List<TreeFilter> additionalFilters = new ArrayList<>();

  public MemorizedDiffer(Path repositoryPath) {
    this.repositoryPath = requireNonNull(requireNonNull(repositoryPath));
    if (!Files.isDirectory(repositoryPath)) {
      throw new IllegalArgumentException("Unsupported git repository");
    }
  }

  public MemorizedDiffer addFilter(@NotNull TreeFilter filter) {
    additionalFilters.add(filter);
    return this;
  }

  @NotNull
  public DiffResult diff() throws IOException {
    try (Repository repo = FileRepositoryBuilder.create(repositoryPath.toFile())) {
      RevCommit compareDest = requireNonNull(getCommitByRef(repo, HEAD));
      RevCommit compareBase =
          requireNonNullElse(getCommitByRef(repo, MEMORY_DEFAULT_BRANCH_NAME), compareDest);

      List<DiffEntry> diffEntries = diffCommits(repo, compareBase, compareDest, additionalFilters);
      return new DiffResult(
          repositoryPath, diffEntries, compareBase, compareDest, MEMORY_DEFAULT_BRANCH_NAME);
    }
  }

  public static class DiffResult {
    private final Path repositoryPath;
    private final List<DiffEntry> diffEntries;
    private final RevCommit compareBase;
    private final RevCommit compareDest;

    private final String memoryBranchName;

    private boolean acknowledged = false;

    private DiffResult(
        Path repositoryPath,
        List<DiffEntry> diffEntries,
        RevCommit compareBase,
        RevCommit compareDest,
        String memoryBranchName) {
      this.repositoryPath = repositoryPath;
      this.diffEntries = diffEntries;
      this.compareBase = compareBase;
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

    public String loadFile(String path) throws IOException {
      try (Repository repo = FileRepositoryBuilder.create(repositoryPath.toFile());
          TreeWalk treeWalk = new TreeWalk(repo)) {
        treeWalk.setFilter(PathFilter.create(path));
        treeWalk.addTree(compareDest.getTree());
        treeWalk.addTree(compareBase.getTree());
        treeWalk.setRecursive(true);

        if (!treeWalk.next()) {
          throw new FileNotFoundException(path + " not found in git");
        }

        ObjectId objectId = treeWalk.getObjectId(0);
        if (objectId.equals(ObjectId.zeroId())) {
          objectId = treeWalk.getObjectId(1);
        }
        ObjectLoader objectReader = repo.open(objectId, Constants.OBJ_BLOB);

        return new String(objectReader.getBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  static List<DiffEntry> diffCommits(
      Repository repo,
      RevCommit oldCommit,
      RevCommit newCommit,
      Collection<TreeFilter> additionalFilters)
      throws IOException {

    TreeFilter filter = TreeFilter.ANY_DIFF;
    if (!additionalFilters.isEmpty()) {
      List<TreeFilter> filters = new ArrayList<>(additionalFilters);
      filters.add(TreeFilter.ANY_DIFF);
      filter = AndTreeFilter.create(filters);
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
