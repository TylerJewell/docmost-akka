package io.akka.docmost.domain;

import java.util.List;

/**
 * A page's restriction. A restriction holding no grants refuses everyone (R3) — it is not the
 * same as a page having no restriction at all, which is represented by the absence of this.
 */
public record Restriction(List<Grant> grants) {

  public Restriction {
    grants = grants == null ? List.of() : List.copyOf(grants);
  }

  /** The highest role the user holds here by any route, or null where they hold none. */
  public PageRole roleFor(String userId, java.util.Set<String> groupIds) {
    PageRole highest = null;
    for (var grant : grants) {
      if (grant.appliesTo(userId, groupIds)
          && (highest == null || grant.role().ordinal() > highest.ordinal())) {
        highest = grant.role();
      }
    }
    return highest;
  }
}
