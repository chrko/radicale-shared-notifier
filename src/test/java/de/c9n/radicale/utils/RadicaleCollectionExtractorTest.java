package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RadicaleCollectionExtractorTest {
  static Path repositoryPath;
  static ObjectId originalHead;

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

    try (Repository repo = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build()) {
      List<Ref> refs = repo.getRefDatabase().getRefs();
      assertThat(refs).isNotEmpty();

      originalHead = repo.exactRef(HEAD).getObjectId();
    }
  }

  @AfterAll
  static void resetRepository() throws IOException, GitAPIException {
    try (Repository repo = new FileRepositoryBuilder().setGitDir(repositoryPath.toFile()).build();
        Git git = Git.wrap(repo)) {
      git.reset().setMode(ResetType.HARD).setRef(originalHead.getName()).call();
    }
  }

  void setupRepository(@NotNull String mainBranchStartPoint, @Nullable String rsnBranchStartPoint)
      throws IOException, GitAPIException {
    requireNonNull(mainBranchStartPoint);

    try (Git git = Git.open(repositoryPath.toFile())) {
      if (rsnBranchStartPoint != null) {
        git.branchCreate()
            .setName(RadicaleCollectionExtractor.LAST_COMPARE_BRANCH_NAME)
            .setStartPoint(rsnBranchStartPoint)
            .setForce(true)
            .call();
      } else {
        git.branchDelete()
            .setBranchNames(RadicaleCollectionExtractor.LAST_COMPARE_BRANCH_NAME)
            .setForce(true)
            .call();
      }

      git.branchCreate().setName("main").setStartPoint(mainBranchStartPoint).setForce(true).call();
      git.reset().setRef("main").setMode(ResetType.HARD).call();
    }
  }

  @Test
  void addEvent() throws IOException, GitAPIException {
    setupRepository(
        "6081cdf38857e9c98969e74946fd6a1c717b07dc", "bde4e8ebce802f3a4605cfacde71ee3cd4fc266d");

    try (RadicaleCollectionExtractor extractor = new RadicaleCollectionExtractor(repositoryPath)) {
      List<DiffEntry> diffEntries = extractor.getDiffEntries();
      assertThat(diffEntries).hasSize(1);
      DiffEntry entry = diffEntries.get(0);
      assertThat(entry.getChangeType()).isEqualTo(ChangeType.ADD);

      extractor.acknowledge();
      assertThat(extractor.getDiffEntries()).isEmpty();
    }
  }
}
