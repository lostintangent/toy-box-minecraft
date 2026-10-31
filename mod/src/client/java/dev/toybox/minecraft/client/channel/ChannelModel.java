package dev.toybox.minecraft.client.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable public Channel values. Private sessions and world presentation have no place here. */
public final class ChannelModel {
  private ChannelModel() {}

  /** Canonical reduced Channel state; a snapshot is this value at one revision. */
  public record State(
      long revision,
      Channel channel,
      Agent lead,
      List<Agent> members,
      List<Message> messages,
      Message request,
      List<Artifact> artifacts,
      List<Routine> routines,
      Map<String, Presence> presence) {
    public State {
      members = List.copyOf(members);
      messages = List.copyOf(messages);
      artifacts = List.copyOf(artifacts);
      routines = List.copyOf(routines);
      presence = Map.copyOf(presence);
    }

    public List<Agent> team() {
      var team = new ArrayList<Agent>();
      team.add(lead);
      team.addAll(members);
      return List.copyOf(team);
    }

    public Agent agent(String id) {
      if (lead.id().equals(id)) return lead;
      return members.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    public List<Task> tasks() {
      return channel.tasks();
    }

    /** The shared artifact a task references by ID. */
    public Artifact artifact(String id) {
      if (id == null) return null;
      return artifacts.stream().filter(a -> id.equals(a.id())).findFirst().orElse(null);
    }

    public Presence presence(String id) {
      return presence.getOrDefault(id, Presence.IDLE);
    }
  }

  public record Channel(
      String id,
      String name,
      String purpose,
      String directory,
      ModelConfiguration model,
      List<Task> tasks,
      String previewUrl,
      long latestSequence,
      long seenThrough,
      Long completedSequence,
      Long requestSequence,
      String updatedAt) {
    public Channel {
      tasks = List.copyOf(tasks);
    }

    public boolean hasUnreadCompletion() {
      return completedSequence != null && completedSequence > seenThrough;
    }

    public boolean hasPendingRequest() {
      return requestSequence != null;
    }
  }

  public record ModelConfiguration(
      String provider,
      String name,
      String reasoningEffort,
      String contextTier,
      Map<String, Object> options) {
    public ModelConfiguration {
      options = immutableMap(options);
    }
  }

  public record Agent(
      String channelId,
      String id,
      String name,
      String role,
      ModelConfiguration model,
      Avatar avatar,
      Status status) {}

  public record Avatar(String mark, String color) {}

  public record Status(String state, String text, String wakeAt, Long lookingAt, Long workingOn) {}

  public record Presence(String state, String text, String wakeAt) {
    public static final Presence IDLE = new Presence("idle", null, null);
  }

  /** A public task; its artifact and diff describe its own work, never its subtasks'. */
  public record Task(
      String id,
      String title,
      String status,
      String ownerId,
      String artifactId,
      DiffStats diff,
      List<Task> children) {
    public Task {
      children = List.copyOf(children);
    }
  }

  /** Reported added and removed lines. Totals derive from a task tree and are never stored. */
  public record DiffStats(long added, long removed) {
    /** Direct and descendant work, as Toy Box sums it; null when nothing was reported. */
    public static DiffStats total(List<Task> tasks) {
      DiffStats total = null;
      for (Task task : tasks) total = sum(sum(total, task.diff()), total(task.children()));
      return total;
    }

    private static DiffStats sum(DiffStats left, DiffStats right) {
      if (left == null) return right;
      if (right == null) return left;
      return new DiffStats(left.added + right.added, left.removed + right.removed);
    }
  }

  public record Sender(String type, String agentId) {
    public static final Sender USER = new Sender("user", null);
  }

  public record Message(
      String id,
      long sequence,
      String timestamp,
      Sender sender,
      String content,
      boolean request,
      SystemMessage system,
      List<Attachment> attachments,
      List<Reaction> reactions) {
    public Message {
      attachments = List.copyOf(attachments);
      reactions = List.copyOf(reactions);
    }

    public boolean isSystem() {
      return sender.type().equals("system");
    }
  }

  public record SystemMessage(
      String type,
      Agent member,
      Sender actor,
      String name,
      String purpose,
      String directory,
      Artifact artifact,
      Routine routine,
      String previewUrl) {}

  /** A channel attachment is either a named resource URL or an uploaded image. */
  public record Attachment(String name, String url, String mimeType, String base64) {
    public boolean isImage() {
      return mimeType != null;
    }
  }

  public record Reaction(String agentId, String reaction) {}

  public record FileRef(String kind, String sessionId, String path) {}

  /** The server-supplied ID is what tasks reference; clients never derive it from the file. */
  public record Artifact(String id, FileRef file, String title, String sharedAt, String url) {}

  public record Routine(String id, String title, String schedule, String prompt) {}

  private static Map<String, Object> immutableMap(Map<String, Object> input) {
    var result = new LinkedHashMap<String, Object>();
    input.forEach((key, value) -> result.put(key, immutableJson(value)));
    return Collections.unmodifiableMap(result);
  }

  private static Object immutableJson(Object value) {
    if (value instanceof Map<?, ?> map) {
      var copy = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> copy.put(key.toString(), immutableJson(item)));
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof List<?> list)
      return Collections.unmodifiableList(list.stream().map(ChannelModel::immutableJson).toList());
    return value;
  }
}
