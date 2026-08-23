package io.akka.docmost.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * R6 (content equality) and R14 (the empty-document shape).
 *
 * <p>Both mirror the enumerations in {@code docmost-port/probes/save-decisions/probe.mjs} case
 * for case: every input the probe put to the original appears here with the answer the original
 * gave.
 */
class DocumentTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Document doc(String json) {
    try {
      return Document.of(MAPPER.readTree(json));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // --- R6 -----------------------------------------------------------------------------

  @Test
  void identicalDocumentsAreEqual() {
    assertTrue(
        doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}")
            .sameContentAs(
                doc(
                    "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}")));
  }

  @Test
  void differingTextIsNotEqual() {
    assertFalse(
        doc("{\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}")
            .sameContentAs(doc("{\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\"ho\"}]}")));
  }

  @Test
  void keyOrderDoesNotMatter() {
    assertTrue(
        doc("{\"type\":\"doc\",\"content\":[]}").sameContentAs(doc("{\"content\":[],\"type\":\"doc\"}")));
  }

  @Test
  void aNullValuedKeyIsNotAnAbsentKey() {
    assertFalse(doc("{\"type\":\"p\",\"content\":null}").sameContentAs(doc("{\"type\":\"p\"}")));
  }

  @Test
  void anEmptyArrayIsNotAnAbsentKey() {
    assertFalse(doc("{\"type\":\"p\",\"content\":[]}").sameContentAs(doc("{\"type\":\"p\"}")));
  }

  @Test
  void aNumberIsNotItsNumericString() {
    assertFalse(
        doc("{\"type\":\"h\",\"attrs\":{\"level\":1}}")
            .sameContentAs(doc("{\"type\":\"h\",\"attrs\":{\"level\":\"1\"}}")));
  }

  @Test
  void anEmptyAttrsObjectIsNotAnAbsentOne() {
    assertFalse(doc("{\"type\":\"p\",\"attrs\":{}}").sameContentAs(doc("{\"type\":\"p\"}")));
  }

  @Test
  void childOrderMatters() {
    assertFalse(
        doc("{\"type\":\"doc\",\"content\":[{\"type\":\"a\"},{\"type\":\"b\"}]}")
            .sameContentAs(doc("{\"type\":\"doc\",\"content\":[{\"type\":\"b\"},{\"type\":\"a\"}]}")));
  }

  @Test
  void twoAbsentContentsAreEqual() {
    assertTrue(Document.absent().sameContentAs(Document.absent()));
  }

  @Test
  void anAbsentContentDoesNotEqualADocument() {
    assertFalse(Document.absent().sameContentAs(doc("{\"type\":\"doc\",\"content\":[]}")));
  }

  // --- R14 ----------------------------------------------------------------------------

  @Test
  void aSingleParagraphWithNoContentKeyIsEmpty() {
    assertTrue(doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}").isEmptyDocument());
  }

  @Test
  void aSingleParagraphWithAnEmptyContentListIsEmpty() {
    assertTrue(
        doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[]}]}")
            .isEmptyDocument());
  }

  @Test
  void aParagraphWithTextIsNotEmpty() {
    assertFalse(
        doc(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}]}")
            .isEmptyDocument());
  }

  @Test
  void aParagraphHoldingAnEmptyTextNodeIsNotEmpty() {
    assertFalse(
        doc(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"\"}]}]}")
            .isEmptyDocument());
  }

  @Test
  void twoEmptyParagraphsAreNotEmpty() {
    assertFalse(
        doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"},{\"type\":\"paragraph\"}]}")
            .isEmptyDocument());
  }

  @Test
  void aDocumentWithNoChildrenIsNotEmpty() {
    assertFalse(doc("{\"type\":\"doc\",\"content\":[]}").isEmptyDocument());
  }

  @Test
  void aDocumentWithNoContentKeyIsNotEmpty() {
    assertFalse(doc("{\"type\":\"doc\"}").isEmptyDocument());
  }

  @Test
  void anEmptyHeadingIsNotEmpty() {
    assertFalse(doc("{\"type\":\"doc\",\"content\":[{\"type\":\"heading\"}]}").isEmptyDocument());
  }

  @Test
  void aNonDocumentRootIsNotEmpty() {
    assertFalse(doc("{\"type\":\"paragraph\"}").isEmptyDocument());
  }

  @Test
  void anAbsentContentIsNotEmpty() {
    assertFalse(Document.absent().isEmptyDocument());
  }

  @Test
  void aNullChildIsNotEmpty() {
    assertFalse(doc("{\"type\":\"doc\",\"content\":[null]}").isEmptyDocument());
  }

  @Test
  void aDocumentReportsWhetherItHasContentAtAll() {
    assertEquals(true, Document.absent().isAbsent());
    assertEquals(false, doc("{\"type\":\"doc\"}").isAbsent());
  }
}
