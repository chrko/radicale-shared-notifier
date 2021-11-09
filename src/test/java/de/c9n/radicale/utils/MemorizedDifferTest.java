package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static de.c9n.radicale.utils.MemorizedDiffer.MEMORY_DEFAULT_BRANCH_NAME;

import de.c9n.radicale.utils.MemorizedDiffer.DiffResult;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CloseResource")
class MemorizedDifferTest extends BaseRadicaleGitTest {
  @Test
  void addEvent() throws Exception {
    setupRepository(
        "6081cdf38857e9c98969e74946fd6a1c717b07dc", "bde4e8ebce802f3a4605cfacde71ee3cd4fc266d");

    DiffResult diffResult = new MemorizedDiffer(repositoryPath).diff();
    List<DiffEntry> diffEntries = diffResult.getDiffEntries();
    assertThat(diffEntries).hasSize(1);
    DiffEntry entry = diffEntries.get(0);
    assertThat(entry.getChangeType()).isEqualTo(ChangeType.ADD);

    diffResult.acknowledge();
  }

  @Test
  void bootstrap() throws Exception {
    setupRepository("6081cdf38857e9c98969e74946fd6a1c717b07dc", null);

    DiffResult diffResult = new MemorizedDiffer(repositoryPath).diff();
    List<DiffEntry> diffEntries = diffResult.getDiffEntries();
    assertThat(diffEntries).isEmpty();
    diffResult.acknowledge();

    Repository repo = testRepository.getRepository();
    Ref branch = repo.findRef(MEMORY_DEFAULT_BRANCH_NAME);
    assertThat(branch.getObjectId().getName())
        .isEqualTo("6081cdf38857e9c98969e74946fd6a1c717b07dc");
  }

  @Test
  void createCollection() throws Exception {
    setupRepository(
        "bde4e8ebce802f3a4605cfacde71ee3cd4fc266d", "ee5881c141256884d853b3976c4bec9ca13ade1c");

    DiffResult diffResult =
        new MemorizedDiffer(repositoryPath).addFilter(PathSuffixFilter.create(".ics")).diff();
    assertThat(diffResult.getDiffEntries()).isEmpty();
  }

  @Test
  void deleteCollection() throws Exception {
    setupRepository(
        "ce6f192edfc2ab92e8335c58c04a02e5f248a399", "60f99aa36ec0f6c305592f2e56afab12a5ebe4ec");

    DiffResult diffResult =
        new MemorizedDiffer(repositoryPath).addFilter(PathSuffixFilter.create(".ics")).diff();
    assertThat(diffResult.getDiffEntries()).hasSize(2);
    DiffEntry entry = diffResult.getDiffEntries().get(0);
    assertThat(entry.getChangeType()).isEqualTo(ChangeType.DELETE);

    String path =
        Path.of(entry.getOldPath()).getParent().resolve(".Radicale.props").normalize().toString();
    String props = diffResult.loadFile(path);
    assertThat(props).contains("Test calendar");
  }
}
