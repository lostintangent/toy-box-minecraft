package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.ChannelModel.*;
import dev.toybox.minecraft.client.channel.ChannelText;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The browser's transcript grouping: agent runs and matching system messages, bounded by day/read
 * position.
 */
public final class TranscriptRows {
  private TranscriptRows() {}

  public record Row(List<Message> messages, boolean startsDay, boolean startsUnread) {
    public Row {
      messages = List.copyOf(messages);
    }
  }

  public static List<Row> group(List<Message> messages, Long unreadAfter) {
    List<Row> rows = new ArrayList<>();
    for (Message message : messages) {
      Row row = rows.isEmpty() ? null : rows.getLast();
      Message previous = row == null ? null : row.messages().getLast();
      boolean startsDay = previous == null || !day(previous).equals(day(message));
      boolean startsUnread =
          previous != null
              && unreadAfter != null
              && previous.sequence() <= unreadAfter
              && message.sequence() > unreadAfter;
      if (row == null
          || startsDay
          || startsUnread
          || !continues(row.messages().getFirst(), previous, message)) {
        rows.add(new Row(List.of(message), startsDay, startsUnread));
      } else {
        var run = new ArrayList<>(row.messages());
        run.add(message);
        rows.set(rows.size() - 1, new Row(run, row.startsDay(), row.startsUnread()));
      }
    }
    return List.copyOf(rows);
  }

  public static java.time.LocalDate day(Message message) {
    return Instant.parse(message.timestamp()).atZone(ZoneId.systemDefault()).toLocalDate();
  }

  private static boolean continues(Message first, Message previous, Message next) {
    if (first.isSystem() && next.isSystem()) {
      String group = systemGroup(first);
      return group != null && group.equals(systemGroup(next));
    }
    return first.sender().type().equals("agent")
        && !next.isSystem()
        && first.sender().equals(next.sender())
        && Instant.parse(next.timestamp()).toEpochMilli()
                - Instant.parse(previous.timestamp()).toEpochMilli()
            <= 600_000;
  }

  private static String systemGroup(Message message) {
    var content = message.system();
    return switch (content.type()) {
      case "member_joined", "member_left" -> content.type();
      case "artifact_shared" ->
          content.actor() != null && content.actor().type().equals("agent")
              ? "artifact_shared:" + content.actor().agentId()
              : null;
      default -> null;
    };
  }

  public static String systemText(Row row, State snapshot) {
    Message first = row.messages().getFirst();
    if (row.messages().size() == 1) return ChannelText.message(first, snapshot);
    if (first.system().type().equals("artifact_shared"))
      return ChannelText.sender(first.system().actor(), snapshot)
          + " shared "
          + row.messages().size()
          + " artifacts: "
          + String.join(
              ", ", row.messages().stream().map(m -> m.system().artifact().title()).toList());
    var names = row.messages().stream().map(m -> m.system().member().name()).toList();
    String team =
        names.size() == 2
            ? names.getFirst() + " and " + names.getLast()
            : names.get(0)
                + ", "
                + names.get(1)
                + " and "
                + (names.size() == 3 ? names.get(2) : (names.size() - 2) + " others");
    return team
        + (first.system().type().equals("member_left")
            ? " left the channel"
            : " joined the channel");
  }
}
