package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R13, R15 — what happens when a window closes. */
class VersionPolicyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Document doc(String json) {
    try {
      return Document.of(MAPPER.readTree(json));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Document text(String s) {
    return doc(
        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
            + s
            + "\"}]}]}");
  }

  private static final Document EMPTY_DOC = doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}");

  private static LinkedHashSet<String> pending(String... ids) {
    return new LinkedHashSet<>(List.of(ids));
  }

  // --- R13 -------------------------------------------------------------------------------

  @Test
  void aFirstVersionIsKeptForAPageWithRealContent() {
    var d = VersionPolicy.decide(text("hello"), null, pending("a"));
    assertTrue(d.keep());
  }

  @Test
  void aFirstVersionIsSkippedForAnEmptyPage() {
    var d = VersionPolicy.decide(EMPTY_DOC, null, pending("a"));
    assertFalse(d.keep());
  }

  /** The empty check is guarded by there being no version yet — it is not a general rule. */
  @Test
  void aLaterVersionIsKeptEvenWhenThePageHasBeenEmptied() {
    var d = VersionPolicy.decide(EMPTY_DOC, text("hello"), pending("a"));
    assertTrue(d.keep());
  }

  @Test
  void aVersionIsSkippedWhenTheContentMatchesTheLastVersion() {
    var d = VersionPolicy.decide(text("hello"), text("hello"), pending("a"));
    assertFalse(d.keep());
  }

  @Test
  void aVersionIsKeptWhenTheContentDiffersFromTheLastVersion() {
    var d = VersionPolicy.decide(text("world"), text("hello"), pending("a"));
    assertTrue(d.keep());
  }

  @Test
  void aFirstVersionIsKeptForAPageWithNoContentAtAll() {
    // Absent content is not the empty document (R14), so the skip does not apply to it.
    var d = VersionPolicy.decide(Document.absent(), null, pending());
    assertTrue(d.keep());
  }

  // --- R15 -------------------------------------------------------------------------------

  @Test
  void aKeptVersionTakesThePendingSetAsItsContributors() {
    var d = VersionPolicy.decide(text("hello"), null, pending("a", "b"));
    assertEquals(List.of("a", "b"), List.copyOf(d.contributorIds()));
  }

  @Test
  void aKeptVersionEmptiesThePendingSet() {
    var d = VersionPolicy.decide(text("hello"), null, pending("a", "b"));
    assertTrue(d.clearsPending());
  }

  @Test
  void aVersionWithNoPendingContributorsIsStillKept() {
    var d = VersionPolicy.decide(text("hello"), null, pending());
    assertTrue(d.keep());
    assertEquals(Set.of(), d.contributorIds());
  }

  /**
   * The two skip routes differ. Emptying the pending set sits on the empty-first-version route
   * and inside the keep branch, but not on the duplicate-content route — which falls out of the
   * job having touched nothing, so those contributors are still waiting for the next window.
   */
  @Test
  void aCloseSkippedForDuplicateContentLeavesThePendingSetAlone() {
    var d = VersionPolicy.decide(text("hello"), text("hello"), pending("a"));
    assertFalse(d.keep());
    assertFalse(d.clearsPending());
  }

  @Test
  void aCloseSkippedForAnEmptyFirstVersionEmptiesThePendingSet() {
    var d = VersionPolicy.decide(EMPTY_DOC, null, pending("a"));
    assertFalse(d.keep());
    assertTrue(d.clearsPending());
  }
}
