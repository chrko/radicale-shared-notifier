package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static de.c9n.radicale.utils.MemorizedDiffer.MEMORY_DEFAULT_BRANCH_NAME;

import de.c9n.radicale.utils.MemorizedDiffer.DiffResult;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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
}
