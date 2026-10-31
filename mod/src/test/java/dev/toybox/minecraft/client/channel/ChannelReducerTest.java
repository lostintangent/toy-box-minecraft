package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelContractTest.require;
import static dev.toybox.minecraft.client.channel.ChannelModel.*;

import com.google.gson.JsonParser;
import java.util.List;

/** Canonical public transitions; transport recovery is covered by the client contract. */
final class ChannelReducerTest {
  static void run() {
    orderedMessagesAndReactions();
    membersStatusesAndSystems();
  }

  private static State initial() {
    return ChannelCodec.decode(ChannelContractTest.fixture());
  }

  private static void orderedMessagesAndReactions() {
    State start = initial();
    var message =
        ChannelCodec.message(ChannelContractTest.messageJson("new", 3, "agent", "a", "Hello"));
    require(
        ChannelReducer.apply(start, new ChannelEvent.Message(3, message)) == start,
        "old revision returns exact same detail");
    State appended = ChannelReducer.apply(start, new ChannelEvent.Message(4, message));
    require(
        appended.messages().size() == 3 && appended.messages().getLast().id().equals("new"),
        "message event appends ordered transcript");
    var attachment = ChannelContractTest.messageJson("image", 4, "agent", "a", "Screenshot");
    attachment.add(
        "attachments",
        JsonParser.parseString(
            "[{\"name\":\"shot.png\",\"url\":\"/api/serve/machine/shot.png\"}]"));

    var resourceEvent = new com.google.gson.JsonObject();
    resourceEvent.addProperty("type", "message");
    resourceEvent.addProperty("revision", 5);
    resourceEvent.add("message", attachment);
    State image = ChannelReducer.apply(appended, ChannelCodec.event(resourceEvent));
    require(
        image
            .messages()
            .getLast()
            .attachments()
            .getFirst()
            .url()
            .equals("/api/serve/machine/shot.png"),
        "live message delta retains resource URLs without a replacement snapshot");
    State reacted =
        ChannelReducer.apply(
            appended,
            event(
                "{\"type\":\"reaction\",\"revision\":5,\"sequence\":1,\"agentId\":\"b\",\"reaction\":\"celebrate\"}"));
    require(
        reacted.messages().getFirst().reactions().size() == 2, "reaction changes its actor only");
    State cleared =
        ChannelReducer.apply(
            reacted,
            event(
                "{\"type\":\"reaction\",\"revision\":6,\"sequence\":1,\"agentId\":\"a\",\"reaction\":null}"));
    require(
        cleared.messages().getFirst().reactions().equals(List.of(new Reaction("b", "celebrate"))),
        "reaction removal preserves other actor");
    State system =
        ChannelReducer.apply(
            cleared,
            event(
                "{\"type\":\"reaction\",\"revision\":7,\"sequence\":2,\"agentId\":\"a\",\"reaction\":\"love\"}"));
    require(
        system.messages().get(1).reactions().isEmpty(), "system messages cannot receive reactions");
    var updated =
        ChannelCodec.message(ChannelContractTest.messageJson("new", 3, "agent", "a", "Updated"));
    State upserted = ChannelReducer.apply(system, new ChannelEvent.Message(8, updated));
    require(
        upserted.messages().size() == 3
            && upserted.messages().getLast().content().equals("Updated"),
        "same message ID replaces rather than duplicates");
    require(
        ChannelReducer.apply(upserted, new ChannelEvent.Message(8, updated)) == upserted,
        "duplicate revision is idempotent");
  }

  private static void membersStatusesAndSystems() {
    State state = initial();
    state =
        ChannelReducer.apply(
            state,
            event(
                "{\"type\":\"member\",\"revision\":4,\"member\":{\"id\":\"a\",\"name\":\"Updated"
                    + " artist\",\"role\":\"Review\"}}"));
    require(
        state.members().size() == 2 && state.members().getLast().name().equals("Updated artist"),
        "member replacement preserves canonical name order");
    state =
        ChannelReducer.apply(
            state,
            event(
                "{\"type\":\"status\",\"revision\":5,\"agentId\":\"channel-native\",\"status\":{\"state\":\"working\",\"text\":\"Building\",\"workingOn\":3}}"));
    require(
        state.lead().status().workingOn() == 3 && state.lead().avatar() != null,
        "lead status updates preserve identity");
    state =
        ChannelReducer.apply(
            state, event("{\"type\":\"status\",\"revision\":6,\"agentId\":\"channel-native\"}"));
    require(state.lead().status() == null, "omitted status clears previous status");
    state =
        ChannelReducer.apply(
            state,
            event(
                "{\"type\":\"status\",\"revision\":7,\"agentId\":\"a\",\"status\":{\"state\":\"waiting\",\"text\":\"Later\",\"wakeAt\":\"2026-10-04T09:00:00Z\"}}"));
    require(
        state.members().getLast().status().wakeAt().equals("2026-10-04T09:00:00Z"),
        "member status wait target retained");
    state = system(state, "member_joined", "\"member\":{\"id\":\"c\",\"name\":\"Writer\"}");
    require(state.members().size() == 3, "member join system payload updates roster");
    state = system(state, "member_left", "\"member\":{\"id\":\"b\",\"name\":\"Engineer\"}");
    require(
        state.members().stream().noneMatch(a -> a.id().equals("b")),
        "member leave system payload updates roster");
    String when = state.artifacts().getFirst().sharedAt();
    state =
        system(
            state,
            "artifact_shared",
            "\"actor\":{\"type\":\"user\"},\"artifact\":{\"file\":{\"kind\":\"session\",\"sessionId\":\"artifact-session\",\"path\":\"result.toy\"},\"title\":\"New"
                + " title\"}");
    require(
        state.artifacts().size() == 1
            && state.artifacts().getFirst().title().equals("New title")
            && state.artifacts().getFirst().sharedAt().equals(when),
        "artifact re-share retitles without changing original shared time");
    state =
        system(
            state,
            "artifact_shared",
            "\"actor\":{\"type\":\"user\"},\"artifact\":{\"file\":{\"kind\":\"machine\",\"path\":\"/new.md\"},\"title\":\"New"
                + " file\",\"url\":\"/api/serve/machine/new.md\"}");
    require(
        state.artifacts().size() == 2
            && state.artifacts().getLast().sharedAt().equals("2026-10-03T12:00:00Z"),
        "new artifact takes system message timestamp");
    require(
        state.artifacts().getLast().url().equals("/api/serve/machine/new.md"),
        "live artifact system delta retains resource URL without a replacement snapshot");
    state = system(state, "routine_scheduled", routine("other", "New routine"));
    state = system(state, "routine_edited", routine("daily", "Changed daily"));
    require(
        state.routines().getFirst().title().equals("Changed daily")
            && state.routines().getLast().id().equals("other"),
        "routine edit preserves original order");
    state = system(state, "routine_deleted", routine("other", "New routine"));
    require(state.routines().size() == 1, "routine deletion removes only matching identity");
  }

  private static ChannelEvent event(String json) {
    return ChannelCodec.event(JsonParser.parseString(json).getAsJsonObject());
  }

  private static State system(State state, String type, String payload) {
    long rev = state.revision() + 1;
    var message =
        ChannelContractTest.messageJson(
            "system-" + rev, state.messages().getLast().sequence() + 1, "system", null, "");
    message.addProperty("timestamp", "2026-10-03T12:00:00Z");
    message.add("content", JsonParser.parseString("{\"type\":\"" + type + "\"," + payload + "}"));
    return ChannelReducer.apply(
        state, new ChannelEvent.Message(rev, ChannelCodec.message(message)));
  }

  private static String routine(String id, String title) {
    return "\"routine\":{\"id\":\""
        + id
        + "\",\"title\":\""
        + title
        + "\",\"schedule\":\"0 9 * * *\",\"prompt\":\"Check\"}";
  }
}
