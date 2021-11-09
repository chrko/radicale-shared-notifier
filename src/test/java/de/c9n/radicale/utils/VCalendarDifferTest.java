package de.c9n.radicale.utils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Summary;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.CloseResource")
class VCalendarDifferTest extends BaseRadicaleGitTest {
  @Test
  void test() throws Exception {
    setupRepository(
        "451fc333aecc09bebfaacc9ff0799e3d6ecf744f", "cd9bf0e08809c74e06ac051e951d2a1d1af177ea");

    MemorizedDiffer.DiffResult diff = new MemorizedDiffer(repositoryPath).diff();
    List<DiffEntry> diffEntries = diff.getDiffEntries();
    assertThat(diffEntries).hasSize(1);
    DiffEntry addedEvent = diffEntries.get(0);
    assertThat(addedEvent.getChangeType()).isEqualTo(DiffEntry.ChangeType.ADD);

    AbbreviatedObjectId abbreviatedObjectId = addedEvent.getNewId();
    assertThat(abbreviatedObjectId).isNotNull();

    Repository repo = testRepository.getRepository();
    ObjectId objectId = abbreviatedObjectId.toObjectId();
    if (objectId == null) {
      try (ObjectReader objReader = repo.newObjectReader()) {
        Collection<ObjectId> objectIds = objReader.resolve(abbreviatedObjectId);
        assertThat(objectIds).hasSize(1);
        objectId = objectIds.toArray(new ObjectId[0])[0];
      }
    }

    assertThat(objectId).isNotNull();

    ObjectLoader objectLoader = repo.open(objectId, Constants.OBJ_BLOB);

    CalendarBuilder calBuilder = new CalendarBuilder();

    Calendar calendar;
    try (InputStream objectInputStream = objectLoader.openStream()) {
      calendar = calBuilder.build(objectInputStream);
    }
    assertThat(calendar).isNotNull();
    Optional<VEvent> optEvent = calendar.getComponent(Component.VEVENT);
    VEvent event = optEvent.orElseThrow();
    Optional<Summary> summary = event.getProperty(Property.SUMMARY);
    assertThat(summary).isPresent();
    assertThat(summary.get().getValue()).startsWith("First event");
  }
}
