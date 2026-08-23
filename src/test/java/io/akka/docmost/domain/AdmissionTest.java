package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * R1–R5.
 *
 * <p>The chain cases mirror {@code docmost-port/probes/permission-sql/cases.sql} one for one: the
 * fifteen shapes that file put to the original's recursive query appear here with the answers it
 * gave. The chain is always root -> mid -> leaf and the question is always about the leaf.
 */
class AdmissionTest {

  private static final String USER = "u1";
  private static final String OTHER = "u2";
  private static final String GROUP = "g1";
  private static final Set<String> IN_GROUP = Set.of(GROUP);
  private static final Set<String> NO_GROUPS = Set.of();

  /** An open page: no restriction of its own. */
  private static Restriction open() {
    return null;
  }

  private static Restriction restricted(Grant... grants) {
    return new Restriction(List.of(grants));
  }

  private static Grant user(String id, PageRole role) {
    return Grant.forUser(id, role);
  }

  private static Grant group(String id, PageRole role) {
    return Grant.forGroup(id, role);
  }

  /**
   * Arguments read root, mid, leaf; the chain is passed nearest-first, so it is reversed here.
   * Arrays.asList rather than List.of: a page with no restriction is a null entry.
   */
  private static Admission.Outcome ask(
      SpaceRole spaceRole, Set<String> groups, Restriction root, Restriction mid, Restriction leaf) {
    return Admission.decide(spaceRole, groups, USER, Arrays.asList(leaf, mid, root), false);
  }

  // --- R1, R2: no page-level restriction anywhere --------------------------------------

  @Test
  void aUserWithNoSpaceRoleIsRefusedEvenWithAPageGrant() {
    var out = ask(null, NO_GROUPS, open(), open(), restricted(user(USER, PageRole.WRITER)));
    assertEquals(Admission.Outcome.REFUSED, out);
  }

  @Test
  void case01_nothingRestrictedAnywhere_spaceWriterEdits() {
    assertEquals(
        Admission.Outcome.WRITABLE, ask(SpaceRole.WRITER, NO_GROUPS, open(), open(), open()));
  }

  @Test
  void case01_nothingRestrictedAnywhere_spaceReaderIsReadOnly() {
    assertEquals(
        Admission.Outcome.READ_ONLY, ask(SpaceRole.READER, NO_GROUPS, open(), open(), open()));
  }

  @Test
  void case01_nothingRestrictedAnywhere_spaceAdminEdits() {
    assertEquals(
        Admission.Outcome.WRITABLE, ask(SpaceRole.ADMIN, NO_GROUPS, open(), open(), open()));
  }

  // --- R3, R4: the chain decides -------------------------------------------------------

  @Test
  void case02_leafRestrictedUserWriter() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(SpaceRole.READER, NO_GROUPS, open(), open(), restricted(user(USER, PageRole.WRITER))));
  }

  @Test
  void case03_leafRestrictedUserReader() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        ask(SpaceRole.WRITER, NO_GROUPS, open(), open(), restricted(user(USER, PageRole.READER))));
  }

  @Test
  void case04_leafRestrictedUserAbsent() {
    assertEquals(
        Admission.Outcome.REFUSED,
        ask(SpaceRole.WRITER, NO_GROUPS, open(), open(), restricted(user(OTHER, PageRole.WRITER))));
  }

  @Test
  void case05_leafRestrictedWithNoGrantsAtAllRefusesEveryone() {
    assertEquals(
        Admission.Outcome.REFUSED, ask(SpaceRole.ADMIN, NO_GROUPS, open(), open(), restricted()));
  }

  @Test
  void case06_rootRestrictedWriterLeafOpen() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(SpaceRole.READER, NO_GROUPS, restricted(user(USER, PageRole.WRITER)), open(), open()));
  }

  @Test
  void case07_rootRestrictedReaderLeafOpen() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        ask(SpaceRole.WRITER, NO_GROUPS, restricted(user(USER, PageRole.READER)), open(), open()));
  }

  /** The pair that shows nearest wins over strongest. */
  @Test
  void case08_rootWriterLeafReader_theNearerReaderDecides() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        ask(
            SpaceRole.WRITER,
            NO_GROUPS,
            restricted(user(USER, PageRole.WRITER)),
            open(),
            restricted(user(USER, PageRole.READER))));
  }

  @Test
  void case09_rootReaderLeafWriter_theNearerWriterDecides() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(
            SpaceRole.READER,
            NO_GROUPS,
            restricted(user(USER, PageRole.READER)),
            open(),
            restricted(user(USER, PageRole.WRITER))));
  }

  @Test
  void case10_aDeniedMiddleAncestorDeniesTheLeaf() {
    assertEquals(
        Admission.Outcome.REFUSED,
        ask(
            SpaceRole.ADMIN,
            NO_GROUPS,
            restricted(user(USER, PageRole.WRITER)),
            restricted(),
            open()));
  }

  @Test
  void case11_aGrantHeldThroughAGroupCounts() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(SpaceRole.READER, IN_GROUP, open(), open(), restricted(group(GROUP, PageRole.WRITER))));
  }

  @Test
  void case12_userReaderAndGroupWriterOnOnePage_theHigherWins() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(
            SpaceRole.READER,
            IN_GROUP,
            open(),
            open(),
            restricted(user(USER, PageRole.READER), group(GROUP, PageRole.WRITER))));
  }

  @Test
  void case13_userWriterAndGroupReaderOnOnePage_theHigherWins() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(
            SpaceRole.READER,
            IN_GROUP,
            open(),
            open(),
            restricted(user(USER, PageRole.WRITER), group(GROUP, PageRole.READER))));
  }

  @Test
  void case14_allThreeRestrictedWriterReaderWriter_theLeafDecides() {
    assertEquals(
        Admission.Outcome.WRITABLE,
        ask(
            SpaceRole.READER,
            NO_GROUPS,
            restricted(user(USER, PageRole.WRITER)),
            restricted(user(USER, PageRole.READER)),
            restricted(user(USER, PageRole.WRITER))));
  }

  @Test
  void case15_rootWriterMidReaderLeafOpen_theMidDecides() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        ask(
            SpaceRole.WRITER,
            NO_GROUPS,
            restricted(user(USER, PageRole.WRITER)),
            restricted(user(USER, PageRole.READER)),
            open()));
  }

  @Test
  void aGroupGrantForAGroupTheUserIsNotInDoesNotCount() {
    assertEquals(
        Admission.Outcome.REFUSED,
        ask(SpaceRole.ADMIN, NO_GROUPS, open(), open(), restricted(group(GROUP, PageRole.WRITER))));
  }

  // --- R5 ------------------------------------------------------------------------------

  @Test
  void aDeletedPageIsReadOnlyForASpaceWriter() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        Admission.decide(SpaceRole.WRITER, NO_GROUPS, USER, Collections.singletonList(open()), true));
  }

  @Test
  void aDeletedPageIsReadOnlyForAPageWriter() {
    assertEquals(
        Admission.Outcome.READ_ONLY,
        Admission.decide(
            SpaceRole.READER,
            NO_GROUPS,
            USER,
            List.of(restricted(user(USER, PageRole.WRITER))),
            true));
  }

  @Test
  void aDeletedPageStillRefusesSomeoneWithNoAccess() {
    assertEquals(
        Admission.Outcome.REFUSED,
        Admission.decide(SpaceRole.WRITER, NO_GROUPS, USER, List.of(restricted()), true));
  }
}
