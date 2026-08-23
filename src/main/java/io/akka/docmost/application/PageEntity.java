package io.akka.docmost.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.akka.docmost.domain.Document;
import io.akka.docmost.domain.HistoryWindow;
import io.akka.docmost.domain.Restriction;
import io.akka.docmost.domain.SavePolicy;
import io.akka.docmost.domain.VersionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A page: its content, who has contributed to it, its restriction, and its kept versions.
 *
 * <p>The history window's closing time is held here rather than in the scheduler. The target's
 * timer replaces a pending timer of the same name — deadline and payload both — so a window held
 * by the scheduler would close a span after the <em>last</em> store rather than the first. Holding
 * it here means a timer is only ever created on the transition into an open window, and the
 * scheduler never sees a name it already has.
 */
@Component(id = "page")
public class PageEntity extends EventSourcedEntity<PageEntity.State, PageEntity.Event> {

  // --- state ---------------------------------------------------------------------------

  public record Version(Document content, List<String> contributorIds, Instant keptAt) {}

  public record State(
      String pageId,
      String parentPageId,
      String spaceId,
      String creatorId,
      Instant createdAt,
      Document content,
      List<String> contributorIds,
      List<String> pendingContributorIds,
      String lastUpdatedById,
      Instant deletedAt,
      Instant windowClosesAt,
      Restriction restriction,
      List<Version> versions) {

    public boolean exists() {
      return pageId != null;
    }

    /** R13 compares against the most recent kept version, or nothing where there is none. */
    public Document lastVersionContent() {
      return versions.isEmpty() ? null : versions.get(versions.size() - 1).content();
    }
  }

  // --- events --------------------------------------------------------------------------

  public sealed interface Event {}

  @TypeName("page-created")
  public record PageCreated(
      String pageId, String parentPageId, String spaceId, String creatorId, Instant createdAt)
      implements Event {}

  @TypeName("page-stored")
  public record PageStored(
      Document content,
      List<String> contributorIds,
      List<String> pendingContributorIds,
      String lastUpdatedById)
      implements Event {}

  /** Emitted only by the store that opens a window, which is the one whose close is scheduled. */
  @TypeName("window-opened")
  public record WindowOpened(String pageId, Instant closesAt) implements Event {}

  @TypeName("window-closed")
  public record WindowClosed(Version version, boolean clearsPending) implements Event {}

  @TypeName("page-restricted")
  public record PageRestricted(Restriction restriction) implements Event {}

  @TypeName("page-deleted")
  public record PageDeleted(Instant deletedAt) implements Event {}

  // --- commands ------------------------------------------------------------------------

  public record CreateCommand(
      String parentPageId, String spaceId, String creatorId, Instant createdAt) {}

  /**
   * @param editorIds who changed the document since the previous store
   * @param now supplied by the caller so a benchmark and a test can drive the window without
   *     waiting on a wall clock
   */
  public record StoreCommand(Document content, List<String> editorIds, String userId, Instant now) {}

  /**
   * @param contributorIds the page's contributors after the store, carried here so a caller
   *     does not have to read the entity a second time to learn them
   */
  public record StoreResult(
      boolean changed,
      boolean windowOpened,
      Instant windowClosesAt,
      List<String> contributorIds) {}

  public record CloseResult(boolean kept, List<String> contributorIds) {}

  @Override
  public State emptyState() {
    return new State(
        null, null, null, null, null, Document.absent(), List.of(), List.of(), null, null, null,
        null, List.of());
  }

  public Effect<String> create(CreateCommand command) {
    if (currentState().exists()) {
      return effects().error("page already exists");
    }
    return effects()
        .persist(
            new PageCreated(
                commandContext().entityId(),
                command.parentPageId(),
                command.spaceId(),
                command.creatorId(),
                command.createdAt()))
        .thenReply(s -> s.pageId());
  }

  public Effect<StoreResult> store(StoreCommand command) {
    var state = currentState();
    if (!state.exists()) {
      return effects().error("page not found");
    }

    var save =
        SavePolicy.decide(
            command.content(),
            state.content(),
            new LinkedHashSet<>(state.contributorIds()),
            new LinkedHashSet<>(command.editorIds()),
            state.creatorId());

    // R6. Nothing changed, so nothing is written, no window opens, and nothing is notified.
    if (!save.changed()) {
      return effects()
          .reply(new StoreResult(false, false, state.windowClosesAt(), state.contributorIds()));
    }

    var pending = new LinkedHashSet<>(state.pendingContributorIds());
    pending.addAll(save.newPendingContributorIds());

    var window = HistoryWindow.onStore(state.windowClosesAt(), state.createdAt(), command.now());

    List<Event> events = new ArrayList<>();
    events.add(
        new PageStored(
            command.content(),
            List.copyOf(save.contributorIds()),
            List.copyOf(pending),
            command.userId()));
    if (window.opened()) {
      events.add(new WindowOpened(state.pageId(), window.closesAt()));
    }

    return effects()
        .persistAll(events)
        .thenReply(
            s ->
                new StoreResult(
                    true, window.opened(), window.closesAt(), s.contributorIds()));
  }

  /** Called when the window's timer fires. Idempotent: a close with no window open does nothing. */
  public Effect<CloseResult> closeWindow() {
    var state = currentState();
    if (!state.exists() || state.windowClosesAt() == null) {
      return effects().reply(new CloseResult(false, List.of()));
    }

    var decision =
        VersionPolicy.decide(
            state.content(),
            state.lastVersionContent(),
            new LinkedHashSet<>(state.pendingContributorIds()));

    var version =
        decision.keep()
            ? new Version(
                state.content(), List.copyOf(decision.contributorIds()), state.windowClosesAt())
            : null;

    return effects()
        .persist(new WindowClosed(version, decision.clearsPending()))
        .thenReply(
            s ->
                new CloseResult(
                    decision.keep(), version == null ? List.of() : version.contributorIds()));
  }

  public Effect<String> restrict(Restriction restriction) {
    if (!currentState().exists()) {
      return effects().error("page not found");
    }
    return effects().persist(new PageRestricted(restriction)).thenReply(s -> "restricted");
  }

  public Effect<String> delete(Instant deletedAt) {
    if (!currentState().exists()) {
      return effects().error("page not found");
    }
    return effects().persist(new PageDeleted(deletedAt)).thenReply(s -> "deleted");
  }

  // Effect, not ReadOnlyEffect: ComponentClient resolves a handler by its exact declared
  // return type, so a read-only handler is not reachable through it in this SDK version.
  public Effect<State> get() {
    if (!currentState().exists()) {
      return effects().error("page not found");
    }
    return effects().reply(currentState());
  }

  public Effect<List<Version>> versions() {
    return effects().reply(currentState().versions());
  }

  // --- events applied -------------------------------------------------------------------

  @Override
  public State applyEvent(Event event) {
    var s = currentState();
    return switch (event) {
      case PageCreated e ->
          new State(
              e.pageId(),
              e.parentPageId(),
              e.spaceId(),
              e.creatorId(),
              e.createdAt(),
              Document.absent(),
              List.of(),
              List.of(),
              null,
              null,
              null,
              null,
              List.of());
      case PageStored e ->
          new State(
              s.pageId(),
              s.parentPageId(),
              s.spaceId(),
              s.creatorId(),
              s.createdAt(),
              e.content(),
              e.contributorIds(),
              e.pendingContributorIds(),
              e.lastUpdatedById(),
              s.deletedAt(),
              s.windowClosesAt(),
              s.restriction(),
              s.versions());
      case WindowOpened e ->
          new State(
              s.pageId(),
              s.parentPageId(),
              s.spaceId(),
              s.creatorId(),
              s.createdAt(),
              s.content(),
              s.contributorIds(),
              s.pendingContributorIds(),
              s.lastUpdatedById(),
              s.deletedAt(),
              e.closesAt(),
              s.restriction(),
              s.versions());
      case WindowClosed e -> {
        var versions = s.versions();
        if (e.version() != null) {
          versions = new ArrayList<>(versions);
          versions.add(e.version());
        }
        yield new State(
            s.pageId(),
            s.parentPageId(),
            s.spaceId(),
            s.creatorId(),
            s.createdAt(),
            s.content(),
            s.contributorIds(),
            e.clearsPending() ? List.of() : s.pendingContributorIds(),
            s.lastUpdatedById(),
            s.deletedAt(),
            null,
            s.restriction(),
            List.copyOf(versions));
      }
      case PageRestricted e ->
          new State(
              s.pageId(),
              s.parentPageId(),
              s.spaceId(),
              s.creatorId(),
              s.createdAt(),
              s.content(),
              s.contributorIds(),
              s.pendingContributorIds(),
              s.lastUpdatedById(),
              s.deletedAt(),
              s.windowClosesAt(),
              e.restriction(),
              s.versions());
      case PageDeleted e ->
          new State(
              s.pageId(),
              s.parentPageId(),
              s.spaceId(),
              s.creatorId(),
              s.createdAt(),
              s.content(),
              s.contributorIds(),
              s.pendingContributorIds(),
              s.lastUpdatedById(),
              e.deletedAt(),
              s.windowClosesAt(),
              s.restriction(),
              s.versions());
    };
  }
}
