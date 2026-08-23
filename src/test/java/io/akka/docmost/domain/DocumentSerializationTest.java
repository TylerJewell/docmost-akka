package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * A document crosses a component boundary as JSON on every store, so surviving that trip is part
 * of R6 rather than separate from it: a document that deserialises to absent compares equal to
 * the page's own absent content, and the store reports nothing changed.
 */
class DocumentSerializationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void aDocumentSurvivesARoundTrip() throws Exception {
    var doc = Document.of(MAPPER.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}"));
    var back = MAPPER.readValue(MAPPER.writeValueAsString(doc), Document.class);
    assertFalse(back.isAbsent());
    assertTrue(doc.sameContentAs(back));
  }

  @Test
  void anAbsentDocumentSurvivesARoundTrip() throws Exception {
    var back = MAPPER.readValue(MAPPER.writeValueAsString(Document.absent()), Document.class);
    assertTrue(back.isAbsent());
  }

  @Test
  void aDocumentNestedInARecordSurvivesARoundTrip() throws Exception {
    record Holder(Document content, String who) {}
    var doc = Document.of(MAPPER.readTree("{\"type\":\"doc\",\"content\":[]}"));
    var back = MAPPER.readValue(MAPPER.writeValueAsString(new Holder(doc, "u1")), Holder.class);
    assertFalse(back.content().isAbsent());
    assertTrue(doc.sameContentAs(back.content()));
  }
}
