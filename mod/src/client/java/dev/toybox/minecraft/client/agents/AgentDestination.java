package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.world.layout.AnchorNames;

/** The public collaboration state determines where an agent belongs in the workshop. */
public final class AgentDestination {
  private AgentDestination() {}

  public static String station(String presence, boolean requestedUserReply) {
    if (requestedUserReply) return AnchorNames.USER;
    return switch (presence) {
      case "working" -> "desk";
      case "waiting" -> AnchorNames.WAITING;
      default -> AnchorNames.LOUNGE;
    };
  }
}
