package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * R9–R12.
 *
 * <p>The window is leading-edge, which is what {@code docmost-port/probes/history-debounce}
 * established of the original: the store that opens a window fixes when it closes, and every
 * store while it is open leaves that time exactly where it was — in both directions.
 */
class HistoryWindowTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

  // --- R11: the span --------------------------------------------------------------------

  @Test
  void aPageUnderFiveMinutesOldGetsTheSixtySecondSpan() {
    assertEquals(
        Duration.ofSeconds(60), HistoryWindow.spanFor(CREATED, CREATED.plusSeconds(0)));
  }

  @Test
  void aPageJustUnderFiveMinutesOldStillGetsTheSixtySecondSpan() {
    assertEquals(
        Duration.ofSeconds(60),
        HistoryWindow.spanFor(CREATED, CREATED.plus(Duration.ofMinutes(5)).minusMillis(1)));
  }

  /** The boundary is exclusive on the fast side: {@code pageAge < THRESHOLD}. */
  @Test
  void aPageExactlyFiveMinutesOldGetsTheFiveMinuteSpan() {
    assertEquals(
        Duration.ofMinutes(5), HistoryWindow.spanFor(CREATED, CREATED.plus(Duration.ofMinutes(5))));
  }

  @Test
  void anOlderPageGetsTheFiveMinuteSpan() {
    assertEquals(
        Duration.ofMinutes(5), HistoryWindow.spanFor(CREATED, CREATED.plus(Duration.ofHours(9))));
  }

  // --- R9, R10: opening, and not moving ---------------------------------------------------

  @Test
  void aStoreWithNoWindowOpenOpensOne() {
    var now = CREATED.plusSeconds(10);
    var w = HistoryWindow.onStore(null, CREATED, now);
    assertTrue(w.opened());
    assertEquals(now.plusSeconds(60), w.closesAt());
  }

  @Test
  void aStoreWhileAWindowIsOpenDoesNotOpenAnother() {
    var opened = CREATED.plusSeconds(10);
    var closesAt = opened.plusSeconds(60);
    var w = HistoryWindow.onStore(closesAt, CREATED, opened.plusSeconds(5));
    assertFalse(w.opened());
    assertEquals(closesAt, w.closesAt());
  }

  /**
   * The page crosses the five-minute age boundary mid-window, so a re-open would ask for a
   * longer span. It must not move the closing time.
   */
  @Test
  void aStoreAskingForALongerSpanDoesNotPushTheCloseOut() {
    var closesAt = CREATED.plus(Duration.ofMinutes(4)).plusSeconds(60);
    var w = HistoryWindow.onStore(closesAt, CREATED, CREATED.plus(Duration.ofSeconds(270)));
    assertFalse(w.opened());
    assertEquals(closesAt, w.closesAt());
  }

  /** The mirror of the case above: a shorter span must not pull the close in either. */
  @Test
  void aStoreAskingForAShorterSpanDoesNotPullTheCloseIn() {
    var openedAt = CREATED.plus(Duration.ofMinutes(10));
    var closesAt = openedAt.plus(Duration.ofMinutes(5));
    var w = HistoryWindow.onStore(closesAt, openedAt, openedAt.plusSeconds(30));
    assertFalse(w.opened());
    assertEquals(closesAt, w.closesAt());
  }

  @Test
  void sixStoresAcrossOneWindowOpenItExactlyOnce() {
    Instant closesAt = null;
    int opens = 0;
    var t = CREATED.plusSeconds(10);
    for (int i = 0; i < 6; i++) {
      var w = HistoryWindow.onStore(closesAt, CREATED, t);
      if (w.opened()) {
        opens++;
      }
      closesAt = w.closesAt();
      t = t.plusSeconds(5);
    }
    assertEquals(1, opens);
    assertEquals(CREATED.plusSeconds(70), closesAt);
  }

  // --- R12: reopening ---------------------------------------------------------------------

  @Test
  void aStoreAfterTheWindowClosedOpensANewOne() {
    // The page is still under five minutes old here, so the new window takes the fast span.
    var later = CREATED.plusSeconds(71);
    var w = HistoryWindow.onStore(null, CREATED, later);
    assertTrue(w.opened());
    assertEquals(later.plusSeconds(60), w.closesAt());
  }

  @Test
  void aWindowReopenedOnAnOldPageTakesTheLongSpan() {
    var later = CREATED.plus(Duration.ofHours(2));
    var w = HistoryWindow.onStore(null, CREATED, later);
    assertTrue(w.opened());
    assertEquals(later.plus(Duration.ofMinutes(5)), w.closesAt());
  }

  /**
   * A window is held open by its closing time being in the future, not by the clock alone: the
   * closing of a window is what clears it, and until then a store at any time inside it is
   * absorbed.
   */
  @Test
  void aStoreExactlyAtTheClosingInstantIsStillAbsorbed() {
    var closesAt = CREATED.plusSeconds(70);
    var w = HistoryWindow.onStore(closesAt, CREATED, closesAt);
    assertFalse(w.opened());
    assertEquals(closesAt, w.closesAt());
  }
}
