package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static de.c9n.radicale.utils.MemorizedDiffer.LAST_COMPARE_BRANCH_NAME;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CloseResource")
class MemorizedDifferTest {
  static Path repositoryPath;
  static ObjectId originalHead;
  static TestRepository<?> testRepository;

  @BeforeAll
  static void setupRepositoryPath() throws URISyntaxException, IOException {
    repositoryPath =
        Path.of(
            requireNonNull(
                    Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("radicale-git/collections/"))
                .toURI());

    assertThat(repositoryPath).isNotNull();
    if (Files.isRegularFile(repositoryPath.resolve(".git"))) {
      String dotGitFile = Files.readString(repositoryPath.resolve(".git"));
      assertThat(dotGitFile).startsWith("gitdir: ");
      String extractedGitDir = dotGitFile.substring("gitdir: ".length(), dotGitFile.length() - 1);
      repositoryPath = repositoryPath.resolve(extractedGitDir).normalize();
    }

    Repository repo = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build();
    testRepository = new TestRepository<>(repo);
    List<Ref> refs = repo.getRefDatabase().getRefs();
    assertThat(refs).isNotEmpty();

    originalHead = repo.exactRef(HEAD).getObjectId();
  }

  @AfterEach
  void resetRepository() throws GitAPIException {
    testRepository.git().reset().setMode(ResetType.HARD).setRef(originalHead.getName()).call();
  }

  @AfterAll
  static void cleanup() {
    testRepository.close();
  }

  void setupRepository(@NotNull String headCommit, @Nullable String rsnBranchCommit)
      throws Exception {
    requireNonNull(headCommit);

    TestRepository<?>.BranchBuilder rsnBranch = testRepository.branch(LAST_COMPARE_BRANCH_NAME);

    if (rsnBranchCommit != null) {
      ObjectId commitObjectId = ObjectId.fromString(rsnBranchCommit);
      RevCommit commit = testRepository.getRepository().parseCommit(commitObjectId);

      rsnBranch.update(commit);
    } else {
      rsnBranch.delete();
    }

    testRepository.reset(headCommit);
  }

  @Test
  void addEvent() throws Exception {
    setupRepository(
        "6081cdf38857e9c98969e74946fd6a1c717b07dc", "bde4e8ebce802f3a4605cfacde71ee3cd4fc266d");

    try (MemorizedDiffer extractor = new MemorizedDiffer(repositoryPath)) {
      List<DiffEntry> diffEntries = extractor.getDiffEntries();
      assertThat(diffEntries).hasSize(1);
      DiffEntry entry = diffEntries.get(0);
      assertThat(entry.getChangeType()).isEqualTo(ChangeType.ADD);

      extractor.acknowledge();
      assertThat(extractor.getDiffEntries()).isEmpty();
    }
  }

  @Test
  void bootstrap() throws Exception {
    setupRepository("6081cdf38857e9c98969e74946fd6a1c717b07dc", null);

    try (MemorizedDiffer extractor = new MemorizedDiffer(repositoryPath)) {
      List<DiffEntry> diffEntries = extractor.getDiffEntries();
      assertThat(diffEntries).isEmpty();
      extractor.acknowledge();
    }

    Repository repo = testRepository.getRepository();
    Ref branch = repo.findRef(LAST_COMPARE_BRANCH_NAME);
    assertThat(branch.getObjectId().getName())
        .isEqualTo("6081cdf38857e9c98969e74946fd6a1c717b07dc");
  }
}
