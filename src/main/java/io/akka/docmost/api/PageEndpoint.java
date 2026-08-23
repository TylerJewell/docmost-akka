package io.akka.docmost.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.CommandException;
import akka.javasdk.http.HttpException;
import io.akka.docmost.application.PageEntity;
import io.akka.docmost.application.SpaceEntity;
import io.akka.docmost.domain.Admission;
import io.akka.docmost.domain.Document;
import io.akka.docmost.domain.Restriction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The capability's own surface: open a session on a page, store a document into it, read its
 * versions.
 *
 * <p>Whether a store is allowed is decided here rather than inside the entity, because it is a
 * property of the <em>session</em> — the user, their roles, and the page's restriction chain —
 * rather than of the page.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/pages")
public class PageEndpoint {

  private final ComponentClient componentClient;

  public PageEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CreateRequest(
      String parentPageId, String spaceId, String creatorId, Instant createdAt) {}

  public record SessionResponse(String pageId, String userId, Admission.Outcome outcome) {}

  public record StoreRequest(Document content, List<String> editorIds, String userId, Instant now) {}

  public record StoreResponse(
      boolean changed, boolean windowOpened, Instant windowClosesAt, List<String> contributorIds) {}

  public record CloseResponse(boolean kept, List<String> contributorIds) {}

  @Post("/{pageId}")
  public HttpResponse create(String pageId, CreateRequest request) {
    var createdAt = request.createdAt() == null ? Instant.now() : request.createdAt();
    var id =
        componentClient
            .forEventSourcedEntity(pageId)
            .method(PageEntity::create)
            .invoke(
            new PageEntity.CreateCommand(
                request.parentPageId(), request.spaceId(), request.creatorId(), createdAt));
    return HttpResponses.created(id, "/pages/" + id);
  }

  @Put("/{pageId}/restriction")
  public String restrict(String pageId, Restriction restriction) {
    return componentClient
        .forEventSourcedEntity(pageId)
        .method(PageEntity::restrict)
        .invoke(restriction);
  }

  @Post("/{pageId}/delete")
  public String delete(String pageId) {
    return componentClient
        .forEventSourcedEntity(pageId)
        .method(PageEntity::delete)
        .invoke(Instant.now());
  }

  /** R1–R5, over the page's chain of ancestors. */
  @Get("/{pageId}/session/{userId}")
  public SessionResponse session(String pageId, String userId) {
    return new SessionResponse(pageId, userId, admit(pageId, userId));
  }

  /**
   * A store on a session that is not writable is refused rather than dropped. The original has no
   * behaviour to copy here — its transport discards such an edit before its own code sees it — and
   * a permission decision that is silently ignored is indistinguishable from one that was applied.
   */
  @Post("/{pageId}/store")
  public StoreResponse store(String pageId, StoreRequest request) {
    var outcome = admit(pageId, request.userId());
    if (outcome != Admission.Outcome.WRITABLE) {
      throw HttpException.error(
          StatusCodes.FORBIDDEN, "session is " + outcome.name().toLowerCase() + ", not writable");
    }
    var now = request.now() == null ? Instant.now() : request.now();
    var editors = request.editorIds() == null ? List.<String>of() : request.editorIds();
    var result =
        componentClient
            .forEventSourcedEntity(pageId)
            .method(PageEntity::store)
            .invoke(new PageEntity.StoreCommand(request.content(), editors, request.userId(), now));
    return new StoreResponse(
        result.changed(), result.windowOpened(), result.windowClosesAt(), result.contributorIds());
  }

  @Get("/{pageId}/versions")
  public List<PageViews.VersionResponse> versions(String pageId) {
    return PageViews.toApi(
        componentClient.forEventSourcedEntity(pageId).method(PageEntity::versions).invoke());
  }

  /**
   * Closes the page's history window now rather than when its timer fires. The behaviour under
   * test is what a close does, not when the runtime gets round to it, so a benchmark and an
   * end-to-end test reach it directly.
   */
  @Post("/{pageId}/close-window")
  public CloseResponse closeWindow(String pageId) {
    var result =
        componentClient.forEventSourcedEntity(pageId).method(PageEntity::closeWindow).invoke();
    return new CloseResponse(result.kept(), result.contributorIds());
  }

  @Get("/{pageId}")
  public PageViews.PageResponse get(String pageId) {
    return PageViews.toApi(page(pageId));
  }

  /**
   * A page the caller named but that does not exist is a 404 rather than the raw 400 a
   * component error surfaces by default.
   */
  private PageEntity.State page(String pageId) {
    try {
      return componentClient.forEventSourcedEntity(pageId).method(PageEntity::get).invoke();
    } catch (CommandException e) {
      throw HttpException.notFound();
    }
  }

  /**
   * Walks from the page to the root collecting each page's restriction, nearest first, then asks
   * the domain. The walk is bounded so a parent cycle cannot spin.
   */
  private Admission.Outcome admit(String pageId, String userId) {
    var page = page(pageId);
    var space =
        componentClient.forKeyValueEntity(page.spaceId()).method(SpaceEntity::get).invoke();

    var chain = new ArrayList<Restriction>();
    var current = page;
    for (int depth = 0; depth < MAX_DEPTH; depth++) {
      chain.add(current.restriction());
      if (current.parentPageId() == null) {
        break;
      }
      current = page(current.parentPageId());
    }

    return Admission.decide(
        space.highestRole(userId),
        space.groupsOf(userId),
        userId,
        chain,
        page.deletedAt() != null);
  }

  private static final int MAX_DEPTH = 64;
}
