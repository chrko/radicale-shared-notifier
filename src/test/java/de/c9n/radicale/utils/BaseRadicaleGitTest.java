package de.c9n.radicale.utils;

import static com.google.common.truth.Truth8.assertThat;
import static de.c9n.radicale.utils.MemorizedDiffer.MEMORY_DEFAULT_BRANCH_NAME;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
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

@SuppressWarnings("PMD.CloseResource")
public class BaseRadicaleGitTest {
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
      Truth.assertThat(dotGitFile).startsWith("gitdir: ");
      String extractedGitDir = dotGitFile.substring("gitdir: ".length(), dotGitFile.length() - 1);
      repositoryPath = repositoryPath.resolve(extractedGitDir).normalize();
    }

    Repository repo = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build();
    testRepository = new TestRepository<>(repo);
    List<Ref> refs = repo.getRefDatabase().getRefs();
    Truth.assertThat(refs).isNotEmpty();

    originalHead = repo.exactRef(HEAD).getObjectId();
  }

  @AfterAll
  static void cleanup() {
    testRepository.close();
  }

  @AfterEach
  void resetRepository() throws GitAPIException {
    testRepository
        .git()
        .reset()
        .setMode(ResetCommand.ResetType.HARD)
        .setRef(originalHead.getName())
        .call();
  }

  void setupRepository(@NotNull String headCommit, @Nullable String rsnBranchCommit)
      throws Exception {
    requireNonNull(headCommit);

    TestRepository<?>.BranchBuilder rsnBranch = testRepository.branch(MEMORY_DEFAULT_BRANCH_NAME);

    if (rsnBranchCommit != null) {
      ObjectId commitObjectId = ObjectId.fromString(rsnBranchCommit);
      RevCommit commit = testRepository.getRepository().parseCommit(commitObjectId);

      rsnBranch.update(commit);
    } else {
      rsnBranch.delete();
    }

    testRepository.reset(headCommit);
  }
}
