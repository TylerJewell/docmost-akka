package io.akka.docmost.domain;

/**
 * One entry in a page's restriction. A grant names a user or a group, never both — the original
 * enforces that with a check constraint on the table.
 */
public record Grant(String userId, String groupId, PageRole role) {

  public Grant {
    if ((userId == null) == (groupId == null)) {
      throw new IllegalArgumentException("a grant names exactly one of a user or a group");
    }
  }

  public static Grant forUser(String userId, PageRole role) {
    return new Grant(userId, null, role);
  }

  public static Grant forGroup(String groupId, PageRole role) {
    return new Grant(null, groupId, role);
  }

  public boolean appliesTo(String candidateUserId, java.util.Set<String> groupIds) {
    return userId != null ? userId.equals(candidateUserId) : groupIds.contains(groupId);
  }
}
