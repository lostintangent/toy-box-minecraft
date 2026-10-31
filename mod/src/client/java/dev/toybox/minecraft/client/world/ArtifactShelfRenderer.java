package dev.toybox.minecraft.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.toybox.minecraft.block.FacingEntityBlock;
import dev.toybox.minecraft.block.ModBlocks;
import dev.toybox.minecraft.block.entity.ArtifactShelfBlockEntity;
import dev.toybox.minecraft.block.entity.StationBlockEntity;
import dev.toybox.minecraft.client.channel.ChannelClient;
import dev.toybox.minecraft.client.channel.ChannelModel.Artifact;
import dev.toybox.minecraft.client.ui.Kit;
import dev.toybox.minecraft.client.ui.TextUtil;
import dev.toybox.minecraft.client.ui.UiStyle;
import dev.toybox.minecraft.client.ui.WorldUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Shared artifact titles on workshop shelves; each label opens the same live file reader. */
public class ArtifactShelfRenderer
    extends StationRenderer<ArtifactShelfBlockEntity, ArtifactShelfRenderer.State> {
  static final float PX_PER_BLOCK = 96f;

  /**
   * The board between the shelves is texel rows 7-8 of the 16 px front: 42..54 at 96 px per block.
   */
  static final int LABEL_Y = 40;

  static final int LABEL_H = 16;
  static final float LIFT = 0.0015f;

  public static class State extends StationRenderState {
    public boolean show;
    public boolean plate;
    public String label = "";
    public String count = "";
    public boolean fresh;
    public boolean plan;
    public int tabColor;
    public int light;
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  /**
   * Position in the shelf section (same facing + binding, same height), counted from the viewer's
   * left.
   */
  public static int rowIndex(Level level, BlockPos pos, BlockState state, String binding) {
    if (!state.hasProperty(FacingEntityBlock.FACING)) {
      return 0;
    }
    Direction facing = state.getValue(FacingEntityBlock.FACING);
    Direction left = facing.getClockWise();
    int k = 0;
    BlockPos p = pos.relative(left);
    while (k < 32) {
      BlockState s = level.getBlockState(p);
      if (!s.is(ModBlocks.ARTIFACT_SHELF) || s.getValue(FacingEntityBlock.FACING) != facing) {
        break;
      }
      String b = level.getBlockEntity(p) instanceof StationBlockEntity be ? be.binding() : "";
      if (!b.equals(binding)) {
        break;
      }
      k++;
      p = p.relative(left);
    }
    return k;
  }

  public static @Nullable Artifact entryAt(int index) {
    var list = ChannelClient.store().artifacts();
    int i = index - 1;
    return i >= 0 && i < list.size() ? list.get(i) : null;
  }

  @Override
  protected void extractStation(ArtifactShelfBlockEntity be, State s, float partialTicks) {
    Level level = be.getLevel();
    s.show = false;
    if (level == null || !ChannelClient.store().hasData()) return;
    s.light = LightCoordsUtil.getLightCoords(level, be.getBlockPos().relative(s.facing));
    int index = rowIndex(level, be.getBlockPos(), be.getBlockState(), s.binding);
    s.fresh = false;
    s.plan = false;
    s.tabColor = UiStyle.CLAY;
    if (index == 0) {
      s.show = true;
      s.plate = true;
      s.label = "Artifacts";
      s.count = String.valueOf(ChannelClient.store().artifacts().size());
      return;
    }
    Artifact artifact = entryAt(index);
    if (artifact != null) {
      s.show = true;
      s.plate = false;
      s.label = artifact.title();
    }
  }

  @Override
  public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
    if (!s.show) {
      return;
    }
    Font font = Minecraft.getInstance().font;
    int light =
        LightCoordsUtil.pack(
            Math.max(LightCoordsUtil.block(s.light), 10), LightCoordsUtil.sky(s.light));
    ps.pushPose();
    toFace(ps, s.facing, -0.004f, PX_PER_BLOCK);
    int x0 = 9;
    int x1 = 87;
    int y = LABEL_Y;
    int ink = UiStyle.color("paper.text");
    // labels are paper, not light: tint the bright kit pill down to cream
    int paper = 0xFFF2ECE1;
    if (s.plate) {
      WorldUi.submitNineSlice(
          ps, c, WorldUi.Layer.SOLID, Kit.PILL, x0, y, x1 - x0, LABEL_H, paper, light);
      String count = s.count;
      int cw = Math.max(10, font.width(count) + 5);
      int cx = x1 - 4 - cw;
      int lx = x0 + 6;
      if (s.fresh) {
        WorldUi.submitSprite(ps, c, Kit.icon("edit"), lx - 1, y + 2, 12, 12, 0xFFFFFFFF, light);
        lx += 12;
      }
      WorldUi.submitText(
          ps,
          c,
          TextUtil.ellipsize(font, s.label.toUpperCase(java.util.Locale.ROOT), cx - 4 - lx),
          lx,
          y + 4,
          ink,
          light);
      ps.pushPose();
      ps.translate(0, 0, -LIFT);
      WorldUi.submitNineSlice(
          ps,
          c,
          WorldUi.Layer.SOLID,
          Kit.progressFill("brass"),
          cx,
          y + 3,
          cw,
          10,
          0xFFFFFFFF,
          light);
      WorldUi.submitText(ps, c, count, cx + (cw - font.width(count) + 1) / 2, y + 4, ink, light);
      ps.popPose();
    } else {
      WorldUi.submitNineSlice(
          ps, c, WorldUi.Layer.SOLID, Kit.PILL, x0, y, x1 - x0, LABEL_H, paper, light);
      int lx = x0 + 9;
      int right = x1 - 5;
      if (s.fresh) {
        WorldUi.submitSprite(ps, c, Kit.icon("edit"), right - 11, y + 2, 12, 12, 0xFFFFFFFF, light);
        right -= 13;
      }
      WorldUi.submitText(
          ps, c, TextUtil.ellipsize(font, s.label, right - lx), lx, y + 4, ink, light);
      ps.pushPose();
      ps.translate(0, 0, -LIFT);
      WorldUi.submitNineSlice(
          ps,
          c,
          WorldUi.Layer.SOLID,
          s.plan ? Kit.progressFill("brass") : Kit.PROGRESS_TRACK,
          x0 + 3,
          y + 3,
          4,
          LABEL_H - 6,
          s.plan ? 0xFFFFFFFF : s.tabColor,
          light);
      ps.popPose();
    }
    ps.popPose();
  }
}
