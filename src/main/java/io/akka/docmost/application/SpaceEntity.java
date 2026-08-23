package io.akka.docmost.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import io.akka.docmost.domain.SpaceRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who belongs to a space and in what roles, and which groups each user belongs to.
 *
 * <p>A user may hold more than one role in a space; R2 uses the highest of them.
 */
@Component(id = "space")
public class SpaceEntity extends KeyValueEntity<SpaceEntity.State> {

  public record State(Map<String, List<SpaceRole>> roles, Map<String, List<String>> groups) {

    /** The highest role the user holds here, or null where they hold none. */
    public SpaceRole highestRole(String userId) {
      var held = roles.get(userId);
      return held == null ? null : SpaceRole.highest(held);
    }

    public Set<String> groupsOf(String userId) {
      return Set.copyOf(groups.getOrDefault(userId, List.of()));
    }
  }

  public record Membership(String userId, List<SpaceRole> roles, List<String> groupIds) {}

  @Override
  public State emptyState() {
    return new State(Map.of(), Map.of());
  }

  public Effect<String> addMember(Membership membership) {
    var roles = new LinkedHashMap<>(currentState().roles());
    var groups = new LinkedHashMap<>(currentState().groups());
    roles.put(membership.userId(), List.copyOf(membership.roles()));
    groups.put(membership.userId(), List.copyOf(membership.groupIds()));
    return effects().updateState(new State(roles, groups)).thenReply("ok");
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }
}
