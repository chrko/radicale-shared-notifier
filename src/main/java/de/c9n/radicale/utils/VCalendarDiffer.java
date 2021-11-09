package de.c9n.radicale.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class VCalendarDiffer {
  private final VEvent a;
  private final VEvent b;

  public VCalendarDiffer(Path repositoryPath, DiffEntry diffEntry)
      throws IOException, ParserException {
    try (Repository repo = FileRepositoryBuilder.create(repositoryPath.toFile())) {
      switch (diffEntry.getChangeType()) {
        case ADD:
          a = null;
          b = loadVEvent(repo, diffEntry.getNewId().toObjectId());
          break;
        case DELETE:
          a = loadVEvent(repo, diffEntry.getOldId().toObjectId());
          b = null;
          break;
        case MODIFY:
          a = loadVEvent(repo, diffEntry.getOldId().toObjectId());
          b = loadVEvent(repo, diffEntry.getNewId().toObjectId());
          break;
        default:
          throw new IllegalStateException("Unsupported changeType");
      }
    }
  }

  static VEvent loadVEvent(Repository repo, ObjectId objectId) throws IOException, ParserException {
    try (ObjectReader reader = repo.newObjectReader()) {
      ObjectLoader objectLoader = reader.open(objectId, Constants.OBJ_BLOB);
      try (ObjectStream objectStream = objectLoader.openStream()) {
        Calendar calendar = new CalendarBuilder().build(objectStream);
        Optional<VEvent> event = calendar.getComponent(Component.VEVENT);
        return event.orElse(null);
      }
    }
  }

  public Optional<VEvent> getA() {
    return Optional.ofNullable(a);
  }

  public Optional<VEvent> getB() {
    return Optional.ofNullable(b);
  }
}
