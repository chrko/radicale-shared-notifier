package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RadicaleCollectionExtractorTest {
  static Path repositoryPath;

  @BeforeAll
  static void setupRepositoryPath() throws URISyntaxException {
    repositoryPath =
        Path.of(
            requireNonNull(
                    Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("radicale-git/collections/"))
                .toURI());

    assertThat(repositoryPath).isNotNull();
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

    RadicaleCollectionExtractor extractor = new RadicaleCollectionExtractor(repositoryPath);
    List<DiffEntry> diffEntries = extractor.getDiffEntries();
    assertThat(diffEntries).hasSize(1);
    DiffEntry entry = diffEntries.get(0);
    assertThat(entry.getChangeType()).isEqualTo(ChangeType.ADD);

    extractor.acknowledge();
    assertThat(extractor.getDiffEntries()).isEmpty();
  }
}
