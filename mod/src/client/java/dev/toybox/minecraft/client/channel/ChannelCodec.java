package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Native v1 wire decoding, keeping the public model independent from mutable JSON trees. */
public final class ChannelCodec {
  private ChannelCodec() {}

  public static State decode(String json) {
    return decode(JsonParser.parseString(json).getAsJsonObject());
  }

  public static State decode(JsonObject root) {
    Channel channel = channel(object(root, "channel"));
    Message request = map(root, "request", ChannelCodec::message);
    if (request != null
        && (request.isSystem()
            || !"agent".equals(request.sender().type())
            || !channel.id().equals(request.sender().agentId())))
      throw new IllegalArgumentException("Pending request must be a public lead message");
    return new State(
        number(root, "revision"),
        channel,
        agent(object(root, "lead")),
        list(root, "members", ChannelCodec::agent),
        list(root, "messages", ChannelCodec::message),
        request,
        list(root, "artifacts", ChannelCodec::artifact),
        list(root, "routines", ChannelCodec::routine),
        presence(root));
  }

  public static Channel channel(JsonObject c) {
    return new Channel(
        required(c, "id"),
        required(c, "name"),
        text(c, "purpose"),
        text(c, "directory"),
        model(optional(c, "model")),
        list(c, "tasks", ChannelCodec::task),
        text(c, "previewUrl"),
        number(c, "latestSequence"),
        number(c, "seenThrough"),
        nullableNumber(c, "completedSequence"),
        nullableNumber(c, "requestSequence"),
        text(c, "updatedAt"));
  }

  public static Map<String, Presence> presence(JsonObject root) {
    var presence = new LinkedHashMap<String, Presence>();
    JsonObject p = optional(root, "presence");
    if (p != null)
      p.entrySet()
          .forEach(
              e -> {
                JsonObject item = e.getValue().getAsJsonObject();
                presence.put(
                    e.getKey(),
                    new Presence(
                        required(item, "state"), text(item, "text"), text(item, "wakeAt")));
              });
    return Map.copyOf(presence);
  }

  /** Decode a durable transition; unknown types are ignored for forward compatibility. */
  public static ChannelEvent event(JsonObject event) {
    long revision = number(event, "revision");
    return switch (required(event, "type")) {
      case "message" -> new ChannelEvent.Message(revision, message(object(event, "message")));
      case "reaction" ->
          new ChannelEvent.Reaction(
              revision,
              number(event, "sequence"),
              required(event, "agentId"),
              text(event, "reaction"));
      case "member" -> new ChannelEvent.Member(revision, agent(object(event, "member")));
      case "status" ->
          new ChannelEvent.Status(
              revision, required(event, "agentId"), map(event, "status", ChannelCodec::status));
      case "tasks" -> new ChannelEvent.Tasks(revision, list(event, "tasks", ChannelCodec::task));
      case "model" ->
          new ChannelEvent.Model(
              revision, model(object(event, "model")), required(event, "updatedAt"));
      case "read" -> new ChannelEvent.Read(revision, number(event, "seenThrough"));
      default -> null;
    };
  }

  private static Status status(JsonObject value) {
    return new Status(
        required(value, "state"),
        text(value, "text"),
        text(value, "wakeAt"),
        nullableNumber(value, "lookingAt"),
        nullableNumber(value, "workingOn"));
  }

  public static Message message(JsonObject m) {
    Sender sender = sender(object(m, "sender"));
    boolean system = sender.type().equals("system");
    return new Message(
        required(m, "id"),
        number(m, "sequence"),
        text(m, "timestamp"),
        sender,
        system ? "" : required(m, "content"),
        m.has("request") && m.get("request").getAsBoolean(),
        system ? system(object(m, "content")) : null,
        array(m, "attachments").asList().stream().map(ChannelCodec::attachment).toList(),
        list(m, "reactions", e -> new Reaction(required(e, "agentId"), required(e, "reaction"))));
  }

  private static SystemMessage system(JsonObject value) {
    return new SystemMessage(
        required(value, "type"),
        map(value, "member", ChannelCodec::agent),
        map(value, "actor", ChannelCodec::sender),
        text(value, "name"),
        text(value, "purpose"),
        text(value, "directory"),
        map(value, "artifact", ChannelCodec::artifact),
        map(value, "routine", ChannelCodec::routine),
        text(value, "previewUrl"));
  }

  private static Agent agent(JsonObject a) {
    return new Agent(
        text(a, "channelId"),
        required(a, "id"),
        required(a, "name"),
        text(a, "role"),
        model(optional(a, "model")),
        map(a, "avatar", v -> new Avatar(required(v, "mark"), required(v, "color"))),
        map(a, "status", ChannelCodec::status));
  }

  private static Sender sender(JsonObject value) {
    return new Sender(required(value, "type"), text(value, "agentId"));
  }

  private static Task task(JsonObject t) {
    return new Task(
        text(t, "id"),
        required(t, "title"),
        required(t, "status"),
        text(t, "ownerId"),
        text(t, "artifactId"),
        map(t, "diff", d -> new DiffStats(number(d, "added"), number(d, "removed"))),
        list(t, "children", ChannelCodec::task));
  }

  private static Artifact artifact(JsonObject a) {
    JsonObject f = object(a, "file");
    return new Artifact(
        text(a, "id"),
        new FileRef(required(f, "kind"), text(f, "sessionId"), required(f, "path")),
        required(a, "title"),
        text(a, "sharedAt"),
        text(a, "url"));
  }

  private static Routine routine(JsonObject r) {
    return new Routine(
        required(r, "id"), required(r, "title"), required(r, "schedule"), required(r, "prompt"));
  }

  private static Attachment attachment(JsonElement value) {
    JsonObject a = value.getAsJsonObject();
    return a.has("url")
        ? new Attachment(required(a, "name"), required(a, "url"), null, null)
        : new Attachment(null, null, required(a, "mimeType"), required(a, "base64"));
  }

  private static ModelConfiguration model(JsonObject m) {
    if (m == null) return null;
    Map<String, Object> options = new LinkedHashMap<>();
    m.entrySet().stream()
        .filter(
            e ->
                !java.util.Set.of("provider", "name", "reasoningEffort", "contextTier")
                    .contains(e.getKey()))
        .forEach(e -> options.put(e.getKey(), jsonValue(e.getValue())));
    return new ModelConfiguration(
        required(m, "provider"),
        required(m, "name"),
        text(m, "reasoningEffort"),
        text(m, "contextTier"),
        options);
  }

  private static Object jsonValue(JsonElement value) {
    if (value.isJsonNull()) return null;
    if (value.isJsonObject()) {
      var map = new LinkedHashMap<String, Object>();
      value.getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(), jsonValue(e.getValue())));
      return map;
    }
    if (value.isJsonArray())
      return value.getAsJsonArray().asList().stream().map(ChannelCodec::jsonValue).toList();
    var p = value.getAsJsonPrimitive();
    return p.isBoolean() ? p.getAsBoolean() : p.isNumber() ? p.getAsBigDecimal() : p.getAsString();
  }

  private static <T> T map(JsonObject o, String key, Function<JsonObject, T> decode) {
    var value = optional(o, key);
    return value == null ? null : decode.apply(value);
  }

  private static <T> List<T> list(JsonObject o, String key, Function<JsonObject, T> decode) {
    var out = new ArrayList<T>();
    for (var e : array(o, key)) out.add(decode.apply(e.getAsJsonObject()));
    return List.copyOf(out);
  }

  public static String text(JsonObject o, String key) {
    var v = o == null ? null : o.get(key);
    return v == null || v.isJsonNull() ? null : v.getAsString();
  }

  private static String required(JsonObject o, String key) {
    String value = text(o, key);
    if (value == null) throw new IllegalArgumentException("Missing " + key);
    return value;
  }

  public static long number(JsonObject o, String key) {
    Long value = nullableNumber(o, key);
    return value == null ? 0 : value;
  }

  private static Long nullableNumber(JsonObject o, String key) {
    var v = o.get(key);
    return v == null || v.isJsonNull() ? null : v.getAsBigDecimal().longValueExact();
  }

  private static JsonArray array(JsonObject o, String key) {
    var v = o.get(key);
    return v == null || v.isJsonNull() ? new JsonArray() : v.getAsJsonArray();
  }

  private static JsonObject optional(JsonObject o, String key) {
    var v = o.get(key);
    return v == null || v.isJsonNull() ? null : v.getAsJsonObject();
  }

  private static JsonObject object(JsonObject o, String key) {
    var v = optional(o, key);
    if (v == null) throw new IllegalArgumentException("Missing " + key);
    return v;
  }
}
