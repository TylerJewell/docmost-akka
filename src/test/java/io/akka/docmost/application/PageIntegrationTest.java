package io.akka.docmost.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.docmost.domain.Document;
import io.akka.docmost.domain.Grant;
import io.akka.docmost.domain.PageRole;
import io.akka.docmost.domain.Restriction;
import io.akka.docmost.domain.SpaceRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rules driven through a real runtime rather than against the domain classes alone: the
 * entity, its events, and the state they rebuild.
 *
 * <p>Names end in {@code startsARuntime} so the split between test phases stays visible — a class
 * that starts a runtime and one that does not are not interchangeable, and a rename that moves a
 * class between phases is how a test silently stops running.
 */
class PageIntegrationTest extends TestKitSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Instant T0 = Instant.parse("2026-03-01T12:00:00Z");

  private static Document text(String s) {
    try {
      return Document.of(
          MAPPER.readTree(
              "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                  + s
                  + "\"}]}]}"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Document emptyDoc() {
    try {
      return Document.of(MAPPER.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String newPage(String id, String parentId, Instant createdAt) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(PageEntity::create)
        .invoke(new PageEntity.CreateCommand(parentId, "space-1", "creator", createdAt));
  }

  private PageEntity.StoreResult store(String id, Document content, Instant now, String... editors) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(PageEntity::store)
        .invoke(new PageEntity.StoreCommand(content, List.of(editors), "u1", now));
  }

  private PageEntity.CloseResult close(String id) {
    return componentClient.forEventSourcedEntity(id).method(PageEntity::closeWindow).invoke();
  }

  private PageEntity.State state(String id) {
    return componentClient.forEventSourcedEntity(id).method(PageEntity::get).invoke();
  }

  // --- R6, R7, R8 -----------------------------------------------------------------------

  @Test
  void aStoreOfUnchangedContentChangesNothing_startsARuntime() {
    var id = "p-unchanged";
    newPage(id, null, T0);
    assertTrue(store(id, text("hello"), T0, "e1").changed());

    var second = store(id, text("hello"), T0.plusSeconds(5), "e2");
    assertFalse(second.changed());
    assertFalse(second.windowOpened());

    var s = state(id);
    // e2 never reached the page: an unchanged store adds no contributor either.
    assertEquals(List.of("e1", "creator"), s.contributorIds());
    assertEquals(List.of("e1"), s.pendingContributorIds());
  }

  @Test
  void contributorsAccumulateAcrossStores_startsARuntime() {
    var id = "p-contributors";
    newPage(id, null, T0);
    store(id, text("one"), T0, "a");
    store(id, text("two"), T0.plusSeconds(1), "b");
    store(id, text("three"), T0.plusSeconds(2), "a");

    var s = state(id);
    assertEquals(List.of("a", "creator", "b"), s.contributorIds());
    assertEquals(List.of("a", "b"), s.pendingContributorIds());
  }

  // --- R9, R10, R12 ---------------------------------------------------------------------

  @Test
  void sixStoresAcrossOneWindowOpenItOnce_startsARuntime() {
    var id = "p-window";
    newPage(id, null, T0);
    int opens = 0;
    for (int i = 0; i < 6; i++) {
      var r = store(id, text("v" + i), T0.plusSeconds(i * 5L), "a");
      if (r.windowOpened()) {
        opens++;
      }
    }
    assertEquals(1, opens);
    // The close is a span after the FIRST store, not the last.
    assertEquals(T0.plusSeconds(60), state(id).windowClosesAt());
  }

  @Test
  void aStoreAfterTheWindowClosedOpensANewOne_startsARuntime() {
    var id = "p-reopen";
    newPage(id, null, T0);
    assertTrue(store(id, text("one"), T0, "a").windowOpened());
    close(id);
    assertEquals(null, state(id).windowClosesAt());
    assertTrue(store(id, text("two"), T0.plusSeconds(120), "a").windowOpened());
  }

  // --- R13, R15, R16 --------------------------------------------------------------------

  @Test
  void aClosedWindowKeepsAVersionCarryingThePendingContributors_startsARuntime() {
    var id = "p-version";
    newPage(id, null, T0);
    store(id, text("one"), T0, "a");
    store(id, text("two"), T0.plusSeconds(5), "b");

    var result = close(id);
    assertTrue(result.kept());
    assertEquals(List.of("a", "b"), result.contributorIds());

    var s = state(id);
    assertEquals(1, s.versions().size());
    assertEquals(text("two"), s.versions().get(0).content());
    // R15: the pending set is emptied, while the cumulative one is untouched.
    assertEquals(List.of(), s.pendingContributorIds());
    assertEquals(List.of("a", "creator", "b"), s.contributorIds());
  }

  @Test
  void aFirstVersionOnAnEmptyPageIsSkipped_startsARuntime() {
    var id = "p-empty-first";
    newPage(id, null, T0);
    store(id, emptyDoc(), T0, "a");

    var result = close(id);
    assertFalse(result.kept());
    assertEquals(List.of(), state(id).versions());
    // R16: this skip route does empty the pending set.
    assertEquals(List.of(), state(id).pendingContributorIds());
  }

  @Test
  void aCloseWithNothingNewKeepsNoSecondVersionAndHoldsItsContributors_startsARuntime() {
    var id = "p-duplicate";
    newPage(id, null, T0);
    store(id, text("one"), T0, "a");
    assertTrue(close(id).kept());

    // A store that changes the page and then changes it back leaves the content matching the
    // kept version, so the next close has nothing to keep.
    store(id, text("two"), T0.plusSeconds(120), "b");
    store(id, text("one"), T0.plusSeconds(121), "c");

    var second = close(id);
    assertFalse(second.kept());
    var s = state(id);
    assertEquals(1, s.versions().size());
    // R16: the duplicate-content route leaves the pending set alone, so b and c wait.
    assertEquals(List.of("b", "c"), s.pendingContributorIds());
  }

  @Test
  void emptyingAPageThatAlreadyHasAVersionDoesKeepOne_startsARuntime() {
    var id = "p-empty-later";
    newPage(id, null, T0);
    store(id, text("one"), T0, "a");
    assertTrue(close(id).kept());

    store(id, emptyDoc(), T0.plusSeconds(120), "b");
    assertTrue(close(id).kept());
    assertEquals(2, state(id).versions().size());
  }

  @Test
  void closingAWindowThatIsNotOpenDoesNothing_startsARuntime() {
    var id = "p-no-window";
    newPage(id, null, T0);
    var result = close(id);
    assertFalse(result.kept());
    assertEquals(List.of(), state(id).versions());
  }

  // --- R11 through the entity ------------------------------------------------------------

  @Test
  void theWindowSpanFollowsThePageAge_startsARuntime() {
    var young = "p-young";
    newPage(young, null, T0);
    assertEquals(T0.plusSeconds(60), store(young, text("x"), T0, "a").windowClosesAt());

    var old = "p-old";
    var createdLongAgo = T0.minus(Duration.ofHours(3));
    newPage(old, null, createdLongAgo);
    assertEquals(
        T0.plus(Duration.ofMinutes(5)), store(old, text("x"), T0, "a").windowClosesAt());
  }

  // --- R1 through R5, over a real chain ---------------------------------------------------

  private void member(String userId, List<SpaceRole> roles, List<String> groups) {
    componentClient
        .forKeyValueEntity("space-1")
        .method(SpaceEntity::addMember)
        .invoke(new SpaceEntity.Membership(userId, roles, groups));
  }

  private io.akka.docmost.api.PageEndpoint.SessionResponse session(String pageId, String userId) {
    return httpClient
        .GET("/pages/" + pageId + "/session/" + userId)
        .responseBodyAs(io.akka.docmost.api.PageEndpoint.SessionResponse.class)
        .invoke()
        .body();
  }

  @Test
  void aChainOfRestrictionsIsResolvedNearestFirst_startsARuntime() {
    member("writer-user", List.of(SpaceRole.WRITER), List.of());
    var root = "chain-root";
    var mid = "chain-mid";
    var leaf = "chain-leaf";
    newPage(root, null, T0);
    newPage(mid, root, T0);
    newPage(leaf, mid, T0);

    componentClient
        .forEventSourcedEntity(root)
        .method(PageEntity::restrict)
        .invoke(new Restriction(List.of(Grant.forUser("writer-user", PageRole.WRITER))));
    componentClient
        .forEventSourcedEntity(leaf)
        .method(PageEntity::restrict)
        .invoke(new Restriction(List.of(Grant.forUser("writer-user", PageRole.READER))));

    // The nearer reader grant decides, though a writer grant sits above it.
    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.READ_ONLY, session(leaf, "writer-user").outcome());
    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.WRITABLE, session(root, "writer-user").outcome());
  }

  @Test
  void aDeniedAncestorDeniesThePage_startsARuntime() {
    member("u-denied", List.of(SpaceRole.ADMIN), List.of());
    var root = "denied-root";
    var leaf = "denied-leaf";
    newPage(root, null, T0);
    newPage(leaf, root, T0);

    componentClient
        .forEventSourcedEntity(root)
        .method(PageEntity::restrict)
        .invoke(new Restriction(List.of()));

    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.REFUSED, session(leaf, "u-denied").outcome());
  }

  @Test
  void aSpaceReaderGetsAReadOnlySessionOnAnUnrestrictedPage_startsARuntime() {
    member("u-reader", List.of(SpaceRole.READER), List.of());
    var id = "open-page";
    newPage(id, null, T0);
    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.READ_ONLY, session(id, "u-reader").outcome());
  }

  @Test
  void aStoreOnAReadOnlySessionIsRefused_startsARuntime() {
    member("u-ro", List.of(SpaceRole.READER), List.of());
    var id = "readonly-store";
    newPage(id, null, T0);

    var request =
        new io.akka.docmost.api.PageEndpoint.StoreRequest(
            text("nope"), List.of("u-ro"), "u-ro", T0);

    var response = httpClient.POST("/pages/" + id + "/store").withRequestBody(request).invoke();
    assertEquals(403, response.httpResponse().status().intValue());

    // Refused, not merely unreported: the page never took the content.
    assertTrue(state(id).content().isAbsent());
  }

  @Test
  void aDeletedPageIsReadOnlyForAWriter_startsARuntime() {
    member("u-writer", List.of(SpaceRole.WRITER), List.of());
    var id = "deleted-page";
    newPage(id, null, T0);
    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.WRITABLE, session(id, "u-writer").outcome());

    componentClient
        .forEventSourcedEntity(id)
        .method(PageEntity::delete)
        .invoke(T0.plusSeconds(1));

    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.READ_ONLY, session(id, "u-writer").outcome());
  }

  @Test
  void aUserWithNoSpaceRoleIsRefusedDespiteAPageGrant_startsARuntime() {
    var id = "stranger-page";
    newPage(id, null, T0);
    componentClient
        .forEventSourcedEntity(id)
        .method(PageEntity::restrict)
        .invoke(new Restriction(List.of(Grant.forUser("stranger", PageRole.WRITER))));

    assertEquals(
        io.akka.docmost.domain.Admission.Outcome.REFUSED, session(id, "stranger").outcome());
  }
}
