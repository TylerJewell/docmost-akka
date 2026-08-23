package io.akka.docmost.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * A page's content as a node tree, and the two questions the save and history paths ask of it.
 *
 * <p>A document is either present or absent; absent is the state of a page nobody has ever
 * stored to, and is distinct from every document including the empty one (R14).
 */
@JsonDeserialize(using = Document.Reader.class)
public final class Document {

  private static final Document ABSENT = new Document(null);

  private final JsonNode root;

  private Document(JsonNode root) {
    this.root = root;
  }

  @JsonCreator
  public static Document of(JsonNode root) {
    return root == null || root instanceof NullNode ? ABSENT : new Document(root);
  }

  public static Document absent() {
    return ABSENT;
  }

  /**
   * A document crosses a component boundary on every store. Absent serialises as a JSON null
   * rather than as nothing, so that the trip back reconstructs absence rather than failing on it.
   */
  @JsonValue
  public JsonNode root() {
    return root == null ? NullNode.getInstance() : root;
  }

  public boolean isAbsent() {
    return root == null;
  }

  /**
   * R6. Deep structural equality, which is what the original's comparison gives on anything a
   * document can be: key order does not matter, a key holding null is not an absent key, and a
   * number is not its numeric string.
   */
  public boolean sameContentAs(Document other) {
    if (root == null || other.root == null) {
      return root == other.root;
    }
    return root.equals(other.root);
  }

  /**
   * R14. Exactly one shape counts: a document node whose content is a single paragraph node
   * carrying no content or an empty content list. Everything else — two paragraphs, no children,
   * an absent content key, a paragraph holding an empty text node, a non-document root — does
   * not.
   */
  public boolean isEmptyDocument() {
    if (root == null || !"doc".equals(typeOf(root))) {
      return false;
    }
    var content = root.get("content");
    if (content == null || !content.isArray() || content.size() != 1) {
      return false;
    }
    var child = content.get(0);
    if (child == null || !"paragraph".equals(typeOf(child))) {
      return false;
    }
    var childContent = child.get("content");
    return childContent == null || childContent.isNull() || (childContent.isArray() && childContent.isEmpty());
  }

  private static String typeOf(JsonNode node) {
    var type = node.get("type");
    return type != null && type.isTextual() ? type.asText() : null;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Document d && sameContentAs(d);
  }

  @Override
  public int hashCode() {
    return root == null ? 0 : root.hashCode();
  }

  /**
   * Jackson skips a value deserializer for a JSON null and hands back a Java null, which would
   * make an absent document indistinguishable from a missing field one layer up. Reading it as
   * absent keeps the two the same thing on both sides of the wire.
   */
  static final class Reader extends JsonDeserializer<Document> {
    @Override
    public Document deserialize(JsonParser parser, DeserializationContext context)
        throws java.io.IOException {
      return Document.of(parser.readValueAsTree());
    }

    @Override
    public Document getNullValue(DeserializationContext context) {
      return ABSENT;
    }
  }

  @Override
  public String toString() {
    return root == null ? "Document[absent]" : root.toString();
  }
}
