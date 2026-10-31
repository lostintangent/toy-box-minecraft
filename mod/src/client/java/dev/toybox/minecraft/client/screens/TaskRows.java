package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.ChannelModel.DiffStats;
import dev.toybox.minecraft.client.channel.ChannelModel.Task;
import dev.toybox.minecraft.client.ui.UiStyle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Task presentation shared by the Tasks tab and the task board. Layout addresses are read-only;
 * they never become task identities or mutation targets.
 */
public final class TaskRows {
  private TaskRows() {}

  public record Row(String address, int depth, Task task) {}

  public static List<Row> flatten(List<Task> tasks) {
    List<Row> rows = new ArrayList<>();
    walk(tasks, "", 0, rows);
    return List.copyOf(rows);
  }

  private static void walk(List<Task> tasks, String parent, int depth, List<Row> rows) {
    for (int i = 0; i < tasks.size(); i++) {
      String address = parent + (i + 1);
      Task task = tasks.get(i);
      rows.add(new Row(address, depth, task));
      walk(task.children(), address + ".", depth + 1, rows);
    }
  }

  public static String label(String status) {
    return switch (status) {
      case "in_progress" -> "In progress";
      case "blocked" -> "Blocked";
      case "done" -> "Done";
      default -> "Pending";
    };
  }

  public static String dot(String status) {
    return switch (status) {
      case "in_progress" -> "working";
      case "blocked" -> "waiting";
      case "done" -> "done";
      default -> "idle";
    };
  }

  /** As Toy Box's checklists show it: own and subtask work, omitted while a task is pending. */
  public static DiffStats diff(Task task) {
    return task.status().equals("pending") ? null : DiffStats.total(List.of(task));
  }

  public static String added(DiffStats diff) {
    return "+" + count(diff.added());
  }

  public static String removed(DiffStats diff) {
    return "−" + count(diff.removed());
  }

  /** The Tasks tab and Task Wall cards are both paper surfaces. */
  public static int addedColor() {
    return UiStyle.color("paper.add_fg");
  }

  public static int removedColor() {
    return UiStyle.color("paper.del_fg");
  }

  /** Compact counts rounded like the web's: 999, 1K, 1.5K, 12.3K, 1M (999,950 rounds up to 1M). */
  public static String count(long value) {
    if (value < 1000) return Long.toString(value);
    int scale = value < 999_950 ? 3 : 6;
    var compact = BigDecimal.valueOf(value, scale).setScale(1, RoundingMode.HALF_UP);
    return compact.stripTrailingZeros().toPlainString() + (scale == 3 ? "K" : "M");
  }
}
