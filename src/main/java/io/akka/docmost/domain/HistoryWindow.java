package io.akka.docmost.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * R9–R12: the window between a page changing and a version of it being kept.
 *
 * <p>The window is <b>leading-edge</b>. The store that opens it fixes when it closes, and every
 * store while it is open leaves that time exactly where it was — a later store asking for a
 * longer or a shorter span moves it in neither direction.
 *
 * <p>The port holds the closing time itself rather than handing it to the scheduler. The target's
 * timer replaces a pending timer of the same name, deadline and payload both, so a scheduler-held
 * window would be trailing-edge. Deciding here and scheduling only on the transition keeps the
 * scheduler from ever seeing a name it already has.
 */
public final class HistoryWindow {

  static final Duration FAST_SPAN = Duration.ofSeconds(60);
  static final Duration SPAN = Duration.ofMinutes(5);
  static final Duration FAST_THRESHOLD = Duration.ofMinutes(5);

  /**
   * @param opened whether this store is the one that opened the window, and so the one whose
   *     close needs scheduling
   * @param closesAt when the window closes, unchanged from before where opened is false
   */
  public record Result(boolean opened, Instant closesAt) {}

  private HistoryWindow() {}

  /** R11. The span is read from the page's age at the moment the window opens. */
  public static Duration spanFor(Instant pageCreatedAt, Instant now) {
    var age = Duration.between(pageCreatedAt, now);
    return age.compareTo(FAST_THRESHOLD) < 0 ? FAST_SPAN : SPAN;
  }

  /**
   * @param closesAt when the currently open window closes, or null where none is open
   */
  public static Result onStore(Instant closesAt, Instant pageCreatedAt, Instant now) {
    if (closesAt != null) {
      // R10. A window is open; this store is absorbed into it and changes nothing.
      return new Result(false, closesAt);
    }
    // R9, R12.
    return new Result(true, now.plus(spanFor(pageCreatedAt, now)));
  }
}
