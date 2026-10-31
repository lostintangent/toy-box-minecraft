package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.toybox.minecraft.client.screens.TaskRows;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executable public native contract, with no Minecraft instance or provider process. */
public final class ChannelContractTest {
  public static void main(String[] args) throws Exception {
    completeImmutableSnapshot();
    meaningfulChanges();
    requestsRemainPublicAndAuthoritative();
    granularUpdatesPreserveDetails();
    sharedReducerConformance();
    taskOutcomes();
    audienceMatchesToyBox();
    systemTranscript();
    transcriptGrouping();
    configUrls();
    ChannelReducerTest.run();
    ChannelTransportTest.run();
    System.out.println(
        "Toy Box native channel: codec, changes, lifecycle, audience and HTTP/SSE contracts"
            + " passed");
  }

  static JsonObject fixture() {
    return JsonParser.parseString(
            """
            {"channel":{"id":"channel-native","name":"Native workshop","purpose":"Collaborate publicly","directory":"/workspace","model":{"provider":"codex","name":"test","reasoningEffort":"high","contextTier":"wide","future":{"nested":[1,null,true]}},"tasks":[{"title":"Build","status":"in_progress","ownerId":"a","children":[{"title":"Sketch","status":"done"}]}],"previewUrl":"/preview","latestSequence":2,"seenThrough":0,"completedSequence":null,"requestSequence":1,"updatedAt":"2026-10-03T10:00:00Z"},"revision":3,"lead":{"id":"channel-native","name":"Lead","role":"Coordinate","avatar":{"mark":"M0 0L24 24","color":"#0891b2"},"status":{"state":"working","text":"Reviewing","lookingAt":1}},"members":[{"channelId":"channel-native","id":"a","name":"Zoë Artist","role":"Design","model":{"provider":"codex","name":"mini"},"avatar":{"mark":"M0 0L12 12","color":"#ff0088"},"status":{"state":"working","text":"Drawing","workingOn":1}},{"channelId":"channel-native","id":"b","name":"Engineer","status":{"state":"waiting","text":"Waiting for output","wakeAt":"2026-10-03T11:00:00Z"}}],"messages":[{"id":"request","sequence":1,"timestamp":"2026-10-03T10:00:00Z","sender":{"type":"agent","agentId":"channel-native"},"content":"Which direction?","attachments":[{"name":"plan.md","url":"/api/serve/machine/plan.md"},{"mimeType":"image/png","base64":"aGVsbG8="}],"reactions":[{"agentId":"a","reaction":"agree"}],"request":true},{"id":"preview","sequence":2,"timestamp":"2026-10-03T10:00:01Z","sender":{"type":"system"},"content":{"type":"preview_changed","previewUrl":"/preview"}}],"artifacts":[{"file":{"kind":"session","sessionId":"artifact-session","path":"result.toy"},"title":"Prototype","sharedAt":"2026-10-03T10:00:00Z","url":"/?files=prototype"}],"routines":[{"id":"daily","title":"Morning check","schedule":"0 9 * * 1-5","prompt":"Check progress"}],"request":{"id":"request","sequence":1,"timestamp":"2026-10-03T10:00:00Z","sender":{"type":"agent","agentId":"channel-native"},"content":"Which direction?","request":true},"presence":{"channel-native":{"state":"working","text":"Reviewing"},"a":{"state":"working","text":"Drawing"},"b":{"state":"waiting","text":"Waiting for output","wakeAt":"2026-10-03T11:00:00Z"}}}
            """)
        .getAsJsonObject();
  }

  private static void granularUpdatesPreserveDetails() {
    var state = new ChannelStore(new ChannelConnection(ChannelConnection.Phase.LIVE, null, 0));
    var changes = new ArrayList<ChannelChanges.Change>();
    state.addListener(
        new ChannelStore.Listener() {
          public void onState(State snapshot, List<ChannelChanges.Change> effects) {
            changes.addAll(effects);
          }
        });
    var initial = ChannelCodec.decode(fixture());
    state.replace(initial);
    state.event(
        new ChannelEvent.Tasks(
            4, List.of(new Task("t1", "Revised tasks", "done", "a", null, null, List.of()))));
    state.event(new ChannelEvent.Read(5, 2));
    state.presence(java.util.Map.of("a", new Presence("working", "Revising", null)), false);
    require(
        state.messages().equals(initial.messages()) && state.channel().latestSequence() == 2,
        "quiet task and read transitions preserve transcript and sequence");
    require(
        state.tasks().getFirst().title().equals("Revised tasks")
            && state.channel().seenThrough() == 2
            && state.request() != null,
        "tasks and read update independently without acknowledging the request");
    require(
        state.presence("b").equals(initial.presence("b")), "sparse presence retains other agents");
    long revision = state.revision();
    state.presence(java.util.Map.of("a", new Presence("working", "Revising", null)), false);
    require(state.revision() == revision, "identical presence produces no observer update");
    state.event(new ChannelEvent.Reaction(6, 1, "a", "love"));
    state.event(
        new ChannelEvent.Message(
            7, ChannelCodec.message(messageJson("reply", 3, "user", null, "Proceed"))));
    require(
        state.request() == null
            && !state.channel().hasPendingRequest()
            && state.channel().latestSequence() == 3,
        "committed reply acknowledges request and advances metadata in one transition");
    require(
        count(changes, ChannelChanges.ReactionChanged.class) == 1
            && count(changes, ChannelChanges.MessagePosted.class) == 1
            && count(changes, ChannelChanges.RequestAcknowledged.class) == 1,
        "each live transition creates its public effect once");
    state.presence(java.util.Map.of("a", Presence.IDLE), true);
    require(
        state.state().presence().equals(java.util.Map.of("a", Presence.IDLE)),
        "complete presence baseline replaces stale entries");
  }

  private static void sharedReducerConformance() throws Exception {
    Path path = Path.of("fixtures/channel-transitions.json");
    if (!Files.exists(path))
      throw new AssertionError("Export the canonical channel transition fixture first");
    var fixture = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    State snapshot = ChannelCodec.decode(fixture.getAsJsonObject("initial"));
    for (var value : fixture.getAsJsonArray("steps")) {
      var step = value.getAsJsonObject();
      var event = step.getAsJsonObject("event");
      snapshot =
          switch (event.get("type").getAsString()) {
            case "snapshot" -> ChannelCodec.decode(event.getAsJsonObject("state"));
            case "presence", "resumed" ->
                ChannelReducer.presence(
                    snapshot,
                    ChannelCodec.presence(event),
                    event.get("type").getAsString().equals("resumed"));
            default -> ChannelReducer.apply(snapshot, ChannelCodec.event(event));
          };
      require(
          snapshot.equals(ChannelCodec.decode(step.getAsJsonObject("expected"))),
          "canonical TypeScript/Java transition parity: " + step.get("name").getAsString());
    }
  }

  /** Exported from Toy Box: diffs display like its checklists; tasks reach outcomes by ID. */
  private static void taskOutcomes() throws Exception {
    State s = ChannelCodec.decode(Files.readString(Path.of("fixtures/channel-snapshot.json")));
    var tasks = TaskRows.flatten(s.tasks()).stream().map(TaskRows.Row::task).toList();
    java.util.function.Function<String, Task> task =
        id -> tasks.stream().filter(t -> id.equals(t.id())).findFirst().orElseThrow();
    Task workshop = task.apply("t2");
    require(
        s.artifact(workshop.artifactId()).title().equals("Workshop plan"),
        "tasks reference shared artifacts by their public ID");
    var shown = TaskRows.diff(workshop);
    require(
        workshop.diff().equals(new DiffStats(1180, 96)) && shown.equals(new DiffStats(1522, 114)),
        "a task shows its own and its subtasks' reported work");
    require(
        TaskRows.added(shown).equals("+1.5K") && TaskRows.removed(shown).equals("−114"),
        "compact counts read like the web checklist");
    require(
        task.apply("t6").diff() != null && TaskRows.diff(task.apply("t6")) == null,
        "pending tasks omit their counts");
    require(
        TaskRows.diff(task.apply("t5")).equals(new DiffStats(0, 0))
            && TaskRows.diff(task.apply("t4")) == null,
        "a measured zero still shows, while unreported work shows nothing");
    require(
        TaskRows.count(999).equals("999")
            && TaskRows.count(1_049).equals("1K")
            && TaskRows.count(1_050).equals("1.1K")
            && TaskRows.count(99_950).equals("100K")
            && TaskRows.count(999_949).equals("999.9K")
            && TaskRows.count(999_950).equals("1M")
            && TaskRows.count(1_250_000).equals("1.3M"),
        "compact rounding matches the web's half-up rounding, including the carry into millions");
  }

  private static void completeImmutableSnapshot() throws Exception {
    JsonObject json = fixture();
    State s = ChannelCodec.decode(json);
    var mixed = messageJson("mixed", 9, "user", null, "Resources");
    mixed.add(
        "attachments",
        JsonParser.parseString(
            """
            [{"name":"first.png","url":"/api/serve/machine/first.png"},
             {"mimeType":"image/png","base64":"AAEC"},
             {"name":"last.png","url":"/api/serve/machine/last.png"}]
            """));
    Message resources = ChannelCodec.message(mixed);
    require(
        resources.attachments().getFirst().url().equals("/api/serve/machine/first.png")
            && resources.attachments().get(1).base64().equals("AAEC")
            && resources.attachments().getLast().name().equals("last.png"),
        "mixed attachments retain names, URLs, and inline image bytes");
    require(
        s.team().size() == 3 && s.agent("a").name().equals("Zoë Artist"),
        "canonical member identity");
    require(
        s.channel().directory().equals("/workspace")
            && s.channel().model().options().containsKey("future"),
        "configuration and future model fields");
    require(
        s.lead().status().lookingAt() == 1 && s.agent("a").status().workingOn() == 1,
        "public status targets");
    require(
        s.agent("a").avatar().color().equals("#ff0088")
            && s.agent("a").model().name().equals("mini"),
        "native avatar and member model");
    require(
        s.tasks().getFirst().children().getFirst().status().equals("done"),
        "task tree preserved without invented IDs");
    require(
        s.routines().getFirst().schedule().equals("0 9 * * 1-5")
            && s.routines().getFirst().prompt().equals("Check progress"),
        "routine full public configuration");
    Message message = s.messages().getFirst();
    require(
        message.attachments().getFirst().name().equals("plan.md")
            && message.attachments().get(1).isImage(),
        "both attachment variants");
    require(
        message.attachments().getFirst().url().equals("/api/serve/machine/plan.md")
            && message.reactions().getFirst().reaction().equals("agree"),
        "attachment URLs and reactions");
    require(
        s.messages().getFirst().request()
            && s.artifacts().getFirst().url().equals("/?files=prototype"),
        "message request flag and artifact URL");
    json.getAsJsonObject("channel").addProperty("name", "Mutated JSON");
    require(s.channel().name().equals("Native workshop"), "JSON cannot mutate retained snapshot");
    expectUnsupported(() -> s.team().clear());
    expectUnsupported(() -> s.tasks().clear());
    expectUnsupported(() -> s.channel().model().options().clear());
    Path supplied = Path.of("fixtures/channel-snapshot.json");
    if (Files.exists(supplied)) ChannelCodec.decode(Files.readString(supplied));
  }

  private static void meaningfulChanges() {
    State before = ChannelCodec.decode(fixture());
    JsonObject after = fixture();
    var detail = after;
    detail.getAsJsonArray("members").remove(1);
    detail
        .getAsJsonArray("members")
        .add(JsonParser.parseString("{\"id\":\"c\",\"name\":\"Writer\"}"));
    after.getAsJsonObject("presence").getAsJsonObject("a").addProperty("text", "Finishing");
    detail
        .getAsJsonArray("messages")
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("reactions")
        .get(0)
        .getAsJsonObject()
        .addProperty("reaction", "love");
    detail.getAsJsonArray("messages").add(messageJson("new", 3, "agent", "a", "Ready @lead"));
    detail
        .getAsJsonArray("messages")
        .add(
            JsonParser.parseString(
                "{\"id\":\"done\",\"sequence\":4,\"sender\":{\"type\":\"system\"},\"content\":{\"type\":\"tasks_completed\"}}"));
    after.getAsJsonObject("channel").addProperty("latestSequence", 4);
    detail
        .getAsJsonArray("artifacts")
        .add(
            JsonParser.parseString(
                "{\"file\":{\"kind\":\"machine\",\"path\":\"/new.txt\"},\"title\":\"New\",\"sharedAt\":\"now\",\"url\":\"/file\"}"));
    var changes = ChannelChanges.diff(before, ChannelCodec.decode(after));
    require(
        count(changes, ChannelChanges.MessagePosted.class) == 2,
        "each new public message including system remains observable");
    require(
        count(changes, ChannelChanges.MemberJoined.class) == 1
            && count(changes, ChannelChanges.MemberLeft.class) == 1,
        "membership transitions");
    require(
        count(changes, ChannelChanges.ReactionChanged.class) == 1,
        "reaction change and departed waiting presence not duplicated");
    require(
        count(changes, ChannelChanges.TasksCompleted.class) == 1
            && count(changes, ChannelChanges.ArtifactShared.class) == 1,
        "completion system event and artifact sharing");
    require(
        ChannelChanges.diff(ChannelCodec.decode(after), ChannelCodec.decode(after)).isEmpty(),
        "duplicate snapshots have no effects");
  }

  private static void requestsRemainPublicAndAuthoritative() {
    JsonObject withoutRequest = fixture();
    withoutRequest.add("request", com.google.gson.JsonNull.INSTANCE);
    withoutRequest
        .getAsJsonObject("channel")
        .add("requestSequence", com.google.gson.JsonNull.INSTANCE);
    require(
        count(
                ChannelChanges.diff(
                    ChannelCodec.decode(withoutRequest), ChannelCodec.decode(fixture())),
                ChannelChanges.RequestOpened.class)
            == 1,
        "a new public request emits one request effect");
    JsonObject json = fixture();
    json.add("messages", new com.google.gson.JsonArray());
    State outsideWindow = ChannelCodec.decode(json);
    require(
        outsideWindow.request().content().equals("Which direction?"),
        "pending request survives bounded message window");
    json.getAsJsonObject("channel").addProperty("seenThrough", 2);
    require(
        count(
                ChannelChanges.diff(outsideWindow, ChannelCodec.decode(json)),
                ChannelChanges.RequestAcknowledged.class)
            == 0,
        "reading never acknowledges request");
    json.getAsJsonObject("request").addProperty("id", "newer-request");
    require(
        count(
                ChannelChanges.diff(outsideWindow, ChannelCodec.decode(json)),
                ChannelChanges.RequestAcknowledged.class)
            == 0,
        "replacement question is not a user acknowledgement");
    json.add("request", com.google.gson.JsonNull.INSTANCE);
    json.getAsJsonObject("channel").add("requestSequence", com.google.gson.JsonNull.INSTANCE);
    require(
        count(
                ChannelChanges.diff(outsideWindow, ChannelCodec.decode(json)),
                ChannelChanges.RequestAcknowledged.class)
            == 1,
        "server-confirmed acknowledgement");
    JsonObject invalid = fixture();
    invalid.getAsJsonObject("request").getAsJsonObject("sender").addProperty("agentId", "a");
    expectInvalid(() -> ChannelCodec.decode(invalid));
  }

  private static void audienceMatchesToyBox() {
    State s = ChannelCodec.decode(fixture());
    audience(s, "Hello", Sender.USER, false, "channel-native");
    audience(s, "@zoe-artist please review", Sender.USER, false, "a");
    audience(s, "@missing hello", Sender.USER, false);
    audience(s, "@missing hello", Sender.USER, true, "channel-native");
    audience(s, "@zoe-artist answer", Sender.USER, true, "channel-native", "a");
    audience(s, "@everyone hello", new Sender("agent", "a"), false, "channel-native", "b");
    audience(s, "Status", new Sender("agent", "channel-native"), false);
    audience(s, "mail@zoe-artist", Sender.USER, false, "channel-native");
    audience(s, "(@ZOE-ARTIST), @engineer!", Sender.USER, false, "a", "b");
    audience(s, "@everyone-agent", Sender.USER, false);
    require(
        ChannelAudience.handle("Évêryone").equals("everyone-agent")
            && ChannelAudience.handle("東京").equals("teammate"),
        "exact handle normalization");
    require(ChannelAudience.label(s.team(), 3).equals("Everyone"), "audience label");
  }

  private static void systemTranscript() {
    State s = ChannelCodec.decode(fixture());
    String[] values = {
      "{\"type\":\"tasks_completed\"}",
      "{\"type\":\"preview_changed\",\"actor\":{\"type\":\"user\"},\"previewUrl\":\"/preview\"}",
      "{\"type\":\"member_joined\",\"member\":{\"id\":\"x\",\"name\":\"New member\"}}",
      "{\"type\":\"member_left\",\"member\":{\"id\":\"x\",\"name\":\"Old member\"}}",
      "{\"type\":\"channel_renamed\",\"actor\":{\"type\":\"user\"},\"name\":\"Fresh\"}",
      "{\"type\":\"channel_purpose_changed\",\"actor\":{\"type\":\"user\"},\"purpose\":null}",
      "{\"type\":\"channel_directory_changed\",\"actor\":{\"type\":\"agent\",\"agentId\":\"deleted\"},\"directory\":\"/new\"}",
      "{\"type\":\"artifact_shared\",\"actor\":{\"type\":\"user\"},\"artifact\":{\"file\":{\"kind\":\"machine\",\"path\":\"/file\"},\"title\":\"File\"}}"
    };
    for (String value : values) {
      var json = messageJson("system", 1, "system", null, "");
      json.add("content", JsonParser.parseString(value));
      require(
          !ChannelText.message(ChannelCodec.message(json), s).isBlank(),
          "system transcript " + value);
    }
    for (String type : List.of("routine_scheduled", "routine_edited", "routine_deleted")) {
      var json = messageJson("routine", 1, "system", null, "");
      json.add(
          "content",
          JsonParser.parseString(
              "{\"type\":\""
                  + type
                  + "\",\"routine\":{\"id\":\"r\",\"title\":\"Daily\",\"schedule\":\"0 9 * *"
                  + " *\",\"prompt\":\"Check\"}}"));
      require(
          ChannelText.message(ChannelCodec.message(json), s).contains("Daily"),
          "routine system transcript");
    }
    require(
        ChannelText.agentName("deleted", s).equals("Deleted agent"),
        "historical deleted sender remains explicit");
  }

  private static void transcriptGrouping() {
    var messages = new ArrayList<Message>();
    for (int i = 1; i <= 3; i++)
      messages.add(ChannelCodec.message(messageJson("m" + i, i, "agent", "a", "Update " + i)));
    var rows = dev.toybox.minecraft.client.screens.TranscriptRows.group(messages, 1L);
    require(
        rows.size() == 2 && rows.getLast().startsUnread() && rows.getLast().messages().size() == 2,
        "agent runs stop at the unread boundary");
    var later = messageJson("later", 4, "agent", "a", "Later");
    later.addProperty("timestamp", "2026-10-03T10:11:00Z");
    messages.add(ChannelCodec.message(later));
    require(
        dev.toybox.minecraft.client.screens.TranscriptRows.group(messages, null).size() == 2,
        "agent runs stop after ten minutes");
    later.addProperty("timestamp", "2026-10-04T10:00:00Z");
    messages.set(3, ChannelCodec.message(later));
    require(
        dev.toybox.minecraft.client.screens.TranscriptRows.group(messages, null)
            .getLast()
            .startsDay(),
        "day changes start a new row");
    messages.clear();
    for (int i = 0; i < 3; i++) {
      var joined = messageJson("join" + i, i + 1, "system", null, "");
      var content = new JsonObject();
      content.addProperty("type", "member_joined");
      var member = new JsonObject();
      member.addProperty("id", "agent" + i);
      member.addProperty("name", List.of("Ada", "Pixel", "Moss").get(i));
      content.add("member", member);
      joined.add("content", content);
      messages.add(ChannelCodec.message(joined));
    }
    rows = dev.toybox.minecraft.client.screens.TranscriptRows.group(messages, null);
    require(
        rows.size() == 1
            && dev.toybox.minecraft.client.screens.TranscriptRows.systemText(rows.getFirst(), null)
                .equals("Ada, Pixel and Moss joined the channel"),
        "membership changes merge without losing attribution");
    var snapshot = ChannelCodec.decode(fixture());
    require(
        dev.toybox.minecraft.client.screens.TranscriptRows.group(snapshot.messages(), null).stream()
            .flatMap(row -> row.messages().stream())
            .anyMatch(m -> m.request() && m.content().equals("Which direction?")),
        "the flagged question stays in the transcript");
  }

  private static void configUrls() throws Exception {
    var c =
        new ChannelConfig(
            java.net.URI.create("https://toy.example/base"), "a/b +c", null, Path.of("config"));
    require(
        c.endpoint().toString().equals("https://toy.example/base/api/v1/channels/a%2Fb%20%2Bc"),
        "API prefix and channel encoding");
    require(c.channelPage().toString().contains("/base/?channels=%5B"), "workspace prefix");
    var directory = Files.createTempDirectory("toy-box-channel-selection");
    try {
      var selected =
          new ChannelConfig(c.server(), "old", null, directory.resolve("connection.json"))
              .select(ChannelConfig.serverUri(" https://toy.example/base/ "), "next");
      var stored = JsonParser.parseString(Files.readString(selected.file())).getAsJsonObject();
      require(
          stored.get("channelId").getAsString().equals("next")
              && stored.get("serverUrl").getAsString().equals("https://toy.example/base"),
          "the selected server and channel are remembered together");
    } finally {
      Files.deleteIfExists(directory.resolve("connection.json"));
      Files.delete(directory);
    }
  }

  private static void audience(State s, String text, Sender sender, boolean ack, String... ids) {
    require(
        ChannelAudience.resolve(s, text, sender, ack).stream()
            .map(Agent::id)
            .toList()
            .equals(List.of(ids)),
        "audience: " + text + " acknowledged=" + ack);
  }

  static JsonObject messageJson(String id, long seq, String sender, String agent, String content) {
    var j = new JsonObject();
    j.addProperty("id", id);
    j.addProperty("sequence", seq);
    j.addProperty("timestamp", "2026-10-03T10:00:00Z");
    var from = new JsonObject();
    from.addProperty("type", sender);
    if (agent != null) from.addProperty("agentId", agent);
    j.add("sender", from);
    j.addProperty("content", content);
    return j;
  }

  static long count(List<ChannelChanges.Change> changes, Class<?> type) {
    return changes.stream().filter(type::isInstance).count();
  }

  static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static void expectUnsupported(Runnable r) {
    try {
      r.run();
      throw new AssertionError("Expected immutable value");
    } catch (UnsupportedOperationException expected) {
    }
  }

  private static void expectInvalid(Runnable r) {
    try {
      r.run();
      throw new AssertionError("Expected invalid wire rejection");
    } catch (IllegalArgumentException | ArithmeticException expected) {
    }
  }
}
