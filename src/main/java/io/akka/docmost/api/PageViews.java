package io.akka.docmost.api;

import io.akka.docmost.application.PageEntity;
import io.akka.docmost.domain.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What the HTTP surface returns, and how a page's state becomes it.
 *
 * <p>The entity's state and events are the port's own shape and change when the rebuild
 * changes; a caller reads these instead, so the two can move separately.
 */
public final class PageViews {

  public record PageResponse(
      String pageId,
      String parentPageId,
      String spaceId,
      String creatorId,
      Instant createdAt,
      Document content,
      List<String> contributorIds,
      List<String> pendingContributorIds,
      String lastUpdatedById,
      Instant deletedAt,
      Instant windowClosesAt,
      boolean restricted,
      int versionCount) {}

  public record VersionResponse(
      int index, Document content, List<String> contributorIds, Instant keptAt) {}

  private PageViews() {}

  public static PageResponse toApi(PageEntity.State state) {
    return new PageResponse(
        state.pageId(),
        state.parentPageId(),
        state.spaceId(),
        state.creatorId(),
        state.createdAt(),
        state.content(),
        state.contributorIds(),
        state.pendingContributorIds(),
        state.lastUpdatedById(),
        state.deletedAt(),
        state.windowClosesAt(),
        state.restriction() != null,
        state.versions().size());
  }

  public static List<VersionResponse> toApi(List<PageEntity.Version> versions) {
    var out = new ArrayList<VersionResponse>(versions.size());
    for (int i = 0; i < versions.size(); i++) {
      var version = versions.get(i);
      out.add(
          new VersionResponse(i, version.content(), version.contributorIds(), version.keptAt()));
    }
    return out;
  }
}
