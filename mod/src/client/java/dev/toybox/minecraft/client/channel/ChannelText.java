package dev.toybox.minecraft.client.channel;

import static dev.toybox.minecraft.client.channel.ChannelModel.*;

/** Plain-language public transcript labels, shared by 2D and world surfaces. */
public final class ChannelText {
  private ChannelText() {}

  public static String agentName(String id, State snapshot) {
    Agent agent = snapshot == null ? null : snapshot.agent(id);
    return agent == null ? "Deleted agent" : agent.name();
  }

  public static String sender(Sender sender, State snapshot) {
    return switch (sender.type()) {
      case "user" -> "You";
      case "agent" -> agentName(sender.agentId(), snapshot);
      default -> "Channel";
    };
  }

  public static String message(Message message, State snapshot) {
    if (!message.isSystem())
      return message.content().isBlank() && !message.attachments().isEmpty()
          ? "Shared an attachment"
          : message.content();
    SystemMessage system = message.system();
    String actor = system.actor() == null ? "Channel" : sender(system.actor(), snapshot);
    return switch (system.type()) {
      case "tasks_completed" -> "All channel tasks completed";
      case "member_joined" -> system.member().name() + " joined the channel";
      case "member_left" -> system.member().name() + " left the channel";
      case "channel_renamed" -> actor + " renamed the channel to " + system.name();
      case "channel_purpose_changed" ->
          actor
              + (system.purpose() == null
                  ? " cleared the channel purpose"
                  : " changed the channel purpose: " + system.purpose());
      case "channel_directory_changed" ->
          actor
              + (system.directory() == null
                  ? " cleared the working directory"
                  : " changed the working directory: " + system.directory());
      case "preview_changed" ->
          actor
              + (system.previewUrl() == null
                  ? " cleared the preview"
                  : " changed the preview: " + system.previewUrl());
      case "artifact_shared" -> actor + " shared " + system.artifact().title();
      case "routine_scheduled" -> "Routine scheduled: " + system.routine().title();
      case "routine_edited" -> "Routine updated: " + system.routine().title();
      case "routine_deleted" -> "Routine removed: " + system.routine().title();
      default -> "Channel updated (" + system.type() + ")";
    };
  }

  public static String reaction(String reaction) {
    return switch (reaction) {
      case "done" -> "Done";
      case "agree" -> "Agree";
      case "celebrate" -> "Celebrate";
      case "love" -> "Love";
      case "laugh" -> "Laugh";
      default -> reaction;
    };
  }
}
