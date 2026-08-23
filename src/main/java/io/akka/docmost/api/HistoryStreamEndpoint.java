package io.akka.docmost.api;

import akka.NotUsed;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.JsonNode;
import io.akka.docmost.application.PageEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The page-history view's data source.
 *
 * <p>RENDERING.md R1: the view shows state the server owns, which changes without anyone
 * touching the page — a window closing keeps a version — so it is pushed rather than polled.
 * The first message carries every version the page already has, so the first render needs no
 * second round trip.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/pages")
public class HistoryStreamEndpoint {

  /** How often the entity is re-read for versions the stream has not sent yet. */
  private static final Duration TICK = Duration.ofMillis(500);

  private final ComponentClient componentClient;

  public HistoryStreamEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record Contributor(String id, String name, String avatarUrl) {}

  public record HistoryItem(
      String id,
      String createdAt,
      List<Contributor> contributors,
      Contributor lastUpdatedBy,
      String title,
      List<String> paragraphs) {}

  public record Payload(List<HistoryItem> versions) {}

  @Get("/{pageId}/versions/stream")
  public akka.http.javadsl.model.HttpResponse stream(String pageId) {
    Source<Payload, NotUsed> source =
        Source.tick(Duration.ZERO, TICK, "")
            .map(ignored -> payloadFor(pageId))
            // Only a change is worth a frame; an unchanged read sends nothing, so an idle
            // page produces no traffic beyond the stream itself.
            .statefulMapConcat(
                () -> {
                  var previous = new Payload[1];
                  return payload -> {
                    if (payload.equals(previous[0])) {
                      return List.of();
                    }
                    previous[0] = payload;
                    return List.of(payload);
                  };
                })
            .mapMaterializedValue(ignored -> NotUsed.getInstance());

    return HttpResponses.serverSentEvents(source);
  }

  private Payload payloadFor(String pageId) {
    List<PageEntity.Version> versions;
    try {
      versions =
          componentClient.forEventSourcedEntity(pageId).method(PageEntity::versions).invoke();
    } catch (RuntimeException e) {
      return new Payload(List.of());
    }

    var items = new ArrayList<HistoryItem>();
    // Newest first, which is the order the original's list renders in.
    for (int i = versions.size() - 1; i >= 0; i--) {
      var version = versions.get(i);
      var contributors = new ArrayList<Contributor>();
      for (var id : version.contributorIds()) {
        contributors.add(new Contributor(id, id, null));
      }
      items.add(
          new HistoryItem(
              pageId + "-v" + i,
              version.keptAt().toString(),
              contributors,
              contributors.isEmpty() ? null : contributors.get(contributors.size() - 1),
              titleOf(version),
              paragraphsOf(version)));
    }
    return new Payload(items);
  }

  private static String titleOf(PageEntity.Version version) {
    var root = version.content().root();
    return root != null && root.hasNonNull("title") ? root.get("title").asText() : "";
  }

  /** Pulls the text out of the node tree so the view can render it without an editor. */
  private static List<String> paragraphsOf(PageEntity.Version version) {
    var out = new ArrayList<String>();
    var root = version.content().root();
    if (root == null) {
      return out;
    }
    var content = root.get("content");
    if (content == null || !content.isArray()) {
      return out;
    }
    for (JsonNode child : content) {
      var text = new StringBuilder();
      collectText(child, text);
      out.add(text.toString());
    }
    return out;
  }

  private static void collectText(JsonNode node, StringBuilder into) {
    if (node == null) {
      return;
    }
    if (node.hasNonNull("text")) {
      into.append(node.get("text").asText());
    }
    var content = node.get("content");
    if (content != null && content.isArray()) {
      for (JsonNode child : content) {
        collectText(child, into);
      }
    }
  }
}
