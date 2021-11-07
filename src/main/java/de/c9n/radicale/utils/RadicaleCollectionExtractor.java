package de.c9n.radicale.utils;

import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadicaleCollectionExtractor implements AutoCloseable {
  private static final String COLLECTION_ROOT = "collection-root";
  private static final String JGIT_DIR_SEPARATOR = "/";
  private static final String USER_PATH_PREFIX = COLLECTION_ROOT + JGIT_DIR_SEPARATOR;

  public static final String LAST_COMPARE_BRANCH_NAME = "rsn";

  private final Git git;
  private final Set<String> users = new HashSet<>();

  private final Object syncCompare = new Object();
  @Nullable private RevCommit compareBase;
  @Nullable private RevCommit compareDest;

  public RadicaleCollectionExtractor(Path repositoryPath) throws IOException {
    git = Git.open(repositoryPath.toFile());
  }

  public void addUser(String user) {
    users.add(user);
  }

  public void removeUser(String user) {
    users.remove(user);
  }

  @NotNull
  public List<DiffEntry> getDiffEntries() throws IOException, RefNotFoundException {
    RevCommit oldCommit, newCommit;
    synchronized (syncCompare) {
      if (compareBase == null && compareDest == null) {
        compareBase = getCommitByRef(LAST_COMPARE_BRANCH_NAME);
        compareDest = getCommitByRef(Constants.HEAD);
      }
      requireNonNull(compareBase);
      requireNonNull(compareDest);

      oldCommit = compareBase;
      newCommit = compareDest;
    }

    if (users.isEmpty()) {
      return diffCommits(git.getRepository(), oldCommit, newCommit);
    } else {
      return diffCommits(git.getRepository(), oldCommit, newCommit, getUserPathFilter());
    }
  }

  public void acknowledge() throws IOException {
    synchronized (syncCompare) {
      RefUpdate updateRef = git.getRepository().updateRef(R_HEADS + LAST_COMPARE_BRANCH_NAME);
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

      compareBase = null; // NOPMD
      compareDest = null; // NOPMD
    }
  }

  @NotNull
  static List<DiffEntry> diffCommits(
      Repository repo, RevCommit oldCommit, RevCommit newCommit, TreeFilter... pathFilters)
      throws IOException {

    TreeFilter filter = TreeFilter.ANY_DIFF;
    if (pathFilters.length > 0) {
      List<TreeFilter> filters = new ArrayList<>(Arrays.asList(pathFilters));
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

  @NotNull
  private TreeFilter getUserPathFilter() {
    List<String> paths = new ArrayList<>();
    for (String user : users) {
      paths.add(USER_PATH_PREFIX + user);
    }
    return PathFilterGroup.createFromStrings(paths);
  }

  @NotNull
  RevCommit getCommitByRef(String refName) throws IOException, RefNotFoundException {
    Ref ref = git.getRepository().findRef(refName);
    if (ref == null) {
      throw new RefNotFoundException(refName);
    }

    return git.getRepository().parseCommit(ref.getObjectId());
  }

  @Override
  public void close() throws Exception {
    git.close();
  }
}
