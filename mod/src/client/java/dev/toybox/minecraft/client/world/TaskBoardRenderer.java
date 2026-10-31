package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.PanelBlock;
import dev.toybox.minecraft.block.entity.TaskBoardBlockEntity;
import dev.toybox.minecraft.client.agents.AgentSkins;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.DiffStats;
import dev.toybox.minecraft.client.channel.ChannelModel.Task;
import dev.toybox.minecraft.client.channel.ChannelText;
import dev.toybox.minecraft.client.screens.TaskRows;
import dev.toybox.minecraft.client.ui.Kit;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import dev.toybox.minecraft.client.ui.WorldUi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The wall groups public tasks by status while retaining each subtask's place in the tree. */
public final class TaskBoardRenderer
    extends StationRenderer<TaskBoardBlockEntity, TaskBoardRenderer.State> {
  public static final class State extends StationRenderState {
    List<Card> cards = List.of();
    boolean stale;
  }

  private record Card(
      Task task,
      String owner,
      @Nullable Identifier portrait,
      List<String> parents,
      @Nullable DiffStats diff,
      boolean hasArtifact) {}

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  @Override
  protected void extractStation(TaskBoardBlockEntity be, State state, float dt) {
    if (!state.panelOrigin) return;
    state.stale = ChannelClient.store().isStale();
    state.cards = cards();
  }

  private static List<Card> cards() {
    var snapshot = ChannelClient.store().state();
    if (snapshot == null) return List.of();
    List<Card> cards = new ArrayList<>();
    List<String> parents = new ArrayList<>();
    for (var row : TaskRows.flatten(snapshot.tasks())) {
      while (parents.size() > row.depth()) parents.removeLast();
      var task = row.task();
      String ownerId = task.ownerId();
      cards.add(
          new Card(
              task,
              ownerId == null ? "Unassigned" : ChannelText.agentName(ownerId, snapshot),
              ownerId != null && snapshot.agent(ownerId) != null
                  ? AgentSkins.portrait(ownerId)
                  : null,
              List.copyOf(parents),
              TaskRows.diff(task),
              snapshot.artifact(task.artifactId()) != null));
      parents.add(task.title());
    }
    return List.copyOf(cards);
  }

  private static final String[] STATES = {"pending", "in_progress", "blocked", "done"};
  private static final String[] STYLES = {"todo", "doing", "blocked", "done"};

  private record Placed(Card card, int x, int y, int w, int h, int column) {}

  private static List<Placed> layout(List<Card> cards, int width, int height) {
    var placed = new ArrayList<Placed>();
    int cw = (width - 32) / 4;
    for (int col = 0; col < 4; col++) {
      int py = 38;
      for (Card card : cards) {
        if (!card.task.status().equals(STATES[col])) continue;
        int ch = card.parents.isEmpty() ? 62 : 74;
        if (py + ch > height - 16) break;
        placed.add(new Placed(card, 16 + col * cw, py, cw - 5, ch, col));
        py += ch + 5;
      }
    }
    return placed;
  }

  public static @Nullable Task taskAt(BlockHitResult hit) {
    var level = Minecraft.getInstance().level;
    if (level == null) return null;
    var state = level.getBlockState(hit.getBlockPos());
    var facing = state.getValue(PanelBlock.FACING);
    if (hit.getDirection() != facing) return null;
    var origin = PanelBlock.origin(level, hit.getBlockPos(), state);
    int[] size = PanelBlock.extent(level, origin, level.getBlockState(origin));
    var right = facing.getCounterClockWise();
    var delta = hit.getLocation().subtract(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
    double x = (delta.x * right.getStepX() + delta.z * right.getStepZ() + .5) * 96;
    double y = (size[1] - delta.y) * 96;
    for (Placed p : layout(cards(), size[0] * 96, size[1] * 96))
      if (x >= p.x && x < p.x + p.w && y >= p.y && y < p.y + p.h) return p.card.task;
    return null;
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.panelOrigin) return;
    int width = s.panelWidth * 96, height = s.panelHeight * 96, light = WorldUi.uiLight();
    var font = Minecraft.getInstance().font;
    ps.pushPose();
    toFace(ps, s.facing, 14f / 16f - 0.003f, 96);
    ps.translate(0, -(s.panelHeight - 1) * 96, 0);
    var placed = layout(s.cards, width, height);
    int cw = (width - 32) / 4;
    for (int col = 0; col < 4; col++) {
      String status = STATES[col];
      long count = s.cards.stream().filter(card -> card.task.status().equals(status)).count();
      int column = col;
      var shown = placed.stream().filter(p -> p.column == column).toList();
      int px = 16 + col * cw;
      WorldUi.submitText(
          ps, c, TaskRows.label(status) + "  " + count, px + 2, 21, UiStyle.INK, light);
      if (shown.size() < count) {
        int py = shown.isEmpty() ? 38 : shown.getLast().y + shown.getLast().h + 5;
        WorldUi.submitText(
            ps,
            c,
            "+" + (count - shown.size()) + " · open task list",
            px + 3,
            py,
            UiStyle.INK,
            light);
      }
    }
    for (Placed p : placed) {
      Card card = p.card;
      int px = p.x, py = p.y, ch = p.h;
      boolean subtask = !card.parents.isEmpty();
      WorldUi.submitNineSlice(
          ps,
          c,
          WorldUi.Layer.SOLID,
          Kit.card(STYLES[p.column]),
          px,
          py,
          cw - 5,
          ch,
          0xffffffff,
          light);
      ps.pushPose();
      ps.translate(0, 0, -0.001f);
      int contentWidth = cw - 17;
      int ly = py + 5;
      if (subtask) {
        WorldUi.submitText(
            ps,
            c,
            parentContext(font, card.parents, contentWidth),
            px + 5,
            ly,
            UiStyle.color("paper.muted"),
            light);
        ly += 12;
      }
      var lines = TextUtil.wrapPlain(font, card.task.title(), contentWidth);
      for (int j = 0; j < Math.min(3, lines.size()); j++) {
        String line =
            j == 2 && lines.size() > 3
                ? TextUtil.ellipsize(
                    font, String.join(" ", lines.subList(j, lines.size())), contentWidth)
                : lines.get(j);
        WorldUi.submitText(ps, c, line, px + 5, ly, UiStyle.INK, light);
        ly += 10;
      }
      // Footer: the owner reads from the left; work and outcome from the right, as in Toy Box.
      int ownerX = px + 5, ownerY = py + ch - 22, right = px + cw - 11;
      if (card.diff != null) {
        String added = TaskRows.added(card.diff), removed = TaskRows.removed(card.diff);
        right -= font.width(removed);
        WorldUi.submitText(ps, c, removed, right, ownerY + 4, TaskRows.removedColor(), light);
        right -= font.width(added) + 3;
        WorldUi.submitText(ps, c, added, right, ownerY + 4, TaskRows.addedColor(), light);
        right -= 5;
      }
      if (card.hasArtifact) {
        right -= 12;
        WorldUi.submitSprite(
            ps, c, Kit.icon("read"), right, ownerY + 2, 12, 12, 0xFFFFFFFF, light);
        right -= 4;
      }
      if (card.portrait != null) {
        portrait(ps, c, card.portrait, ownerX, ownerY, 16, light);
        ownerX += 20;
      }
      WorldUi.submitText(
          ps,
          c,
          TextUtil.ellipsize(font, card.owner, right - ownerX),
          ownerX,
          ownerY + 4,
          UiStyle.color("paper.muted"),
          light);
      ps.popPose();
    }
    if (s.stale)
      WorldUi.submitText(
          ps, c, "Offline · last known tasks", 16, height - 12, UiStyle.CLAY_DARK, light);
    ps.popPose();
  }

  /** Keep the immediate parent visible even when a deep breadcrumb cannot fit on the card. */
  private static String parentContext(Font font, List<String> parents, int width) {
    String full = "Under: " + String.join(" / ", parents);
    if (font.width(full) <= width) return full;
    return TextUtil.ellipsize(
        font, "Under: " + (parents.size() > 1 ? "… / " : "") + parents.getLast(), width);
  }

  /** Runtime portraits are complete 64px textures, not cropped GUI-atlas sprites. */
  private static void portrait(
      PoseStack ps,
      SubmitNodeCollector collector,
      Identifier texture,
      float x,
      float y,
      float size,
      int light) {
    collector
        .order(1)
        .submitCustomGeometry(
            ps,
            RenderTypes.textPolygonOffset(texture),
            (pose, vertices) ->
                WorldUi.quad(
                    pose, vertices, x, y, x + size, y + size, 0f, 0f, 1f, 1f, 0xffffffff, light));
  }
}
