package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Meaningful public changes between consecutive observations on one live connection. */
public final class ChannelChanges {
  private ChannelChanges() {}

  public sealed interface Change
      permits MessagePosted,
          MemberJoined,
          MemberLeft,
          ReactionChanged,
          TasksCompleted,
          ArtifactShared,
          RequestOpened,
          RequestAcknowledged {}

  public record MessagePosted(Message message) implements Change {}

  public record MemberJoined(Agent member) implements Change {}

  public record MemberLeft(Agent member) implements Change {}

  public record ReactionChanged(String agentId, String current) implements Change {}

  public record TasksCompleted() implements Change {}

  public record ArtifactShared(Artifact artifact) implements Change {}

  public record RequestOpened() implements Change {}

  public record RequestAcknowledged() implements Change {}

  public static List<Change> diff(State previous, State next) {
    if (previous == null || !previous.channel().id().equals(next.channel().id())) return List.of();
    var changes = new ArrayList<Change>();
    for (Agent agent : next.team()) {
      if (previous.agent(agent.id()) == null) changes.add(new MemberJoined(agent));
    }
    for (Agent agent : previous.team())
      if (next.agent(agent.id()) == null) changes.add(new MemberLeft(agent));
    if (previous.messages() != next.messages()) {
      Map<String, Message> oldMessages = new HashMap<>();
      previous.messages().forEach(m -> oldMessages.put(m.id(), m));
      for (Message message : next.messages()) {
        Message before = oldMessages.get(message.id());
        if (before == null && message.sequence() > previous.channel().latestSequence()) {
          changes.add(new MessagePosted(message));
          if (message.system() != null && message.system().type().equals("tasks_completed"))
            changes.add(new TasksCompleted());
        }
        if (before != null && before.reactions() != message.reactions()) {
          var oldReactions = reactions(before);
          var newReactions = reactions(message);
          var agents = new java.util.LinkedHashSet<>(oldReactions.keySet());
          agents.addAll(newReactions.keySet());
          for (String agent : agents)
            if (!Objects.equals(oldReactions.get(agent), newReactions.get(agent)))
              changes.add(new ReactionChanged(agent, newReactions.get(agent)));
        }
      }
    }
    for (Artifact artifact : next.artifacts())
      if (previous.artifacts().stream().noneMatch(a -> a.file().equals(artifact.file())))
        changes.add(new ArtifactShared(artifact));
    Message oldRequest = previous.request(), request = next.request();
    if (oldRequest != null && !next.channel().hasPendingRequest())
      changes.add(new RequestAcknowledged());
    if (request != null && (oldRequest == null || !request.id().equals(oldRequest.id())))
      changes.add(new RequestOpened());
    return List.copyOf(changes);
  }

  private static Map<String, String> reactions(Message message) {
    var result = new java.util.LinkedHashMap<String, String>();
    message.reactions().forEach(r -> result.put(r.agentId(), r.reaction()));
    return result;
  }
}
