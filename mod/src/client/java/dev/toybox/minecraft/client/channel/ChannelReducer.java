package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java equivalent of the complete public Channel reducer. */
public final class ChannelReducer {
  private ChannelReducer() {}

  /** Presence is ephemeral: a reconnect baseline replaces it; live updates merge by Agent ID. */
  public static State presence(State previous, Map<String, Presence> incoming, boolean replace) {
    var presence = new LinkedHashMap<String, Presence>();
    if (!replace) presence.putAll(previous.presence());
    presence.putAll(incoming);
    return presence.equals(previous.presence())
        ? previous
        : new State(
            previous.revision(),
            previous.channel(),
            previous.lead(),
            previous.members(),
            previous.messages(),
            previous.request(),
            previous.artifacts(),
            previous.routines(),
            presence);
  }

  public static State apply(State previous, ChannelEvent event) {
    if (event.revision() <= previous.revision()) return previous;
    Channel channel = previous.channel();
    String name = channel.name(), purpose = channel.purpose(), directory = channel.directory();
    String previewUrl = channel.previewUrl(), updatedAt = channel.updatedAt();
    ModelConfiguration model = channel.model();
    List<Task> tasks = channel.tasks();
    long latestSequence = channel.latestSequence(), seenThrough = channel.seenThrough();
    Long completedSequence = channel.completedSequence(),
        requestSequence = channel.requestSequence();
    Map<String, Presence> presence = previous.presence();
    Message request = previous.request();
    Agent lead = previous.lead();
    List<Agent> members = previous.members();
    List<Message> messages = previous.messages();
    List<Artifact> artifacts = previous.artifacts();
    List<Routine> routines = previous.routines();
    switch (event) {
      case ChannelEvent.Message posted -> {
        Message message = posted.message();
        latestSequence = Math.max(latestSequence, message.sequence());
        updatedAt = message.timestamp();
        if (message.sender().type().equals("user")) {
          if (request != null && message.sequence() > request.sequence()) request = null;
          requestSequence = null;
        } else if (message.request()) {
          request = message;
          requestSequence = message.sequence();
        }
        var ordered =
            new ArrayList<>(messages.stream().filter(m -> !m.id().equals(message.id())).toList());
        int index = 0;
        while (index < ordered.size() && ordered.get(index).sequence() < message.sequence())
          index++;
        ordered.add(index, message);
        messages = List.copyOf(ordered);
        if (message.system() != null) {
          SystemMessage system = message.system();
          switch (system.type()) {
            case "member_joined" -> members = setMember(members, system.member());
            case "member_left" -> {
              String id = system.member().id();
              members = members.stream().filter(m -> !m.id().equals(id)).toList();
              tasks = unassign(tasks, id);
              presence = new LinkedHashMap<>(presence);
              presence.remove(id);
            }
            case "channel_renamed" -> name = system.name();
            case "channel_purpose_changed" -> purpose = system.purpose();
            case "channel_directory_changed" -> directory = system.directory();
            case "preview_changed" -> previewUrl = system.previewUrl();
            case "tasks_completed" -> completedSequence = message.sequence();
            case "artifact_shared" ->
                artifacts = shareArtifact(artifacts, system.artifact(), message.timestamp());
            case "routine_scheduled", "routine_edited" -> {
              Routine routine = system.routine();
              if (routines.stream().anyMatch(r -> r.id().equals(routine.id())))
                routines =
                    routines.stream().map(r -> r.id().equals(routine.id()) ? routine : r).toList();
              else {
                var added = new ArrayList<>(routines);
                added.add(routine);
                routines = List.copyOf(added);
              }
            }
            case "routine_deleted" ->
                routines =
                    routines.stream().filter(r -> !r.id().equals(system.routine().id())).toList();
            default -> {}
          }
        }
      }
      case ChannelEvent.Reaction reaction -> {
        messages = messages.stream().map(message -> react(message, reaction)).toList();
        if (request != null) request = react(request, reaction);
      }
      case ChannelEvent.Member member -> members = setMember(members, member.member());
      case ChannelEvent.Status status -> {
        if (lead.id().equals(status.agentId())) lead = withStatus(lead, status.status());
        else
          members =
              members.stream()
                  .map(m -> m.id().equals(status.agentId()) ? withStatus(m, status.status()) : m)
                  .toList();
      }
      case ChannelEvent.Tasks change -> tasks = change.tasks();
      case ChannelEvent.Model change -> {
        model = change.model();
        updatedAt = change.updatedAt();
      }
      case ChannelEvent.Read read -> {
        seenThrough = read.seenThrough();
      }
    }
    Channel nextChannel =
        new Channel(
            channel.id(),
            name,
            purpose,
            directory,
            model,
            tasks,
            previewUrl,
            latestSequence,
            seenThrough,
            completedSequence,
            requestSequence,
            updatedAt);
    return new State(
        event.revision(),
        nextChannel.equals(channel) ? channel : nextChannel,
        lead,
        members,
        messages,
        request,
        artifacts,
        routines,
        presence);
  }

  private static Message react(Message message, ChannelEvent.Reaction reaction) {
    if (message.sequence() != reaction.sequence() || message.isSystem()) return message;
    var reactions =
        new ArrayList<>(
            message.reactions().stream()
                .filter(r -> !r.agentId().equals(reaction.agentId()))
                .toList());
    if (reaction.reaction() != null)
      reactions.add(new Reaction(reaction.agentId(), reaction.reaction()));
    return new Message(
        message.id(),
        message.sequence(),
        message.timestamp(),
        message.sender(),
        message.content(),
        message.request(),
        message.system(),
        message.attachments(),
        reactions);
  }

  private static List<Task> unassign(List<Task> tasks, String agentId) {
    return tasks.stream()
        .map(
            task ->
                new Task(
                    task.id(),
                    task.title(),
                    task.status(),
                    agentId.equals(task.ownerId()) ? null : task.ownerId(),
                    task.artifactId(),
                    task.diff(),
                    unassign(task.children(), agentId)))
        .toList();
  }

  private static List<Agent> setMember(List<Agent> members, Agent member) {
    var next = new ArrayList<>(members.stream().filter(m -> !m.id().equals(member.id())).toList());
    next.add(member);
    var collator = java.text.Collator.getInstance(java.util.Locale.ENGLISH);
    collator.setDecomposition(java.text.Collator.CANONICAL_DECOMPOSITION);
    next.sort(
        (left, right) -> {
          int byName = collator.compare(left.name(), right.name());
          return byName != 0 ? byName : collator.compare(left.id(), right.id());
        });
    return List.copyOf(next);
  }

  private static Agent withStatus(Agent agent, Status status) {
    return new Agent(
        agent.channelId(),
        agent.id(),
        agent.name(),
        agent.role(),
        agent.model(),
        agent.avatar(),
        status);
  }

  private static List<Artifact> shareArtifact(
      List<Artifact> artifacts, Artifact shared, String timestamp) {
    if (artifacts.stream().anyMatch(a -> a.file().equals(shared.file())))
      return artifacts.stream()
          .map(
              a ->
                  a.file().equals(shared.file())
                      ? new Artifact(
                          a.id(),
                          a.file(),
                          shared.title(),
                          a.sharedAt(),
                          shared.url() == null ? a.url() : shared.url())
                      : a)
          .toList();
    var next = new ArrayList<>(artifacts);
    next.add(new Artifact(shared.id(), shared.file(), shared.title(), timestamp, shared.url()));
    return List.copyOf(next);
  }
}
