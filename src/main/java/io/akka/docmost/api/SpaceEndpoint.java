package io.akka.docmost.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import io.akka.docmost.application.SpaceEntity;

/** Space membership, which admission is decided against before any page-level grant. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/spaces")
public class SpaceEndpoint {

  private final ComponentClient componentClient;

  public SpaceEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put("/{spaceId}/members")
  public String addMember(String spaceId, SpaceEntity.Membership membership) {
    return componentClient
        .forKeyValueEntity(spaceId)
        .method(SpaceEntity::addMember)
        .invoke(membership);
  }

  @Get("/{spaceId}")
  public SpaceEntity.State get(String spaceId) {
    return componentClient.forKeyValueEntity(spaceId).method(SpaceEntity::get).invoke();
  }
}
