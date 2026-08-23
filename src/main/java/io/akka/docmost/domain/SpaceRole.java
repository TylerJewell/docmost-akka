package io.akka.docmost.domain;

/** A user's role in a space, weakest first. Only READER yields a read-only session (R2). */
public enum SpaceRole {
  READER,
  WRITER,
  ADMIN;

  public boolean canEdit() {
    return this != READER;
  }

  /** The highest role a user holds across their memberships in one space. */
  public static SpaceRole highest(Iterable<SpaceRole> roles) {
    SpaceRole highest = null;
    for (var role : roles) {
      if (highest == null || role.ordinal() > highest.ordinal()) {
        highest = role;
      }
    }
    return highest;
  }
}
