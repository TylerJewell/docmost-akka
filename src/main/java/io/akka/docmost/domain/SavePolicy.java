package io.akka.docmost.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** R6–R8: what a store changes about the page. */
public final class SavePolicy {

  /**
   * @param changed false where the stored document matched what the page already held, in which
   *     case nothing else in this record differs from the page's current state
   * @param contributorIds the page's cumulative contributors after the store
   * @param newPendingContributorIds who to add to the page's pending set — the editors alone,
   *     which is a different set from the cumulative one
   */
  public record Decision(
      boolean changed, LinkedHashSet<String> contributorIds, Set<String> newPendingContributorIds) {}

  private SavePolicy() {}

  public static Decision decide(
      Document stored,
      Document current,
      Set<String> existingContributorIds,
      Set<String> editorIds,
      String creatorId) {

    // R6. An unchanged document short-circuits the whole path: no write, no window, nothing
    // notified.
    if (stored.sameContentAs(current)) {
      return new Decision(false, new LinkedHashSet<>(existingContributorIds), Set.of());
    }

    // R7. Union in this order, deduplicated; the set never shrinks.
    var contributors = new LinkedHashSet<>(existingContributorIds);
    contributors.addAll(editorIds);
    contributors.add(creatorId);

    // R8. The pending set takes the editors and not the creator.
    return new Decision(true, contributors, Set.copyOf(editorIds));
  }
}
