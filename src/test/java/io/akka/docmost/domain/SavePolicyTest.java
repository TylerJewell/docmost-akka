package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * R6, R7, R8 — what a store changes about the page.
 *
 * <p>The contributor cases mirror the {@code contributors} group in {@code
 * docmost-port/probes/save-decisions/probe.mjs}.
 */
class SavePolicyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Document doc(String text) {
    try {
      return Document.of(
          MAPPER.readTree(
              "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                  + text
                  + "\"}]}]}"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static LinkedHashSet<String> set(String... ids) {
    return new LinkedHashSet<>(List.of(ids));
  }

  // --- R6 --------------------------------------------------------------------------------

  @Test
  void aStoreOfTheContentAlreadyHeldChangesNothing() {
    var decision = SavePolicy.decide(doc("hi"), doc("hi"), set("a"), set("e"), "creator");
    assertFalse(decision.changed());
  }

  @Test
  void aStoreOfDifferentContentChangesThePage() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a"), set("e"), "creator");
    assertTrue(decision.changed());
  }

  @Test
  void theFirstStoreOntoAPageWithNoContentChangesIt() {
    var decision = SavePolicy.decide(doc("hi"), Document.absent(), set(), set("e"), "creator");
    assertTrue(decision.changed());
  }

  @Test
  void anUnchangedStoreLeavesTheContributorsExactlyAsTheyWere() {
    var existing = set("a", "b");
    var decision = SavePolicy.decide(doc("hi"), doc("hi"), existing, set("e"), "creator");
    assertEquals(List.of("a", "b"), List.copyOf(decision.contributorIds()));
    assertEquals(Set.of(), decision.newPendingContributorIds());
  }

  // --- R7 --------------------------------------------------------------------------------

  @Test
  void theCreatorIsAlwaysAContributor() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set(), set(), "creator");
    assertEquals(List.of("creator"), List.copyOf(decision.contributorIds()));
  }

  @Test
  void anEditorIsAddedAndTheOrderIsKept() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a"), set("b"), "creator");
    assertEquals(List.of("a", "b", "creator"), List.copyOf(decision.contributorIds()));
  }

  @Test
  void anEditorAlreadyPresentIsNotRepeated() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a"), set("a"), "creator");
    assertEquals(List.of("a", "creator"), List.copyOf(decision.contributorIds()));
  }

  @Test
  void aCreatorAlreadyPresentKeepsItsOriginalPosition() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("creator"), set("b"), "creator");
    assertEquals(List.of("creator", "b"), List.copyOf(decision.contributorIds()));
  }

  @Test
  void theContributorSetNeverShrinks() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a", "b"), set(), "creator");
    assertEquals(List.of("a", "b", "creator"), List.copyOf(decision.contributorIds()));
  }

  // --- R8 --------------------------------------------------------------------------------

  @Test
  void thePendingSetTakesTheEditorsAndNotTheCreator() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a"), set("b"), "creator");
    assertEquals(Set.of("b"), decision.newPendingContributorIds());
  }

  @Test
  void thePendingSetIsSeparateFromTheCumulativeOne() {
    var decision = SavePolicy.decide(doc("hi"), doc("ho"), set("a", "b", "c"), set("b"), "creator");
    assertEquals(Set.of("b"), decision.newPendingContributorIds());
    assertEquals(List.of("a", "b", "c", "creator"), List.copyOf(decision.contributorIds()));
  }
}
