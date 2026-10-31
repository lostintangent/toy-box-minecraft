package dev.toybox.minecraft.client.agents;

import dev.toybox.minecraft.client.channel.ChannelModel.*;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Current native Channel member presentation, derived by AgentManager on the client thread. */
public final class AgentView {
  public final String id;
  public String name, role = "", state = "idle", activity = "", station = "desk";
  public int nameColor;
  public @Nullable String anchor;
  public @Nullable String wakeAt;
  public @Nullable Long lookingAt, workingOn;
  public boolean stale, awaitingUser;
  Nameplate.@Nullable Data plateCache, compactCache;

  public AgentView(String id) {
    this.id = id;
    this.name = id;
  }

  public void update(Agent a, Presence presence, State snapshot, boolean stale) {
    name = a.name();
    role = a.role() == null ? "" : a.role();
    nameColor = AvatarArt.onDark(AvatarArt.color(a.avatar() == null ? null : a.avatar().color()));
    state = presence.state();
    wakeAt = presence.wakeAt();
    this.stale = stale;
    Status status = state.equals("working") ? a.status() : null;
    lookingAt = status == null ? null : status.lookingAt();
    workingOn = status == null ? null : status.workingOn();
    activity = presence.text() == null ? "" : presence.text();
    if (activity.isBlank())
      activity =
          reference(
              snapshot,
              workingOn != null ? workingOn : lookingAt,
              workingOn != null ? "Working on" : "Reading");
    if (activity.isBlank())
      activity =
          switch (state) {
            case "working" -> "Working";
            case "waiting" -> "Waiting";
            default -> "Idle";
          };
    Message request = snapshot.request();
    awaitingUser =
        request != null && request.sender() != null && id.equals(request.sender().agentId());
  }

  private static String reference(State snapshot, @Nullable Long sequence, String verb) {
    if (sequence == null) return "";
    for (Message m : snapshot.messages())
      if (m.sequence() == sequence) {
        String content = m.content().replaceAll("\\s+", " ").trim();
        if (content.length() > 80) content = content.substring(0, 77) + "…";
        return verb + " #" + sequence + (content.isEmpty() ? "" : ": " + content);
      }
    return verb + " message #" + sequence;
  }

  public String activityLine() {
    if (stale) return "Reconnecting · last known " + state;
    if (wakeAt != null)
      try {
        long seconds =
            Math.max(
                0,
                (Instant.parse(wakeAt).toEpochMilli() - System.currentTimeMillis() + 999) / 1000);
        String when =
            seconds == 0
                ? "wake scheduled"
                : seconds < 60
                    ? "wakes in " + seconds + "s"
                    : "wakes in " + ((seconds + 59) / 60) + "m";
        // Put the clock first so a long waiting reason cannot truncate the scheduled wake.
        return when + " · " + activity;
      } catch (java.time.format.DateTimeParseException ignored) {
      }
    return activity;
  }

  public int plateWeight() {
    return stale
        ? 0
        : needsYou() ? 6 : state.equals("working") ? 4 : state.equals("waiting") ? 3 : 1;
  }

  public boolean needsYou() {
    return !stale && awaitingUser;
  }
}
