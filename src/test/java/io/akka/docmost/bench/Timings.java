package io.akka.docmost.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.docmost.domain.Admission;
import io.akka.docmost.domain.Document;
import io.akka.docmost.domain.Grant;
import io.akka.docmost.domain.PageRole;
import io.akka.docmost.domain.Restriction;
import io.akka.docmost.domain.SpaceRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Times the port's decisions, and prints them as JSON for the benchmark report.
 *
 * <p>A window is sized from a pilot so that it runs for tens of milliseconds — the figure is the
 * window's total divided by what was in it, never the minimum of many short windows, which
 * reports the best artefact rather than the typical cost. Five windows are run and the median
 * taken, because one correctly sized window is still one reading.
 *
 * <p>Not a test: it has no assertions and nothing about it should fail a build. It lives under
 * the test sources so it can see the domain classes without shipping in the service.
 *
 * <pre>mvn -q test-compile exec:java -Dexec.mainClass=io.akka.docmost.bench.Timings -Dexec.classpathScope=test</pre>
 */
public final class Timings {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int WINDOWS = 5;
  private static final long TARGET_NS = 50_000_000L;

  private record Case(String name, java.util.function.IntUnaryOperator body) {}

  /**
   * Consumed and printed at the end so no window can be optimised away. A body whose result
   * nothing reads measures zero, and zero is the reading that says nothing was measured.
   */
  private static long blackhole;

  public static void main(String[] args) throws Exception {
    // Structurally equal but distinct trees, which is what a store actually compares: a page
    // re-storing content it already holds arrives as a freshly parsed document, never as the
    // same object.
    var docA =
        Document.of(
            MAPPER.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                    + "[{\"type\":\"text\",\"text\":\"the quick brown fox\"}]},"
                    + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"jumps over\"}]}]}"));
    var docB =
        Document.of(
            MAPPER.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                    + "[{\"type\":\"text\",\"text\":\"the quick brown fox\"}]},"
                    + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"jumps over!\"}]}]}"));
    var emptyDoc =
        Document.of(MAPPER.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}"));

    var chain =
        Arrays.asList(
            new Restriction(List.of(Grant.forUser("u1", PageRole.READER))),
            null,
            new Restriction(List.of(Grant.forUser("u1", PageRole.WRITER))));
    Set<String> groups = Set.of();

    var docAcopy = Document.of(MAPPER.readTree(docA.root().toString()));

    var cases =
        List.of(
            new Case("content-equality-equal", i -> docA.sameContentAs(docAcopy) ? 1 : 0),
            new Case("content-equality-differing", i -> docA.sameContentAs(docB) ? 1 : 0),
            new Case("empty-document-judgement", i -> emptyDoc.isEmptyDocument() ? 1 : 0),
            new Case(
                "admission-three-deep-chain",
                i -> Admission.decide(SpaceRole.WRITER, groups, "u1", chain, false).ordinal()));

    var out = new ArrayList<String>();
    for (var c : cases) {
      out.add(measure(c));
    }
    System.out.println("[\n  " + String.join(",\n  ", out) + "\n]");
  }

  private static String measure(Case c) {
    // Warm the JIT before the pilot, or the pilot sizes the window from interpreted code.
    for (int i = 0; i < 200_000; i++) {
      blackhole += c.body().applyAsInt(i);
    }

    int pilotReps = 1_000;
    long pilot = time(c.body(), pilotReps);
    double per = (double) pilot / pilotReps;
    if (per <= 0) {
      throw new IllegalStateException(
          c.name() + ": the pilot measured nothing, so no window can be sized from it");
    }
    int reps = (int) Math.max(pilotReps, Math.min(20_000_000L, (long) (TARGET_NS / per)));
    // A pilot on a not-quite-warm loop overestimates the cost and undersizes the window, so
    // the size is corrected from a real window before any figure is taken from one.
    for (int attempt = 0; attempt < 5; attempt++) {
      long trial = time(c.body(), reps);
      if (trial >= TARGET_NS / 2) {
        break;
      }
      reps = (int) Math.min(20_000_000L, Math.max(reps + 1L, reps * (TARGET_NS / Math.max(1L, trial))));
    }

    var perOp = new double[WINDOWS];
    for (int w = 0; w < WINDOWS; w++) {
      long total = time(c.body(), reps);
      if (total <= 0) {
        throw new IllegalStateException(c.name() + ": a window measured nothing");
      }
      perOp[w] = (double) total / reps;
    }
    Arrays.sort(perOp);
    double median = perOp[WINDOWS / 2];

    return String.format(
        "{\"name\": \"%s\", \"nsPerOp\": %.1f, \"repetitions\": %d, \"windows\": %d,"
            + " \"windowMs\": %.1f}",
        c.name(), median, reps, WINDOWS, median * reps / 1_000_000.0);
  }

  private static long time(java.util.function.IntUnaryOperator body, int reps) {
    long start = System.nanoTime();
    long local = 0;
    for (int i = 0; i < reps; i++) {
      local += body.applyAsInt(i);
    }
    long elapsed = System.nanoTime() - start;
    blackhole += local;
    return elapsed;
  }
}
