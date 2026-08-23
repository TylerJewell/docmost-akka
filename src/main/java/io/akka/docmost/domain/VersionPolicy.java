package io.akka.docmost.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** R13, R15, R16: what happens when a history window closes. */
public final class VersionPolicy {

  /**
   * @param keep whether a version is kept
   * @param contributorIds who the kept version is attributed to; empty where nothing is kept
   * @param clearsPending whether the page's pending set is emptied
   */
  public record Decision(boolean keep, Set<String> contributorIds, boolean clearsPending) {}

  private VersionPolicy() {}

  /**
   * @param lastVersionContent the most recent kept version's content, or null where the page has
   *     no version yet
   */
  public static Document nothing() {
    return Document.absent();
  }

  public static Decision decide(
      Document content, Document lastVersionContent, Set<String> pendingContributorIds) {

    boolean firstVersion = lastVersionContent == null;

    // R13, R14. The empty-content skip is guarded by there being no version yet, so emptying a
    // page that already has history does keep a version.
    if (firstVersion && content.isEmptyDocument()) {
      return new Decision(false, Set.of(), true);
    }

    // R13. Nothing to keep where the page still holds what the last version holds. R16: this
    // route leaves the pending set alone, unlike the one above.
    if (!firstVersion && content.sameContentAs(lastVersionContent)) {
      return new Decision(false, Set.of(), false);
    }

    // R15.
    return new Decision(true, new LinkedHashSet<>(pendingContributorIds), true);
  }
}
