package dev.toybox.minecraft.client.screens;

import dev.toybox.minecraft.client.channel.*;
import dev.toybox.minecraft.client.channel.ChannelModel.State;
import dev.toybox.minecraft.client.channel.ChannelModel.Task;
import dev.toybox.minecraft.client.screens.artifacts.ArtifactScreen;
import dev.toybox.minecraft.client.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

public final class TasksScreen extends ChannelScreen {
  /** A task and its actions; the widgets are rebuilt whenever the task list changes. */
  private record Row(TaskRows.Row row, KitButton discuss, @Nullable KitButton artifact) {}

  private List<Task> tasks = List.of();
  private List<Row> rows = List.of();

  public TasksScreen() {
    super(Page.TASKS, "Channel tasks");
  }

  @Override
  protected void init() {
    super.init();
    var snapshot = ChannelClient.store().state();
    tasks = snapshot == null ? List.of() : snapshot.tasks();
    rows =
        TaskRows.flatten(tasks).stream()
            .map(row -> new Row(row, discuss(row.task()), artifact(row.task(), snapshot)))
            .toList();
  }

  @Override
  public void tick() {
    var snapshot = ChannelClient.store().state();
    if (!(snapshot == null ? List.of() : snapshot.tasks()).equals(tasks)) rebuildWidgets();
  }

  private KitButton discuss(Task task) {
    return button(
        "Discuss", 62, () -> Screens.openTranscript("About “" + task.title() + "”: "));
  }

  /** A task's own artifact, named so the association reads without opening it. */
  private @Nullable KitButton artifact(Task task, State snapshot) {
    var artifact = snapshot.artifact(task.artifactId());
    if (artifact == null) return null;
    return button(
        artifact.title(),
        Math.min(240, font.width(artifact.title()) + 16),
        () -> Minecraft.getInstance().gui.setScreen(new ArtifactScreen(artifact.file())));
  }

  @Override
  protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float dt) {
    int py = bodyY - (int) scroll;
    var snapshot = ChannelClient.store().state();
    g.enableScissor(x + 10, bodyY, x + w - 10, bodyY + bodyH);
    if (rows.isEmpty())
      py =
          text(
              g,
              "The lead has not published tasks yet.",
              x + 16,
              py + 10,
              w - 32,
              UiStyle.color("paper.muted"));
    for (var row : rows) {
      int indent = Math.min(row.row().depth(), 8) * 12, px = x + 16 + indent;
      var task = row.row().task();
      Panels.dot(g, TaskRows.dot(task.status()), px, py + 2, false);
      int bottom = text(g, task.title(), px + 12, py, w - 48 - indent, UiStyle.color("paper.text"));
      String owner =
          task.ownerId() == null ? "Unassigned" : ChannelText.agentName(task.ownerId(), snapshot);
      String meta = TaskRows.label(task.status()) + " · " + owner;
      g.text(font, meta, px + 12, bottom + 2, UiStyle.color("paper.muted"), false);
      var diff = TaskRows.diff(task);
      if (diff != null) {
        int dx = px + 12 + font.width(meta) + 8;
        String added = TaskRows.added(diff);
        g.text(font, added, dx, bottom + 2, TaskRows.addedColor(), false);
        g.text(
            font,
            TaskRows.removed(diff),
            dx + font.width(added) + 4,
            bottom + 2,
            TaskRows.removedColor(),
            false);
      }
      int by = bottom + 15;
      boolean shown = by >= bodyY && by + 20 < bodyY + bodyH;
      row.discuss().setPosition(px + 12, by);
      row.discuss().active = !ChannelClient.store().isStale();
      row.discuss().visible = shown;
      if (row.artifact() != null) {
        row.artifact().setPosition(px + 12 + row.discuss().getWidth() + 4, by);
        row.artifact().visible = shown;
      }
      py = by + 30;
    }
    contentHeight = py - bodyY + (int) scroll;
    g.disableScissor();
    clampScroll();
  }
}
