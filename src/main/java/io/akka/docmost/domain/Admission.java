package io.akka.docmost.domain;

import java.util.List;
import java.util.Set;

/**
 * R1–R5: whether a user may open an editing session on a page, and whether it is writable.
 *
 * <p>Two independent questions are answered over the same chain, and they do not agree. Reaching
 * the page at all is a conjunction over <em>every</em> restricted page in the chain. Editing it is
 * decided by the <em>nearest</em> restricted page alone — so the strongest grant a user holds is
 * not necessarily the one that applies.
 */
public final class Admission {

  public enum Outcome {
    REFUSED,
    READ_ONLY,
    WRITABLE
  }

  private Admission() {}

  /**
   * @param spaceRole the highest role the user holds in the page's space, or null for none
   * @param groupIds the groups the user belongs to
   * @param chain the restriction on each page from the page itself up to its root, nearest
   *     first; an entry is null where that page carries no restriction
   * @param deleted whether the page is in the trash
   */
  public static Outcome decide(
      SpaceRole spaceRole,
      Set<String> groupIds,
      String userId,
      List<Restriction> chain,
      boolean deleted) {

    // R1. Space membership is checked before the chain, so no page-level grant admits a
    // non-member.
    if (spaceRole == null) {
      return Outcome.REFUSED;
    }

    PageRole nearestRole = null;
    boolean anyRestriction = false;
    boolean permittedEverywhere = true;

    for (var restriction : chain) {
      if (restriction == null) {
        continue;
      }
      anyRestriction = true;
      var role = restriction.roleFor(userId, groupIds);
      if (role == null) {
        // R3. One restricted ancestor the user holds nothing on denies the page, however
        // strong their grants elsewhere in the chain.
        permittedEverywhere = false;
      } else if (nearestRole == null) {
        // R4. The chain runs nearest-first, so the first role found is the nearest one.
        nearestRole = role;
      }
    }

    boolean writable;
    if (!anyRestriction) {
      // R2. Nothing restricted anywhere: the space role alone decides.
      writable = spaceRole.canEdit();
    } else {
      if (!permittedEverywhere) {
        return Outcome.REFUSED;
      }
      writable = nearestRole == PageRole.WRITER;
    }

    // R5. Applied last, so no role overrides it.
    return deleted || !writable ? Outcome.READ_ONLY : Outcome.WRITABLE;
  }
}
